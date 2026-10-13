// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyuael.solune.MainViewModel
import com.zyuael.solune.PlayerState
import com.zyuael.solune.Progress
import com.zyuael.solune.data.Lyrics
import com.zyuael.solune.data.LyricsRepo
import com.zyuael.solune.data.Song
import com.zyuael.solune.data.formatTime
import com.zyuael.solune.ui.components.ArcSeekArtwork
import com.zyuael.solune.ui.components.ArtMotion
import com.zyuael.solune.ui.components.Cover
import com.zyuael.solune.ui.components.EqualizerBars
import com.zyuael.solune.ui.components.RoundIcon
import com.zyuael.solune.ui.components.TransportControls
import kotlin.math.abs

@Composable
fun PlayerScreen(vm: MainViewModel, motion: ArtMotion, onClose: () -> Unit) {
    val c = Solune.colors
    val menu = LocalSongMenu.current
    val player by vm.player.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val liked by vm.liked.collectAsStateWithLifecycle()
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    val song = player.current ?: return
    val isLiked = liked.any { it.id == song.id }

    var scrub by remember { mutableStateOf<Float?>(null) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            // Become the hit target so touches don't fall through to the screen underneath.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(Icons.Rounded.KeyboardArrowDown, "Close player", onClose, iconSize = 28.dp)
            Text(
                if (showLyrics) "Lyrics" else "Now playing",
                color = c.sub, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            RoundIcon(Icons.Rounded.MoreVert, "More options", { menu(song, null) }, iconSize = 22.dp)
        }

        AnimatedContent(
            targetState = showLyrics,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "art-lyrics",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { lyricsMode ->
            if (lyricsMode) {
                Column(Modifier.fillMaxSize()) {
                    NowPlayingTitle(song, player, Modifier.padding(horizontal = 28.dp, vertical = 6.dp))
                    LyricsPanel(song, progress, vm, Modifier.weight(1f))
                    LinearSeek(progress, scrub, { scrub = it }, vm::seekTo, Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                }
            } else {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            // A soft terracotta glow that breathes with the music.
                            val k = motion.intensity
                            if (k > 0f) drawCircle(
                                Brush.radialGradient(
                                    listOf(c.accent.copy(alpha = (0.16f + 0.06f * motion.breath()) * k), c.accent.copy(alpha = 0f)),
                                    center = Offset(size.width / 2f, size.height * 0.48f),
                                    radius = size.minDimension * 0.62f,
                                ),
                                radius = size.minDimension * 0.62f,
                                center = Offset(size.width / 2f, size.height * 0.48f),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Arch + arc needs ≈ 1.3·w + 34dp of height; leave room for the time label.
                    val artWidth = min(min(maxWidth * 0.68f, (maxHeight - 34.dp - 56.dp) / 1.3f), 320.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ArcSeekArtwork(
                            song = song,
                            artWidth = artWidth,
                            progress = progress.fraction,
                            scrub = scrub,
                            onScrub = { scrub = it },
                            onSeek = vm::seekTo,
                            motion = motion,
                            onSwipe = { next -> if (next) vm.next() else vm.previousTrack() },
                        )
                        TimeLabel(progress, scrub, Modifier.padding(top = 10.dp))
                    }
                }
            }
        }

        TransportControls(
            state = player,
            onShuffle = vm::toggleShuffle,
            onPrevious = vm::previous,
            onPlayPause = vm::togglePlay,
            onNext = vm::next,
            onRepeat = vm::cycleRepeat,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        )

        val upNext = player.upNext
        Text(
            if (upNext != null) "Up next  ·  ${upNext.title}" else "End of queue",
            color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 8.dp),
        )

        val sleepLabel = sleep?.let { s ->
            if (s.endOfTrack) "End of song" else s.endsAt?.let { formatTime(((it - System.currentTimeMillis()) / 1000).coerceAtLeast(0)) }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            QuickAction(Icons.Rounded.Lyrics, "Lyrics", showLyrics) { showLyrics = !showLyrics }
            QuickAction(Icons.Rounded.Bedtime, sleepLabel ?: "Sleep", sleep != null) { showSleep = true }
            QuickAction(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", false) { showQueue = true }
            QuickAction(if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (isLiked) "Liked" else "Like", isLiked) { vm.toggleLike(song) }
        }
    }

    if (showQueue) QueueSheet(vm, player) { showQueue = false }
    if (showSleep) SleepTimerSheet(vm) { showSleep = false }
}

@Composable
private fun NowPlayingTitle(song: Song, player: PlayerState, modifier: Modifier = Modifier) {
    val c = Solune.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Cover(song.artworkUrl, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        EqualizerBars(player.isPlaying)
    }
}

@Composable
private fun TimeLabel(progress: Progress, scrub: Float?, modifier: Modifier = Modifier) {
    val c = Solune.colors
    val shownMs = scrub?.let { (it * progress.durationMs).toLong() } ?: progress.positionMs
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(shownMs / 1000), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("  /  " + formatTime(progress.durationMs / 1000), color = c.sub, fontSize = 13.sp)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val c = Solune.colors
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (active) c.accent else c.sub, modifier = Modifier.size(22.dp))
        Text(label, color = if (active) c.accent else c.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

/** Straight seek bar used under the lyrics. */
@Composable
private fun LinearSeek(progress: Progress, scrub: Float?, onScrub: (Float?) -> Unit, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    val c = Solune.colors
    val shown = scrub ?: progress.fraction
    var width by remember { mutableFloatStateOf(1f) }
    val currentScrub by rememberUpdatedState(scrub)
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    width = size.width.toFloat()
                    detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onScrub((it.x / size.width).coerceIn(0f, 1f)) },
                        onDragEnd = { currentScrub?.let(onSeek); onScrub(null) },
                        onDragCancel = { onScrub(null) },
                    ) { change, _ -> onScrub((change.position.x / size.width).coerceIn(0f, 1f)) }
                }
                .drawBehind {
                    val y = size.height / 2
                    drawLine(c.faint, Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(c.ink, Offset(0f, y), Offset(size.width * shown, y), 3.5.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                    drawCircle(c.ink, 6.dp.toPx(), Offset(size.width * shown, y))
                },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(((scrub ?: progress.fraction) * progress.durationMs / 1000).toLong()), color = c.sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(formatTime(progress.durationMs / 1000), color = c.sub, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LyricsPanel(song: Song, progress: Progress, vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = Solune.colors
    val lyrics by produceState<Lyrics?>(null, song.id) { value = null; value = LyricsRepo.get(song) }
    val current = lyrics

    Box(modifier.fillMaxWidth().clipToBounds()) {
        when {
            current == null -> MotifNote("Finding lyrics…", loading = true)
            current.isEmpty -> MotifNote("No lyrics found for this song.")
            !current.synced.isNullOrEmpty() -> {
                val lines = current.synced!!
                val active = lines.indexOfLast { it.timeMs <= progress.positionMs + 250 }.coerceAtLeast(0)
                val listState = rememberLazyListState()
                LaunchedEffect(active) { listState.animateScrollToItem((active - 2).coerceAtLeast(0)) }
                LazyColumn(state = listState, contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 200.dp)) {
                    itemsIndexed(lines) { i, line ->
                        val isActive = i == active
                        val alpha by animateFloatAsState(if (isActive) 1f else if (i < active) 0.38f else 0.55f, label = "line")
                        Text(
                            line.text.ifBlank { "♪" },
                            color = if (isActive) c.ink else c.sub,
                            fontSize = if (isActive) 24.sp else 21.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                            lineHeight = 30.sp,
                            modifier = Modifier
                                .graphicsLayer { this.alpha = alpha }
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { vm.seekToMs(line.timeMs) }
                                .padding(vertical = 8.dp),
                        )
                    }
                    item {
                        Text("Lyrics from LRCLIB", color = c.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 18.dp))
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 20.dp)) {
                Text(current.plain.orEmpty(), color = c.ink, fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
                Text("Not time-synced · Lyrics from LRCLIB", color = c.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 18.dp, bottom = 40.dp))
            }
        }
    }
}

/** The play queue: tap to jump, drag ≡ to reorder, × to remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(vm: MainViewModel, player: PlayerState, onDismiss: () -> Unit) {
    val c = Solune.colors
    val rowHeight = with(LocalDensity.current) { 64.dp.toPx() }
    // Stable keys even when a song appears twice.
    val keys = remember(player.queue) {
        val seen = HashMap<String, Int>()
        player.queue.map { s -> "${s.id}#${seen.merge(s.id, 1, Int::plus)}" }
    }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val queueSize by rememberUpdatedState(player.queue.size)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.card) {
        Row(Modifier.padding(start = 28.dp, end = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Queue", color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${songCount(player.queue.size)}", color = c.sub, fontSize = 12.sp)
        }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (player.index - 1).coerceAtLeast(0))
        LazyColumn(state = listState, modifier = Modifier.navigationBarsPadding()) {
            itemsIndexed(player.queue, key = { i, _ -> keys.getOrElse(i) { "$i" } }) { i, s ->
                val key = keys.getOrElse(i) { "$i" }
                val index by rememberUpdatedState(i)
                val isCurrent = i == player.index
                val dragging = dragKey == key
                Row(
                    Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f; shadowElevation = if (dragging) 12f else 0f }
                        .background(if (dragging) c.background else c.card)
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { vm.playQueueIndex(i) }
                        .padding(start = 24.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(s.artworkUrl, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, color = if (isCurrent) c.accent else c.ink, fontSize = 14.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.artist, color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (isCurrent) EqualizerBars(player.isPlaying, Modifier.padding(horizontal = 8.dp))
                    else RoundIcon(Icons.Rounded.Close, "Remove from queue", { vm.removeFromQueue(i) }, size = 38.dp, iconSize = 18.dp, tint = c.sub)
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .pointerInput(key) {
                                var dragIndex = index
                                detectDragGestures(
                                    onDragStart = { dragKey = key; dragOffset = 0f; dragIndex = index },
                                    onDragEnd = { dragKey = null; dragOffset = 0f },
                                    onDragCancel = { dragKey = null; dragOffset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    // Each full row of travel swaps with the neighbour.
                                    if (abs(dragOffset) > rowHeight * 0.6f) {
                                        val dir = if (dragOffset > 0) 1 else -1
                                        val target = dragIndex + dir
                                        if (target in 0 until queueSize) {
                                            vm.moveInQueue(dragIndex, target)
                                            dragIndex = target // track it ourselves; the list catches up a frame later
                                            dragOffset -= dir * rowHeight
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.DragHandle, "Drag to reorder", tint = c.sub, modifier = Modifier.size(20.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
