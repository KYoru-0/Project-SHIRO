package com.example.shiro

import org.junit.Assert.*
import org.junit.Test

class MusicDynamicsTest {
    @Test fun fastConfidentBeatsAreStrongerAndRecoverFaster() {
        val slow = MusicDynamics(-6f, 1f, 80f, 1f)
        val fast = MusicDynamics(-6f, 1f, 190f, 1f)
        val bands = FloatArray(24) { 0.6f }
        var slowBars = FloatArray(24)
        var fastBars = FloatArray(24)
        repeat(6) {
            slowBars = advanceMusicSpectrum(slowBars, bands, slow, true, 1f / 60f)
            fastBars = advanceMusicSpectrum(fastBars, bands, fast, true, 1f / 60f)
        }
        assertTrue(fastBars[0] > slowBars[0] * 1.25f)
        assertTrue(fast.impactDrive > slow.impactDrive * 3f)
        val high = FloatArray(24) { 0.9f }
        val silence = FloatArray(24)
        val fastFall = advanceMusicSpectrum(high, silence, fast, true, 1f / 30f)
        val slowFall = advanceMusicSpectrum(high, silence, slow, true, 1f / 30f)
        assertTrue(fastFall[0] < slowFall[0])
        assertEquals(0f, fast.copy(tempoConfidence = 0f).tempoDrive, 0f)
    }

    @Test fun silenceStaysEmptyAndLoudnessChangesIntensityWithoutFillingEmptyBands() {
        val dramatic = MusicDynamics(-3f, 1f, 220f, 1f)
        assertTrue(advanceMusicSpectrum(FloatArray(24), FloatArray(24), dramatic, true, 1f).all { it == 0f })
        val bands = floatArrayOf(0.8f, 0.5f, 0f, Float.NaN)
        val loud = advanceMusicSpectrum(FloatArray(4), bands, dramatic, true, 1f)
        val quiet = advanceMusicSpectrum(FloatArray(4), bands, dramatic.copy(loudnessDb = -45f, beatPulse = 0.1f), true, 1f)
        assertTrue(loud[0] > quiet[0])
        assertTrue(loud[0] > loud[1])
        assertEquals(0f, loud[2], 0f)
        assertEquals(0f, loud[3], 0f)
        assertTrue(loud.all { it.isFinite() && it in 0f..1f })
    }

    @Test fun pauseDescendsSmoothlyAndResumeReformsQuickly() {
        val music = MusicDynamics(-6f, 1f, 190f, 1f)
        val source = FloatArray(24) { 0.9f }
        var bars = source.copyOf()
        repeat(72) { frame ->
            val previous = bars[0]
            bars = advanceMusicSpectrum(bars, source, music, false, 1f / 60f)
            assertTrue(bars[0] <= previous)
            if (frame == 0) assertTrue(bars[0] > 0.7f)
        }
        assertTrue(bars.all { it == 0f })
        repeat(3) { bars = advanceMusicSpectrum(bars, source, music, true, 1f / 60f) }
        assertTrue(bars[0] > 0.7f)
    }

    @Test fun sleepFadeAttenuatesBandsAndBeatStrengthTogetherWithoutChangingTempo() {
        val original = SpectrumFrame(FloatArray(24) { 0.8f }, 0.7f, MusicDynamics(-10f, 0.9f, 180f, 1f))
        val faded = original.attenuated(0.5f)
        assertEquals(0.4f, faded.bands[0], 0f)
        assertEquals(0.45f, faded.dynamics.beatPulse, 0f)
        assertEquals(-16.0206f, faded.dynamics.loudnessDb, 0.001f)
        assertEquals(180f, faded.dynamics.tempoBpm, 0f)
        assertEquals(0.9f, original.dynamics.beatPulse, 0f)
        assertSame(original, original.attenuated(1f))
        assertEquals(MusicDynamics(), original.attenuated(0f).dynamics)
        assertEquals(MusicDynamics(), original.attenuated(Float.NaN).dynamics)
    }

    @Test fun rhythmArrivesWithAudibleSpectrumAndDisappearsAfterSeekOrExpiry() {
        val frame = SpectrumFrame(FloatArray(24) { 0.6f }, 0.5f, MusicDynamics(-10f, 0.8f, 180f, 1f))
        val queue = SpectrumFrameQueue()
        queue.offer(1_000_000L, 1L, frame)
        assertEquals(0f, queue.currentFrame(999_999L, 1L).dynamics.beatPulse, 0f)
        assertSame(frame, queue.currentFrame(1_000_000L, 1L))
        assertEquals(MusicDynamics(), queue.currentFrame(1_250_001L, 1L).dynamics)
        assertEquals(MusicDynamics(), queue.currentFrame(1_000_000L, 2L).dynamics)
    }

    @Test fun beatAccentOnlyBoostsRealLandingsAndKeepsTwentyCatsBounded() {
        fun landed(drive: Float, floor: Float = 40f): WaveCatBody = WaveCatBody(100f).apply {
            height = floor + 10f; velocityY = -160f
            step(floor, 0f, 1f / 120f, 10f, floor, impactDrive = drive)
        }
        assertTrue(landed(1f).velocityY > landed(0f).velocityY + 60f)
        assertEquals(landed(0f, 0f).velocityY, landed(1f, 0f).velocityY, 0f)
        repeat(20) { index ->
            val body = WaveCatBody(30f + index * 15f, generation = 1).apply { height = 100f }
            body.step(40f, 0f, 1f / 120f, 10f, 40f, impactDrive = 1f)
            assertTrue(body.velocityY < 0f) // No midair beat kick.
            body.grab()
            val heldHeight = body.height
            body.step(100f, 0f, 1f / 120f, 10f, 100f, impactDrive = 1f)
            assertEquals(heldHeight, body.height, 0f)
            assertEquals(0f, body.velocityY, 0f)
            body.release()
            body.height = 0f; body.velocityY = -2000f
            body.step(40f, 0f, 1f / 120f, 10f, 40f, impactDrive = 100f)
            assertTrue(body.velocityY <= 330f)
        }
    }
}
