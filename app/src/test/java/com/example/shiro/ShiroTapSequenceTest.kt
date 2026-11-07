package com.example.shiro

import org.junit.Assert.*
import org.junit.Test

class ShiroTapSequenceTest {
    private fun ShiroTapSequence.second(hit: Int?, x: Float = 40f, y: Float = 80f, time: Long = 1200L) =
        secondTarget(hit, x, y, time, minimum = 40L, timeout = 300L, tolerance = 16f)

    @Test fun secondTapCanFollowTheBouncingCatOrUseItsPreviousLocation() {
        val taps = ShiroTapSequence()
        taps.record(2, 40f, 80f, 1000L)
        assertEquals(2, taps.second(2, y = 20f))
        assertEquals(2, taps.second(null, x = 45f))
        assertNull(taps.second(null, x = 65f))
        assertNull("Another cat must remain independently tappable", taps.second(3))
    }

    @Test fun expiredOrTooQuickTapsCannotSplit() {
        val taps = ShiroTapSequence()
        assertNull(taps.second(2))
        taps.record(2, 40f, 80f, 1000L)
        assertNull(taps.second(2, time = 1039L))
        assertEquals(2, taps.second(2, time = 1040L))
        assertEquals(2, taps.second(2, time = 1300L))
        assertNull(taps.second(2, time = 1301L))
    }

    @Test fun draggingCancellationAndCompletedPairsClearTheSequence() {
        val taps = ShiroTapSequence()
        taps.record(2, 40f, 80f, 1000L)
        taps.clear()
        assertNull(taps.second(2))
        assertNull(taps.second(null))
        taps.record(4, 40f, 80f, 1100L)
        assertNull(taps.second(2))
        assertEquals(4, taps.second(4))
    }
}
