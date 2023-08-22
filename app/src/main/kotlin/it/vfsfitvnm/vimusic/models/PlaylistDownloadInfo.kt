package it.vfsfitvnm.vimusic.models

import androidx.room.DatabaseView

@DatabaseView(
    """
        SELECT SongPlaylistMap.songId as songId, MAX(contentLength) as contentLength FROM SongPlaylistMap
        LEFT JOIN Format on SongPlaylistMap.songId = Format.songId
        JOIN Playlist on playlistId = id 
        WHERE download = 1
        GROUP BY SongPlaylistMap.songId
    """
)
data class PlaylistDownloadInfo(
    override val songId: String,
    override val contentLength: Long?,
) : SongDownloadInfo

@DatabaseView(
    """
        SELECT songId, contentLength FROM Song JOIN Format WHERE likedAt IS NOT NULL
    """
)
data class FavouritesDownloadInfo(
    override val songId: String,
    override val contentLength: Long?,
) : SongDownloadInfo

interface SongDownloadInfo {
    val songId: String
    val contentLength: Long?
}