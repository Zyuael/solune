// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zyuael.solune.ui.Solune

/*
 * Solune's art language: a handful of mid-century shapes (a round-topped arch, a "sun",
 * a horizon line, dotted rings) in ink plus one terracotta accent. Keep it sparse.
 */

/** Round-topped arch standing on [bottom], spanning [left]..[right]. */
private fun archPath(left: Float, right: Float, top: Float, bottom: Float) = Path().apply {
    val r = (right - left) / 2f
    moveTo(left, bottom)
    lineTo(left, top + r)
    arcTo(Rect(left, top, right, top + 2 * r), 180f, 180f, false)
    lineTo(right, bottom)
    close()
}

/**
 * The brand motif (same composition as the launcher icon): a sun setting behind an arch on
 * a horizon. With [animated] the sun drifts gently, which doubles as a calm loading indicator.
 */
@Composable
fun ArchMotif(modifier: Modifier = Modifier, animated: Boolean = false, motion: ArtMotion? = null) {
    val c = Solune.colors
    val loading = if (animated) {
        rememberInfiniteTransition(label = "sun").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "drift",
        )
    } else null

    Canvas(modifier) {
        // While music plays the little sun rises and sets with the beat of the clock.
        val drift = loading?.value ?: (0.5f + (motion?.let { sin(it.time * 1.6f) * 0.45f * it.intensity } ?: 0f))
        val w = size.width
        val h = size.height
        val horizon = h * 0.88f
        val stroke = 1.6.dp.toPx()

        // Sun first, so the arch sits in front of it like a sunset.
        val sunR = h * 0.16f
        drawCircle(c.accent, sunR, Offset(w * 0.72f, h * (0.22f + 0.16f * drift)))

        val left = w * 0.24f
        val right = w * 0.60f
        drawPath(archPath(left, right, h * 0.20f, horizon), c.ink)
        val inset = (right - left) * 0.28f
        drawPath(archPath(left + inset, right - inset, h * 0.20f + inset * 1.6f, horizon), c.background)

        drawLine(c.ink, Offset(w * 0.04f, horizon), Offset(w * 0.96f, horizon), stroke, StrokeCap.Round)
        // Three small footprints of rhythm.
        repeat(3) { i ->
            drawCircle(c.ink.copy(alpha = 0.35f), 1.6.dp.toPx(), Offset(w * (0.72f + i * 0.08f), horizon + h * 0.08f))
        }
    }
}

/**
 * A clock that only runs while music plays. [time] advances in seconds, scaled by
 * [intensity], so when playback pauses everything glides to rest instead of freezing.
 */
@Stable
class ArtMotion internal constructor(private val timeState: State<Float>, private val intensityState: State<Float>) {
    val time: Float get() = timeState.value
    val intensity: Float get() = intensityState.value

    /** -1..1 breathing wave (period ≈ 2.6 s), already faded by intensity. */
    fun breath(): Float = sin(time * 2f * PI.toFloat() / 2.6f) * intensity
}

@Composable
fun rememberArtMotion(playing: Boolean): ArtMotion {
    val intensity = animateFloatAsState(if (playing) 1f else 0f, tween(1100), label = "intensity")
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        var last = 0L
        while (playing || intensity.value > 0.001f) {
            withFrameNanos { now ->
                if (last != 0L) time.floatValue += (now - last) / 1_000_000_000f * intensity.value
                last = now
            }
        }
    }
    return remember { ArtMotion(time, intensity) }
}

/**
 * Ornament drawn behind an arch artwork card (this draw area): a terracotta sun peeking from
 * behind the top-right corner and a dotted ring echoing the card's round bottom, [ringGap]
 * outside it. With [motion] the ring turns, the sun drifts and soft rings pulse outward.
 */
fun DrawScope.drawArchOrnament(
    accent: Color,
    ring: Color,
    ringGap: Dp,
    motion: ArtMotion? = null,
    sunRadius: Dp = 18.dp,
) {
    val w = size.width
    val h = size.height
    val r = w / 2f
    val center = Offset(w / 2f, h - r)
    val t = motion?.time ?: 0f
    val k = motion?.intensity ?: 0f

    // Pulses: two rings swelling out from the card's curve, like sound leaving a speaker.
    if (k > 0f) repeat(2) { i ->
        val p = ((t / 2.4f) + i * 0.5f) % 1f
        val pr = r + 4.dp.toPx() + p * ringGap.toPx() * 1.4f
        drawArc(
            color = accent.copy(alpha = (1f - p) * 0.28f * k),
            startAngle = 190f, sweepAngle = -200f, useCenter = false,
            topLeft = Offset(center.x - pr, center.y - pr), size = Size(pr * 2, pr * 2),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }

    val drift = sin(t * 1.3f) * 5.dp.toPx() * k
    drawCircle(accent, sunRadius.toPx() * (1f + 0.06f * sin(t * 2.1f) * k), Offset(w - 6.dp.toPx() + drift * 0.4f, 10.dp.toPx() + drift))

    val ringR = r + ringGap.toPx()
    rotate(degrees = t * 14f, pivot = center) {
        drawArc(
            color = ring,
            startAngle = 200f,
            sweepAngle = -220f,
            useCenter = false,
            topLeft = Offset(center.x - ringR, center.y - ringR),
            size = Size(ringR * 2, ringR * 2),
            style = Stroke(
                width = 1.4.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 7.dp.toPx())),
            ),
        )
    }
}
