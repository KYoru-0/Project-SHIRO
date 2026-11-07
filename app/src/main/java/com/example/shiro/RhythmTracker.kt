package com.example.shiro

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10

/** Small online onset detector. Runs on the existing FFT worker; never decodes a second audio stream. */
internal class RhythmTracker(private val bandCount: Int) {
    private val previous = FloatArray(bandCount)
    private val intervals = FloatArray(6)
    private val sortedIntervals = FloatArray(6)
    private var intervalCount = 0
    private var intervalIndex = 0
    private var primed = false
    private var time = 0.0
    private var lastBeat = Double.NEGATIVE_INFINITY
    private var meanFlux = 0f
    private var deviation = 0f
    private var pulse = 0f
    private var bpm = 0f
    private var confidence = 0f
    private var loudness = -96f
    private var previousDb = -96f

    fun reset() {
        previous.fill(0f)
        intervalCount = 0; intervalIndex = 0; primed = false
        time = 0.0; lastBeat = Double.NEGATIVE_INFINITY
        meanFlux = 0f; deviation = 0f; pulse = 0f
        bpm = 0f; confidence = 0f; loudness = -96f; previousDb = -96f
    }

    fun step(levels: FloatArray, meanSquare: Double, seconds: Float): MusicDynamics {
        time += seconds
        val db = (10.0 * log10(meanSquare.coerceAtLeast(1e-10))).toFloat().coerceIn(-96f, 0f)
        val levelBlend = 1f - exp(-seconds / if (db > loudness) 0.045f else 0.25f)
        loudness += (db - loudness) * levelBlend
        var flux = 0f
        var weights = 0f
        for (i in 0 until bandCount) {
            // Favor kicks/body over a busy high-hat pattern, while retaining mid/high onsets.
            val weight = if (i < bandCount / 3) 1.7f else 1f
            flux += (levels[i] - previous[i]).coerceAtLeast(0f) * weight
            weights += weight
            previous[i] = levels[i]
        }
        flux /= weights
        // A transient split across FFT windows can have modest spectral flux but a clear RMS rise.
        flux = if (primed) 0.65f * flux + 0.35f * ((db - previousDb) / 24f).coerceIn(0f, 1f) else 0f
        previousDb = db
        val fast = ((bpm - 90f) / 100f).coerceIn(0f, 1f) * confidence
        pulse *= exp(-seconds / (0.24f - 0.12f * fast))
        val threshold = (meanFlux * 1.7f + deviation * 1.2f).coerceAtLeast(0.012f)
        val sinceBeat = time - lastBeat
        if (primed && db > -60f && flux > threshold && sinceBeat >= 0.2) {
            if (sinceBeat in 0.22..1.5) {
                intervals[intervalIndex] = sinceBeat.toFloat()
                intervalIndex = (intervalIndex + 1) % intervals.size
                intervalCount = (intervalCount + 1).coerceAtMost(intervals.size)
                for (i in 0 until intervalCount) sortedIntervals[i] = intervals[i]
                sortedIntervals.sort(0, intervalCount)
                val median = sortedIntervals[intervalCount / 2]
                var consistent = 0
                var intervalSum = 0f
                for (i in 0 until intervalCount) if (abs(intervals[i] - median) < median * 0.2f) {
                    consistent++
                    intervalSum += intervals[i]
                }
                // Average only the consistent intervals to remove the 30 Hz window quantization.
                bpm = (60f * consistent / intervalSum).coerceIn(40f, 240f)
                confidence = if (intervalCount < 3) 0f else consistent.toFloat() / intervalCount
            } else if (sinceBeat > 1.5) {
                intervalCount = 0; intervalIndex = 0; bpm = 0f; confidence = 0f
            }
            lastBeat = time
            val strength = (flux / (threshold * 3f)).coerceIn(0f, 1f)
            val energy = ((db + 55f) / 49f).coerceIn(0f, 1f)
            pulse = strength * energy
        }
        if (time - lastBeat > 1.8) {
            bpm = 0f; confidence = 0f; intervalCount = 0; intervalIndex = 0
        }
        if (db <= -80f) pulse = 0f
        if (pulse < 0.001f) pulse = 0f
        val adapt = 1f - exp(-seconds / 0.8f)
        deviation += (abs(flux - meanFlux) - deviation) * adapt
        meanFlux += (flux - meanFlux) * adapt
        primed = true
        return MusicDynamics(loudness, pulse, bpm, confidence)
    }
}
