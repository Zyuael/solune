// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import coil.compose.AsyncImage
import com.zyuael.solune.data.Song
import com.zyuael.solune.ui.Solune
import com.zyuael.solune.ui.softShadow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Rounded top, fully semicircular bottom: the "arch" card from the reference design. */
class ArchShape(private val topCorner: Dp = 30.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val r = w / 2f
        val tc = with(density) { topCorner.toPx() }.coerceAtMost(w / 2f)
        val path = Path().apply {
            moveTo(0f, h - r)
            lineTo(0f, tc)
            arcTo(Rect(0f, 0f, 2 * tc, 2 * tc), 180f, 90f, false)
            lineTo(w - tc, 0f)
            arcTo(Rect(w - 2 * tc, 0f, w, 2 * tc), 270f, 90f, false)
            lineTo(w, h - r)
            arcTo(Rect(0f, h - 2 * r, w, h), 0f, 180f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun ArchArtwork(
    song: Song,
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 20.sp,
    /** When set, draws the sun + dotted-ring ornament this far outside the card's curve. */
    ornamentGap: Dp? = null,
    /** While music plays: the card breathes, the ring turns, the sun drifts, rings pulse outward. */
    motion: ArtMotion? = null,
    showText: Boolean = true,
) {
    val shape = ArchShape()
    val c = Solune.colors
    Box(
        modifier.drawBehind {
            if (ornamentGap != null) drawArchOrnament(c.accent, c.sub.copy(alpha = 0.55f), ornamentGap, motion)
        },
    ) {
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                val s = 1f + (motion?.breath() ?: 0f) * 0.022f
                scaleX = s
                scaleY = s
            }
            .softShadow(shape, 22.dp)
            .clip(shape)
            .background(Color(0xFF1E2A38)),
    ) {
        // A flaky network shouldn't leave the card blank for good: retry a couple of times.
        var attempt by remember(song.artworkUrl) { mutableIntStateOf(0) }
        val context = LocalContext.current
        AsyncImage(
            model = remember(song.artworkUrl, attempt) {
                ImageRequest.Builder(context)
                    .data(song.artworkUrl)
                    .setParameter("attempt", attempt, memoryCacheKey = null)
                    .crossfade(true)
                    .build()
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = {
                Log.w("Solune", "Artwork failed (attempt $attempt) for ${song.artworkUrl}", it.result.throwable)
                if (attempt < 2) attempt++
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (showText) Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to Color(0xE6101622),
                    )
                )
        )
        if (showText) Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 28.dp, end = 28.dp, bottom = 44.dp),
        ) {
            Text(
                song.title,
                color = Color.White,
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = titleSize * 1.15f,
            )
            Text(
                song.artist,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    }
}

private const val ARC_START = 200f   // degrees, canvas convention (clockwise from 3 o'clock)
private const val ARC_SWEEP = 220f   // runs counter-clockwise under the arch

/**
 * Arch artwork wrapped by a curved seek bar that hugs its rounded bottom.
 * [scrub] is the in-progress drag position (null when idle).
 */
@Composable
fun ArcSeekArtwork(
    song: Song,
    artWidth: Dp,
    progress: Float,
    scrub: Float?,
    onScrub: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    motion: ArtMotion? = null,
    /** Swipe the cover: true = next song (swiped left), false = previous (swiped right). */
    onSwipe: ((next: Boolean) -> Unit)? = null,
) {
    val c = Solune.colors
    val gap = 20.dp
    val knob = 9.dp
    val artHeight = artWidth * 1.3f
    val radius = artWidth / 2 + gap
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val shown = scrub ?: progress
    val swipe = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier
            .size(radius * 2 + knob * 2 + 8.dp, artHeight + gap + knob + 6.dp)
            // Runs after the seek arc in the main pass, so a drag on the arc (consumed) never swipes.
            .pointerInput(Unit) {
                val threshold = size.width * 0.22f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val v = swipe.value
                            val handler = currentOnSwipe
                            if (handler != null && kotlin.math.abs(v) > threshold) {
                                val dir = if (v < 0) -1f else 1f
                                swipe.animateTo(dir * size.width, tween(170))
                                handler(v < 0)
                                swipe.snapTo(-dir * size.width * 0.5f) // new cover enters from the other side
                            }
                            swipe.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 300f))
                        }
                    },
                    onDragCancel = { scope.launch { swipe.animateTo(0f, spring()) } },
                ) { change, amount ->
                    if (currentOnSwipe == null) return@detectHorizontalDragGestures
                    change.consume()
                    scope.launch { swipe.snapTo(swipe.value + amount) }
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        ArchArtwork(
            song,
            Modifier
                .size(artWidth, artHeight)
                .graphicsLayer {
                    val w = size.width.coerceAtLeast(1f)
                    translationX = swipe.value
                    rotationZ = swipe.value / w * 6f
                    alpha = 1f - (kotlin.math.abs(swipe.value) / w * 0.55f).coerceAtMost(0.7f)
                },
            titleSize = 22.sp, ornamentGap = gap + 14.dp, motion = motion,
        )

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(artWidth) {
                    val cy = (artHeight - artWidth / 2).toPx()
                    val r = radius.toPx()
                    val touchSlop = 28.dp.toPx()

                    fun fractionAt(p: Offset): Float {
                        var a = Math.toDegrees(atan2(p.y - cy, p.x - size.width / 2f).toDouble()).toFloat()
                        if (a < -90f) a += 360f
                        return ((ARC_START - a) / ARC_SWEEP).coerceIn(0f, 1f)
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = hypot(down.position.x - size.width / 2f, down.position.y - cy)
                        val nearArc = d in (r - touchSlop)..(r + touchSlop) && down.position.y > cy - r * 0.45f
                        if (!nearArc) return@awaitEachGesture
                        down.consume()
                        var value = fractionAt(down.position)
                        currentOnScrub(value)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            value = fractionAt(change.position)
                            currentOnScrub(value)
                            change.consume()
                        }
                        currentOnSeek(value)
                        currentOnScrub(null)
                    }
                },
        ) {
            val cx = size.width / 2f
            val cy = (artHeight - artWidth / 2).toPx()
            val r = radius.toPx()
            val topLeft = Offset(cx - r, cy - r)
            val arcSize = Size(2 * r, 2 * r)

            drawArc(
                c.faint, ARC_START, -ARC_SWEEP, false, topLeft, arcSize,
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                c.ink, ARC_START, -ARC_SWEEP * shown, false, topLeft, arcSize,
                style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round),
            )

            val angle = Math.toRadians((ARC_START - ARC_SWEEP * shown).toDouble())
            val knobCenter = Offset(cx + r * cos(angle).toFloat(), cy + r * sin(angle).toFloat())
            drawCircle(c.card, knob.toPx(), knobCenter)
            drawCircle(c.ink, knob.toPx(), knobCenter, style = Stroke(3.dp.toPx()))
        }
    }
}
