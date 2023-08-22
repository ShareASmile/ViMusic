package it.vfsfitvnm.vimusic.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.scheduler.Requirements
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import it.vfsfitvnm.innertube.requests.player
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.models.Format
import it.vfsfitvnm.vimusic.models.SongDownloadInfo
import it.vfsfitvnm.vimusic.query
import it.vfsfitvnm.vimusic.utils.downloadFavouritesKey
import it.vfsfitvnm.vimusic.utils.findNextMediaItemById
import it.vfsfitvnm.vimusic.utils.preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executor

object Downloader {
    private var currentCount: Int = 0
    private const val channelId = "download"
    private const val notificationId = 7123
    private var initialized = false
    lateinit var cacheDatabase: StandaloneDatabaseProvider
    private lateinit var downloadManager: DownloadManager
    private lateinit var cache: Cache
    private lateinit var player: ExoPlayer

    @SuppressLint("StaticFieldLeak")
    private lateinit var builder: NotificationCompat.Builder

    fun getCacheDatabase(context: Context): StandaloneDatabaseProvider {
        if (!::cacheDatabase.isInitialized) {
            cacheDatabase = StandaloneDatabaseProvider(context.applicationContext)
        }
        return cacheDatabase
    }

    fun initDownloadManager(context: Context, cache: Cache, player: ExoPlayer) {
        this.cache = cache
        this.player = player
        val dataSourceFactory = DefaultHttpDataSource.Factory()
        val downloadExecutor = Executor(Runnable::run)
        downloadManager = DownloadManager(
            context.applicationContext,
            cacheDatabase,
            cache,
            dataSourceFactory,
            downloadExecutor
        )
        val requirements = Requirements(Requirements.NETWORK)
        downloadManager.requirements = requirements
        downloadManager.maxParallelDownloads = 100
        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                super.onDownloadChanged(downloadManager, download, finalException)
                Log.d("cache", "download ${download.request.id} ${download.state}")
            }
        })
    }

    @Synchronized
    suspend fun checkPlaylistDownloads() {
        Log.d("cache", "checking playlists")
        val songDownloadInfo = Database.songDownloadInfo()
        if (!::cache.isInitialized || !::player.isInitialized) return
        songDownloadInfo.forEach {
            downloadSong(it)
        }
    }

    @Synchronized
    suspend fun checkFavouritesDownloads(context: Context) {
        Log.d("cache", "checking favourites")
        val shouldDownload = context.preferences.getBoolean(downloadFavouritesKey, false)
        if(!shouldDownload) return
        val songDownloadInfo = Database.favouritesDownloadInfo()
        Log.d("cache", "favourites $songDownloadInfo")
        if (!::cache.isInitialized || !::player.isInitialized) return
        songDownloadInfo.forEach {
            downloadSong(it)
        }
    }

    private suspend fun downloadSong(song: SongDownloadInfo) {
        Log.d("cache", "checking ${song.songId}")
        val length = song.contentLength
        if (length == null || !cache.isCached(
                song.songId,
                0,
                length
            )
        ) {
            Innertube.player(PlayerBody(videoId = song.songId))?.map { body ->
                val uri = if (body.playabilityStatus?.status == "OK") {
                    body.streamingData?.highestQualityFormat?.let { format ->
                        val mediaItem =
                            runBlocking(Dispatchers.Main) {
                                player.findNextMediaItemById(
                                    song.songId
                                )
                            }

                        if (mediaItem?.mediaMetadata?.extras?.getString(
                                "durationText"
                            ) == null
                        ) {
                            format.approxDurationMs?.div(
                                1000
                            )
                                ?.let(DateUtils::formatElapsedTime)
                                ?.removePrefix("0")
                                ?.let { durationText ->
                                    mediaItem?.mediaMetadata?.extras?.putString(
                                        "durationText",
                                        durationText
                                    )
                                    Database.updateDurationText(
                                        song.songId,
                                        durationText
                                    )
                                }
                        }
                        query {
                            mediaItem?.let(Database::insert)
                            Database.insert(
                                Format(
                                    songId = song.songId,
                                    itag = format.itag,
                                    mimeType = format.mimeType,
                                    bitrate = format.bitrate,
                                    loudnessDb = body.playerConfig?.audioConfig?.normalizedLoudnessDb,
                                    contentLength = format.contentLength,
                                    lastModified = format.lastModified
                                )
                            )
                        }

                        format.url
                    } ?: return
                } else return
                val downloadRequest =
                    DownloadRequest.Builder(song.songId, Uri.parse(uri))
                        .setCustomCacheKey(song.songId)
                        .build()
                Log.d("cache", "downloading ${song.songId}")
                downloadManager.addDownload(downloadRequest)
                downloadManager.resumeDownloads()
            }
        }
    }

    private fun removeNotification(context: Context) {
        NotificationManagerCompat.from(context).apply {
            builder.setContentText("Download complete").setProgress(0, 0, false)
            notify(notificationId, builder.build())
        }
    }

    private fun registerChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("cache", "registering channel")
            val channel = NotificationChannel(
                channelId,
                "ViMusic pre-cache",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Pre-cache download progress"
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

    }

    private fun createNotification(context: Context) {
        if (!initialized) registerChannel(context)
        builder = NotificationCompat.Builder(context, "download")
            .setContentTitle("Pre-caching songs")
            .setContentText("Download in progress")
            .setSmallIcon(R.drawable.baseline_download_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        val progressMax = 100
        val progressCurrent = 0
        NotificationManagerCompat.from(context).apply {
            builder.setProgress(progressMax, progressCurrent, false)
            notify(notificationId, builder.build())
        }
    }
}