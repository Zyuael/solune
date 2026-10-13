// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.zyuael.solune.ui.components.ArchMotif
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyuael.solune.MainViewModel
import com.zyuael.solune.ui.components.ListRow
import com.zyuael.solune.ui.components.RoundIcon
import com.zyuael.solune.ui.components.SectionHeader

@Composable
fun LibraryScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val c = Solune.colors
    val nav = LocalNavigator.current
    val liked by vm.liked.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val autoplay by vm.autoplay.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val topArtists by vm.topArtists.collectAsStateWithLifecycle()

    if (creating) NameDialog("New playlist", "", "Create", onDismiss = { creating = false }) { name ->
        val p = vm.createPlaylist(name)
        creating = false
        nav.push(Route.PlaylistPage(p.id))
    }

    LazyColumn(
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize().background(c.background).statusBarsPadding(),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = 22.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Your Library", color = c.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, modifier = Modifier.weight(1f))
                RoundIcon(Icons.Rounded.Add, "New playlist", { creating = true }, size = 44.dp, iconSize = 26.dp)
            }
        }
        item { ListRow({ Tile(Icons.Rounded.Favorite, c.accent, Color.White) }, "Liked Songs", "${songCount(liked.size)}", { nav.push(Route.LikedPage) }) }
        item { ListRow({ Tile(Icons.Rounded.DownloadDone, c.ink, c.accent) }, "Downloads", "${songCount(downloads.size)} · plays offline", { nav.push(Route.DownloadsPage) }) }
        item { ListRow({ Tile(Icons.Rounded.History, Color(0xFFD9CBB4), Color(0xFF1C1D21)) }, "Recently played", "${songCount(recent.size)}", { nav.push(Route.RecentPage) }) }

        item { SectionHeader("Playlists", action = "New") { creating = true } }
        if (playlists.isEmpty()) item { MotifNote("Create your first playlist.\nTap + or use ⋯ → Add to playlist on any song.", height = 200) }
        items(playlists, key = { it.id }) { p ->
            ListRow({ PlaylistCover(p, 56.dp) }, p.name, "Playlist · ${songCount(p.songs.size)}", { nav.push(Route.PlaylistPage(p.id)) })
        }

        item { SectionHeader("Your taste", action = if (topArtists.isNotEmpty()) "Reset" else null) { confirmReset = true } }
        item {
            if (topArtists.isEmpty()) {
                Text(
                    "Solune learns from what you finish, skip and like, then shapes your Daily Mixes and autoplay around it. Listen for a while and your favourite artists will show up here.",
                    color = c.sub, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 28.dp),
                )
            } else {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    items(topArtists) { name ->
                        Text(
                            name, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = 0.14f))
                                .clickable { nav.push(Route.ArtistPage(com.zyuael.solune.data.Artist(name, "", null))) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        item { SectionHeader("Playback") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text("Autoplay", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("Keep playing similar songs when your queue ends", color = c.sub, fontSize = 12.sp)
                }
                Switch(
                    checked = autoplay,
                    onCheckedChange = { vm.toggleAutoplay() },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = Color.White, uncheckedTrackColor = c.faint, uncheckedBorderColor = c.faint, uncheckedThumbColor = c.card),
                )
            }
        }
        item { SectionHeader("About") }
        item {
            ListRow(
                { Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(c.faint.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) { ArchMotif(Modifier.size(40.dp, 30.dp)) } },
                "About Solune", "Made by ZYUAEL · open source", { showAbout = true },
            )
        }
    }

    if (showAbout) AboutDialog { showAbout = false }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        containerColor = c.card,
        title = { Text("Reset your taste?", color = c.ink, fontWeight = FontWeight.Bold) },
        text = { Text("Solune will forget what you've finished, skipped and marked Not interested. Your likes and playlists stay.", color = c.sub) },
        confirmButton = { TextButton(onClick = { vm.resetTaste(); confirmReset = false }) { Text("Reset", color = c.accent, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel", color = c.sub) } },
    )
}

/** Credits shown inside every copy of the app. */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val c = Solune.colors
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        icon = { ArchMotif(Modifier.size(84.dp, 62.dp)) },
        title = { Text("Solune", color = c.ink, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Version $version", color = c.sub, fontSize = 13.sp)
                Text("Made by ZYUAEL", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                Text("© 2026 ZYUAEL · GPL-3.0-or-later", color = c.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                Text(
                    "Free and open-source software. Built with NewPipeExtractor, AndroidX Media3, Jetpack Compose, OkHttp and Coil. Lyrics by LRCLIB.\n\nNot affiliated with YouTube, Google or Spotify.",
                    color = c.sub, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 17.sp, modifier = Modifier.padding(top = 14.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = c.accent, fontWeight = FontWeight.Bold) } },
    )
}

@Composable
private fun Tile(icon: ImageVector, bg: Color, fg: Color) {
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp))
    }
}
