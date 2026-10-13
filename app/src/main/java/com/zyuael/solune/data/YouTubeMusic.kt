// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import org.schabi.newpipe.extractor.Page as NpPage

/** The YouTube Music catalogue, accessed through NewPipeExtractor (no API key, no login). */
object YouTubeMusic {
    private val yt get() = ServiceList.YouTube

    class Page internal constructor(
        val songs: List<Song>,
        internal val handler: SearchQueryHandler,
        internal val next: NpPage?,
    ) {
        val hasMore: Boolean get() = NpPage.isValid(next)
    }

    suspend fun search(query: String): Page = withContext(Dispatchers.IO) {
        val handler = yt.searchQHFactory.fromQuery(
            query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "",
        )
        val info = SearchInfo.getInfo(yt, handler)
        Page(info.relatedItems.toSongs(), handler, info.nextPage)
    }

    suspend fun more(page: Page): Page = withContext(Dispatchers.IO) {
        val result = SearchInfo.getMoreItems(yt, page.handler, page.next)
        Page(result.items.toSongs(), page.handler, result.nextPage)
    }

    suspend fun searchAlbums(query: String): List<Album> = withContext(Dispatchers.IO) {
        val handler = yt.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS), "")
        SearchInfo.getInfo(yt, handler).relatedItems.filterIsInstance<PlaylistInfoItem>().map {
            Album(url = it.url, title = cleanAlbumTitle(it.name), artist = it.uploaderName.orEmpty(), artworkUrl = bestArtwork(it.thumbnails))
        }
    }

    suspend fun searchArtists(query: String): List<Artist> = withContext(Dispatchers.IO) {
        val handler = yt.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS), "")
        SearchInfo.getInfo(yt, handler).relatedItems.filterIsInstance<ChannelInfoItem>().map {
            Artist(name = it.name, url = it.url, avatarUrl = bestArtwork(it.thumbnails))
        }
    }

    /** An album's tracks, all wearing the album cover (track thumbnails are often video frames). */
    suspend fun albumSongs(album: Album): List<Song> = withContext(Dispatchers.IO) {
        val info = PlaylistInfo.getInfo(yt, album.url)
        val cover = bestArtwork(info.thumbnails) ?: album.artworkUrl
        info.relatedItems.toSongs().map { it.copy(artworkUrl = cover ?: it.artworkUrl) }
    }

    /** An artist page: their most relevant songs and albums, matched by name. */
    suspend fun artistPage(artist: Artist): Pair<List<Song>, List<Album>> = withContext(Dispatchers.IO) {
        fun matches(name: String) = name.contains(artist.name, ignoreCase = true)
        val songs = search(artist.name).songs.let { all -> all.filter { matches(it.artist) }.ifEmpty { all } }
        val albums = searchAlbums(artist.name).let { all -> all.filter { matches(it.artist) }.ifEmpty { all } }
        songs to albums
    }

    /** YouTube Music's "radio" for a song: a mix of similar tracks, seeded by that song. */
    suspend fun radio(seedId: String): List<Song> = withContext(Dispatchers.IO) {
        PlaylistInfo.getInfo(yt, "https://www.youtube.com/watch?v=$seedId&list=RDAMVM$seedId")
            .relatedItems.toSongs()
    }

    private fun cleanAlbumTitle(name: String) = name.replace(Regex("^(Album|Single|EP)\\s*[·•-]\\s*"), "")

    private fun List<InfoItem>.toSongs(): List<Song> =
        filterIsInstance<StreamInfoItem>().mapNotNull { item ->
            runCatching {
                Song(
                    id = yt.streamLHFactory.getId(item.url),
                    title = item.name,
                    artist = item.uploaderName.orEmpty().removeSuffix(" - Topic"),
                    durationSec = item.duration.coerceAtLeast(0),
                    artworkUrl = bestArtwork(item.thumbnails),
                )
            }.getOrNull()
        }.distinctBy { it.id }

    /** YouTube Music thumbnails are tiny by default; ask Google's image CDN for a large square. */
    private fun bestArtwork(images: List<Image>): String? {
        val url = images.maxByOrNull { it.height }?.url ?: return null
        return url.replace(Regex("=w\\d+-h\\d+"), "=w720-h720").replace(Regex("=s\\d+-"), "=s544-")
    }
}

data class Album(val url: String, val title: String, val artist: String, val artworkUrl: String?)

data class Artist(val name: String, val url: String, val avatarUrl: String?)

/** Turns a video id into a playable audio URL. Called on ExoPlayer's loader thread. */
object StreamResolver {
    private data class Entry(val url: String, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    fun resolve(id: String): String {
        val now = System.currentTimeMillis()
        cache[id]?.takeIf { it.expiresAt > now }?.let { return it.url }

        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
        val candidates = info.audioStreams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
        }
        val original = candidates
            .filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .ifEmpty { candidates }
        val best = original.maxByOrNull { it.averageBitrate }
            ?: throw IOException("No playable audio stream for $id")

        val expiresAt = Uri.parse(best.content).getQueryParameter("expire")?.toLongOrNull()
            ?.let { it * 1000 - 10 * 60_000 }
            ?: (now + 3 * 60 * 60_000)
        cache[id] = Entry(best.content, expiresAt)
        return best.content
    }

    fun invalidate(id: String) {
        cache.remove(id)
    }

    /** googlevideo checks that the requesting client matches the one the URL was issued for. */
    fun headersFor(url: String): Map<String, String> = when {
        YoutubeParsingHelper.isAndroidStreamingUrl(url) ->
            mapOf("User-Agent" to YoutubeParsingHelper.getAndroidUserAgent(null))
        YoutubeParsingHelper.isIosStreamingUrl(url) ->
            mapOf("User-Agent" to YoutubeParsingHelper.getIosUserAgent(null))
        YoutubeParsingHelper.isVisionOsStreamingUrl(url) ->
            mapOf("User-Agent" to YoutubeParsingHelper.getVisionOsUserAgent(null))
        else -> mapOf(
            "User-Agent" to Http.BROWSER_UA,
            "Origin" to "https://www.youtube.com",
            "Referer" to "https://www.youtube.com/",
        )
    }
}
