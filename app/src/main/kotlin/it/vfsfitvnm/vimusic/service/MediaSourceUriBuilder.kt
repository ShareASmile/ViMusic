package it.vfsfitvnm.vimusic.service

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.models.Format
import it.vfsfitvnm.vimusic.query
import it.vfsfitvnm.youtubemusic.YouTube

suspend fun BuildMediaUrl(mediaItem: MediaItem): Result<Uri> {
    val urlResult = YouTube.player(mediaItem.mediaId)
        .mapCatching { body ->
            when (val status = body.playabilityStatus.status) {
                "OK" -> body.streamingData?.adaptiveFormats?.findLast { format ->
                    format.itag == 251 || format.itag == 140
                }?.let { format ->
                    query {
                        mediaItem.let(Database::insert)

                        Database.insert(
                            Format(
                                songId = mediaItem.mediaId,
                                itag = format.itag,
                                mimeType = format.mimeType,
                                bitrate = format.bitrate,
                                loudnessDb = body.playerConfig?.audioConfig?.loudnessDb?.toFloat(),
                                contentLength = format.contentLength,
                                lastModified = format.lastModified,
                                isDownloaded = false
                            )
                        )
                    }

                    val uri = format.url?.toUri()

                    uri
                } ?: throw PlayableFormatNotFoundException()
                "UNPLAYABLE" -> throw UnplayableException()
                "LOGIN_REQUIRED" -> throw LoginRequiredException()
                else -> throw PlaybackException(
                    status,
                    null,
                    PlaybackException.ERROR_CODE_REMOTE_ERROR
                )
            }
        }
    return urlResult
}