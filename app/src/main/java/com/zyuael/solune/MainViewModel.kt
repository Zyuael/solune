// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.zyuael.solune.data.Album
import com.zyuael.solune.data.Artist
import com.zyuael.solune.data.Downloads
import com.zyuael.solune.data.Library
import com.zyuael.solune.data.Playlist
import com.zyuael.solune.data.Song
import com.zyuael.solune.data.Taste
import com.zyuael.solune.data.YouTubeMusic
import com.zyuael.solune.playback.PlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Year

data class PlayerState(
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
    val index: Int = 0,
    /** What actually plays next, honouring shuffle and repeat. */
    val upNext: Song? = null,
)

data class Progress(val positionMs: Long = 0, val durationMs: Long = 0) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

enum class ThemeMode(val label: String) { System("Follow system"), Light("Light"), Dark("Dark") }

/** A mood "mix": a named search that behaves like a Made-for-you playlist. */
data class Mix(val title: String, val query: String)

val MIXES = listOf(
    Mix("Chill Mix", "chill songs"),
    Mix("Lo-fi Mix", "lofi beats"),
    Mix("Bollywood Mix", "bollywood hits"),
    Mix("Hip Hop Mix", "hip hop hits"),
    Mix("Indie Mix", "indie songs"),
    Mix("Workout Mix", "workout songs"),
    Mix("Focus Mix", "focus music instrumental"),
    Mix("Jazz Mix", "jazz classics"),
    Mix("Romance Mix", "romantic songs"),
    Mix("Party Mix", "party songs"),
)

val TRENDING = Mix("Trending now", "top songs ${Year.now().value}")

enum class SearchFilter { Songs, Albums, Artists }

data class SearchState(
    val submitted: String = "",
    val filter: SearchFilter = SearchFilter.Songs,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
)

data class HomeState(
    val trending: List<Song> = emptyList(),
    val trendingLoading: Boolean = true,
    val trendingError: String? = null,
    /** "Because you like <seed.artist>": radio seeded from a song you like or played. */
    val forYouSeed: Song? = null,
    val forYou: List<Song> = emptyList(),
    val dailyMixes: List<DailyMix> = emptyList(),
)

data class DailyMix(val title: String, val subtitle: String, val songs: List<Song>)

/** [endsAt] for a timed sleep, or [endOfTrack]. */
data class SleepTimer(val endsAt: Long? = null, val endOfTrack: Boolean = false)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val library = Library(app)
    private val taste = Taste(app)
    private var trackedSong: Song? = null
    private var trackedFraction = 0f
    private var mixesBuiltAt = 0L
    private var mixesJob: Job? = null
    val liked: StateFlow<List<Song>> = library.liked
    val recent: StateFlow<List<Song>> = library.recent
    val playlists: StateFlow<List<Playlist>> = library.playlists
    val downloads: StateFlow<List<Song>> = Downloads.songs
    val downloadProgress: StateFlow<Map<String, Float>> = Downloads.progress

    private val _player = MutableStateFlow(PlayerState())
    val player: StateFlow<PlayerState> = _player.asStateFlow()

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val settings = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == settings.getString(KEY_THEME, null) } ?: ThemeMode.System
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _autoplay = MutableStateFlow(settings.getBoolean(KEY_AUTOPLAY, true))
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

    private val _sleep = MutableStateFlow<SleepTimer?>(null)
    val sleep: StateFlow<SleepTimer?> = _sleep.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private var page: YouTubeMusic.Page? = null
    private var searchJob: Job? = null
    private var moreJob: Job? = null
    private var radioJob: Job? = null
    private var lastRadioSeed: String? = null

    private val controllerFuture = MediaController.Builder(
        app, SessionToken(app, ComponentName(app, PlaybackService::class.java)),
    ).buildAsync()
    private val controller: MediaController?
        get() = if (controllerFuture.isDone && !controllerFuture.isCancelled) runCatching { controllerFuture.get() }.getOrNull() else null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync()
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                _player.value.current?.let(library::addRecent)
            }
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED)) extendWithRadio()
        }

        override fun onPlayerError(error: PlaybackException) {
            say("Couldn't play this track — ${error.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')}")
        }

        // The "end of song" sleep timer is done only when it actually paused playback.
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && _sleep.value?.endOfTrack == true) _sleep.value = null
        }

        // Learn from how the previous song ended: finished, or skipped early.
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            val prev = trackedSong
            if (prev != null && prev.id != mediaItem?.mediaId) {
                val finished = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                taste.recordListen(prev, if (finished) 1f else trackedFraction, skipped = reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
            }
            trackedSong = mediaItem?.let(Song::fromMediaItem)
            trackedFraction = 0f
        }
    }

    init {
        controllerFuture.addListener({
            controller?.let {
                it.addListener(listener)
                sync()
            }
        }, ContextCompat.getMainExecutor(app))

        viewModelScope.launch {
            while (isActive) {
                controller?.let { c ->
                    val duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 }
                        ?: ((_player.value.current?.durationSec ?: 0L) * 1000)
                    val next = Progress(c.currentPosition.coerceAtLeast(0), duration)
                    if (next != _progress.value) _progress.value = next
                    if (c.currentMediaItem?.mediaId == trackedSong?.id) trackedFraction = maxOf(trackedFraction, next.fraction)
                }
                _sleep.value?.endsAt?.let { if (System.currentTimeMillis() >= it) _sleep.value = null }
                delay(250)
            }
        }

        loadHome()
    }

    private fun say(message: String) {
        _messages.tryEmit(message)
    }

    private fun sync() {
        val c = controller ?: return
        _player.value = PlayerState(
            current = c.currentMediaItem?.let(Song::fromMediaItem),
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING && c.playWhenReady,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = List(c.mediaItemCount) { Song.fromMediaItem(c.getMediaItemAt(it)) },
            index = c.currentMediaItemIndex,
            upNext = c.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }
                ?.let { Song.fromMediaItem(c.getMediaItemAt(it)) },
        )
    }

    // ---- Home ----

    fun loadHome() {
        _home.update { it.copy(trendingLoading = true, trendingError = null) }
        viewModelScope.launch {
            catching { YouTubeMusic.search(TRENDING.query).songs }
                .onSuccess { songs -> _home.update { it.copy(trending = songs, trendingLoading = false) } }
                .onFailure { e -> _home.update { it.copy(trendingLoading = false, trendingError = e.message ?: "Something went wrong") } }
        }
        // "Because you like": seeded from the song you seem to enjoy most.
        val seed = (liked.value + recent.value).distinctBy { it.id }.filterNot(taste::isUnwanted).maxByOrNull(taste::score)
        if (seed != null && seed.id != _home.value.forYouSeed?.id) {
            viewModelScope.launch {
                catching { YouTubeMusic.radio(seed.id) }.onSuccess { songs ->
                    _home.update { it.copy(forYouSeed = seed, forYou = taste.personalize(songs.filterNot { s -> s.id == seed.id })) }
                }
            }
        }
        refreshDailyMixes(force = true)
    }


    /**
     * Daily Mixes: one per favourite artist, built from your songs by them plus a radio of
     * similar music, filtered and ordered by your taste. Rebuilt at most every 10 minutes.
     */
    fun refreshDailyMixes(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - mixesBuiltAt < 10 * 60_000) return
        if (mixesJob?.isActive == true) return
        val favourites = taste.topArtists.value.take(3)
        if (favourites.isEmpty()) return
        mixesBuiltAt = System.currentTimeMillis()
        val known = (liked.value + recent.value).distinctBy { it.id }
        mixesJob = viewModelScope.launch {
            val mixes = favourites.mapIndexedNotNull { i, artist ->
                val theirs = known.filter { taste.key(it.artist) == taste.key(artist) && !taste.isUnwanted(it) }
                val seed = theirs.maxByOrNull(taste::score) ?: return@mapIndexedNotNull null
                val radio = catching { YouTubeMusic.radio(seed.id) }.getOrNull() ?: return@mapIndexedNotNull null
                val songs = (theirs.shuffled().take(6) + taste.personalize(radio)).distinctBy { it.id }.take(40)
                val alsoIn = songs.map { it.artist.split(",", "&").first().trim() }.distinct()
                    .filterNot { taste.key(it) == taste.key(artist) }.take(2)
                DailyMix(
                    title = "Daily Mix ${i + 1}",
                    subtitle = (listOf(artist) + alsoIn).joinToString(", ") + " and more",
                    songs = songs,
                )
            }
            if (mixes.isNotEmpty()) _home.update { it.copy(dailyMixes = mixes) }
        }
    }

    val topArtists: StateFlow<List<String>> get() = taste.topArtists

    fun resetTaste() {
        taste.reset()
        _home.update { it.copy(dailyMixes = emptyList()) }
        say("Your taste profile was reset")
    }

    /** "Not interested": hide this song from suggestions and lean away from it. */
    fun notInterested(song: Song) {
        taste.block(song)
        val c = controller
        if (c != null) {
            if (c.currentMediaItem?.mediaId == song.id) c.seekToNext()
            else (0 until c.mediaItemCount).reversed().filter { c.getMediaItemAt(it).mediaId == song.id }.forEach(c::removeMediaItem)
        }
        _home.update { h -> h.copy(forYou = h.forYou.filterNot { it.id == song.id }, dailyMixes = h.dailyMixes.map { m -> m.copy(songs = m.songs.filterNot { it.id == song.id }) }) }
        say("Got it. You'll hear less like this.")
    }

    // ---- Search ----

    fun setQuery(text: String) {
        _query.value = text
    }

    fun clearQuery() {
        searchJob?.cancel()
        _query.value = ""
        _search.value = SearchState(filter = _search.value.filter)
    }

    fun submitQuery() {
        val q = _query.value.trim()
        if (q.isEmpty()) return clearQuery()
        runSearch(q, _search.value.filter)
    }

    fun setFilter(filter: SearchFilter) {
        if (filter == _search.value.filter) return
        val submitted = _search.value.submitted
        if (submitted.isEmpty()) _search.update { it.copy(filter = filter) } else runSearch(submitted, filter)
    }

    private fun runSearch(query: String, filter: SearchFilter) {
        searchJob?.cancel()
        moreJob?.cancel()
        _search.value = SearchState(submitted = query, filter = filter, loading = true)
        searchJob = viewModelScope.launch {
            catching {
                when (filter) {
                    SearchFilter.Songs -> YouTubeMusic.search(query).also { page = it }.let { SearchState(songs = it.songs) }
                    SearchFilter.Albums -> SearchState(albums = YouTubeMusic.searchAlbums(query))
                    SearchFilter.Artists -> SearchState(artists = YouTubeMusic.searchArtists(query))
                }
            }.onSuccess { r ->
                _search.value = r.copy(submitted = query, filter = filter)
            }.onFailure { e ->
                _search.value = SearchState(submitted = query, filter = filter, error = e.message ?: "Something went wrong")
            }
        }
    }

    fun loadMore() {
        val current = page ?: return
        val state = _search.value
        if (state.filter != SearchFilter.Songs || !current.hasMore || state.loading || state.loadingMore || moreJob?.isActive == true) return
        _search.update { it.copy(loadingMore = true) }
        moreJob = viewModelScope.launch {
            val result = catching { YouTubeMusic.more(current) }.getOrNull()
            if (result != null) page = result
            _search.update { s ->
                val known = s.songs.mapTo(HashSet()) { it.id }
                s.copy(loadingMore = false, songs = s.songs + result?.songs.orEmpty().filterNot { it.id in known })
            }
        }
    }

    // ---- Appearance & settings ----

    fun cycleTheme() {
        val next = ThemeMode.entries[(_themeMode.value.ordinal + 1) % ThemeMode.entries.size]
        _themeMode.value = next
        settings.edit().putString(KEY_THEME, next.name).apply()
        say("Theme: ${next.label}")
    }

    fun toggleAutoplay() {
        _autoplay.value = !_autoplay.value
        settings.edit().putBoolean(KEY_AUTOPLAY, _autoplay.value).apply()
        say(if (_autoplay.value) "Autoplay on: similar songs play when your queue ends" else "Autoplay off")
        if (_autoplay.value) extendWithRadio()
    }

    // ---- Library ----

    fun isLiked(id: String) = library.isLiked(id)

    fun toggleLike(song: Song) {
        val wasLiked = library.isLiked(song.id)
        library.toggleLike(song)
        taste.recordLike(song, liked = !wasLiked)
        say(if (wasLiked) "Removed from Liked Songs" else "Added to Liked Songs")
    }

    fun createPlaylist(name: String, firstSong: Song? = null): Playlist {
        val playlist = library.createPlaylist(name)
        if (firstSong != null) library.addToPlaylist(playlist.id, firstSong)
        say(if (firstSong != null) "Added to ${playlist.name}" else "Created ${playlist.name}")
        return playlist
    }

    fun renamePlaylist(id: String, name: String) = library.renamePlaylist(id, name)

    fun deletePlaylist(playlist: Playlist) {
        library.deletePlaylist(playlist.id)
        say("Deleted ${playlist.name}")
    }

    fun addToPlaylist(playlist: Playlist, song: Song) {
        say(if (library.addToPlaylist(playlist.id, song)) "Added to ${playlist.name}" else "Already in ${playlist.name}")
    }

    fun removeFromPlaylist(playlist: Playlist, song: Song) = library.removeFromPlaylist(playlist.id, song.id)

    fun download(song: Song) {
        if (Downloads.isDownloaded(song.id)) return
        say("Downloading ${song.title}…")
        viewModelScope.launch {
            catching { Downloads.download(song) }
                .onSuccess { say("Downloaded ${song.title}") }
                .onFailure { say("Download failed: ${it.message ?: "network error"}") }
        }
    }

    fun downloadAll(songs: List<Song>) {
        val todo = songs.filterNot { Downloads.isDownloaded(it.id) }
        if (todo.isEmpty()) return say("Already downloaded")
        say("Downloading ${todo.size} songs…")
        viewModelScope.launch {
            var failed = 0
            todo.forEach { song -> if (catching { Downloads.download(song) }.isFailure) failed++ }
            say(if (failed == 0) "Downloaded ${todo.size} songs" else "Downloaded ${todo.size - failed} of ${todo.size}")
        }
    }

    fun removeDownload(song: Song) {
        Downloads.remove(song)
        say("Removed download")
    }

    // ---- Transport ----

    fun playSongs(songs: List<Song>, index: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        // Only toggle when this exact list is already the queue at this position; otherwise start it.
        val sameQueue = _player.value.queue.map { it.id } == songs.map { it.id } && _player.value.index == index
        if (!shuffle && sameQueue && c.mediaItemCount > 0) {
            togglePlay()
            return
        }
        radioJob?.cancel() // a radio fetched for the old queue must not land in this one
        lastRadioSeed = null
        c.shuffleModeEnabled = shuffle
        val start = if (shuffle) songs.indices.random() else index
        c.setMediaItems(songs.map(Song::toMediaItem), start, 0L)
        c.prepare()
        c.play()
    }

    fun startRadio(seed: Song) {
        val c = controller ?: return
        say("Starting radio from ${seed.title}…")
        c.shuffleModeEnabled = false
        c.setMediaItems(listOf(seed.toMediaItem()), 0, 0L)
        c.prepare()
        c.play()
        lastRadioSeed = seed.id
        radioJob?.cancel()
        radioJob = viewModelScope.launch {
            catching { YouTubeMusic.radio(seed.id) }
                .onSuccess { songs ->
                    if (seedStillQueued(seed.id)) controller?.addMediaItems(taste.personalize(songs.filterNot { it.id == seed.id }).map(Song::toMediaItem))
                }
                .onFailure { say("Couldn't start radio") }
        }
    }

    /** Autoplay: when the queue is about to run out, top it up with songs like the current one. */
    private fun extendWithRadio() {
        val c = controller ?: return
        val current = _player.value.current ?: return
        if (!_autoplay.value || c.repeatMode != Player.REPEAT_MODE_OFF) return
        if (c.nextMediaItemIndex != C.INDEX_UNSET || radioJob?.isActive == true || lastRadioSeed == current.id) return
        lastRadioSeed = current.id
        radioJob = viewModelScope.launch {
            val queued = _player.value.queue.mapTo(HashSet()) { it.id }
            catching { YouTubeMusic.radio(current.id) }.onSuccess { songs ->
                val fresh = taste.personalize(songs.filterNot { it.id in queued }).take(25)
                if (fresh.isNotEmpty() && seedStillQueued(current.id)) controller?.addMediaItems(fresh.map(Song::toMediaItem))
            }
        }
    }

    /** Radio results only belong in the queue that still contains the song they were seeded from. */
    private fun seedStillQueued(seedId: String): Boolean {
        val c = controller ?: return false
        return (0 until c.mediaItemCount).any { c.getMediaItemAt(it).mediaId == seedId }
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playSongs(listOf(song))
        c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
        say("Playing next: ${song.title}")
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playSongs(listOf(song))
        c.addMediaItem(song.toMediaItem())
        say("Added to queue")
    }

    fun removeFromQueue(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (from == to || to !in 0 until c.mediaItemCount) return
        c.moveMediaItem(from, to)
    }

    fun playQueueIndex(index: Int) {
        val c = controller ?: return
        c.seekTo(index, 0L)
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        when {
            c.isPlaying -> c.pause()
            else -> {
                if (c.playbackState == Player.STATE_IDLE) c.prepare()
                if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
                c.play()
            }
        }
    }

    fun next() {
        controller?.seekToNext()
    }

    /** Always the previous song (unlike [previous], which restarts the current one after 3 s). */
    fun previousTrack() {
        controller?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0L) }
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(fraction: Float) {
        val duration = _progress.value.durationMs
        if (duration > 0) seekToMs((duration * fraction).toLong())
    }

    fun seekToMs(positionMs: Long) {
        val c = controller ?: return
        c.seekTo(positionMs)
        _progress.value = _progress.value.copy(positionMs = positionMs)
        if (!c.isPlaying) c.play()
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    /** [minutes] > 0 for a timer, [PlaybackService.SLEEP_END_OF_TRACK] for end of song, 0 to cancel. */
    fun setSleepTimer(minutes: Int) {
        val c = controller ?: return
        c.sendCustomCommand(SessionCommand(PlaybackService.CMD_SLEEP, android.os.Bundle.EMPTY), bundleOf(PlaybackService.ARG_MINUTES to minutes))
        _sleep.value = when {
            minutes > 0 -> SleepTimer(endsAt = System.currentTimeMillis() + minutes * 60_000L)
            minutes == PlaybackService.SLEEP_END_OF_TRACK -> SleepTimer(endOfTrack = true)
            else -> null
        }
        say(
            when {
                minutes > 0 -> "Sleep timer: music stops in $minutes min"
                minutes == PlaybackService.SLEEP_END_OF_TRACK -> "Sleep timer: music stops after this song"
                else -> "Sleep timer off"
            }
        )
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_AUTOPLAY = "autoplay"
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
    }
}

/** runCatching that lets coroutine cancellation through instead of reporting it as an error. */
private inline fun <T> catching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
