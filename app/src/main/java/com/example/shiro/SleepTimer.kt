package com.example.shiro

import android.os.Bundle
import androidx.media3.session.SessionCommand

/** Shared command/state keys; the service is the only owner of the timer. */
object SleepTimer {
    const val ACTION = "com.example.shiro.SET_SLEEP_TIMER"
    const val MINUTES = "minutes"
    const val DEADLINE = "sleep_deadline_elapsed_ms"
    const val END_OF_TRACK = "sleep_end_of_track"
    const val CANCEL = 0
    const val AT_TRACK_END = -1
    const val EXTEND_FIVE = -2
    const val FADE_DURATION_MS = 30_000L
    val command: SessionCommand get() = SessionCommand(ACTION, Bundle.EMPTY)

    fun isValid(minutes: Int): Boolean = minutes in setOf(CANCEL, AT_TRACK_END, EXTEND_FIVE, 15, 30, 60)
}
