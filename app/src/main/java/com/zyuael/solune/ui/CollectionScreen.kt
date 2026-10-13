// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyuael.solune.MIXES
import com.zyuael.solune.MainViewModel
import com.zyuael.solune.data.Album
import com.zyuael.solune.data.Playlist
import com.zyuael.solune.data.Song
import com.zyuael.solune.data.YouTubeMusic
import com.zyuael.solune.ui.components.AlbumCard
import com.zyuael.solune.ui.components.ArchArtwork
import com.zyuael.solune.ui.components.ArtMotion
import com.zyuael.solune.ui.components.Cover
import com.zyuael.solune.ui.components.MixCard
import com.zyuael.solune.ui.components.RoundIcon
import com.zyuael.solune.ui.components.SectionHeader

/** What a detail page shows once loaded. */
private data class Page(
    val title: String,
    val subtitle: String,
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artUrl: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@Composable
fun CollectionScreen(route: Route, vm: MainViewModel, motion: ArtMotion, contentPadding: PaddingValues) {
    val c = Solune.colors
    val nav = LocalNavigator.current
    val menu = LocalSongMenu.current
    val flags = rememberSongFlags(vm)
    val liked by vm.liked.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    var reload by remember { mutableIntStateOf(0) }

    val playlist: Playlist? = (route as? Route.PlaylistPage)?.let { r -> playlists.firstOrNull { it.id == r.id } }
    if (route is Route.PlaylistPage && playlist == null) {
        LaunchedEffect(Unit) { nav.back() } // deleted
        return
    }

    // Remote pages load once; local ones are live views of the library.
    val remote by produceState(Page("", "", loading = true), route, reload) {
        value = Page("", "", loading = true)
        value = runCatching {
            when (route) {
                is Route.AlbumPage -> Page(route.album.title, "Album · ${route.album.artist}", YouTubeMusic.albumSongs(route.album), artUrl = route.album.artworkUrl)
                is Route.ArtistPage -> {
                    val artist = if (route.artist.avatarUrl == null) {
                        YouTubeMusic.searchArtists(route.artist.name).firstOrNull() ?: route.artist
                    } else route.artist
                    val (songs, albums) = YouTubeMusic.artistPage(artist)
                    Page(artist.name, "Artist", songs, albums, artUrl = artist.avatarUrl)
                }
                is Route.MixPage -> Page(route.mix.title, "Made for you · fresh from YouTube Music", YouTubeMusic.search(route.mix.query).songs)
                else -> Page("", "")
            }
        }.getOrElse { Page("", "", error = it.message ?: "Something went wrong") }
    }

    val page = when (route) {
        Route.LikedPage -> Page("Liked Songs", "${songCount(liked.size)}", liked)
        Route.DownloadsPage -> Page("Downloads", "${songCount(downloads.size)} · available offline", downloads)
        Route.RecentPage -> Page("Recently played", "${songCount(recent.size)}", recent)
        is Route.DailyMixPage -> home.dailyMixes.getOrNull(route.index)?.let { Page(it.title, it.subtitle, it.songs) }
            ?: Page("Daily Mix", "", error = "This mix was refreshed. Go back for the new one.")
        is Route.PlaylistPage -> Page(playlist!!.name, "Playlist · ${songCount(playlist.songs.size)}", playlist.songs)
        else -> remote
    }

    var showMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    if (renaming && playlist != null) NameDialog("Rename playlist", playlist.name, "Save", onDismiss = { renaming = false }) {
        vm.renamePlaylist(playlist.id, it)
        renaming = false
    }

    LazyColumn(
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize().background(c.background).statusBarsPadding(),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { nav.back() })
                Box(Modifier.weight(1f))
                if (playlist != null) Box {
                    RoundIcon(Icons.Rounded.MoreVert, "Playlist options", { showMenu = true })
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = c.card) {
                        DropdownMenuItem(text = { Text("Rename", color = c.ink) }, leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = c.ink) }, onClick = { showMenu = false; renaming = true })
                        DropdownMenuItem(text = { Text("Delete playlist", color = c.accent) }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = c.accent) }, onClick = { showMenu = false; vm.deletePlaylist(playlist) })
                    }
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                HeaderArt(route, page, motion)
                Text(
                    page.title, color = c.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 22.dp),
                )
                if (page.subtitle.isNotEmpty()) Text(page.subtitle, color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        if (page.songs.isNotEmpty()) item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                // Icon buttons beside one pill keep the row on one line even on narrow phones.
                if (route != Route.DownloadsPage) CircleAction(Icons.Rounded.Download, "Download all") { vm.downloadAll(page.songs) }
                CircleAction(Icons.Rounded.Shuffle, "Shuffle") { vm.playSongs(page.songs, shuffle = true) }
                PillButton("Play", Icons.Rounded.PlayArrow, { vm.playSongs(page.songs, 0) }, filled = true)
            }
        }

        when {
            page.loading -> item { MotifNote("Tuning in…", loading = true) }
            page.error != null -> item {
                MotifNote("Couldn't load this page.") {
                    Text(
                        "Try again", color = c.onInk, fontSize = 13.sp,
                        modifier = Modifier.clip(CircleShape).background(c.ink).clickable { reload++ }.padding(horizontal = 18.dp, vertical = 9.dp),
                    )
                }
            }
            page.songs.isEmpty() -> item {
                MotifNote(
                    when (route) {
                        Route.LikedPage -> "Songs you like will appear here.\nTap ♥ on the player or ⋯ → Like."
                        Route.DownloadsPage -> "Download songs to play them without internet.\nUse ⋯ → Download on any song."
                        is Route.PlaylistPage -> "This playlist is empty.\nUse ⋯ → Add to playlist on any song."
                        else -> "Nothing here yet."
                    }
                )
            }
            route is Route.ArtistPage -> {
                item { SectionHeader("Popular") }
                trackItems(page.songs.take(10), flags, { vm.playSongs(page.songs, it) }, { menu(it, null) }, keyPrefix = "p", showArt = true, numbered = true)
                if (page.albums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(page.albums, key = { it.url }) { a -> AlbumCard(a, onClick = { nav.push(Route.AlbumPage(a)) }) }
                        }
                    }
                }
            }
            else -> trackItems(
                page.songs, flags, { vm.playSongs(page.songs, it) }, { menu(it, playlist) },
                keyPrefix = "c", showArt = route !is Route.AlbumPage, numbered = route is Route.AlbumPage,
            )
        }
    }
}

@Composable
private fun CircleAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = Solune.colors
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(c.faint.copy(alpha = 0.35f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = c.ink, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun HeaderArt(route: Route, page: Page, motion: ArtMotion) {
    val c = Solune.colors
    when (route) {
        is Route.ArtistPage -> Cover(page.artUrl, 176.dp, circle = true)
        is Route.MixPage -> MixCard(route.mix, MIXES.indexOf(route.mix).coerceAtLeast(1), onClick = {}, size = 190.dp)
        is Route.DailyMixPage -> com.zyuael.solune.ui.components.DailyMixCard(page.title, "", page.songs, onClick = {}, size = 190.dp)
        Route.LikedPage -> Box(Modifier.size(176.dp).clip(RoundedCornerShape(44.dp)).background(c.accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(64.dp))
        }
        else -> {
            val first = page.songs.firstOrNull()
            val cover = page.artUrl ?: first?.artworkUrl
            if (cover == null) {
                Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
                    com.zyuael.solune.ui.components.ArchMotif(Modifier.size(150.dp, 112.dp), animated = page.loading)
                }
            } else {
                ArchArtwork(
                    Song(id = "cover", title = page.title, artist = "", durationSec = 0, artworkUrl = cover),
                    Modifier.size(180.dp, 205.dp),
                    ornamentGap = 14.dp,
                    showText = false,
                )
            }
        }
    }
}
