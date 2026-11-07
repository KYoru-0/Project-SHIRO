package com.example.shiro

/** Peak trails catch rising bars, hold briefly, then fall at a frame-rate-independent speed. */
internal class SpectrumPeaks(count: Int) {
    val levels = FloatArray(count)
    private val holds = FloatArray(count)

    fun reset() { levels.fill(0f); holds.fill(0f) }

    fun step(bands: FloatArray, seconds: Float) {
        for (i in levels.indices) {
            val band = bands.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            if (band >= levels[i]) {
                levels[i] = band
                holds[i] = 0.4f
            } else {
                val fallingTime = (seconds - holds[i]).coerceAtLeast(0f)
                holds[i] = (holds[i] - seconds).coerceAtLeast(0f)
                levels[i] = maxOf(band, levels[i] - fallingTime * 0.18f)
            }
        }
    }
}
