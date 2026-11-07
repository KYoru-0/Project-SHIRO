package com.example.shiro

import kotlin.math.exp

/** Audio-relative loudness and detected rhythm, timestamped with the spectrum that produced them. */
data class MusicDynamics(
    val loudnessDb: Float = -96f,
    val beatPulse: Float = 0f,
    val tempoBpm: Float = 0f,
    val tempoConfidence: Float = 0f
) {
    val energy: Float get() = ((loudnessDb + 55f) / 49f).coerceIn(0f, 1f)
    val tempoDrive: Float get() = ((tempoBpm - 90f) / 100f).coerceIn(0f, 1f) * tempoConfidence
    val impactDrive: Float get() = beatPulse * (0.25f + 0.75f * tempoDrive)
}

/** One cheap display-frame interpolation, shared by the bars and their physical contact surface. */
internal fun advanceMusicSpectrum(current: FloatArray, bands: FloatArray, dynamics: MusicDynamics,
    playing: Boolean, seconds: Float, maximumRise: Float = Float.POSITIVE_INFINITY): FloatArray {
    val fast = dynamics.tempoDrive
    val rise = (1f - exp(-seconds / (0.035f - 0.017f * fast)))
    val fall = (1f - exp(-seconds / if (playing) (0.14f - 0.07f * fast) else 0.16f))
    val scale = 0.66f + 0.24f * dynamics.energy
    val accent = dynamics.beatPulse * (0.2f + 0.7f * fast)
    return FloatArray(current.size) { index ->
        val raw = if (playing) bands.getOrElse(index) { 0f } else 0f
        val level = if (raw.isFinite()) raw.coerceIn(0f, 1f) else 0f
        val base = level * scale
        val target = (base + level * (1f - base) * accent).coerceIn(0f, 1f)
        val previous = current[index]
        val change = (target - previous) * if (target > previous) rise else fall
        val next = previous + change.coerceAtMost(maximumRise)
        if (next < 0.001f) 0f else next
    }
}

/** Ease the first audible rise after play/resume, then leave normal beat response unchanged. */
internal class SpectrumResumeRamp {
    private var elapsed = 0f

    fun reset() { elapsed = 0f }

    fun maximumRise(playing: Boolean, bands: FloatArray, seconds: Float): Float {
        if (!playing) { reset(); return Float.POSITIVE_INFINITY }
        if (elapsed >= 0.45f) return Float.POSITIVE_INFINITY
        // Buffering/silence must not consume the ramp before the first audio data arrives.
        if (elapsed == 0f && bands.none { it.isFinite() && it > 0.001f }) return 0f
        val dt = if (seconds.isFinite()) seconds.coerceIn(0f, 1f / 30f) else 0f
        val progress = ((elapsed + dt * 0.5f) / 0.45f).coerceIn(0f, 1f)
        val eased = progress * progress * (3f - 2f * progress)
        elapsed = (elapsed + dt).coerceAtMost(0.45f)
        return (0.8f + 4.2f * eased) * dt
    }
}
