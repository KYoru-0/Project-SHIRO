package com.example.shiro

import org.junit.Assert.*
import org.junit.Test

class SpectrumHandoffTest {
    private val dt = 1f / 60f
    private val music = MusicDynamics(-8f, 0f, 160f, 1f)

    @Test fun analyzerRestartDoesNotInventASecondRiseFromZero() {
        val analyzer = SpectrumAnalyzer(48_000, 1)
        val sample = FloatArray(1)
        var first: SpectrumFrame? = null
        var last: SpectrumFrame? = null
        repeat(48_000) { index ->
            sample[0] = (kotlin.math.sin(index * 2.0 * Math.PI * 1000.0 / 48_000) * 0.5).toFloat()
            analyzer.addFrame(sample, 0)?.let {
                if (first == null) first = it
                last = it
            }
        }
        val band = analyzer.bandForFrequency(1000.0)
        assertTrue(first!!.bands[band] > 0.7f)
        assertEquals(last!!.bands[band], first!!.bands[band], 0.02f)
        analyzer.reset()
        var restarted: SpectrumFrame? = null
        repeat(2048) { index ->
            sample[0] = (kotlin.math.sin(index * 2.0 * Math.PI * 1000.0 / 48_000) * 0.5).toFloat()
            analyzer.addFrame(sample, 0)?.let { restarted = it }
        }
        assertEquals(first!!.bands[band], restarted!!.bands[band], 0.0001f)
    }

    @Test fun navigationGapKeepsTheVisibleWaveUntilFreshPcmArrives() {
        val queue = SpectrumFrameQueue()
        val handoff = SpectrumHandoff()
        val previous = FloatArray(SpectrumFrame.BAND_COUNT) { 0.55f }
        var displayed = previous.copyOf()
        repeat(30) {
            val incoming = queue.currentFrame(100L, 2)
            assertFalse(incoming.available)
            if (!handoff.shouldHold(true, incoming.available, dt)) {
                displayed = advanceMusicSpectrum(displayed, incoming.bands, incoming.dynamics, true, dt)
            }
            assertArrayEquals(previous, displayed, 0f)
        }
        val next = SpectrumFrame(FloatArray(SpectrumFrame.BAND_COUNT) { 0.8f }, 0.8f, music)
        queue.offer(100L, 2, next)
        val incoming = queue.currentFrame(100L, 2)
        assertFalse(handoff.shouldHold(true, incoming.available, dt))
        displayed = advanceMusicSpectrum(displayed, incoming.bands, incoming.dynamics, true, dt)
        assertTrue(displayed.all { it > 0.55f && it < 0.8f })
    }

    @Test fun realSilenceIsAvailableAndDoesNotWaitThroughTheHandoff() {
        val queue = SpectrumFrameQueue()
        queue.offer(100L, 1, SpectrumFrame.silence())
        val silent = queue.currentFrame(100L, 1)
        assertTrue(silent.available)
        assertFalse(SpectrumHandoff().shouldHold(true, silent.available, dt))
        val lower = advanceMusicSpectrum(FloatArray(24) { 0.7f }, silent.bands, silent.dynamics, true, dt)
        assertTrue(lower.all { it < 0.7f })
    }

    @Test fun pauseDescendsImmediatelyEvenWhenPcmIsUnavailable() {
        val handoff = SpectrumHandoff()
        assertTrue(handoff.shouldHold(true, false, dt))
        assertFalse(handoff.shouldHold(false, false, dt))
        val lower = advanceMusicSpectrum(FloatArray(24) { 0.7f }, FloatArray(24), music, false, dt)
        assertTrue(lower.all { it < 0.7f })
    }

    @Test fun unavailableAudioTimesOutRatherThanKeepingAStaleWaveForever() {
        val handoff = SpectrumHandoff()
        repeat(61) { handoff.shouldHold(true, false, dt) }
        assertFalse(handoff.shouldHold(true, false, dt))
        assertFalse(handoff.shouldHold(true, true, dt))
        assertTrue("Fresh audio rearms protection for a later navigation gap", handoff.shouldHold(true, false, dt))
    }

    @Test fun validAudioKeepsChangingTheWaveThroughoutPlayback() {
        val handoff = SpectrumHandoff()
        var displayed = FloatArray(24) { 0.2f }
        var minimum = 1f
        var maximum = 0f
        repeat(180) { index ->
            val level = if (index / 30 % 2 == 0) 0.8f else 0.2f
            assertFalse(handoff.shouldHold(true, true, dt))
            displayed = advanceMusicSpectrum(displayed, FloatArray(24) { level }, music, true, dt)
            minimum = minOf(minimum, displayed[0])
            maximum = maxOf(maximum, displayed[0])
        }
        assertTrue("Live frames must continue to animate instead of staying frozen", maximum - minimum > 0.3f)
    }
}
