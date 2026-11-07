package com.example.shiro

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class SpectrumAnalyzerTest {
    private val sampleRate = 48_000

    private fun tone(
        analyzer: SpectrumAnalyzer,
        frequency: Double,
        leftGain: Float = 1f,
        rightGain: Float? = null,
        frames: Int = sampleRate
    ): SpectrumFrame {
        val samples = FloatArray(if (rightGain == null) 1 else 2)
        var result = SpectrumFrame.silence()
        repeat(frames) { index ->
            val sample = (0.8 * sin(2 * PI * frequency * index / sampleRate)).toFloat()
            samples[0] = sample * leftGain
            if (rightGain != null) samples[1] = sample * rightGain
            analyzer.addFrame(samples, 0)?.let { result = it }
        }
        return result
    }

    @Test fun silenceHasNoEnergyAndOutputIsBounded() {
        val analyzer = SpectrumAnalyzer(sampleRate, 1)
        val silence = tone(analyzer, 1000.0, leftGain = 0f)
        assertTrue(silence.bands.all { it == 0f })
        assertEquals(0f, silence.bassEnergy)
        val loud = tone(analyzer, 120.0, leftGain = 10f)
        assertTrue(loud.bands.all { it.isFinite() && it in 0f..1f })
        assertTrue(loud.bassEnergy in 0f..1f)
    }

    @Test fun tonesLandInTheirLogarithmicFrequencyBands() {
        for (frequency in listOf(120.0, 1200.0, 7000.0)) {
            val analyzer = SpectrumAnalyzer(sampleRate, 1)
            val frame = tone(analyzer, frequency)
            val strongest = frame.bands.indices.maxBy { frame.bands[it] }
            assertTrue("$frequency Hz strongest in band $strongest",
                abs(strongest - analyzer.bandForFrequency(frequency)) <= 1)
            assertTrue(frame.bands[strongest] > 0.8f)
        }
    }

    @Test fun oppositePhaseStereoHasSamePowerAsMatchingStereo() {
        val matching = tone(SpectrumAnalyzer(sampleRate, 2), 1200.0, rightGain = 1f)
        val opposite = tone(SpectrumAnalyzer(sampleRate, 2), 1200.0, rightGain = -1f)
        assertArrayEquals(matching.bands, opposite.bands, 0.00001f)
    }

    @Test fun rightOnlyAudioIsVisibleAndMatchesLeftOnlyAudio() {
        val left = tone(SpectrumAnalyzer(sampleRate, 2), 7000.0, leftGain = 1f, rightGain = 0f)
        val right = tone(SpectrumAnalyzer(sampleRate, 2), 7000.0, leftGain = 0f, rightGain = 1f)
        assertArrayEquals(left.bands, right.bands, 0.00001f)
        assertTrue(right.bands.max() > 0.8f)
    }

    @Test fun resetRemovesThePreviousTracksSmoothingAndSamples() {
        val analyzer = SpectrumAnalyzer(sampleRate, 1)
        val before = tone(analyzer, 120.0)
        assertTrue(before.bassEnergy > 0.8f)
        val saved = before.bands.copyOf()
        analyzer.reset()
        val after = tone(analyzer, 120.0, leftGain = 0f, frames = 2048)
        assertTrue(after.bands.all { it == 0f })
        assertArrayEquals(saved, before.bands, 0f)
    }

    @Test fun snapshotsAreThrottledAndNonFiniteInputIsSilence() {
        val analyzer = SpectrumAnalyzer(sampleRate, 1)
        var count = 0
        repeat(sampleRate) {
            val result = analyzer.addFrame(floatArrayOf(Float.NaN), 0)
            if (result != null) {
                count++
                assertTrue(result.bands.all { value -> value == 0f })
            }
        }
        assertTrue("Produced $count frames", count in 28..30)
    }
}
