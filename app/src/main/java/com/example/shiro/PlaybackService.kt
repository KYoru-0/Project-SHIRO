package com.example.shiro

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionError
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Owns playback independently of Activity/Compose lifetime, including queue and sleep. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    companion object {
        const val ACTION_FAVORITE = "com.example.shiro.ACTION_FAVORITE"
        const val ACTION_CLOSE = "com.example.shiro.ACTION_CLOSE"
    }

    private val favoriteCommand = SessionCommand(ACTION_FAVORITE, Bundle.EMPTY)
    private val closeCommand = SessionCommand(ACTION_CLOSE, Bundle.EMPTY)

    private fun updateCustomLayout() {
        val favoriteIds = loadFavoriteIds(this)
        val currentUri = player.currentMediaItem?.mediaId.orEmpty()
        val isFavorite = currentUri.isNotBlank() && currentUri in favoriteIds

        val favoriteButton = CommandButton.Builder()
            .setDisplayName(if (isFavorite) "Remove from favorites" else "Add to favorites")
            .setIconResId(if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
            .setSessionCommand(favoriteCommand)
            .build()

        val closeButton = CommandButton.Builder()
            .setDisplayName("Exit Shiro")
            .setIconResId(R.drawable.ic_close)
            .setSessionCommand(closeCommand)
            .build()

        session?.setCustomLayout(listOf(favoriteButton, closeButton))
        session?.setMediaButtonPreferences(listOf(favoriteButton, closeButton))
    }
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var sleepDeadline = 0L
    private var sleepAtTrackEnd = false
    private var sleepFinished = false
    private var volumeBeforeFade: Float? = null
    private var fadeStartRemainingMs = 0L
    private var fadeStartVolume = 1f
    private var restoring = true
    private val failedThisPass = mutableSetOf<String>()
    private val unavailableIds = mutableSetOf<String>()
    private var recoveryMessage = ""
    private var pendingRecovery: Runnable? = null
    private var recoverySeekTarget: String? = null
    private lateinit var spectrum: SpectrumEngine

    private val spectrumTick = object : Runnable {
        override fun run() {
            if (!PlaybackSpectrum.visible || !player.isPlaying) return
            val frame = spectrum.currentFrame()
            val gain = player.volume.coerceIn(0f, 1f)
            PlaybackSpectrum.publish(if (gain == 1f) frame else SpectrumFrame(
                FloatArray(frame.bands.size) { frame.bands[it] * gain }, frame.bassEnergy * gain
            ))
            handler.postDelayed(this, 33L)
        }
    }

    private val checkpoint = object : Runnable {
        override fun run() {
            persistPlayback()
            if (player.isPlaying) handler.postDelayed(this, 5_000L)
        }
    }
    private val sleepTick = object : Runnable {
        override fun run() {
            if (sleepDeadline == 0L && !sleepAtTrackEnd) return
            if (sleepDeadline != 0L && sleepDeadline <= SystemClock.elapsedRealtime()) {
                finishSleep()
            } else {
                updateSleepFade()
                publishSleepState()
                handler.postDelayed(this, if (volumeBeforeFade != null && player.isPlaying) 100L else 1_000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        spectrum = SpectrumEngine()
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = SpectrumAudioSink(
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build(),
                spectrum
            )
        }
        player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // Let ExoPlayer handle audio focus loss and recovery.
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setMaxSeekToPreviousPositionMs(1_000L)
            .build()
        PlaybackSpectrum.onVisibilityChanged = { handler.post { updateSpectrumLoop() } }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        session = MediaSession.Builder(this, player)
            .setSessionActivity(PendingIntent.getActivity(
                this, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(favoriteCommand)
                        .add(closeCommand)
                    if (controller.packageName == packageName) commands.add(SleepTimer.command)

                    val favoriteIds = loadFavoriteIds(this@PlaybackService)
                    val currentUri = player.currentMediaItem?.mediaId.orEmpty()
                    val isFavorite = currentUri.isNotBlank() && currentUri in favoriteIds

                    val favoriteButton = CommandButton.Builder()
                        .setDisplayName(if (isFavorite) "Remove from favorites" else "Add to favorites")
                        .setIconResId(if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
                        .setSessionCommand(favoriteCommand)
                        .build()

                    val closeButton = CommandButton.Builder()
                        .setDisplayName("Exit Shiro")
                        .setIconResId(R.drawable.ic_close)
                        .setSessionCommand(closeCommand)
                        .build()

                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands.build())
                        .setCustomLayout(listOf(favoriteButton, closeButton))
                        .setMediaButtonPreferences(listOf(favoriteButton, closeButton))
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == ACTION_FAVORITE) {
                        val currentUri = player.currentMediaItem?.mediaId.orEmpty()
                        if (currentUri.isNotBlank()) {
                            val favoriteIds = loadFavoriteIds(this@PlaybackService).toMutableSet()
                            if (currentUri in favoriteIds) {
                                favoriteIds.remove(currentUri)
                            } else {
                                favoriteIds.add(currentUri)
                            }
                            saveFavoriteIds(this@PlaybackService, favoriteIds)
                            updateCustomLayout()
                            publishSleepState()
                        }
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    if (customCommand.customAction == ACTION_CLOSE) {
                        persistPlayback()
                        player.pause()
                        player.clearMediaItems()
                        sendBroadcast(Intent("com.example.shiro.EXIT_APP").setPackage(packageName))
                        stopSelf()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    val minutes = args.getInt(SleepTimer.MINUTES, Int.MIN_VALUE)
                    if (controller.packageName != packageName || customCommand.customAction != SleepTimer.ACTION ||
                        !SleepTimer.isValid(minutes) || (minutes != SleepTimer.CANCEL && player.mediaItemCount == 0)) {
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    }
                    if (minutes == SleepTimer.EXTEND_FIVE) {
                        if (sleepDeadline == 0L || sleepDeadline <= SystemClock.elapsedRealtime()) {
                            if (sleepDeadline != 0L) finishSleep()
                            return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
                        }
                        // Extend the current deadline, including while paused; never start playback.
                        sleepDeadline += 5 * 60_000L
                        restoreVolume()
                        publishSleepState()
                        handler.removeCallbacks(sleepTick)
                        handler.post(sleepTick)
                    } else {
                        setSleepTimer(minutes)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()

        // Restore once in the service. Reopening or rotating the UI never resets live playback.
        val prefs = getSharedPreferences("shiro_prefs", MODE_PRIVATE)
        val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "favorite_track_uris") {
                updateCustomLayout()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        val tracks = loadPlaylistCache(this)
        player.shuffleModeEnabled = prefs.getBoolean("is_shuffle_on", false)
        player.repeatMode = if (prefs.getBoolean("is_repeat_one_on", false)) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
        if (tracks.isNotEmpty()) {
            val uri = prefs.getString("saved_track_uri", "")
            val fileName = prefs.getString("saved_file_name", "")
            val savedIndex = if (!uri.isNullOrBlank()) {
                tracks.indexOfFirst { it.uri.toString() == uri }.takeIf { it >= 0 }
            } else {
                tracks.indexOfFirst { it.fileName == fileName }.takeIf { it >= 0 }
            }
            player.setMediaItems(tracks.map { it.toMediaItem() }, savedIndex ?: 0,
                if (savedIndex != null) prefs.getLong("saved_position_ms", 0L).coerceAtLeast(0L) else 0L)
            player.prepare()
        }
        restoring = false
        publishSleepState()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                updateCustomLayout()
            }

            override fun onPlayerError(error: PlaybackException) {
                spectrum.reset()
                recoverFromTrackError()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateSpectrumLoop()
                if (isPlaying) {
                    sleepFinished = false
                    cancelPendingRecovery()
                    player.currentMediaItem?.mediaId?.let { unavailableIds.remove(it) }
                    recoveryMessage = if (unavailableIds.isEmpty()) "" else
                        "${unavailableIds.size} unavailable track(s). See Playlist to retry."
                    publishSleepState()
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    cancelPendingRecovery()
                    failedThisPass.clear()
                    val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
                    unavailableIds.retainAll(ids)
                    recoveryMessage = if (unavailableIds.isEmpty()) "" else
                        "${unavailableIds.size} unavailable track(s). See Playlist to retry."
                    publishSleepState()
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                // Fade ticks change volume frequently; they need no disk checkpoint or new fade.
                if (events.size() == 1 && events.contains(Player.EVENT_VOLUME_CHANGED)) return
                // A timed sleep may expire while paused/asleep. Never resume past its deadline.
                if (sleepDeadline != 0L && SystemClock.elapsedRealtime() >= sleepDeadline) finishSleep()
                if (player.mediaItemCount == 0 && (sleepDeadline != 0L || sleepAtTrackEnd)) setSleepTimer(SleepTimer.CANCEL)
                updateSleepFade()
                persistPlayback()
                handler.removeCallbacks(checkpoint)
                if (player.isPlaying) handler.postDelayed(checkpoint, 5_000L)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) {
                    val wasRecovering = pendingRecovery != null
                    cancelPendingRecovery()
                    if (wasRecovering) recoveryMessage = "Playback paused. Unavailable tracks are marked in Playlist."
                    publishSleepState()
                } else {
                    // An explicit Play starts a fresh attempt. Recovery itself never calls play().
                    failedThisPass.clear()
                }
                if (!playWhenReady && sleepAtTrackEnd && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    finishSleep()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (sleepAtTrackEnd && playbackState == Player.STATE_ENDED) finishSleep()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // A seek/skip changes time remaining. Start the new slope from the current gain.
                if (sleepAtTrackEnd) fadeStartRemainingMs = 0L
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    if (recoverySeekTarget == newPosition.mediaItem?.mediaId) {
                        recoverySeekTarget = null
                    } else {
                        cancelPendingRecovery()
                        failedThisPass.clear()
                        publishSleepState()
                    }
                }
            }
        })
    }

    private fun updateSpectrumLoop() {
        handler.removeCallbacks(spectrumTick)
        if (PlaybackSpectrum.visible && player.isPlaying) handler.post(spectrumTick)
        else PlaybackSpectrum.publish(SpectrumFrame.silence())
    }

    private fun setSleepTimer(minutes: Int) {
        handler.removeCallbacks(sleepTick)
        sleepFinished = false
        restoreVolume()
        sleepDeadline = if (minutes > 0) SystemClock.elapsedRealtime() + minutes * 60_000L else 0L
        sleepAtTrackEnd = minutes == SleepTimer.AT_TRACK_END
        // Pause before automatic advancement, including shuffle and repeat-one.
        player.pauseAtEndOfMediaItems = sleepAtTrackEnd
        publishSleepState()
        if (sleepDeadline != 0L || sleepAtTrackEnd) handler.post(sleepTick)
    }

    private fun finishSleep() {
        if (pendingRecovery != null) recoveryMessage = "Sleep timer stopped playback. Unavailable tracks are marked in Playlist."
        cancelPendingRecovery()
        // Pause before restoring gain, so expiry never produces a full-volume burst.
        player.pause()
        setSleepTimer(SleepTimer.CANCEL)
        sleepFinished = true
        publishSleepState()
        persistPlayback()
    }

    private fun updateSleepFade() {
        val remaining = when {
            sleepDeadline != 0L -> sleepDeadline - SystemClock.elapsedRealtime()
            sleepAtTrackEnd && player.duration != C.TIME_UNSET && player.duration > 0L ->
                player.duration - player.currentPosition
            else -> null
        }
        if (remaining == null || remaining > SleepTimer.FADE_DURATION_MS) {
            restoreVolume()
            return
        }
        if (volumeBeforeFade == null) volumeBeforeFade = player.volume
        if (fadeStartRemainingMs == 0L) {
            // Enabling sleep with, say, 10 seconds left starts at the current volume,
            // then fades over those 10 seconds instead of jumping to one-third gain.
            fadeStartRemainingMs = remaining.coerceAtLeast(1L)
            fadeStartVolume = player.volume
        }
        player.volume = fadeStartVolume * (remaining.toFloat() / fadeStartRemainingMs).coerceIn(0f, 1f)
    }

    private fun restoreVolume() {
        fadeStartRemainingMs = 0L
        val originalVolume = volumeBeforeFade ?: return
        volumeBeforeFade = null
        player.volume = originalVolume
    }

    private fun publishSleepState() {
        session?.setSessionExtras(Bundle().apply {
            putLong(SleepTimer.DEADLINE, sleepDeadline)
            putBoolean(SleepTimer.END_OF_TRACK, sleepAtTrackEnd)
            val originalVolume = volumeBeforeFade
            val fadeProgress = if (originalVolume != null && originalVolume > 0f) {
                1f - (player.volume / originalVolume).coerceIn(0f, 1f)
            } else 0f
            putFloat(SleepTimer.FADE_PROGRESS, fadeProgress)
            putBoolean(SleepTimer.FINISHED, sleepFinished)
            putStringArrayList(PlaybackRecovery.UNAVAILABLE_IDS, ArrayList(unavailableIds))
            putString(PlaybackRecovery.MESSAGE, recoveryMessage)
            putBoolean(PlaybackRecovery.PENDING, pendingRecovery != null)
        })
    }

    private fun cancelPendingRecovery() {
        pendingRecovery?.let { handler.removeCallbacks(it) }
        pendingRecovery = null
    }

    private fun recoverFromTrackError() {
        cancelPendingRecovery()
        val item = player.currentMediaItem ?: return
        val failedId = item.mediaId
        val title = item.mediaMetadata.title?.toString().orEmpty().ifBlank { "this track" }
        unavailableIds.add(failedId)
        failedThisPass.add(failedId)
        recoveryMessage = "Couldn't play $title. Tap it in Playlist to retry."
        if (!player.playWhenReady) {
            publishSleepState()
            return
        }

        recoveryMessage = "Couldn't play $title. Trying another track…"
        val recovery = Runnable {
            pendingRecovery = null
            // Wait until the error transition settles, then recheck user intent and timer state.
            if (!player.playWhenReady || player.currentMediaItem?.mediaId != failedId || player.playerError == null) {
                publishSleepState()
                return@Runnable
            }
            if (player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
                player.pause()
                recoveryMessage = "Playback paused. Tap Play when you're ready to try again."
                publishSleepState()
                return@Runnable
            }
            if (sleepAtTrackEnd || (sleepDeadline != 0L && sleepDeadline <= SystemClock.elapsedRealtime())) {
                finishSleep()
                recoveryMessage = "Sleep timer stopped playback. $title was unavailable."
                publishSleepState()
                return@Runnable
            }

            val timeline = player.currentTimeline
            var candidate = player.currentMediaItemIndex
            var nextIndex = C.INDEX_UNSET
            // Walk at most one queue pass, using shuffle order but ignoring repeat-one on failure.
            for (attempt in 0 until player.mediaItemCount) {
                if (candidate !in 0 until timeline.windowCount) break
                candidate = timeline.getNextWindowIndex(candidate, Player.REPEAT_MODE_ALL, player.shuffleModeEnabled)
                if (candidate == C.INDEX_UNSET) break
                if (player.getMediaItemAt(candidate).mediaId !in failedThisPass) {
                    nextIndex = candidate
                    break
                }
            }
            if (nextIndex == C.INDEX_UNSET) {
                player.pause()
                setSleepTimer(SleepTimer.CANCEL)
                recoveryMessage = "No playable tracks found. Playback stopped. Reload folders or tap a track to retry."
            } else {
                recoverySeekTarget = player.getMediaItemAt(nextIndex).mediaId
                player.seekToDefaultPosition(nextIndex)
                player.prepare()
                // Keep playWhenReady unchanged: never undo Pause or audio-focus loss.
            }
            publishSleepState()
        }
        pendingRecovery = recovery
        publishSleepState()
        handler.postDelayed(recovery, 500L)
    }

    private fun persistPlayback() {
        if (restoring) return
        val item = player.currentMediaItem
        getSharedPreferences("shiro_prefs", MODE_PRIVATE).edit()
            .putString("saved_track_uri", item?.mediaId.orEmpty())
            .putString("saved_file_name", item?.mediaMetadata?.extras?.getString("file_name").orEmpty())
            .putString("saved_title", item?.mediaMetadata?.title?.toString().orEmpty())
            .putLong("saved_position_ms", player.currentPosition.coerceAtLeast(0L))
            .putLong("saved_duration_ms", player.duration.coerceAtLeast(0L))
            .putBoolean("is_shuffle_on", player.shuffleModeEnabled)
            .putBoolean("is_repeat_one_on", player.repeatMode == Player.REPEAT_MODE_ONE)
            .apply()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistPlayback()
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        persistPlayback()
        PlaybackSpectrum.onVisibilityChanged = null
        handler.removeCallbacksAndMessages(null)
        session?.release()
        session = null
        player.release()
        spectrum.close()
        super.onDestroy()
    }
}
