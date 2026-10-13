// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.zyuael.solune.data.Downloads
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.zyuael.solune.MainActivity
import com.zyuael.solune.data.Http
import com.zyuael.solune.data.Song
import com.zyuael.solune.data.StreamResolver
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val retried = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()

        val http = OkHttpDataSource.Factory(Http.client).setUserAgent(Http.BROWSER_UA)

        // Queue items carry a music.youtube.com watch URL; swap it for a real stream URL
        // only when bytes are actually needed (and only on a cache miss).
        val resolving = ResolvingDataSource.Factory(http) { spec ->
            val id = spec.uri.takeIf { it.host == "music.youtube.com" }?.getQueryParameter("v")
                ?: return@Factory spec
            val url = StreamResolver.resolve(id)
            spec.withUri(Uri.parse(url)).withAdditionalHeaders(StreamResolver.headersFor(url))
        }

        val cached = CacheDataSource.Factory()
            .setCache(MediaCache.get(this))
            .setUpstreamDataSourceFactory(resolving)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Downloaded songs play straight from disk (works offline); everything else streams.
        val offlineFirst = ResolvingDataSource.Factory(DefaultDataSource.Factory(this, cached)) { spec ->
            val id = spec.uri.takeIf { it.host == "music.youtube.com" }?.getQueryParameter("v")
            Downloads.fileFor(id ?: return@Factory spec)?.let { spec.withUri(Uri.fromFile(it)) } ?: spec
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(offlineFirst))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val id = player.currentMediaItem?.mediaId ?: return
                StreamResolver.invalidate(id)
                // No connection: stay on this song (paused) instead of failing through the whole queue.
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                ) return
                if (retried.add(id)) {
                    // Most failures are an expired or rejected stream URL: re-resolve once. A later
                    // resolve may pick another format, so drop partly cached bytes of the old one.
                    runCatching { MediaCache.get(this@PlaybackService).removeResource(id) }
                    player.prepare()
                    player.play()
                } else if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }

            // One retry per song per visit: a song that fails again (even mid-stream) is skipped.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                retried.clear()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // "End of track" sleep timer fired: back to normal for next time.
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) player.pauseAtEndOfMediaItems = false
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                            .add(SessionCommand(CMD_SLEEP, Bundle.EMPTY))
                            .build()
                    )
                    .build()

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == CMD_SLEEP) setSleepTimer(player, args.getInt(ARG_MINUTES))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                // MediaItem URIs don't survive the controller→session hop; rebuild them from the id.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
                    mediaItems.map {
                        it.buildUpon()
                            .setUri(Song.watchUrl(it.mediaId))
                            .setCustomCacheKey(it.mediaId)
                            .build()
                    }.toMutableList()
                )
            })
            .build()
    }

    private val handler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null

    /** [minutes] > 0: pause after that long; [SLEEP_END_OF_TRACK]: pause when this song ends; 0: cancel. */
    private fun setSleepTimer(player: ExoPlayer, minutes: Int) {
        sleepRunnable?.let(handler::removeCallbacks)
        sleepRunnable = null
        player.pauseAtEndOfMediaItems = minutes == SLEEP_END_OF_TRACK
        if (minutes > 0) {
            sleepRunnable = Runnable { player.pause() }.also { handler.postDelayed(it, minutes * 60_000L) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    companion object {
        const val CMD_SLEEP = "solune.SLEEP"
        const val ARG_MINUTES = "minutes"
        const val SLEEP_END_OF_TRACK = -1
    }

    override fun onDestroy() {
        sleepRunnable?.let(handler::removeCallbacks)
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

/** One cache per process; SimpleCache refuses to open the same folder twice. */
@OptIn(UnstableApi::class)
private object MediaCache {
    private var cache: SimpleCache? = null

    @Synchronized
    fun get(context: Context): SimpleCache = cache ?: SimpleCache(
        File(context.cacheDir, "media"),
        LeastRecentlyUsedCacheEvictor(512L * 1024 * 1024),
        StandaloneDatabaseProvider(context),
    ).also { cache = it }
}
