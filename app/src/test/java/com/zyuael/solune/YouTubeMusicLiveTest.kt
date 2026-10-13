// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune

import com.zyuael.solune.data.Http
import com.zyuael.solune.data.OkHttpDownloader
import com.zyuael.solune.data.StreamResolver
import com.zyuael.solune.data.YouTubeMusic
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo

/** Hits the real network: search YouTube Music, extract a stream, and read its first bytes. */
class YouTubeMusicLiveTest {

    @Test
    fun searchResolveAndFetch() = runBlocking {
        NewPipe.init(OkHttpDownloader(Http.client), Localization("en", "US"))

        val page = YouTubeMusic.search("daylight david kushner")
        println("results=${page.songs.size} hasMore=${page.hasMore}")
        page.songs.take(5).forEach { println("  ${it.id}  ${it.title} — ${it.artist} (${it.durationSec}s) ${it.artworkUrl}") }
        assertTrue(page.songs.isNotEmpty())

        val more = YouTubeMusic.more(page)
        println("page2=${more.songs.size}")

        val id = page.songs.first().id
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
        val stream = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxBy { it.averageBitrate }
        println("stream ${stream.format} ${stream.averageBitrate}kbps")

        val req = Request.Builder().url(stream.content).header("Range", "bytes=0-65535")
        StreamResolver.headersFor(stream.content).forEach { (k, v) -> req.header(k, v) }
        Http.client.newCall(req.build()).execute().use {
            val bytes = it.body?.bytes()?.size ?: 0
            println("fetch HTTP ${it.code}, $bytes bytes")
            assertTrue(it.code in 200..299 && bytes > 0)
        }
    }
}
