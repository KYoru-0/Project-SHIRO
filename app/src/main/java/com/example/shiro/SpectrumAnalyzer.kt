package com.example.shiro

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Pure DSP, owned exclusively by the spectrum worker. Each channel contributes power, not phase. */
internal class SpectrumAnalyzer(
    private val sampleRate: Int,
    private val channels: Int,
    private val windowSize: Int = 2048
) {
    private val samples = Array(channels) { FloatArray(windowSize) }
    private val hann = DoubleArray(windowSize) { 0.5 - 0.5 * cos(2.0 * PI * it / (windowSize - 1)) }
    private val real = DoubleArray(windowSize)
    private val imaginary = DoubleArray(windowSize)
    private val powers = DoubleArray(windowSize / 2 + 1)
    private val smoothed = FloatArray(SpectrumFrame.BAND_COUNT)
    private val rawLevels = FloatArray(SpectrumFrame.BAND_COUNT)
    private val rhythm = RhythmTracker(SpectrumFrame.BAND_COUNT)
    private var writeIndex = 0
    private var samplesSeen = 0
    private var samplesSinceFrame = 0
    private val hopSize = max(1, sampleRate / 30)
    private val minimumHz = 40.0
    private val maximumHz = min(16_000.0, sampleRate / 2.0)
    private val bandRatio = maximumHz / minimumHz

    init {
        require(sampleRate >= 1000 && channels in 1..8)
        require(windowSize >= 2 && windowSize and (windowSize - 1) == 0)
    }

    fun reset() {
        samples.forEach { it.fill(0f) }
        smoothed.fill(0f)
        rhythm.reset()
        writeIndex = 0
        samplesSeen = 0
        samplesSinceFrame = 0
    }

    /** Returns a new snapshot at most 30 times per second of audio. */
    fun addFrame(interleaved: FloatArray, offset: Int): SpectrumFrame? {
        for (channel in 0 until channels) {
            val value = interleaved[offset + channel]
            samples[channel][writeIndex] = if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
        }
        writeIndex = (writeIndex + 1) % windowSize
        samplesSeen = min(windowSize, samplesSeen + 1)
        samplesSinceFrame++
        if (samplesSeen < windowSize || samplesSinceFrame < hopSize) return null
        samplesSinceFrame = 0
        return analyze()
    }

    fun bandForFrequency(frequencyHz: Double): Int =
        ((ln(frequencyHz.coerceAtLeast(minimumHz) / minimumHz) / ln(bandRatio)) * SpectrumFrame.BAND_COUNT)
            .toInt().coerceIn(0, SpectrumFrame.BAND_COUNT - 1)

    private fun analyze(): SpectrumFrame {
        powers.fill(0.0)
        var squareSum = 0.0
        for (channel in 0 until channels) {
            for (i in 0 until windowSize) {
                val sample = samples[channel][(writeIndex + i) % windowSize].toDouble()
                squareSum += sample * sample
                real[i] = sample * hann[i]
                imaginary[i] = 0.0
            }
            fft()
            // Hann coherent gain is about 1/2. Average channel POWER so opposite-phase
            // stereo, or music present in only the right channel, never cancels to silence.
            val normalization = 16.0 / (windowSize.toDouble() * windowSize * channels)
            for (bin in powers.indices) {
                powers[bin] += (real[bin] * real[bin] + imaginary[bin] * imaginary[bin]) * normalization
            }
        }
        val output = FloatArray(SpectrumFrame.BAND_COUNT)
        var bass = 0f
        for (band in output.indices) {
            val lowHz = minimumHz * bandRatio.pow(band.toDouble() / output.size)
            val highHz = minimumHz * bandRatio.pow((band + 1.0) / output.size)
            val first = max(1, kotlin.math.ceil(lowHz * windowSize / sampleRate).toInt())
                .coerceAtMost(powers.lastIndex)
            val last = max(first, kotlin.math.ceil(highHz * windowSize / sampleRate).toInt() - 1)
                .coerceAtMost(powers.lastIndex)
            var peakPower = 0.0
            for (bin in first..last) peakPower = max(peakPower, powers[bin])
            val decibels = 10.0 * log10(peakPower.coerceAtLeast(1e-12))
            rawLevels[band] = ((decibels + 65.0) / 65.0).toFloat().coerceIn(0f, 1f)
        }
        val dynamics = rhythm.step(rawLevels, squareSum / (windowSize.toDouble() * channels),
            hopSize.toFloat() / sampleRate)
        val fast = dynamics.tempoDrive
        val lastBassBand = bandForFrequency(250.0)
        val stepSeconds = hopSize.toDouble() / sampleRate
        val rise = (1.0 - exp(-stepSeconds / (0.035 - 0.016 * fast))).toFloat()
        val fall = (1.0 - exp(-stepSeconds / (0.18 - 0.09 * fast))).toFloat()
        for (band in output.indices) {
            val level = rawLevels[band]
            val blend = if (level > smoothed[band]) rise else fall
            smoothed[band] += blend * (level - smoothed[band])
            output[band] = smoothed[band].let { if (it < 0.001f) 0f else it.coerceIn(0f, 1f) }
            if (band <= lastBassBand) bass = max(bass, output[band])
        }
        return SpectrumFrame(output, bass, dynamics)
    }

    /** In-place radix-2 FFT; all scratch arrays are reused. */
    private fun fft() {
        var j = 0
        for (i in 1 until windowSize) {
            var bit = windowSize shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val value = real[i]; real[i] = real[j]; real[j] = value
            }
        }
        var length = 2
        while (length <= windowSize) {
            val angle = -2.0 * PI / length
            val stepReal = cos(angle)
            val stepImaginary = sin(angle)
            var start = 0
            while (start < windowSize) {
                var twiddleReal = 1.0
                var twiddleImaginary = 0.0
                for (offset in 0 until length / 2) {
                    val even = start + offset
                    val odd = even + length / 2
                    val oddReal = real[odd] * twiddleReal - imaginary[odd] * twiddleImaginary
                    val oddImaginary = real[odd] * twiddleImaginary + imaginary[odd] * twiddleReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                    val nextReal = twiddleReal * stepReal - twiddleImaginary * stepImaginary
                    twiddleImaginary = twiddleReal * stepImaginary + twiddleImaginary * stepReal
                    twiddleReal = nextReal
                }
                start += length
            }
            length = length shl 1
        }
    }
}
