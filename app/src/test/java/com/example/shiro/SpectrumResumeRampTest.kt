package com.example.shiro

import org.junit.Assert.*
import org.junit.Test

class SpectrumResumeRampTest {
    private val music = MusicDynamics(-6f, 1f, 190f, 1f)
    private val source = FloatArray(24) { 0.9f }

    @Test fun loudFastResumeDoesNotLaunchTheSurfaceInOneFrame() {
        val ramp = SpectrumResumeRamp()
        var bars = FloatArray(24)
        repeat(36) { frame ->
            val previous = bars[0]
            val limit = ramp.maximumRise(true, source, 1f / 60f)
            bars = advanceMusicSpectrum(bars, source, music, true, 1f / 60f, limit)
            if (frame == 0) {
                assertTrue("First frame moves less than 4dp on the 208dp spectrum", bars[0] * 208f < 4f)
                val abrupt = advanceMusicSpectrum(FloatArray(24), source, music, true, 1f / 60f)
                assertTrue(abrupt[0] > bars[0] * 20f)
            }
            assertTrue(bars[0] >= previous)
        }
        assertTrue("Full energy returns within 600ms", bars[0] > 0.95f)
        val expected = advanceMusicSpectrum(bars, FloatArray(24) { 0.4f }, music, true, 1f / 60f)
        val actual = advanceMusicSpectrum(bars, FloatArray(24) { 0.4f }, music, true, 1f / 60f,
            ramp.maximumRise(true, source, 1f / 60f))
        assertArrayEquals("Normal beat response is unchanged after startup", expected, actual, 0f)
    }

    @Test fun bufferingPauseAndVisibilityResetRearmTheRampWithoutResettingExistingBars() {
        val ramp = SpectrumResumeRamp()
        repeat(180) { assertEquals(0f, ramp.maximumRise(true, FloatArray(24), 1f / 60f), 0f) }
        assertTrue(ramp.maximumRise(true, source, 1f / 60f) < 0.02f)
        repeat(40) { ramp.maximumRise(true, source, 1f / 60f) }
        val current = FloatArray(24) { 0.5f }
        val paused = advanceMusicSpectrum(current, source, music, false, 1f / 60f,
            ramp.maximumRise(false, source, 1f / 60f))
        assertArrayEquals(advanceMusicSpectrum(current, source, music, false, 1f / 60f), paused, 0f)
        val resumed = advanceMusicSpectrum(paused, source, music, true, 1f / 60f,
            ramp.maximumRise(true, source, 1f / 60f))
        assertTrue(resumed[0] > paused[0])
        assertTrue(resumed[0] - paused[0] < 0.02f)
        ramp.reset()
        assertTrue(ramp.maximumRise(true, source, 1f / 60f) < 0.02f)
    }

    @Test fun startupIsConsistentAcrossFrameRatesAndDoesNotCatchUpInOneStalledFrame() {
        val settled = listOf(30, 60, 120).map { fps ->
            val ramp = SpectrumResumeRamp()
            var bars = FloatArray(24)
            repeat(fps / 2) {
                val dt = 1f / fps
                bars = advanceMusicSpectrum(bars, source, music, true, dt, ramp.maximumRise(true, source, dt))
            }
            bars[0]
        }
        assertTrue(settled.max() - settled.min() < 0.02f)
        val stalled = SpectrumResumeRamp().maximumRise(true, source, 0.5f)
        assertTrue(stalled < 0.03f)
    }
}
