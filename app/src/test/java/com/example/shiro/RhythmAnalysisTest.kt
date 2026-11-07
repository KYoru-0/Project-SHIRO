package com.example.shiro

import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class RhythmAnalysisTest {
    private val sampleRate = 48_000

    private fun signal(analyzer: SpectrumAnalyzer, seconds: Int, bpm: Float? = null, gain: Float = 0.8f,
        oppositeStereo: Boolean = false): List<SpectrumFrame> {
        val frames = ArrayList<SpectrumFrame>()
        val samples = FloatArray(if (oppositeStereo) 2 else 1)
        repeat(sampleRate * seconds) { i ->
            val time = i.toDouble() / sampleRate
            val phase = if (bpm == null) 0.0 else time % (60.0 / bpm)
            val envelope = if (bpm == null) 1.0 else exp(-phase / 0.045)
            samples[0] = (gain * sin(2 * PI * 120 * time) * envelope).toFloat()
            if (oppositeStereo) samples[1] = -samples[0]
            analyzer.addFrame(samples, 0)?.let { frames.add(it) }
        }
        return frames
    }

    @Test fun actualPcmBeatTrainsDistinguishSlowAndFastTempo() {
        val report = StringBuilder("Synthetic kick trains through the real PCM/FFT pipeline; tempo estimates are approximate.\n")
        for (bpm in listOf(80f, 120f, 180f, 210f)) {
            val frames = signal(SpectrumAnalyzer(sampleRate, 1), 12, bpm)
            val tail = frames.takeLast(150).map { it.dynamics }
            val estimates = tail.filter { it.tempoConfidence > 0.6f }.map { it.tempoBpm }.sorted()
            assertTrue("No stable estimate for $bpm BPM", estimates.size > 100)
            val estimated = estimates[estimates.size / 2]
            assertEquals("Tempo of $bpm BPM", bpm, estimated, 12f)
            val hits = frames.zipWithNext().count { (a, b) -> b.dynamics.beatPulse > a.dynamics.beatPulse + 0.1f }
            assertTrue("$bpm BPM produced $hits pulses", hits in (bpm / 5 - 4).toInt()..(bpm / 5 + 2).toInt())
            assertTrue(tail.all { it.beatPulse in 0f..1f && it.tempoConfidence in 0f..1f && it.loudnessDb.isFinite() })
            report.append("Input $bpm BPM -> median %.1f BPM, %d beat pulses in 12 s.\n".format(estimated, hits))
        }
        File("build/reports/shiro-rhythm-analysis.txt").apply { parentFile?.mkdirs(); writeText(report.toString()) }
        println(report)
    }

    @Test fun steadyToneHasNoInventedBeatsAndHalfAmplitudeIsSixDbQuieter() {
        val loud = signal(SpectrumAnalyzer(sampleRate, 1), 3)
        val quiet = signal(SpectrumAnalyzer(sampleRate, 1), 3, gain = 0.4f)
        assertTrue(loud.drop(10).all { it.dynamics.beatPulse == 0f && it.dynamics.tempoBpm == 0f })
        assertEquals(-4.95f, loud.last().dynamics.loudnessDb, 0.2f)
        assertEquals(6.0206f, loud.last().dynamics.loudnessDb - quiet.last().dynamics.loudnessDb, 0.05f)
        val stereo = signal(SpectrumAnalyzer(sampleRate, 2), 3, oppositeStereo = true)
        assertEquals(loud.last().dynamics.loudnessDb, stereo.last().dynamics.loudnessDb, 0.001f)
    }

    @Test fun silenceAndResetClearRhythmWithoutReusingOldTempo() {
        val analyzer = SpectrumAnalyzer(sampleRate, 1)
        assertTrue(signal(analyzer, 6, 180f).last().dynamics.tempoBpm > 150f)
        val silence = signal(analyzer, 3, gain = 0f)
        assertTrue(silence.drop(5).all { it.dynamics.beatPulse == 0f })
        assertEquals(0f, silence.last().dynamics.tempoBpm, 0f)
        signal(analyzer, 5, 180f)
        analyzer.reset()
        val fresh = signal(analyzer, 1, gain = 0f)
        assertTrue(fresh.all { it.dynamics == MusicDynamics() })
    }

    @Test fun tempoChangesAreLearnedAndQuietFastBeatsStayGentler() {
        val analyzer = SpectrumAnalyzer(sampleRate, 1)
        val slow = signal(analyzer, 6, 80f).last().dynamics
        assertEquals(80f, slow.tempoBpm, 10f)
        val fast = signal(analyzer, 6, 180f)
        assertEquals(180f, fast.last().dynamics.tempoBpm, 10f)
        assertTrue(fast.last().dynamics.tempoConfidence > 0.6f)
        val quiet = signal(SpectrumAnalyzer(sampleRate, 1), 6, 180f, gain = 0.025f)
        val loudPeak = fast.takeLast(90).maxOf { it.dynamics.impactDrive }
        val quietPeak = quiet.takeLast(90).maxOf { it.dynamics.impactDrive }
        assertTrue("Quiet fast music should not launch cats as strongly", quietPeak < loudPeak * 0.6f)
        assertEquals(180f, quiet.last().dynamics.tempoBpm, 10f)
    }
}
