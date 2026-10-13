// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Playlist(val id: String, val name: String, val songs: List<Song>)

/** Liked songs, listening history and the user's own playlists, kept on-device. */
class Library(context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    private val _liked = MutableStateFlow(load(KEY_LIKED))
    val liked: StateFlow<List<Song>> = _liked.asStateFlow()

    private val _recent = MutableStateFlow(load(KEY_RECENT))
    val recent: StateFlow<List<Song>> = _recent.asStateFlow()

    private val _playlists = MutableStateFlow(loadPlaylists())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    fun isLiked(id: String) = _liked.value.any { it.id == id }

    fun toggleLike(song: Song) {
        val current = _liked.value
        _liked.value = if (current.any { it.id == song.id }) current.filterNot { it.id == song.id }
        else listOf(song) + current
        save(KEY_LIKED, _liked.value)
    }

    fun addRecent(song: Song) {
        if (_recent.value.firstOrNull()?.id == song.id) return
        _recent.value = (listOf(song) + _recent.value.filterNot { it.id == song.id }).take(MAX_RECENT)
        save(KEY_RECENT, _recent.value)
    }

    // ---- Playlists ----

    fun createPlaylist(name: String): Playlist {
        val playlist = Playlist(UUID.randomUUID().toString(), name.trim().ifEmpty { "My playlist" }, emptyList())
        updatePlaylists { listOf(playlist) + it }
        return playlist
    }

    fun renamePlaylist(id: String, name: String) =
        updatePlaylists { all -> all.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it } }

    fun deletePlaylist(id: String) = updatePlaylists { all -> all.filterNot { it.id == id } }

    /** Returns false when the song was already in the playlist. */
    fun addToPlaylist(id: String, song: Song): Boolean {
        val target = _playlists.value.firstOrNull { it.id == id } ?: return false
        if (target.songs.any { it.id == song.id }) return false
        updatePlaylists { all -> all.map { if (it.id == id) it.copy(songs = it.songs + song) else it } }
        return true
    }

    fun removeFromPlaylist(id: String, songId: String) =
        updatePlaylists { all -> all.map { if (it.id == id) it.copy(songs = it.songs.filterNot { s -> s.id == songId }) else it } }

    private fun updatePlaylists(change: (List<Playlist>) -> List<Playlist>) {
        _playlists.value = change(_playlists.value)
        val array = JSONArray()
        _playlists.value.forEach { p ->
            array.put(
                JSONObject().put("id", p.id).put("name", p.name)
                    .put("songs", JSONArray().apply { p.songs.forEach { put(it.toJson()) } })
            )
        }
        prefs.edit().putString(KEY_PLAYLISTS, array.toString()).apply()
    }

    private fun loadPlaylists(): List<Playlist> = runCatching {
        val array = JSONArray(prefs.getString(KEY_PLAYLISTS, "[]"))
        List(array.length()) {
            val o = array.getJSONObject(it)
            val songs = o.getJSONArray("songs")
            Playlist(o.getString("id"), o.getString("name"), List(songs.length()) { i -> Song.fromJson(songs.getJSONObject(i)) })
        }
    }.getOrDefault(emptyList())

    private fun load(key: String): List<Song> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        List(array.length()) { Song.fromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun save(key: String, songs: List<Song>) {
        val array = JSONArray().apply { songs.forEach { put(it.toJson()) } }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private companion object {
        const val KEY_LIKED = "liked"
        const val KEY_RECENT = "recent"
        const val KEY_PLAYLISTS = "playlists"
        const val MAX_RECENT = 60
    }
}
