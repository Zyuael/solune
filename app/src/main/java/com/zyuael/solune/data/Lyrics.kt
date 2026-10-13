// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class LyricLine(val timeMs: Long, val text: String)

/** [synced] when time-coded lyrics exist, otherwise [plain]; both null means none were found. */
data class Lyrics(val synced: List<LyricLine>?, val plain: String?) {
    val isEmpty get() = synced.isNullOrEmpty() && plain.isNullOrBlank()
}

/** Lyrics from LRCLIB (https://lrclib.net), a free, open lyrics database. */
object LyricsRepo {
    private const val UA = "Solune/2.1 (open-source Android music player)"
    private val cache = ConcurrentHashMap<String, Lyrics>()

    suspend fun get(song: Song): Lyrics = cache[song.id] ?: withContext(Dispatchers.IO) {
        val title = song.title
            .replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), "") // "(feat. X)", "(From "Film")", "[Remastered]"
            .substringBefore(" - ")
            .trim()
        val artist = song.artist.split(",", "&", " x ", " feat").first().trim()

        val exact = runCatching {
            fetch(
                "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                    .addQueryParameter("track_name", title)
                    .addQueryParameter("artist_name", artist)
                    .apply { if (song.durationSec > 0) addQueryParameter("duration", song.durationSec.toString()) }
                    .build().toString()
            )?.let(::JSONObject)
        }.getOrNull()

        val best = exact?.takeIf { it.hasLyrics() } ?: runCatching {
            val results = fetch(
                "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                    .addQueryParameter("track_name", title)
                    .addQueryParameter("artist_name", artist)
                    .build().toString()
            )?.let(::JSONArray) ?: JSONArray()
            val all = List(results.length()) { results.getJSONObject(it) }
            all.firstOrNull { !it.optString("syncedLyrics").isNullOrBlank() } ?: all.firstOrNull { it.hasLyrics() }
        }.getOrNull()

        Lyrics(
            synced = best?.optString("syncedLyrics")?.takeIf { it.isNotBlank() && it != "null" }?.let(::parseLrc),
            plain = best?.optString("plainLyrics")?.takeIf { it.isNotBlank() && it != "null" },
        ).also { if (!it.isEmpty) cache[song.id] = it } // don't remember a failure; it may be the network
    }

    private fun JSONObject.hasLyrics() =
        optString("syncedLyrics").let { it.isNotBlank() && it != "null" } ||
            optString("plainLyrics").let { it.isNotBlank() && it != "null" }

    private fun fetch(url: String): String? =
        Http.client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use {
            if (it.isSuccessful) it.body?.string() else null
        }

    private val stamp = Regex("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]")

    fun parseLrc(lrc: String): List<LyricLine> = lrc.lineSequence().flatMap { line ->
        val stamps = stamp.findAll(line).toList()
        val text = line.replace(stamp, "").trim()
        stamps.map { m ->
            val (min, sec, frac) = m.destructured
            val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
            LyricLine(min.toLong() * 60_000 + sec.toLong() * 1000 + ms, text)
        }
    }.sortedBy { it.timeMs }.toList()
}
