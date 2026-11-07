package com.example.shiro

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

@OptIn(UnstableApi::class)
class KickDetectorAudioProcessor(
    var onLowBandHit: () -> Unit = {},
    var onMidBandHit: () -> Unit = {},
    var onHighBandHit: () -> Unit = {}
) : BaseAudioProcessor() {

    private var sampleRate = 44100
    private var channelCount = 2

    // Filter coefficients for Multi-Band decomposition
    private var alphaLow = 0.035f  // ~250 Hz Low-Pass
    private var alphaMid = 0.35f   // ~2500 Hz Low-Pass

    // Filter state
    private var lpLow = 0f
    private var lpMid = 0f

    // Previous frame RMS band energies for rate-of-change (Novelty)
    private var prevELow = 0f
    private var prevEMid = 0f
    private var prevEHigh = 0f

    // Dynamic adaptive thresholding per band
    private var movingAvgLow = 0f
    private var movingAvgMid = 0f
    private var movingAvgHigh = 0f

    private var lastLowTimeMs = 0L
    private var lastMidTimeMs = 0L
    private var lastHighTimeMs = 0L

    // BPM Tracking State & 1/4th beat debounce
    private var estimatedBpm = 120f
    private var dynamicMinIntervalMs = 125L // Default for 120 BPM (1/4th of a beat)
    private val onsetIntervalsMs = mutableListOf<Long>()
    private var bpmTrackingFinished = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount

        val dt = 1f / sampleRate
        val rcLow = 1f / (2f * Math.PI.toFloat() * 250f)
        val rcMid = 1f / (2f * Math.PI.toFloat() * 2500f)

        alphaLow = dt / (rcLow + dt)
        alphaMid = dt / (rcMid + dt)

        resetBpmTracking()

        return inputAudioFormat
    }

    fun resetBpmTracking() {
        estimatedBpm = 120f
        dynamicMinIntervalMs = 125L
        onsetIntervalsMs.clear()
        bpmTrackingFinished = false
        lastLowTimeMs = 0L
        lastMidTimeMs = 0L
        lastHighTimeMs = 0L
    }

    private fun updateBpmFromOnsets(deltaMs: Long) {
        if (bpmTrackingFinished) return

        if (deltaMs in 200L..1200L) {
            onsetIntervalsMs.add(deltaMs)

            if (onsetIntervalsMs.size >= 8) {
                val sorted = onsetIntervalsMs.sorted()
                val medianDelta = sorted[sorted.size / 2].toFloat()

                var rawBpm = 60000f / medianDelta
                while (rawBpm < 80f) rawBpm *= 2f
                while (rawBpm > 170f) rawBpm /= 2f

                estimatedBpm = rawBpm
                val beatDurationMs = 60000f / estimatedBpm
                dynamicMinIntervalMs = (beatDurationMs / 4f).toLong().coerceIn(50L, 250L)

                bpmTrackingFinished = true
            }
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val remaining = limit - position
        if (remaining <= 0) return

        val buffer = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        var sumLow = 0f
        var sumMid = 0f
        var sumHigh = 0f
        var sampleCount = 0
        var i = position

        while (i < limit - 1) {
            val rawSample = buffer.getShort(i) / 32768f

            // Multi-band filterbank
            lpLow += alphaLow * (rawSample - lpLow)
            lpMid += alphaMid * (rawSample - lpMid)

            val sLow = lpLow
            val sMid = lpMid - lpLow
            val sHigh = rawSample - lpMid

            sumLow += sLow * sLow
            sumMid += sMid * sMid
            sumHigh += sHigh * sHigh
            sampleCount++

            i += 2 * channelCount
        }

        if (sampleCount > 0) {
            val eLow = sqrt((sumLow / sampleCount).toDouble()).toFloat()
            val eMid = sqrt((sumMid / sampleCount).toDouble()).toFloat()
            val eHigh = sqrt((sumHigh / sampleCount).toDouble()).toFloat()

            // Calculate positive rate-of-change (Novelty) per band
            val dLow = (eLow - prevELow).coerceAtLeast(0f)
            val dMid = (eMid - prevEMid).coerceAtLeast(0f)
            val dHigh = (eHigh - prevEHigh).coerceAtLeast(0f)

            prevELow = eLow
            prevEMid = eMid
            prevEHigh = eHigh

            val currentTimeMs = System.currentTimeMillis()

            movingAvgLow = 0.92f * movingAvgLow + 0.08f * dLow
            movingAvgMid = 0.92f * movingAvgMid + 0.08f * dMid
            movingAvgHigh = 0.92f * movingAvgHigh + 0.08f * dHigh

            val threshLow = (movingAvgLow * 1.5f).coerceAtLeast(0.005f)
            val threshMid = (movingAvgMid * 1.5f).coerceAtLeast(0.005f)
            val threshHigh = (movingAvgHigh * 1.5f).coerceAtLeast(0.005f)

            // Low Band Hit (Kicks / Bass)
            if (dLow > threshLow && (currentTimeMs - lastLowTimeMs >= dynamicMinIntervalMs)) {
                if (lastLowTimeMs > 0L) updateBpmFromOnsets(currentTimeMs - lastLowTimeMs)
                lastLowTimeMs = currentTimeMs
                onLowBandHit()
            }

            // Mid-Band Hit (Vocals / Melody / Guitars / Piano)
            if (dMid > threshMid && (currentTimeMs - lastMidTimeMs >= dynamicMinIntervalMs)) {
                lastMidTimeMs = currentTimeMs
                onMidBandHit()
            }

            // High Band Hit (Snares / Cymbals / Hi-Hats)
            if (dHigh > threshHigh && (currentTimeMs - lastHighTimeMs >= dynamicMinIntervalMs)) {
                lastHighTimeMs = currentTimeMs
                onHighBandHit()
            }
        }

        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }
}
