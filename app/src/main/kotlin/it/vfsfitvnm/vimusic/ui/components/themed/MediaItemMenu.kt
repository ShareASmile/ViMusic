package it.vfsfitvnm.vimusic.ui.components.themed


import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.with
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import it.vfsfitvnm.route.RouteHandler
import it.vfsfitvnm.route.empty
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.download.MediaDownloadService
import it.vfsfitvnm.vimusic.enums.PlaylistSortBy
import it.vfsfitvnm.vimusic.enums.SortOrder
import it.vfsfitvnm.vimusic.models.DetailedSong
import it.vfsfitvnm.vimusic.models.Playlist
import it.vfsfitvnm.vimusic.models.SongPlaylistMap
import it.vfsfitvnm.vimusic.query
import it.vfsfitvnm.vimusic.service.BuildMediaUrl
import it.vfsfitvnm.vimusic.transaction
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.screens.albumRoute
import it.vfsfitvnm.vimusic.ui.screens.artistRoute
import it.vfsfitvnm.vimusic.ui.screens.viewPlaylistsRoute
import it.vfsfitvnm.vimusic.utils.addNext
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.enqueue
import it.vfsfitvnm.vimusic.utils.forcePlay
import it.vfsfitvnm.vimusic.utils.globalCache
import it.vfsfitvnm.vimusic.utils.shareAsYouTubeSong
import it.vfsfitvnm.youtubemusic.models.NavigationEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@ExperimentalAnimationApi
@Composable
fun InFavoritesMediaItemMenu(
    song: DetailedSong,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    NonQueuedMediaItemMenu(
        mediaItem = song.asMediaItem,
        onDismiss = onDismiss,
        onRemoveFromFavorites = {
            query {
                Database.like(song.id, null)
            }
        },
        modifier = modifier
    )
}

@ExperimentalAnimationApi
@Composable
fun InHistoryMediaItemMenu(
    song: DetailedSong,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    val menuState = LocalMenuState.current
    val context = LocalContext.current

    var isHiding by remember {
        mutableStateOf(false)
    }

    if (isHiding) {
        ConfirmationDialog(
            text = "Do you really want to delete this song?",
            onDismiss = { isHiding = false },
            onConfirm = {
                (onDismiss ?: menuState::hide).invoke()
                query {
                    // Not sure we can to this here
                    context.globalCache.removeResource(song.id)
                    Database.markDownloaded(song.id, false)
                    Database.incrementTotalPlayTimeMs(song.id, -song.totalPlayTimeMs)
                }
            }
        )
    }

    NonQueuedMediaItemMenu(
        mediaItem = song.asMediaItem,
        onDismiss = onDismiss,
        onHideFromDatabase = { isHiding = true },
        modifier = modifier
    )
}

@ExperimentalAnimationApi
@Composable
fun InPlaylistMediaItemMenu(
    playlistId: Long,
    positionInPlaylist: Int,
    song: DetailedSong,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    NonQueuedMediaItemMenu(
        mediaItem = song.asMediaItem,
        onDismiss = onDismiss,
        onRemoveFromPlaylist = {
            transaction {
                Database.move(playlistId, positionInPlaylist, Int.MAX_VALUE)
                Database.delete(SongPlaylistMap(song.id, playlistId, Int.MAX_VALUE))
            }
        },
        modifier = modifier
    )
}

@ExperimentalAnimationApi
@Composable
fun NonQueuedMediaItemMenu(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onHideFromDatabase: (() -> Unit)? = null,
    onRemoveFromFavorites: (() -> Unit)? = null,
) {
    val menuState = LocalMenuState.current
    val binder = LocalPlayerServiceBinder.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    BaseMediaItemMenu(
        mediaItem = mediaItem,
        onDismiss = onDismiss ?: menuState::hide,
        onDownload = {
            coroutineScope.launch {
                val uri = BuildMediaUrl(mediaItem)

                uri
                    .getOrNull()
                    ?.let {
                        val id = mediaItem.mediaId
                        val request = DownloadRequest
                            .Builder(id, it)
                            .setCustomCacheKey(id)
                            .build()
                        DownloadService.sendAddDownload(
                            context,
                            MediaDownloadService::class.java,
                            request,
                            true
                        )
                    }
            }
        },
        onPlayNext = { binder?.player?.addNext(mediaItem) },
        onEnqueue = { binder?.player?.enqueue(mediaItem) },
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        onHideFromDatabase = onHideFromDatabase,
        onRemoveFromFavorites = onRemoveFromFavorites,
        modifier = modifier
    )
}

@ExperimentalAnimationApi
@Composable
fun QueuedMediaItemMenu(
    mediaItem: MediaItem,
    indexInQueue: Int?,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    onGlobalRouteEmitted: (() -> Unit)? = null
) {
    val menuState = LocalMenuState.current
    val binder = LocalPlayerServiceBinder.current

    BaseMediaItemMenu(
        mediaItem = mediaItem,
        onDismiss = onDismiss ?: menuState::hide,
        onRemoveFromQueue = if (indexInQueue != null) ({
            binder?.player?.removeMediaItem(indexInQueue)
        }) else null,
        onGlobalRouteEmitted = onGlobalRouteEmitted,
        modifier = modifier
    )
}

@ExperimentalAnimationApi
@Composable
fun BaseMediaItemMenu(
    mediaItem: MediaItem,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDownload: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onEnqueue: (() -> Unit)? = null,
    onRemoveFromQueue: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onHideFromDatabase: (() -> Unit)? = null,
    onRemoveFromFavorites: (() -> Unit)? = null,
    onGlobalRouteEmitted: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    MediaItemMenu(
        mediaItem = mediaItem,
        onDismiss = onDismiss,
        onDownload = onDownload,
        onPlayNext = onPlayNext,
        onEnqueue = onEnqueue,
        onAddToPlaylist = { playlist, position ->
            transaction {
                Database.insert(mediaItem)
                Database.insert(
                    SongPlaylistMap(
                        songId = mediaItem.mediaId,
                        playlistId = Database.insert(playlist).takeIf { it != -1L } ?: playlist.id,
                        position = position
                    )
                )
            }
        },
        onHideFromDatabase = onHideFromDatabase,
        onRemoveFromFavorites = onRemoveFromFavorites,
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        onRemoveFromQueue = onRemoveFromQueue,
        onGoToAlbum = albumRoute::global,
        onGoToArtist = artistRoute::global,
        onShare = {
            context.shareAsYouTubeSong(mediaItem)
        },
        onGlobalRouteEmitted = onGlobalRouteEmitted,
        modifier = modifier
    )
}

@ExperimentalAnimationApi
@Composable
fun MediaItemMenu(
    mediaItem: MediaItem,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDownload: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onEnqueue: (() -> Unit)? = null,
    onHideFromDatabase: (() -> Unit)? = null,
    onRemoveFromQueue: (() -> Unit)? = null,
    onRemoveFromFavorites: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onAddToPlaylist: ((Playlist, Int) -> Unit)? = null,
    onGoToAlbum: ((String) -> Unit)? = null,
    onGoToArtist: ((String) -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onGlobalRouteEmitted: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    Menu(modifier = modifier) {
        RouteHandler(
            transitionSpec = {
                when (targetState.route) {
                    viewPlaylistsRoute -> slideIntoContainer(AnimatedContentScope.SlideDirection.Left) with
                            slideOutOfContainer(AnimatedContentScope.SlideDirection.Left)
                    else -> when (initialState.route) {
                        viewPlaylistsRoute -> slideIntoContainer(AnimatedContentScope.SlideDirection.Right) with
                                slideOutOfContainer(AnimatedContentScope.SlideDirection.Right)
                        else -> empty
                    }
                }
            }
        ) {
            viewPlaylistsRoute {
                val playlistPreviews by remember {
                    Database.playlistPreviews(PlaylistSortBy.DateAdded, SortOrder.Descending)
                }.collectAsState(initial = emptyList(), context = Dispatchers.IO)

                var isCreatingNewPlaylist by rememberSaveable {
                    mutableStateOf(false)
                }

                if (isCreatingNewPlaylist && onAddToPlaylist != null) {
                    TextFieldDialog(
                        hintText = "Enter the playlist name",
                        onDismiss = {
                            isCreatingNewPlaylist = false
                        },
                        onDone = { text ->
                            onDismiss()
                            onAddToPlaylist(Playlist(name = text), 0)
                        }
                    )
                }

                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        MenuBackButton(onClick = pop)

                        if (onAddToPlaylist != null) {
                            MenuIconButton(
                                icon = R.drawable.add,
                                onClick = {
                                    isCreatingNewPlaylist = true
                                }
                            )
                        }
                    }

                    onAddToPlaylist?.let { onAddToPlaylist ->
                        if (onRemoveFromFavorites == null) {
                            MenuEntry(
                                icon = R.drawable.heart,
                                text = "Favorites",
                                onClick = {
                                    onDismiss()
                                    query {
                                        Database.insert(mediaItem)
                                        Database.like(mediaItem.mediaId, System.currentTimeMillis())
                                    }
                                }
                            )
                        }

                        playlistPreviews.forEach { playlistPreview ->
                            MenuEntry(
                                icon = R.drawable.playlist,
                                text = playlistPreview.playlist.name,
                                secondaryText = "${playlistPreview.songCount} songs",
                                onClick = {
                                    onDismiss()
                                    onAddToPlaylist(
                                        playlistPreview.playlist,
                                        playlistPreview.songCount
                                    )
                                }
                            )
                        }
                    }
                }
            }

            host {
                Column(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures { }
                        }
                ) {
                    val chunkLength = 512 * 1024L
                    if (onDownload != null && !context.globalCache.isCached(
                            mediaItem.mediaId,
                            0,
                            chunkLength
                        )
                    ) {
                        MenuEntry(
                            icon = R.drawable.download,
                            text = "Download",
                            onClick = {
                                onDismiss()
                                onDownload()
                            })
                    }

                    onPlayNext?.let { onPlayNext ->
                        MenuEntry(
                            icon = R.drawable.play_skip_forward,
                            text = "Play next",
                            onClick = {
                                onDismiss()
                                onPlayNext()
                            }
                        )
                    }

                    onEnqueue?.let { onEnqueue ->
                        MenuEntry(
                            icon = R.drawable.enqueue,
                            text = "Enqueue",
                            onClick = {
                                onDismiss()
                                onEnqueue()
                            }
                        )
                    }

                    if (onAddToPlaylist != null) {
                        MenuEntry(
                            icon = R.drawable.playlist,
                            text = "Add to playlist or favorites",
                            onClick = {
                                viewPlaylistsRoute()
                            }
                        )
                    }

                    onGoToAlbum?.let { onGoToAlbum ->
                        mediaItem.mediaMetadata.extras?.getString("albumId")?.let { albumId ->
                            MenuEntry(
                                icon = R.drawable.disc,
                                text = "Go to album",
                                onClick = {
                                    onDismiss()
                                    onGlobalRouteEmitted?.invoke()
                                    onGoToAlbum(albumId)
                                }
                            )
                        }
                    }

                    onGoToArtist?.let { onGoToArtist ->
                        mediaItem.mediaMetadata.extras?.getStringArrayList("artistNames")
                            ?.let { artistNames ->
                                mediaItem.mediaMetadata.extras?.getStringArrayList("artistIds")
                                    ?.let { artistIds ->
                                        artistNames.zip(artistIds)
                                            .forEach { (authorName, authorId) ->
                                                if (authorId != null) {
                                                    MenuEntry(
                                                        icon = R.drawable.person,
                                                        text = "More of $authorName",
                                                        onClick = {
                                                            onDismiss()
                                                            onGlobalRouteEmitted?.invoke()
                                                            onGoToArtist(authorId)
                                                        }
                                                    )
                                                }
                                            }
                                    }
                            }
                    }

                    onShare?.let { onShare ->
                        MenuEntry(
                            icon = R.drawable.share_social,
                            text = "Share",
                            onClick = {
                                onDismiss()
                                onShare()
                            }
                        )
                    }

                    onRemoveFromQueue?.let { onRemoveFromQueue ->
                        MenuEntry(
                            icon = R.drawable.trash,
                            text = "Remove from queue",
                            onClick = {
                                onDismiss()
                                onRemoveFromQueue()
                            }
                        )
                    }

                    onRemoveFromFavorites?.let { onRemoveFromFavorites ->
                        MenuEntry(
                            icon = R.drawable.heart_dislike,
                            text = "Remove from favorites",
                            onClick = {
                                onDismiss()
                                onRemoveFromFavorites()
                            }
                        )
                    }

                    onRemoveFromPlaylist?.let { onRemoveFromPlaylist ->
                        MenuEntry(
                            icon = R.drawable.trash,
                            text = "Remove from playlist",
                            onClick = {
                                onDismiss()
                                onRemoveFromPlaylist()
                            }
                        )
                    }

                    onHideFromDatabase?.let { onHideFromDatabase ->
                        MenuEntry(
                            icon = R.drawable.trash,
                            text = "Delete",
                            onClick = onHideFromDatabase
                        )
                    }
                }
            }
        }
    }
}
