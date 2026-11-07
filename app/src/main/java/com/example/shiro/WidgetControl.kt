package com.example.shiro

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken

/** Widget commands are acknowledged by the existing service before releasing the short connection. */
@OptIn(UnstableApi::class)
internal object WidgetControl {
    const val ACTION = "com.example.shiro.WIDGET_CONTROL"
    const val EXTRA = "control"
    val command get() = SessionCommand(ACTION, Bundle.EMPTY)

    fun execute(player: Player, action: WidgetAction): Boolean {
        if (player.mediaItemCount == 0) return false
        when (action) {
            WidgetAction.PLAY -> {
                if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
            WidgetAction.PAUSE -> player.pause()
            WidgetAction.PREVIOUS -> if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)) player.seekToPrevious()
            WidgetAction.NEXT -> if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)) player.seekToNext()
        }
        return true
    }

    fun send(context: Context, action: WidgetAction, finished: () -> Unit) {
        val app = context.applicationContext
        val main = ContextCompat.getMainExecutor(app)
        val handler = Handler(Looper.getMainLooper())
        val future = try {
            MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        } catch (_: Exception) {
            ShiroWidgetUpdates.connectionFailed(app, finished)
            return
        }
        var completed = false
        lateinit var timeout: Runnable
        fun finish(failed: Boolean) {
            if (completed) return
            completed = true
            handler.removeCallbacks(timeout)
            MediaController.releaseFuture(future)
            if (failed) ShiroWidgetUpdates.connectionFailed(app, finished) else finished()
        }
        timeout = Runnable { finish(true) }
        handler.postDelayed(timeout, 7_000L)
        future.addListener({
            if (!completed) try {
                val result = future.get().sendCustomCommand(command, Bundle().apply { putString(EXTRA, action.name) })
                result.addListener({
                    try { finish(result.get().resultCode != SessionResult.RESULT_SUCCESS) }
                    catch (_: Exception) { finish(true) }
                }, main)
            } catch (_: Exception) { finish(true) }
        }, main)
    }
}
