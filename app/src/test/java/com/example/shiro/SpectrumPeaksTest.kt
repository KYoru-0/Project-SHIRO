package com.example.shiro

import org.junit.Assert.*
import org.junit.Test

class SpectrumPeaksTest {
    @Test fun capsCatchRisesHoldThenFallWithoutDroppingThroughBars() {
        val peaks = SpectrumPeaks(1)
        peaks.step(floatArrayOf(0.8f), 0f)
        assertEquals(0.8f, peaks.levels[0], 0f)
        peaks.step(floatArrayOf(0.2f), 0.39f)
        assertEquals(0.8f, peaks.levels[0], 0f)
        peaks.step(floatArrayOf(0.2f), 0.21f)
        assertEquals(0.764f, peaks.levels[0], 0.0001f)
        peaks.step(floatArrayOf(0.95f), 0.01f)
        assertEquals(0.95f, peaks.levels[0], 0f)
        peaks.step(floatArrayOf(0.2f), 5f)
        assertEquals(0.2f, peaks.levels[0], 0f)
    }

    @Test fun decayIsFrameRateIndependentAndEventuallyStopsOnPause() {
        fun fall(steps: Int): Float {
            val peaks = SpectrumPeaks(1)
            peaks.step(floatArrayOf(1f), 0f)
            repeat(steps) { peaks.step(floatArrayOf(0f), 1f / steps) }
            return peaks.levels[0]
        }
        assertEquals(fall(30), fall(120), 0.0001f)
        val peaks = SpectrumPeaks(2)
        peaks.step(floatArrayOf(1f, 0.5f), 0f)
        peaks.step(floatArrayOf(), 6f)
        assertTrue(peaks.levels.all { it == 0f })
        peaks.step(floatArrayOf(0.8f), 0f)
        peaks.reset()
        assertTrue(peaks.levels.all { it == 0f })
    }
}
