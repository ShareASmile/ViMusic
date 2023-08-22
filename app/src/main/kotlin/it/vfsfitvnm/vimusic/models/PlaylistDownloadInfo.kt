package it.vfsfitvnm.vimusic.models

import androidx.room.DatabaseView


data class SongDownloadInfo (
    val songId: String,
    val contentLength: Long?
)