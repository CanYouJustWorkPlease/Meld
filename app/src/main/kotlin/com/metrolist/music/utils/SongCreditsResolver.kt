/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Artist/album ids found for a song whose own metadata lacks them. A null field means "unchanged". */
data class ResolvedCredits(
    val artists: List<Artist>?,
    val album: Album?,
)

/**
 * Songs coming from search or Spotify often lack artist ids or carry a non-album id, which hides
 * "View artist"/"View album". This looks the real ids up on YouTube Music.
 */
suspend fun resolveSongCredits(
    videoId: String,
    title: String,
    artists: List<Artist>,
    album: Album?,
): ResolvedCredits =
    withContext(Dispatchers.IO) {
        val needArtists = artists.isEmpty() || artists.any { it.id == null }
        val albumId = album?.id
        val needAlbum = albumId == null || !(albumId.startsWith("MPREb_") || albumId.startsWith("MPSP"))
        if (!needArtists && !needAlbum) return@withContext ResolvedCredits(null, null)

        val fromNext =
            runCatching {
                YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()?.items?.let { l ->
                    l.firstOrNull { it.id == videoId }
                        ?: l.firstOrNull { SameTrack.norm(it.title) == SameTrack.norm(title) }
                }
            }.getOrNull()

        var resolvedArtists: List<Artist>? = null
        if (needArtists) {
            resolvedArtists = fromNext?.artists?.takeIf { l -> l.isNotEmpty() && l.all { it.id != null } }
            if (resolvedArtists == null) {
                val named = fromNext?.artists?.takeIf { it.isNotEmpty() } ?: artists
                val filled =
                    named.map { a ->
                        if (a.id != null) {
                            a
                        } else {
                            val hit =
                                runCatching {
                                    YouTube.search(a.name, YouTube.SearchFilter.FILTER_ARTIST)
                                        .getOrNull()?.items
                                        ?.filterIsInstance<ArtistItem>()
                                        ?.firstOrNull { it.title.equals(a.name, ignoreCase = true) }
                                }.getOrNull()
                            if (hit != null) Artist(name = a.name, id = hit.id) else a
                        }
                    }
                if (filled.any { it.id != null }) resolvedArtists = filled
            }
        }

        var resolvedAlbum: Album? = null
        if (needAlbum) {
            resolvedAlbum = fromNext?.album?.takeIf { it.id.startsWith("MPREb_") }
            if (resolvedAlbum == null) {
                val albumName = album?.name
                val artistName = (fromNext?.artists ?: artists).firstOrNull()?.name
                if (albumName != null && artistName != null) {
                    resolvedAlbum =
                        runCatching {
                            YouTube.search("$albumName $artistName", YouTube.SearchFilter.FILTER_ALBUM)
                                .getOrNull()?.items
                                ?.filterIsInstance<AlbumItem>()
                                ?.firstOrNull { it.title.equals(albumName, ignoreCase = true) }
                                ?.let { Album(name = it.title, id = it.browseId) }
                        }.getOrNull()
                }
            }
        }
        ResolvedCredits(resolvedArtists, resolvedAlbum)
    }
