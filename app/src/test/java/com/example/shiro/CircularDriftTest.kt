package com.example.shiro

import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class CircularDriftTest {
    private fun turn(from: Float, to: Float): Float =
        (((to - from + PI) % (2 * PI) + 2 * PI) % (2 * PI) - PI).toFloat()

    @Test fun peaksTravelAndReverseSmoothlyWithNoJumpOnTrackChange() {
        val drift = CircularDrift(Random(8))
        var previous = drift.advance("first", 0.7f, true, 0f)
        val initial = previous.angle
        var clockwise = false; var counterclockwise = false; var travel = 0f
        repeat(60 * 90) { i ->
            val current = drift.advance(if (i < 600) "first" else "second", 0.7f, true, 1f / 60)
            assertTrue("No angular teleports, including next song", abs(turn(previous.angle, current.angle)) < 0.0045f)
            assertTrue("Changes of speed/direction must ease", abs(current.speed - previous.speed) < 0.0063f)
            assertTrue(abs(current.speed) <= 0.26f)
            if (current.speed > 0.02f) counterclockwise = true
            if (current.speed < -0.02f) clockwise = true
            travel += abs(turn(previous.angle, current.angle))
            previous = current
        }
        assertTrue(clockwise && counterclockwise && travel > 7f)
        assertNotEquals(initial, CircularDrift(Random(19)).advance("first", 0.7f, true, 0f).angle)
    }

    @Test fun pauseAndHiddenSceneKeepTheAngleAndResumeGently() {
        val drift = CircularDrift(Random(5))
        var frame = CircularRotation()
        repeat(180) { frame = drift.advance("song", 1f, true, 1f / 60) }
        val paused = drift.advance("song", 1f, false, 1f / 60)
        assertTrue(abs(paused.speed) < abs(frame.speed))
        repeat(360) { frame = drift.advance("song", 1f, false, 1f / 60) }
        assertTrue(drift.isSettled)
        assertEquals(frame, drift.advance("song", 0f, true, 1f / 60))
        val resumed = drift.advance("song", 1f, true, 1f / 60)
        assertTrue(abs(turn(frame.angle, resumed.angle)) < 0.0001f)
        drift.stop()
        val hidden = drift.advance("song", 1f, true, 1f / 60, motionEnabled = false)
        assertEquals(resumed.angle, hidden.angle, 0f)
        assertEquals(hidden, drift.advance("song", 1f, true, 10f, motionEnabled = false))
        assertEquals(hidden, drift.advance("song", Float.NaN, true, Float.NaN))
    }

    @Test fun rotationHasConsistentPaceAndBoundsStalledFrames() {
        fun run(hz: Int): CircularRotation {
            val drift = CircularDrift(Random(17))
            var frame = CircularRotation()
            repeat(hz * 5) { frame = drift.advance("song", 0.8f, true, 1f / hz) }
            return frame
        }
        val thirty = run(30); val sixty = run(60); val fast = run(120)
        assertTrue(abs(turn(thirty.angle, fast.angle)) < 0.003f)
        assertTrue(abs(turn(sixty.angle, fast.angle)) < 0.002f)
        assertEquals(thirty.speed, fast.speed, 0.0001f)
        assertEquals(CircularDrift(Random(17)).advance("song", 1f, true, 1f / 30),
            CircularDrift(Random(17)).advance("song", 1f, true, 20f))
    }
}
