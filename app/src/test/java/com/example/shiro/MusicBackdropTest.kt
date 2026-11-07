package com.example.shiro

import org.junit.Assert.*
import org.junit.Test

class MusicBackdropTest {
    private val loud = FloatArray(24) { 0.7f }
    private val fast = MusicDynamics(-6f, 1f, 190f, 1f)

    @Test fun ambientFlowAndFrameStallsStayBounded() {
        val motion = MusicBackdropMotion()
        val first = motion.advance(loud, fast, true, 1f / 60f)
        assertTrue(first.energy in 0f..0.03f && first.pulse in 0.06f..0.08f)
        var current = first
        repeat(180) {
            val next = motion.advance(loud, fast, true, 1f / 60f)
            assertTrue(next.phase - current.phase in 0.0..0.007)
            assertTrue(next.energy - current.energy < 0.03f)
            current = next
        }
        assertTrue(current.energy > 0.95f && current.bass > 0.95f)
        val quiet = MusicBackdropMotion()
        var gentle = MusicBackdropFrame()
        repeat(181) { gentle = quiet.advance(FloatArray(24) { 0.04f }, MusicDynamics(-40f), true, 1f / 60f) }
        assertTrue(current.phase > gentle.phase * 5)
        assertTrue(gentle.energy < 0.1f)
        val stalled = MusicBackdropMotion().advance(loud, fast, true, 30f)
        val bounded = MusicBackdropMotion().advance(loud, fast, true, 1f / 30f)
        assertEquals(bounded.phase, stalled.phase, 0.0)
        assertEquals(bounded.energy, stalled.energy, 0f)
        assertArrayEquals(bounded.spectrum, stalled.spectrum, 0f)
    }

    @Test fun repeatedBeatsSwellContinuouslyWithoutFlashing() {
        val motion = MusicBackdropMotion()
        val steady = fast.copy(beatPulse = 0f)
        repeat(180) { motion.advance(loud, steady, true, 1f / 60) }
        var frame = MusicBackdropFrame()
        repeat(5) {
            var peak = 0f
            repeat(6) {
                val next = motion.advance(loud, fast, true, 1f / 60)
                assertTrue(next.pulse - frame.pulse < 0.07f)
                frame = next; peak = maxOf(peak, frame.pulse)
            }
            assertTrue("Music should gently swell the layers", peak in 0.28f..0.65f)
            repeat(24) { frame = motion.advance(loud, steady, true, 1f / 60) }
            assertTrue("Layers should ease down between beats, keeping continuous flow", frame.pulse in 0.1f..peak)
        }
    }

    @Test fun pauseSettlesAndHiddenOrDisabledMotionResumesWithoutJumping() {
        val motion = MusicBackdropMotion()
        repeat(180) { motion.advance(loud, fast, true, 1f / 60f) }
        val fading = motion.advance(loud, fast, false, 1f / 60f)
        assertTrue(fading.energy > 0.9f)
        var paused = fading
        repeat(600) { paused = motion.advance(loud, fast, false, 1f / 60f) }
        assertTrue(motion.isSettled)
        assertEquals(paused, motion.advance(FloatArray(24), fast, true, 1f / 60f))
        val resumed = motion.advance(loud, fast, true, 1f / 60f)
        assertTrue(resumed.energy < 0.03f && resumed.phase - paused.phase < 0.001)
        val hidden = motion.reset()
        assertTrue(motion.isSettled)
        assertEquals(resumed.phase, hidden.phase, 0.0)
        repeat(120) {
            assertEquals(hidden, motion.advance(loud, fast, true, 1f / 60f, motionEnabled = false))
        }
        val invalid = motion.advance(floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY),
            MusicDynamics(beatPulse = Float.NaN, tempoBpm = Float.NaN), true, Float.NaN)
        assertEquals(hidden, invalid)
    }

    @Test fun flowHasTheSamePaceAcrossDisplayRates() {
        fun run(hz: Int): MusicBackdropFrame {
            val motion = MusicBackdropMotion()
            var frame = MusicBackdropFrame()
            repeat(hz * 5) { frame = motion.advance(loud, fast, true, 1f / hz) }
            return frame
        }
        val thirty = run(30)
        val sixty = run(60)
        val oneTwenty = run(120)
        assertEquals(thirty.energy, oneTwenty.energy, 0.001f)
        assertEquals(thirty.bass, oneTwenty.bass, 0.001f)
        assertEquals(thirty.phase, oneTwenty.phase, 0.006)
        assertEquals(sixty.phase, oneTwenty.phase, 0.003)
    }

    @Test fun outgoingCircleKeepsItsPoseDuringTheLayoutFade() {
        val motion = MusicBackdropMotion()
        motion.advance(loud, fast, true, 1f / 60, rotation = 2.3f, circleHeight = 250f)
        val outgoing = motion.advance(loud, fast, true, 1f / 60)
        assertEquals(2.3f, outgoing.rotation, 0f)
        assertEquals(250f, outgoing.circleHeight, 0f)
        val returned = motion.advance(loud, fast, true, 1f / 60, rotation = 2.4f, circleHeight = 260f)
        assertEquals(2.4f, returned.rotation, 0f)
        assertEquals(260f, returned.circleHeight, 0f)
    }
}
