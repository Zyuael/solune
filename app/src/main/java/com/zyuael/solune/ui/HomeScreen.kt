// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyuael.solune.MIXES
import com.zyuael.solune.MainViewModel
import com.zyuael.solune.TRENDING
import com.zyuael.solune.ThemeMode
import com.zyuael.solune.ui.components.ArchArtwork
import com.zyuael.solune.ui.components.ArchMotif
import com.zyuael.solune.ui.components.ArtMotion
import com.zyuael.solune.ui.components.DailyMixCard
import androidx.compose.runtime.LaunchedEffect
import com.zyuael.solune.ui.components.MixCard
import com.zyuael.solune.ui.components.RoundIcon
import com.zyuael.solune.ui.components.SectionHeader
import com.zyuael.solune.ui.components.SongCard
import java.time.LocalTime

@Composable
fun HomeScreen(vm: MainViewModel, motion: ArtMotion, contentPadding: PaddingValues) {
    val c = Solune.colors
    val nav = LocalNavigator.current
    val menu = LocalSongMenu.current
    val home by vm.home.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val flags = rememberSongFlags(vm)
    val hero = player.current ?: home.trending.firstOrNull()
    LaunchedEffect(Unit) { vm.refreshDailyMixes() }

    LazyColumn(
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize().background(c.background).statusBarsPadding(),
    ) {
        item { Header(themeMode, vm::cycleTheme, motion) }

        if (hero != null) item {
            Box(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                ArchArtwork(
                    hero,
                    ornamentGap = 16.dp,
                    motion = if (player.current != null) motion else null,
                    modifier = Modifier
                        .width(224.dp)
                        .height(258.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (player.current != null) nav.openPlayer() else vm.playSongs(home.trending, 0)
                        },
                )
            }
        }

        if (recent.isNotEmpty()) {
            item { SectionHeader("Jump back in", action = "See all") { nav.push(Route.RecentPage) } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(recent.take(12), key = { i, s -> "r-${s.id}-$i" }) { i, s ->
                        SongCard(s, onClick = { vm.playSongs(recent, i) }, onLongClick = { menu(s, null) })
                    }
                }
            }
        }

        if (home.dailyMixes.isNotEmpty()) {
            item { SectionHeader("Your Daily Mixes") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(home.dailyMixes, key = { _, m -> m.title }) { i, mix ->
                        DailyMixCard(mix.title, mix.subtitle, mix.songs, onClick = { nav.push(Route.DailyMixPage(i)) })
                    }
                }
            }
        }

        item { SectionHeader("Made for you") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                itemsIndexed(MIXES, key = { _, m -> m.title }) { i, mix ->
                    MixCard(mix, i, onClick = { nav.push(Route.MixPage(mix)) })
                }
            }
        }

        val seed = home.forYouSeed
        if (seed != null && home.forYou.isNotEmpty()) {
            item { SectionHeader("Because you like ${seed.artist.split(",", "&").first().trim()}") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(home.forYou.take(15), key = { i, s -> "f-${s.id}-$i" }) { i, s ->
                        SongCard(s, onClick = { vm.playSongs(home.forYou, i) }, onLongClick = { menu(s, null) })
                    }
                }
            }
        }

        item { SectionHeader(TRENDING.title, action = "See all") { nav.push(Route.MixPage(TRENDING)) } }
        when {
            home.trendingLoading -> item { MotifNote("Tuning in…", loading = true, height = 180) }
            home.trendingError != null -> item {
                MotifNote("Couldn't reach YouTube Music", height = 220) {
                    Text(
                        "Try again", color = c.onInk, fontSize = 13.sp,
                        modifier = Modifier.clip(CircleShape).background(c.ink).clickable(onClick = vm::loadHome).padding(horizontal = 18.dp, vertical = 9.dp),
                    )
                }
            }
            else -> trackItems(home.trending.take(10), flags, onPlay = { vm.playSongs(home.trending, it) }, onMore = { menu(it, null) }, keyPrefix = "t", showArt = true)
        }
    }
}

@Composable
private fun Header(themeMode: ThemeMode, onCycleTheme: () -> Unit, motion: ArtMotion) {
    val c = Solune.colors
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 24.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(greeting, color = c.sub, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("solune", color = c.ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                Text(".", color = c.accent, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            RoundIcon(
                icon = when (themeMode) {
                    ThemeMode.System -> Icons.Rounded.BrightnessAuto
                    ThemeMode.Light -> Icons.Rounded.LightMode
                    ThemeMode.Dark -> Icons.Rounded.DarkMode
                },
                contentDescription = "Theme: ${themeMode.label}. Tap to change.",
                onClick = onCycleTheme,
                size = 40.dp,
                iconSize = 20.dp,
                tint = c.sub,
            )
            ArchMotif(Modifier.size(width = 78.dp, height = 58.dp).padding(bottom = 6.dp), motion = motion)
        }
    }
}
