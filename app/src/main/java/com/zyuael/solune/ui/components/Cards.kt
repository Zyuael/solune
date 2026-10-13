// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zyuael.solune.Mix
import com.zyuael.solune.data.Album
import com.zyuael.solune.data.Artist
import com.zyuael.solune.data.Song
import com.zyuael.solune.data.formatTime
import com.zyuael.solune.ui.Solune
import com.zyuael.solune.ui.softShadow

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    val c = Solune.colors
    Row(
        modifier.fillMaxWidth().padding(start = 28.dp, end = 20.dp, top = 26.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action != null) {
            Text(
                action, color = c.sub, fontSize = 13.sp,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** Small rounded cover image with a quiet placeholder colour. */
@Composable
fun Cover(url: String?, size: Dp, modifier: Modifier = Modifier, circle: Boolean = false) {
    val shape = if (circle) CircleShape else RoundedCornerShape(size * 0.24f)
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(shape).background(Solune.colors.faint),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    song: Song,
    active: Boolean,
    playing: Boolean,
    liked: Boolean,
    downloaded: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    showArt: Boolean = false,
    number: Int? = null,
) {
    val c = Solune.colors
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMore()
                },
            )
            .padding(start = 28.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Text("$number", color = if (active) c.accent else c.sub, fontSize = 14.sp, modifier = Modifier.width(30.dp))
        }
        if (showArt) {
            Cover(song.artworkUrl, 46.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (active) c.accent else c.ink,
                fontSize = 15.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                if (downloaded) Icon(Icons.Rounded.DownloadDone, "Downloaded", tint = c.accent, modifier = Modifier.padding(end = 4.dp).size(13.dp))
                if (liked) Icon(Icons.Rounded.Favorite, "Liked", tint = c.accent, modifier = Modifier.padding(end = 4.dp).size(12.dp))
                Text(song.artist, color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (active) EqualizerBars(playing, Modifier.padding(start = 10.dp))
        if (song.durationSec > 0) {
            Text(formatTime(song.durationSec), color = c.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp))
        }
        RoundIcon(Icons.Rounded.MoreVert, "More options", onMore, size = 40.dp, iconSize = 20.dp, tint = c.sub)
    }
}

/** Arch-shaped cover card used in horizontal shelves. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongCard(song: Song, onClick: () -> Unit, onLongClick: () -> Unit, width: Dp = 128.dp) {
    val c = Solune.colors
    Column(
        Modifier.width(width).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        ArchArtwork(song, Modifier.width(width).padding(top = 4.dp).size(width, width * 1.12f), showText = false)
        Text(song.title, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp, start = 2.dp))
        Text(song.artist, color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp))
    }
}

private val MIX_TONES = listOf(
    Color(0xFF1C1D21) to Color(0xFFC96A4B), // ink & terracotta
    Color(0xFFC96A4B) to Color(0xFFF2EEE8), // terracotta & paper
    Color(0xFFD9CBB4) to Color(0xFF1C1D21), // sand & ink
    Color(0xFF9DAA97) to Color(0xFF1C1D21), // sage & ink
    Color(0xFF5B6B7C) to Color(0xFFF2EEE8), // slate & paper
    Color(0xFF3E3A52) to Color(0xFFE08A6C), // dusk & clay
)

/** A "Made for you" tile: a little abstract poster, different for each mix. */
@Composable
fun MixCard(mix: Mix, index: Int, onClick: () -> Unit, size: Dp? = 140.dp, modifier: Modifier = Modifier) {
    val (bg, fg) = MIX_TONES[index % MIX_TONES.size]
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .then(if (size != null) Modifier.size(size) else Modifier)
            .softShadow(shape, 8.dp)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val variant = index % 4
            val sun = Offset(w * listOf(0.72f, 0.3f, 0.5f, 0.78f)[variant], h * listOf(0.3f, 0.28f, 0.42f, 0.22f)[variant])
            drawCircle(fg, w * listOf(0.16f, 0.2f, 0.26f, 0.12f)[variant], sun)
            val stroke = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round)
            when (variant) {
                0 -> repeat(3) { i -> drawLine(fg.copy(alpha = 0.6f), Offset(w * 0.12f, h * (0.5f + i * 0.07f)), Offset(w * (0.55f - i * 0.1f), h * (0.5f + i * 0.07f)), 1.6.dp.toPx(), StrokeCap.Round) }
                1 -> drawArc(fg.copy(alpha = 0.7f), 180f, 180f, false, Offset(w * 0.45f, h * 0.3f), androidx.compose.ui.geometry.Size(w * 0.4f, w * 0.4f), style = stroke)
                2 -> drawLine(fg.copy(alpha = 0.7f), Offset(w * 0.1f, h * 0.42f), Offset(w * 0.9f, h * 0.42f), 1.6.dp.toPx(), StrokeCap.Round)
                else -> repeat(2) { i -> drawCircle(fg.copy(alpha = 0.55f), w * (0.18f + i * 0.1f), Offset(w * 0.3f, h * 0.36f), style = stroke) }
            }
        }
        Text(
            mix.title.removeSuffix(" Mix"),
            color = fg,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 20.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
        )
        Text(
            "MIX", color = fg.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
        )
    }
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, width: Dp = 132.dp) {
    val c = Solune.colors
    Column(Modifier.width(width).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Cover(album.artworkUrl, width)
        Text(album.title, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(album.artist, color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AlbumRow(album: Album, onClick: () -> Unit) = ListRow(
    leading = { Cover(album.artworkUrl, 56.dp) },
    title = album.title,
    subtitle = "Album · ${album.artist}",
    onClick = onClick,
)

@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit) = ListRow(
    leading = { Cover(artist.avatarUrl, 56.dp, circle = true) },
    title = artist.name,
    subtitle = "Artist",
    onClick = onClick,
)

@Composable
fun ListRow(leading: @Composable () -> Unit, title: String, subtitle: String, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val c = Solune.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        trailing()
    }
}

/** A Daily Mix tile: a 2×2 collage of covers with a terracotta label band. */
@Composable
fun DailyMixCard(title: String, subtitle: String, songs: List<Song>, onClick: () -> Unit, size: Dp = 150.dp) {
    val c = Solune.colors
    val covers = songs.mapNotNull { it.artworkUrl }.distinct().take(4)
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.width(size).clickable(onClick = onClick)) {
        Box(Modifier.size(size).softShadow(shape, 8.dp).clip(shape).background(c.faint)) {
            Column {
                for (row in 0..1) Row {
                    for (col in 0..1) {
                        AsyncImage(
                            model = covers.getOrNull(row * 2 + col) ?: covers.firstOrNull(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(size / 2),
                        )
                    }
                }
            }
            Text(
                title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(c.accent.copy(alpha = 0.92f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
        Text(subtitle, color = c.sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp, start = 2.dp))
    }
}
