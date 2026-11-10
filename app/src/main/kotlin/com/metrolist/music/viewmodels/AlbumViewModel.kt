/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.pages.AlbumPage
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.isSpotifyId
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel
@Inject
constructor(
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val albumId = savedStateHandle.get<String>("albumId")!!
    val playlistId = MutableStateFlow("")
    // The id actually displayed. Normally the same as [albumId], but when that id can't be opened on
    // YouTube (e.g. a stale/placeholder id saved with a liked song) it is switched to the album found
    // by searching for its name.
    private val displayedAlbumId = MutableStateFlow(albumId)

    @OptIn(ExperimentalCoroutinesApi::class)
    val albumWithSongs =
        displayedAlbumId
            .flatMapLatest { database.albumWithSongs(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    var otherVersions = MutableStateFlow<List<AlbumItem>>(emptyList())

    // null = still loading or success. Non-null = fetch failed and UI must
    // surface an error state instead of an endless spinner (issue #131).
    val fetchError = MutableStateFlow<String?>(null)

    init {
        fetchAlbum()
    }

    fun fetchAlbum() {
        viewModelScope.launch {
            if (albumId.isSpotifyId()) return@launch
            fetchError.value = null
            val album = database.album(albumId).first()
            var result = runCatching {
                withTimeout(FETCH_TIMEOUT_MS) {
                    YouTube.album(albumId)
                }
            }.getOrElse { Result.failure(it) }

            // Fallback: the saved album id can't be opened, so find the album by name + artist.
            if (result.isFailure && album == null) {
                val resolved = resolveByName()
                if (resolved != null) {
                    displayedAlbumId.value = resolved.album.browseId
                    result = Result.success(resolved)
                }
            }

            result
                .onSuccess {
                    playlistId.value = it.album.playlistId
                    otherVersions.value = it.otherVersions
                    database.transaction {
                        if (album == null) {
                            insert(it)
                        } else {
                            update(album.album, it, album.artists)
                        }
                    }
                }.onFailure {
                    reportException(it)
                    if (it.message?.contains("NOT_FOUND") == true) {
                        database.query {
                            album?.album?.let(::delete)
                        }
                    }
                    // Only show an error state when we don't already have the
                    // album cached locally — otherwise the screen can render
                    // from DB and the failure is invisible to the user.
                    if (album == null) {
                        fetchError.value = when (it) {
                            is TimeoutCancellationException -> "timeout"
                            else -> it.message ?: it.javaClass.simpleName
                        }
                    }
                }
        }
    }

    private suspend fun resolveByName(): AlbumPage? {
        val name = database.albumNameFromSongs(albumId)?.takeIf { it.isNotBlank() } ?: return null
        val artist = database.albumArtistNameFromSongs(albumId)
        return runCatching {
            withTimeout(FETCH_TIMEOUT_MS * 2) {
                val query = listOfNotNull(name, artist).joinToString(" ")
                val candidates =
                    YouTube
                        .search(query, YouTube.SearchFilter.FILTER_ALBUM)
                        .getOrThrow()
                        .items
                        .filterIsInstance<AlbumItem>()
                val sameTitle = candidates.filter { it.title.equals(name, ignoreCase = true) }
                val match =
                    sameTitle.firstOrNull { c ->
                        artist != null && c.artists?.any { it.name.equals(artist, ignoreCase = true) } == true
                    } ?: sameTitle.firstOrNull()
                        ?: candidates.firstOrNull { c ->
                            artist != null && c.artists?.any { it.name.equals(artist, ignoreCase = true) } == true
                        }
                match?.let { YouTube.album(it.browseId).getOrNull() }
            }
        }.getOrNull()
    }

    companion object {
        private const val FETCH_TIMEOUT_MS = 15_000L
    }
}
