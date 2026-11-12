/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.artist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.music.R
import com.metrolist.music.viewmodels.ArtistViewModel

/**
 * Grid with every item of one section of the artist page (used for sections that YouTube Music
 * gives no "more" page for). [viewModel] is the artist page's view model, so the data is already
 * loaded.
 */
@Composable
fun ArtistSectionScreen(
    navController: NavController,
    sectionTitle: String,
    viewModel: ArtistViewModel,
) {
    val extraAlbums by viewModel.extraAlbums.collectAsStateWithLifecycle()
    val albumsTitle = stringResource(R.string.albums)

    val sectionItems = viewModel.artistPage?.sections?.firstOrNull { it.title == sectionTitle }?.items.orEmpty()
    val items =
        if (sectionTitle.equals(albumsTitle, ignoreCase = true) && extraAlbums.isNotEmpty()) {
            (sectionItems + extraAlbums)
                .distinctBy { it.id }
                .sortedByDescending { (it as? AlbumItem)?.year ?: 0 }
        } else {
            sectionItems
        }

    YTItemsGridScreen(navController = navController, title = sectionTitle, sourceItems = items)
}
