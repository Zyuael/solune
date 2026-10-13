// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import com.zyuael.solune.Mix
import com.zyuael.solune.data.Album
import com.zyuael.solune.data.Artist
import com.zyuael.solune.data.Playlist
import com.zyuael.solune.data.Song

enum class Tab { Home, Search, Library }

/** Detail pages pushed on top of the current tab. */
sealed interface Route {
    data class AlbumPage(val album: Album) : Route
    data class ArtistPage(val artist: Artist) : Route
    data class PlaylistPage(val id: String) : Route
    data class MixPage(val mix: Mix) : Route
    data object LikedPage : Route
    data object DownloadsPage : Route
    data object RecentPage : Route
    data class DailyMixPage(val index: Int) : Route
}

class Navigator(
    private val tabState: MutableState<Tab>,
    val stack: SnapshotStateList<Route>,
    private val playerOpen: MutableState<Boolean>,
) {
    val tab: Tab get() = tabState.value
    val isPlayerOpen: Boolean get() = playerOpen.value

    fun select(tab: Tab) {
        if (tabState.value == tab) stack.clear() else tabState.value = tab.also { stack.clear() }
    }

    fun push(route: Route) {
        playerOpen.value = false
        stack.add(route)
    }

    /** Back: closes the player first, then pops detail pages. False when there's nothing to go back to. */
    fun back(): Boolean = when {
        playerOpen.value -> { playerOpen.value = false; true }
        stack.isNotEmpty() -> { stack.removeAt(stack.lastIndex); true }
        tabState.value != Tab.Home -> { tabState.value = Tab.Home; true }
        else -> false
    }

    fun openPlayer() {
        playerOpen.value = true
    }

    fun closePlayer() {
        playerOpen.value = false
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }

/** Opens the ⋯ song menu. [inPlaylist] adds "Remove from this playlist". */
val LocalSongMenu = staticCompositionLocalOf<(song: Song, inPlaylist: Playlist?) -> Unit> { { _, _ -> } }
