package it.vfsfitvnm.vimusic.utils

import android.content.Context
import android.text.format.DateUtils
import android.util.Log
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSpec.HTTP_METHOD_GET
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.exoplayer.ExoPlayer
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.PlayerResponse
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import it.vfsfitvnm.innertube.requests.player
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.models.Format
import it.vfsfitvnm.vimusic.models.Song
import it.vfsfitvnm.vimusic.query
import it.vfsfitvnm.vimusic.service.Downloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

fun processVideoRequest(
    body: PlayerResponse,
    player: ExoPlayer,
    cache: CacheDataSource,
    songId: String
) {
    val uri = if (body.playabilityStatus?.status == "OK") {
        body.streamingData?.highestQualityFormat?.let { format ->
            val mediaItem =
                runBlocking(Dispatchers.Main) {
                    player.findNextMediaItemById(
                        songId
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
                            songId,
                            durationText
                        )
                    }
            }
            query {
                mediaItem?.let(Database::insert)
                Database.insert(
                    Format(
                        songId = songId,
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
    val data = DataSpec.Builder()
        .setKey(songId)
        .setHttpMethod(HTTP_METHOD_GET)
        .setPosition(0)
        .setUri(uri)
        .build()
    CacheWriter(
        cache,
        data,
        null,
        null
    ).cache()
}

class ParallelDistributor<T>(
    val items: List<T>,
    val poolSize: Int = 10,
    val worker: suspend (T) -> Unit
) {
    var index = poolSize

    private suspend fun jobWrapper(jobIndex: Int) {
        var currentIndex = jobIndex
        while (currentIndex < items.size) {
            worker(items[currentIndex])
            currentIndex = getNextJob()
        }
    }

    suspend fun start() {
        coroutineScope {
            for (i in 0 until poolSize) {
                launch { jobWrapper(i) }
            }
        }
    }

    @Synchronized
    fun getNextJob(): Int {
        return index++
    }
}

suspend fun downloadParallel(
    context: Context,
    songs: List<Song>,
    player: ExoPlayer,
    dataSource: CacheDataSource
) {
    withContext(Dispatchers.Main) {
        Downloader.addItem(context, songs.size)
    }
    val pool = ParallelDistributor(songs, 10) { song ->
        Innertube.player(PlayerBody(videoId = song.id))
            ?.map { body ->
                Log.d("cache", "starting ${song.id}")
                processVideoRequest(
                    body,
                    player,
                    dataSource,
                    song.id
                )
            }
        dataSource.close()
        withContext(Dispatchers.Main) {
            Downloader.removeItem(context)
        }
        Log.d("cache", "finished downloading ${song.id}")
    }
    pool.start()
}