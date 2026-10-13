// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.IOException

/**
 * Songs saved for offline listening. Shared by the UI and the playback service (same process):
 * the service plays a saved file instead of streaming whenever one exists.
 */
object Downloads {
    private const val CHUNK = 1L shl 20 // googlevideo is happiest serving ranges of ~1 MB

    private lateinit var dir: File
    private lateinit var index: File

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    /** Download progress 0..1 for songs currently being saved. */
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    fun init(context: Context) {
        dir = File(context.filesDir, "downloads").apply { mkdirs() }
        index = File(dir, "index.json")
        _songs.value = runCatching {
            val array = JSONArray(String(AtomicFile(index).readFully()))
            List(array.length()) { Song.fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList()).filter { fileFor(it.id) != null }
    }

    fun fileFor(id: String): File? = File(dir, "$id.audio").takeIf { it.exists() && it.length() > 0 }

    fun isDownloaded(id: String) = _songs.value.any { it.id == id }

    suspend fun download(song: Song) = withContext(Dispatchers.IO) {
        // Atomic claim, so two taps (or "Download all") can't start the same song twice.
        val claimed = synchronized(this@Downloads) {
            if (isDownloaded(song.id) || song.id in _progress.value) false
            else { _progress.update { it + (song.id to 0f) }; true }
        }
        if (!claimed) return@withContext
        val tmp = File(dir, "${song.id}.part")
        try {
            val url = StreamResolver.resolve(song.id)
            val headers = StreamResolver.headersFor(url)
            tmp.outputStream().use { out ->
                var offset = 0L
                var total: Long? = null
                var done = false
                while (!done) {
                    val request = Request.Builder().url(url)
                        .header("Range", "bytes=$offset-${offset + CHUNK - 1}")
                        .apply { headers.forEach { (k, v) -> header(k, v) } }
                        .build()
                    val bytes = Http.client.newCall(request).execute().use { response ->
                        when {
                            response.code == 416 -> ByteArray(0) // asked past the end: finished
                            !response.isSuccessful -> throw IOException("HTTP ${response.code}")
                            else -> {
                                total = total ?: response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                                response.body!!.bytes()
                            }
                        }
                    }
                    out.write(bytes)
                    offset += bytes.size
                    val known = total
                    done = bytes.isEmpty() || known == null || offset >= known // no Content-Range: whole file came at once
                    if (known != null) _progress.update { it + (song.id to (offset.toFloat() / known).coerceIn(0f, 1f)) }
                }
            }
            if (!tmp.renameTo(File(dir, "${song.id}.audio"))) throw IOException("Couldn't save file")
            _songs.update { listOf(song) + it.filterNot { s -> s.id == song.id } }
            saveIndex()
        } catch (e: Exception) {
            tmp.delete()
            throw e
        } finally {
            _progress.update { it - song.id }
        }
    }

    fun remove(song: Song) {
        File(dir, "${song.id}.audio").delete()
        _songs.update { all -> all.filterNot { it.id == song.id } }
        saveIndex()
    }

    /** Serialized and atomic (write-then-swap), so a crash mid-write can't wipe the list. */
    @Synchronized
    private fun saveIndex() {
        val file = AtomicFile(index)
        val out = file.startWrite()
        try {
            out.write(JSONArray().apply { _songs.value.forEach { put(it.toJson()) } }.toString().toByteArray())
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
        }
    }
}
