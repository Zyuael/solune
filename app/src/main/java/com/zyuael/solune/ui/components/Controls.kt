// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.zyuael.solune.PlayerState
import com.zyuael.solune.ui.Solune
import com.zyuael.solune.ui.softShadow

@Composable
fun RoundIcon(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    tint: androidx.compose.ui.graphics.Color = Solune.colors.ink,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun PlayButton(isPlaying: Boolean, isBuffering: Boolean, onClick: () -> Unit, size: Dp) {
    val c = Solune.colors
    Box(
        Modifier
            .softShadow(CircleShape, 14.dp)
            .size(size)
            .clip(CircleShape)
            .background(c.ink)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = if (isBuffering) 2 else if (isPlaying) 1 else 0,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.7f)) togetherWith fadeOut() },
            label = "play",
        ) { state ->
            when (state) {
                2 -> CircularProgressIndicator(
                    color = c.onInk, strokeWidth = 2.dp, modifier = Modifier.size(size * 0.36f),
                )
                1 -> Icon(Icons.Rounded.Pause, "Pause", tint = c.onInk, modifier = Modifier.size(size * 0.46f))
                else -> Icon(Icons.Rounded.PlayArrow, "Play", tint = c.onInk, modifier = Modifier.size(size * 0.5f))
            }
        }
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    val c = Solune.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundIcon(icon, description, onClick, iconSize = 21.dp, tint = if (active) c.ink else c.sub)
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (active) c.ink else androidx.compose.ui.graphics.Color.Transparent)
        )
    }
}

/**
 * Shuffle · [prev ● next] · repeat. The full variant wraps the centre trio in a raised pill,
 * the compact one (mini bar) keeps it flat.
 */
@Composable
fun TransportControls(
    state: PlayerState,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val c = Solune.colors
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleIcon(Icons.Rounded.Shuffle, "Shuffle", state.shuffle, onShuffle)

        val trio: @Composable () -> Unit = {
            RoundIcon(Icons.Rounded.SkipPrevious, "Previous", onPrevious, size = if (compact) 44.dp else 52.dp)
            PlayButton(state.isPlaying, state.isBuffering, onPlayPause, if (compact) 50.dp else 66.dp)
            RoundIcon(Icons.Rounded.SkipNext, "Next", onNext, size = if (compact) 44.dp else 52.dp)
        }
        if (compact) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) { trio() }
        } else {
            val pill = RoundedCornerShape(50)
            Row(
                Modifier
                    .softShadow(pill, 12.dp)
                    .clip(pill)
                    .background(c.card)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { trio() }
        }

        ToggleIcon(
            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            "Repeat",
            state.repeatMode != Player.REPEAT_MODE_OFF,
            onRepeat,
        )
    }
}

/** Three little bars that bounce while the row's track is playing. */
@Composable
fun EqualizerBars(playing: Boolean, modifier: Modifier = Modifier) {
    val c = Solune.colors
    val transition = rememberInfiniteTransition(label = "eq")
    val heights = listOf(520, 380, 640).map { duration ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(duration), RepeatMode.Reverse),
            label = "bar",
        )
    }
    Row(
        modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.forEachIndexed { i, h ->
            val fraction = if (playing) h.value else listOf(0.45f, 0.8f, 0.6f)[i]
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.ink)
            )
        }
    }
}
