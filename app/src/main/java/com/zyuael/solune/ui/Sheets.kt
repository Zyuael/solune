// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyuael.solune.MainViewModel
import com.zyuael.solune.data.Artist
import com.zyuael.solune.data.Playlist
import com.zyuael.solune.data.Song
import com.zyuael.solune.playback.PlaybackService
import com.zyuael.solune.ui.components.ArchMotif
import com.zyuael.solune.ui.components.Cover

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: androidx.compose.ui.graphics.Color = Solune.colors.ink) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = Solune.colors.ink, fontSize = 15.sp)
    }
}

/** The ⋯ menu for a song: everything you can do with it, Spotify-style. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenuSheet(song: Song, inPlaylist: Playlist?, vm: MainViewModel, onDismiss: () -> Unit) {
    val c = Solune.colors
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val liked by vm.liked.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val progress by vm.downloadProgress.collectAsStateWithLifecycle()
    val isLiked = liked.any { it.id == song.id }
    val isDownloaded = downloads.any { it.id == song.id }
    var pickPlaylist by remember { mutableStateOf(false) }

    if (pickPlaylist) {
        PlaylistPickerSheet(song, vm, onDismiss = onDismiss)
        return
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.card) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 28.dp).padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(song.artworkUrl, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(song.title, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            fun act(block: () -> Unit) = { block(); onDismiss() }
            SheetAction(if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (isLiked) "Remove from Liked Songs" else "Like", act { vm.toggleLike(song) }, tint = if (isLiked) c.accent else c.ink)
            SheetAction(Icons.Rounded.SkipNext, "Play next", act { vm.playNext(song) })
            SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue", act { vm.addToQueue(song) })
            SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist", { pickPlaylist = true })
            if (inPlaylist != null) SheetAction(Icons.Rounded.PlaylistRemove, "Remove from this playlist", act { vm.removeFromPlaylist(inPlaylist, song) })
            SheetAction(Icons.Rounded.Radio, "Go to song radio", act { vm.startRadio(song) })
            SheetAction(Icons.Rounded.ThumbDownOffAlt, "Not interested", act { vm.notInterested(song) })
            when {
                isDownloaded -> SheetAction(Icons.Rounded.DownloadDone, "Remove download", act { vm.removeDownload(song) }, tint = c.accent)
                song.id in progress -> SheetAction(Icons.Rounded.Download, "Downloading… ${((progress[song.id] ?: 0f) * 100).toInt()}%", onDismiss)
                else -> SheetAction(Icons.Rounded.Download, "Download", act { vm.download(song) })
            }
            SheetAction(Icons.Rounded.Person, "Go to artist", act {
                nav.push(Route.ArtistPage(Artist(name = song.artist.split(",", "&").first().trim(), url = "", avatarUrl = null)))
            })
            SheetAction(Icons.Rounded.Share, "Share", act {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "${song.title} · ${song.artist}\n${song.watchUrl}")
                context.startActivity(Intent.createChooser(send, "Share song"))
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerSheet(song: Song, vm: MainViewModel, onDismiss: () -> Unit) {
    val c = Solune.colors
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var naming by remember { mutableStateOf(false) }

    if (naming) {
        NameDialog("New playlist", "", "Create", onDismiss = onDismiss) { name ->
            vm.createPlaylist(name, song)
            onDismiss()
        }
        return
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.card) {
        Text("Add to playlist", color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 28.dp, bottom = 8.dp))
        LazyColumn(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            item {
                SheetAction(Icons.Rounded.Add, "New playlist", { naming = true })
            }
            items(playlists, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.addToPlaylist(p, song); onDismiss() }.padding(horizontal = 28.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaylistCover(p, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(p.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("${songCount(p.songs.size)}", color = c.sub, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** First song's cover, or the brand motif for an empty playlist. */
@Composable
fun PlaylistCover(playlist: Playlist, size: androidx.compose.ui.unit.Dp) {
    val art = playlist.songs.firstOrNull()?.artworkUrl
    if (art != null) Cover(art, size)
    else Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(Solune.colors.faint.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) { ArchMotif(Modifier.size(size * 0.7f, size * 0.55f)) }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val c = Solune.colors
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(title, color = c.ink, fontWeight = FontWeight.Bold) },
        text = {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = c.ink, fontSize = 18.sp),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onConfirm(text) }),
                decorationBox = { inner ->
                    Column {
                        Box { if (text.isEmpty()) Text("My playlist", color = c.sub, fontSize = 18.sp); inner() }
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(1.5.dp).background(c.accent))
                    }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm, color = c.accent, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.sub) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val c = Solune.colors
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.card) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 28.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bedtime, null, tint = c.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text("Sleep timer", color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            listOf(5, 15, 30, 45, 60).forEach { m ->
                SheetOption("$m minutes") { vm.setSleepTimer(m); onDismiss() }
            }
            SheetOption("End of this song") { vm.setSleepTimer(PlaybackService.SLEEP_END_OF_TRACK); onDismiss() }
            if (sleep != null) SheetOption("Turn off timer", accent = true) { vm.setSleepTimer(0); onDismiss() }
        }
    }
}

@Composable
private fun SheetOption(label: String, accent: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (accent) Solune.colors.accent else Solune.colors.ink,
        fontSize = 15.sp,
        fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 15.dp),
    )
}

/** Small pill used for "Play", "Shuffle", "Download" actions on collection pages. */
@Composable
fun PillButton(label: String, icon: ImageVector, onClick: () -> Unit, filled: Boolean = false) {
    val c = Solune.colors
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (filled) c.ink else c.background)
            .then(if (filled) Modifier else Modifier.background(c.faint.copy(alpha = 0.35f)))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = if (filled) c.onInk else c.ink, modifier = Modifier.size(18.dp))
        Text(label, color = if (filled) c.onInk else c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
