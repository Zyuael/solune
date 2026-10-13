// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zyuael.solune.data.Playlist
import com.zyuael.solune.data.Song
import com.zyuael.solune.ui.CollectionScreen
import com.zyuael.solune.ui.HomeScreen
import com.zyuael.solune.ui.LibraryScreen
import com.zyuael.solune.ui.LocalNavigator
import com.zyuael.solune.ui.LocalSongMenu
import com.zyuael.solune.ui.Solune
import com.zyuael.solune.ui.SoluneTheme
import com.zyuael.solune.ui.Navigator
import com.zyuael.solune.ui.PlayerScreen
import com.zyuael.solune.ui.Route
import com.zyuael.solune.ui.SearchScreen
import com.zyuael.solune.ui.SongMenuSheet
import com.zyuael.solune.ui.Tab
import com.zyuael.solune.ui.components.ArtMotion
import com.zyuael.solune.ui.components.Cover
import com.zyuael.solune.ui.components.rememberArtMotion
import com.zyuael.solune.ui.softShadow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Keep status/navigation bar icons readable against the chosen theme.
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            SoluneTheme(dark = dark) { SoluneRoot(vm) }
        }
    }
}

private val NAV_HEIGHT = 64.dp
private val MINI_HEIGHT = 68.dp

@Composable
private fun SoluneRoot(vm: MainViewModel) {
    val context = LocalContext.current
    val c = Solune.colors
    val player by vm.player.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()

    val tabState = rememberSaveable { mutableStateOf(Tab.Home) }
    val playerOpen = rememberSaveable { mutableStateOf(false) }
    val stack = remember { mutableStateListOf<Route>() }
    val nav = remember { Navigator(tabState, stack, playerOpen) }
    val motion = rememberArtMotion(player.isPlaying)
    var menuFor by remember { mutableStateOf<Pair<Song, Playlist?>?>(null) }
    val pages = rememberSaveableStateHolder()
    val routesByKey = remember { mutableMapOf<String, Route>() }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    BackHandler(enabled = playerOpen.value || stack.isNotEmpty() || tabState.value != Tab.Home) { nav.back() }

    CompositionLocalProvider(
        LocalNavigator provides nav,
        LocalSongMenu provides { song, inPlaylist -> menuFor = song to inPlaylist },
    ) {
        Box(Modifier.fillMaxSize().background(c.background)) {
            val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val bottom = NAV_HEIGHT + systemBottom + (if (player.current != null) MINI_HEIGHT else 0.dp) + 16.dp
            val padding = PaddingValues(bottom = bottom)

            // Each tab and page keeps its own scroll position.
            val screenKey = stack.lastOrNull()?.let { "${tabState.value}/${stack.size}/$it" } ?: tabState.value.name
            stack.lastOrNull()?.let { routesByKey[screenKey] = it }
            Crossfade(targetState = screenKey, animationSpec = tween(220), label = "screens") { key ->
                pages.SaveableStateProvider(key) {
                    val route = routesByKey[key]
                    when {
                        route != null -> CollectionScreen(route, vm, motion, padding)
                        key == Tab.Search.name -> SearchScreen(vm, padding)
                        key == Tab.Library.name -> LibraryScreen(vm, padding)
                        key == Tab.Home.name -> HomeScreen(vm, motion, padding)
                        else -> Box(Modifier.fillMaxSize().background(c.background)) // page fading out
                    }
                }
            }

            Column(Modifier.align(Alignment.BottomCenter)) {
                AnimatedVisibility(
                    visible = player.current != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    player.current?.let { song ->
                        MiniPlayer(song, player.isPlaying, player.isBuffering, progress.fraction, motion, vm, onOpen = nav::openPlayer)
                    }
                }
                BottomBar(nav)
            }

            AnimatedVisibility(
                visible = playerOpen.value && player.current != null,
                enter = slideInVertically(tween(380)) { it } + fadeIn(tween(240)),
                exit = slideOutVertically(tween(320)) { it } + fadeOut(tween(200)),
            ) {
                PlayerScreen(vm, motion, onClose = nav::closePlayer)
            }

            menuFor?.let { (song, playlist) -> SongMenuSheet(song, playlist, vm) { menuFor = null } }
        }
    }
}

@Composable
private fun MiniPlayer(
    song: Song,
    playing: Boolean,
    buffering: Boolean,
    fraction: Float,
    motion: ArtMotion,
    vm: MainViewModel,
    onOpen: () -> Unit,
) {
    val c = Solune.colors
    val shape = RoundedCornerShape(22.dp)
    val swipe = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(MINI_HEIGHT - 8.dp)
            .softShadow(shape, 14.dp)
            .clip(shape)
            .background(c.card)
            .clickable(onClick = onOpen)
            // Swipe the bar sideways to change song, like the album cover in the player.
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val v = swipe.value
                            if (kotlin.math.abs(v) > size.width * 0.25f) {
                                if (v < 0) vm.next() else vm.previousTrack()
                                swipe.snapTo(-v * 0.4f)
                            }
                            swipe.animateTo(0f, spring(dampingRatio = 0.8f))
                        }
                    },
                    onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch { swipe.snapTo(swipe.value + amount) }
                }
            },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = swipe.value; alpha = 1f - (kotlin.math.abs(swipe.value) / size.width.coerceAtLeast(1f)).coerceAtMost(0.6f) }
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Cover(song.artworkUrl, 44.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(c.ink).clickable(onClick = vm::togglePlay),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    buffering -> CircularProgressIndicator(color = c.onInk, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    playing -> Icon(Icons.Rounded.Pause, "Pause", tint = c.onInk, modifier = Modifier.size(22.dp))
                    else -> Icon(Icons.Rounded.PlayArrow, "Play", tint = c.onInk, modifier = Modifier.size(24.dp))
                }
            }
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = vm::next), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SkipNext, "Next", tint = c.ink, modifier = Modifier.size(24.dp))
            }
        }
        // Progress hairline along the bottom edge, in the accent colour.
        Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp).fillMaxWidth().height(2.dp).clip(CircleShape).background(c.faint)) {
            Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(c.accent))
        }
    }
}

@Composable
private fun BottomBar(nav: Navigator) {
    val c = Solune.colors
    // Elevated so shadowed cards scrolling underneath can't draw over it.
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.faint.copy(alpha = 0.6f)))
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { shadowElevation = 0.1f }
            .background(c.background)
            .navigationBarsPadding()
            .height(NAV_HEIGHT),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomItem(Icons.Rounded.Home, "Home", nav.tab == Tab.Home) { nav.select(Tab.Home) }
        BottomItem(Icons.Rounded.Search, "Search", nav.tab == Tab.Search) { nav.select(Tab.Search) }
        BottomItem(Icons.Rounded.LibraryMusic, "Library", nav.tab == Tab.Library) { nav.select(Tab.Library) }
    }
}

@Composable
private fun BottomItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val c = Solune.colors
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (active) c.ink else c.sub, modifier = Modifier.size(24.dp))
        Text(label, color = if (active) c.ink else c.sub, fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
        Box(Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape).background(if (active) c.accent else c.background.copy(alpha = 0f)))
    }
}
