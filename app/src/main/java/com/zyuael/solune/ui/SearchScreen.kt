// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyuael.solune.MIXES
import com.zyuael.solune.MainViewModel
import com.zyuael.solune.SearchFilter
import com.zyuael.solune.ui.components.AlbumRow
import com.zyuael.solune.ui.components.ArtistRow
import com.zyuael.solune.ui.components.MixCard
import com.zyuael.solune.ui.components.RoundIcon

@Composable
fun SearchScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val c = Solune.colors
    val nav = LocalNavigator.current
    val menu = LocalSongMenu.current
    val query by vm.query.collectAsStateWithLifecycle()
    val state by vm.search.collectAsStateWithLifecycle()
    val flags = rememberSongFlags(vm)
    val listState = rememberLazyListState()

    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, state.songs.size) { if (nearEnd) vm.loadMore() }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize().background(c.background).statusBarsPadding(),
    ) {
        item {
            Text("Search", color = c.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, modifier = Modifier.padding(start = 28.dp, top = 22.dp, bottom = 16.dp))
        }
        item { SearchField(query, vm::setQuery, vm::submitQuery, vm::clearQuery) }

        if (state.submitted.isEmpty()) {
            item { Text("Browse all", color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 28.dp, top = 26.dp, bottom = 12.dp)) }
            items(MIXES.chunked(2)) { pair ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEach { mix ->
                        val i = MIXES.indexOf(mix)
                        Box(Modifier.weight(1f)) {
                            MixCard(mix, i, onClick = { nav.push(Route.MixPage(mix)) }, size = null, modifier = Modifier.fillMaxWidth().height(110.dp))
                        }
                    }
                }
            }
            return@LazyColumn
        }

        item { Filters(state.filter, vm::setFilter) }

        when {
            state.loading -> item { MotifNote("Searching…", loading = true) }
            state.error != null -> item { MotifNote("Couldn't reach YouTube Music.\n${state.error}") }
            state.filter == SearchFilter.Songs -> {
                if (state.songs.isEmpty()) item { MotifNote("No songs found.") }
                trackItems(state.songs, flags, onPlay = { vm.playSongs(state.songs, it) }, onMore = { menu(it, null) }, keyPrefix = "s", showArt = true)
                if (state.loadingMore) item {
                    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.sub, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    }
                }
            }
            state.filter == SearchFilter.Albums -> {
                if (state.albums.isEmpty()) item { MotifNote("No albums found.") }
                items(state.albums, key = { it.url }) { AlbumRow(it) { nav.push(Route.AlbumPage(it)) } }
            }
            else -> {
                if (state.artists.isEmpty()) item { MotifNote("No artists found.") }
                items(state.artists, key = { it.url }) { ArtistRow(it) { nav.push(Route.ArtistPage(it)) } }
            }
        }
    }
}

@Composable
private fun Filters(selected: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    val c = Solune.colors
    Row(Modifier.padding(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchFilter.entries.forEach { f ->
            val active = f == selected
            Text(
                f.name,
                color = if (active) c.onInk else c.sub,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) c.ink else c.background)
                    .border(1.dp, if (active) c.ink else c.faint, CircleShape)
                    .clickable { onSelect(f) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, onSearch: () -> Unit, onClear: () -> Unit) {
    val c = Solune.colors
    val focus = LocalFocusManager.current
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .softShadow(shape, 10.dp)
            .clip(shape)
            .background(c.card)
            .height(52.dp)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.sub, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("What do you want to listen to?", color = c.sub, fontSize = 14.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = c.ink, fontSize = 14.sp),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus()
                    onSearch()
                }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            RoundIcon(Icons.Rounded.Close, "Clear", {
                focus.clearFocus()
                onClear()
            }, size = 40.dp, iconSize = 18.dp, tint = c.sub)
        }
    }
}
