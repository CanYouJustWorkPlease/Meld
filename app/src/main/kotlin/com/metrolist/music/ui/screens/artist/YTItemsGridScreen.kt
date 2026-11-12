/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.artist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.padding
import com.metrolist.innertube.models.YTItem
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.menu.YouTubeArtistMenu
import com.metrolist.music.ui.menu.YouTubePlaylistMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.GridItemSize
import com.metrolist.music.constants.GridItemsSizeKey
import com.metrolist.music.constants.GridThumbnailHeight
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberEnumPreference

/**
 * Grid with the given items (a section of the artist page, or of the home page), in the order
 * given, with an optional release-year sort for albums and singles.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun YTItemsGridScreen(
    navController: NavController,
    title: String,
    sourceItems: List<YTItem>,
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost
    val lazyGridState = rememberLazyGridState()
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    // Albums/singles can be ordered by release year (tap the button in the top bar).
    var yearAscending by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val showSort = sourceItems.any { it is AlbumItem }
    val shownItems =
        remember(sourceItems, yearAscending) {
            val distinct = sourceItems.distinctBy { it.id }
            when (yearAscending) {
                null -> distinct
                true -> distinct.sortedBy { (it as? AlbumItem)?.year ?: Int.MAX_VALUE }
                false -> distinct.sortedByDescending { (it as? AlbumItem)?.year ?: -1 }
            }
        }

    LazyVerticalGrid(
        state = lazyGridState,
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        items(
            items = shownItems,
            key = { "artist_section_${it.id}" },
        ) { item ->
            YouTubeGridItem(
                item = item,
                isActive =
                    when (item) {
                        is SongItem -> mediaMetadata?.id == item.id
                        is AlbumItem -> mediaMetadata?.album?.id == item.id
                        else -> false
                    },
                isPlaying = isPlaying,
                fillMaxWidth = true,
                coroutineScope = coroutineScope,
                modifier =
                    Modifier
                        .combinedClickable(
                            onClick = {
                                when (item) {
                                    is SongItem, is EpisodeItem -> {
                                        if (!isGuest) {
                                            playerConnection.playQueue(
                                                YouTubeQueue(
                                                    WatchEndpoint(videoId = item.id),
                                                    (item as? SongItem)?.toMediaMetadata()
                                                        ?: (item as EpisodeItem).toMediaMetadata(),
                                                ),
                                            )
                                        }
                                    }
                                    is AlbumItem -> navController.navigate("album/${item.id}")
                                    is ArtistItem -> navController.navigate("artist/${item.id}")
                                    is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                    is PodcastItem -> navController.navigate("online_podcast/${item.id}")
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    when (item) {
                                        is SongItem -> YouTubeSongMenu(song = item, onDismiss = menuState::dismiss)
                                        is AlbumItem -> YouTubeAlbumMenu(albumItem = item, onDismiss = menuState::dismiss)
                                        is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                                        is PlaylistItem ->
                                            YouTubePlaylistMenu(
                                                playlist = item,
                                                coroutineScope = coroutineScope,
                                                onDismiss = menuState::dismiss,
                                            )
                                        is PodcastItem ->
                                            YouTubePlaylistMenu(
                                                playlist = item.asPlaylistItem(),
                                                coroutineScope = coroutineScope,
                                                onDismiss = menuState::dismiss,
                                            )
                                        is EpisodeItem -> YouTubeSongMenu(song = item.asSongItem(), onDismiss = menuState::dismiss)
                                    }
                                }
                            },
                        ).animateItem(),
            )
        }
    }

    TopAppBar(
        title = { Text(title) },
        actions = {
            if (showSort) {
                TextButton(
                    onClick = { yearAscending = if (yearAscending == true) false else true },
                ) {
                    Text(stringResource(R.string.sort_by_year))
                    Icon(
                        painter = painterResource(if (yearAscending == true) R.drawable.arrow_upward else R.drawable.arrow_downward),
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}
