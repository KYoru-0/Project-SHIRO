package com.example.shiro

import org.junit.Assert.*
import org.junit.Test

class ShiroWidgetStateTest {
    private val track = ShiroWidgetState("content://music/1", "A song", "An artist")

    @Test fun bufferingAndAudioFocusKeepPauseAvailableWithoutAnAwakeCat() {
        val buffering = track.copy(wantsPlayback = true, buffering = true)
        assertEquals(WidgetStatus.LOADING, buffering.status)
        assertEquals(WidgetAction.PAUSE, buffering.primaryAction)
        assertFalse(buffering.playing)
        val suppressed = buffering.copy(buffering = false)
        assertEquals(WidgetStatus.WAITING, suppressed.status)
        assertEquals(WidgetAction.PAUSE, suppressed.primaryAction)
    }

    @Test fun playPauseAndFailuresHaveClearDistinctStates() {
        assertEquals(WidgetStatus.PAUSED, track.status)
        assertEquals(WidgetAction.PLAY, track.primaryAction)
        val playing = track.copy(playing = true, wantsPlayback = true)
        assertEquals(WidgetStatus.PLAYING, playing.status)
        assertEquals(WidgetAction.PAUSE, playing.primaryAction)
        val failed = track.copy(error = true, wantsPlayback = true)
        assertEquals(WidgetStatus.ERROR, failed.status)
        assertEquals(WidgetAction.PLAY, failed.primaryAction)
    }

    @Test fun stoppingOrRecreatingTheProcessCannotRestoreAStalePlayingState() {
        val playing = track.copy(playing = true, wantsPlayback = true, buffering = true)
        assertEquals(track, playing.stopped())
        assertEquals(WidgetAction.PLAY, playing.stopped().primaryAction)
        assertEquals(WidgetStatus.PAUSED, playing.stopped().status)
    }

    @Test fun emptyQueueOpensTheAppEvenWithStalePlaybackFlags() {
        val empty = ShiroWidgetState(playing = true, wantsPlayback = true, buffering = true, error = true)
        assertFalse(empty.hasTrack)
        assertEquals(WidgetStatus.EMPTY, empty.status)
        assertEquals(WidgetAction.PLAY, empty.primaryAction)
    }
}
