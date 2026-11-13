/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Returns the id of the YouTube Music album that really contains the given song.
 *
 * A song's stored album id can be wrong (a different release of the track, or an id picked up while
 * matching it from Spotify), which made "View album" open an album the song isn't on. The stored id
 * is kept when that album lists the song; otherwise the album is looked up from a search for the
 * song. Returns null when nothing better is found (or offline): use the stored id then.
 */
suspend fun findAlbumContainingSong(
    title: String,
    artistName: String?,
    currentAlbumId: String?,
): String? =
    withContext(Dispatchers.IO) {
        val wanted = SameTrack.norm(title)

        suspend fun albumHasSong(id: String): Boolean =
            runCatching {
                YouTube.album(id).getOrNull()?.songs?.any { SameTrack.norm(it.title) == wanted } == true
            }.getOrDefault(false)

        if (currentAlbumId != null && currentAlbumId.startsWith("MPREb_") && albumHasSong(currentAlbumId)) {
            return@withContext currentAlbumId
        }

        val query = listOfNotNull(title, artistName).joinToString(" ")

        // The search result for the song itself names the album it is on.
        val fromSong =
            runCatching {
                YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items
                    ?.filterIsInstance<SongItem>()
                    ?.firstOrNull { song ->
                        SameTrack.norm(song.title) == wanted &&
                            (artistName == null || song.artists.any { it.name.equals(artistName, ignoreCase = true) })
                    }?.album?.id?.takeIf { it.startsWith("MPREb_") }
            }.getOrNull()
        if (fromSong != null) return@withContext fromSong

        runCatching {
            YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()?.items
                ?.filterIsInstance<AlbumItem>()
                ?.take(3)
                ?.firstOrNull { albumHasSong(it.browseId) }
                ?.browseId
        }.getOrNull()
    }
