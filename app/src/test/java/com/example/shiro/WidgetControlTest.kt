package com.example.shiro

import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class WidgetControlTest {
    private class PlayerCalls(var state: Int = Player.STATE_READY, val count: Int = 2,
        val skipsAvailable: Boolean = true) {
        val calls = mutableListOf<String>()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "getMediaItemCount" -> count
                "getPlaybackState" -> state
                "isCommandAvailable" -> skipsAvailable
                else -> { calls += method.name; null }
            }
        } as Player
    }

    @Test fun resumePreparesIdlePlayerButDoesNotSeekAnExistingPausedSong() {
        val paused = PlayerCalls()
        assertTrue(WidgetControl.execute(paused.player, WidgetAction.PLAY))
        assertEquals(listOf("play"), paused.calls)
        val idle = PlayerCalls(Player.STATE_IDLE)
        WidgetControl.execute(idle.player, WidgetAction.PLAY)
        assertEquals(listOf("prepare", "play"), idle.calls)
        val ended = PlayerCalls(Player.STATE_ENDED)
        WidgetControl.execute(ended.player, WidgetAction.PLAY)
        assertEquals(listOf("seekToDefaultPosition", "play"), ended.calls)
    }

    @Test fun pauseCancelsPendingPlaybackAndSkippingDoesNotStartAPausedPlayer() {
        val buffering = PlayerCalls(Player.STATE_BUFFERING)
        WidgetControl.execute(buffering.player, WidgetAction.PAUSE)
        assertEquals(listOf("pause"), buffering.calls)
        val paused = PlayerCalls()
        WidgetControl.execute(paused.player, WidgetAction.NEXT)
        WidgetControl.execute(paused.player, WidgetAction.PREVIOUS)
        assertEquals(listOf("seekToNext", "seekToPrevious"), paused.calls)
    }

    @Test fun emptyQueueAndUnavailableSkipsDoNotIssuePlayerCommands() {
        val empty = PlayerCalls(count = 0)
        for (action in WidgetAction.entries) assertFalse(WidgetControl.execute(empty.player, action))
        assertTrue(empty.calls.isEmpty())
        val single = PlayerCalls(skipsAvailable = false)
        WidgetControl.execute(single.player, WidgetAction.NEXT)
        WidgetControl.execute(single.player, WidgetAction.PREVIOUS)
        assertTrue(single.calls.isEmpty())
    }
}
