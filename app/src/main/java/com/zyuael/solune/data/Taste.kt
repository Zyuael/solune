// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * A small on-device model of what you like, learned from how you listen:
 * finishing a song and liking it count for, skipping early and "Not interested" count against.
 * Nothing leaves the phone.
 */
class Taste(context: Context) {
    private val prefs = context.getSharedPreferences("taste", Context.MODE_PRIVATE)

    private class SongStat(var plays: Int = 0, var completes: Int = 0, var skips: Int = 0, var blocked: Boolean = false)

    private val songs = HashMap<String, SongStat>()
    private val artists = HashMap<String, Double>()

    /** Top artists by score, for "Your taste" and Daily Mixes. Display names, best first. */
    private val _topArtists = MutableStateFlow<List<String>>(emptyList())
    val topArtists: StateFlow<List<String>> = _topArtists.asStateFlow()

    private val displayNames = HashMap<String, String>()

    init {
        runCatching {
            val root = JSONObject(prefs.getString(KEY, "{}")!!)
            root.optJSONObject("songs")?.let { s ->
                s.keys().forEach { id ->
                    val o = s.getJSONObject(id)
                    songs[id] = SongStat(o.optInt("p"), o.optInt("c"), o.optInt("s"), o.optBoolean("b"))
                }
            }
            root.optJSONObject("artists")?.let { a -> a.keys().forEach { artists[it] = a.getDouble(it) } }
            root.optJSONObject("names")?.let { n -> n.keys().forEach { displayNames[it] = n.getString(it) } }
        }
        publish()
    }

    /** Called when a song stops being the current one. [fraction] = how much of it was heard. */
    fun recordListen(song: Song, fraction: Float, skipped: Boolean) {
        if (song.durationSec in 1..44) return // ignore very short tracks/ads
        val stat = songs.getOrPut(song.id) { SongStat() }
        stat.plays++
        when {
            fraction >= 0.8f -> { stat.completes++; bump(song, +2.0) }
            skipped && fraction < 0.3f -> { stat.skips++; bump(song, -1.5) }
            fraction >= 0.5f -> bump(song, +0.75)
        }
        save()
    }

    fun recordLike(song: Song, liked: Boolean) {
        bump(song, if (liked) +3.0 else -1.0)
        save()
    }

    /** "Not interested": never suggest this song again, and lean away from the artist. */
    fun block(song: Song) {
        songs.getOrPut(song.id) { SongStat() }.blocked = true
        bump(song, -4.0)
        save()
    }

    fun reset() {
        songs.clear(); artists.clear(); displayNames.clear()
        save()
    }

    val hasSignal: Boolean get() = artists.values.any { it > 0 }

    fun artistScore(song: Song): Double = artists[key(song.artist)] ?: 0.0

    /** Songs we should keep out of suggestions. */
    fun isUnwanted(song: Song): Boolean {
        val s = songs[song.id]
        return s?.blocked == true || (s != null && s.skips >= 2 && s.completes == 0) || artistScore(song) <= -4.0
    }

    /** Predicted enjoyment; higher plays first. Ties keep their original (relevance) order. */
    fun score(song: Song): Double {
        val s = songs[song.id]
        return artistScore(song) * 0.6 + (s?.completes ?: 0) * 1.5 - (s?.skips ?: 0) * 2.5
    }

    /** Filter out unwanted songs and gently reorder by taste, keeping some variety. */
    fun personalize(list: List<Song>): List<Song> =
        list.filterNot(::isUnwanted)
            .withIndex()
            .sortedByDescending { (i, s) -> score(s) - i * 0.15 } // relevance still matters
            .map { it.value }

    /** The artist name used for grouping ("A, B & C" → "a"). */
    fun key(artist: String) = artist.split(",", "&", " x ", " feat", " ft.").first().trim().lowercase()

    private fun bump(song: Song, delta: Double) {
        val k = key(song.artist)
        if (k.isEmpty()) return
        displayNames[k] = song.artist.split(",", "&", " x ", " feat", " ft.").first().trim()
        artists[k] = ((artists[k] ?: 0.0) + delta).coerceIn(-20.0, 60.0)
    }

    private fun publish() {
        _topArtists.value = artists.entries.filter { it.value > 1.0 }.sortedByDescending { it.value }
            .take(8).map { displayNames[it.key] ?: it.key }
    }

    private fun save() {
        publish()
        // Keep the file small: drop stale one-off plays when it grows.
        if (songs.size > 3000) {
            songs.entries.filter { it.value.plays <= 1 && !it.value.blocked && it.value.completes == 0 }
                .take(songs.size - 2500).forEach { songs.remove(it.key) }
        }
        val s = JSONObject()
        songs.forEach { (id, v) -> s.put(id, JSONObject().put("p", v.plays).put("c", v.completes).put("s", v.skips).put("b", v.blocked)) }
        val a = JSONObject()
        artists.forEach { (k, v) -> a.put(k, v) }
        val n = JSONObject()
        displayNames.forEach { (k, v) -> n.put(k, v) }
        prefs.edit().putString(KEY, JSONObject().put("songs", s).put("artists", a).put("names", n).toString()).apply()
    }

    private companion object {
        const val KEY = "model"
    }
}
