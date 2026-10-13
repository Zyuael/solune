// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyuael.solune.MainViewModel
import com.zyuael.solune.data.Song
import com.zyuael.solune.ui.components.ArchMotif
import com.zyuael.solune.ui.components.TrackRow

/** What a song row needs to know about the world, gathered once per screen. */
data class SongFlags(val liked: Set<String>, val downloaded: Set<String>, val currentId: String?, val playing: Boolean)

@Composable
fun rememberSongFlags(vm: MainViewModel): SongFlags {
    val liked by vm.liked.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val likedIds = remember(liked) { liked.mapTo(HashSet()) { it.id } }
    val downloadedIds = remember(downloads) { downloads.mapTo(HashSet()) { it.id } }
    return SongFlags(likedIds, downloadedIds, player.current?.id, player.isPlaying)
}

fun LazyListScope.trackItems(
    songs: List<Song>,
    flags: SongFlags,
    onPlay: (index: Int) -> Unit,
    onMore: (Song) -> Unit,
    keyPrefix: String,
    showArt: Boolean = false,
    numbered: Boolean = false,
) {
    itemsIndexed(songs, key = { i, s -> "$keyPrefix-${s.id}-$i" }) { i, song ->
        TrackRow(
            song = song,
            active = song.id == flags.currentId,
            playing = flags.playing,
            liked = song.id in flags.liked,
            downloaded = song.id in flags.downloaded,
            onClick = { onPlay(i) },
            onMore = { onMore(song) },
            showArt = showArt,
            number = if (numbered) i + 1 else null,
        )
    }
}

/** Calm full-width message with the brand motif; [loading] makes the sun drift. */
@Composable
fun MotifNote(text: String, loading: Boolean = false, height: Int = 220, action: (@Composable () -> Unit)? = null) {
    val c = Solune.colors
    Box(Modifier.fillMaxWidth().height(height.dp).padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ArchMotif(Modifier.size(width = 104.dp, height = 78.dp), animated = loading)
            Text(text, color = c.sub, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp, bottom = 12.dp))
            action?.invoke()
        }
    }
}

fun songCount(n: Int) = if (n == 1) "1 song" else "$n songs"
