// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class SolunePalette(
    val background: Color,
    val card: Color,
    val ink: Color,
    val onInk: Color,
    val sub: Color,
    val faint: Color,
    val shadow: Color,
    /** The single art accent: a muted terracotta "sun". Use sparingly. */
    val accent: Color,
    val isDark: Boolean,
)

private val Light = SolunePalette(
    background = Color(0xFFEDEEF1),
    card = Color(0xFFFAFAFB),
    ink = Color(0xFF121316),
    onInk = Color(0xFFFFFFFF),
    sub = Color(0xFF8B8F98),
    faint = Color(0xFFD3D6DC),
    shadow = Color(0xFF8A93A6),
    accent = Color(0xFFC96A4B),
    isDark = false,
)

private val Dark = SolunePalette(
    background = Color(0xFF111214),
    card = Color(0xFF1C1D20),
    ink = Color(0xFFF1F1F3),
    onInk = Color(0xFF111214),
    sub = Color(0xFF8C9099),
    faint = Color(0xFF34363B),
    shadow = Color(0xFF000000),
    accent = Color(0xFFE08A6C),
    isDark = true,
)

private val LocalPalette = staticCompositionLocalOf { Light }

object Solune {
    val colors: SolunePalette
        @Composable get() = LocalPalette.current
}

@Composable
fun SoluneTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) Dark else Light
    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.ink, onPrimary = palette.onInk,
            background = palette.background, surface = palette.card,
            onBackground = palette.ink, onSurface = palette.ink,
        )
    } else {
        lightColorScheme(
            primary = palette.ink, onPrimary = palette.onInk,
            background = palette.background, surface = palette.card,
            onBackground = palette.ink, onSurface = palette.ink,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}

/** The soft, diffuse drop shadow used on every raised surface. */
@Composable
fun Modifier.softShadow(shape: Shape, elevation: Dp = 16.dp): Modifier {
    val c = Solune.colors
    return shadow(elevation, shape, clip = false, ambientColor = c.shadow.copy(alpha = 0.25f), spotColor = c.shadow.copy(alpha = 0.35f))
}
