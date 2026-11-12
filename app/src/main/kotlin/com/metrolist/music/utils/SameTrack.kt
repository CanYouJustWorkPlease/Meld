/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.innertube.models.SongItem
import com.metrolist.music.db.entities.Song
import kotlin.math.abs

/**
 * Decides whether two songs are the same track listed under different ids, e.g. a collaboration
 * that YouTube Music publishes once per artist ("Victory" by A, "Victory" by B, "Victory" by A and
 * B). Spotify treats those as one track, so likes should too.
 *
 * Two songs are the same track when the titles are equal (ignoring case/punctuation), the durations
 * are within [DURATION_TOLERANCE_SEC] seconds, and the artists of one are all contained in the
 * artists of the other.
 */
object SameTrack {
    private const val DURATION_TOLERANCE_SEC = 5
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

    fun norm(s: String): String = s.lowercase().replace(NON_ALNUM, " ").trim()

    private fun artistsCompatible(a: Collection<String>, b: Collection<String>): Boolean {
        val x = a.map(::norm).filter { it.isNotEmpty() }.toSet()
        val y = b.map(::norm).filter { it.isNotEmpty() }.toSet()
        if (x.isEmpty() || y.isEmpty()) return false
        return y.containsAll(x) || x.containsAll(y)
    }

    fun matches(
        titleA: String,
        durationA: Int,
        artistsA: Collection<String>,
        titleB: String,
        durationB: Int,
        artistsB: Collection<String>,
    ): Boolean {
        if (norm(titleA) != norm(titleB)) return false
        if (durationA > 0 && durationB > 0 && abs(durationA - durationB) > DURATION_TOLERANCE_SEC) return false
        return artistsCompatible(artistsA, artistsB)
    }

    fun matches(item: SongItem, song: Song): Boolean =
        matches(
            item.title, item.duration ?: -1, item.artists.map { it.name },
            song.song.title, song.song.duration, song.artists.map { it.name },
        )

    fun matches(a: Song, b: Song): Boolean =
        matches(
            a.song.title, a.song.duration, a.artists.map { it.name },
            b.song.title, b.song.duration, b.artists.map { it.name },
        )

    /** Collapses same-track entries into one, keeping the one that lists the most artists. */
    fun dedupe(songs: List<Song>): List<Song> {
        val byTitle = HashMap<String, MutableList<Int>>()
        val out = ArrayList<Song>(songs.size)
        for (s in songs) {
            val indices = byTitle.getOrPut(norm(s.song.title)) { mutableListOf() }
            val existing = indices.firstOrNull { matches(out[it], s) }
            if (existing == null) {
                indices += out.size
                out += s
            } else if (s.artists.size > out[existing].artists.size) {
                out[existing] = s
            }
        }
        return out
    }
}
