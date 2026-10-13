// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.data

import android.net.Uri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONObject

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val artworkUrl: String?,
) {
    val watchUrl: String get() = watchUrl(id)

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(watchUrl)
        .setCustomCacheKey(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(artworkUrl?.let(Uri::parse))
                .setExtras(bundleOf(EXTRA_DURATION to durationSec))
                .build()
        )
        .build()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("duration", durationSec)
        .put("artwork", artworkUrl ?: "")

    companion object {
        private const val EXTRA_DURATION = "solune.duration"

        fun watchUrl(id: String) = "https://music.youtube.com/watch?v=$id"

        fun fromJson(o: JSONObject) = Song(
            id = o.getString("id"),
            title = o.optString("title"),
            artist = o.optString("artist"),
            durationSec = o.optLong("duration"),
            artworkUrl = o.optString("artwork").ifBlank { null },
        )

        fun fromMediaItem(item: MediaItem): Song {
            val m = item.mediaMetadata
            return Song(
                id = item.mediaId,
                title = m.title?.toString().orEmpty(),
                artist = m.artist?.toString().orEmpty(),
                durationSec = m.extras?.getLong(EXTRA_DURATION) ?: 0L,
                artworkUrl = m.artworkUri?.toString(),
            )
        }
    }
}

fun formatTime(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}
