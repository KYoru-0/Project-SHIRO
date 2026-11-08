package com.example.shiro

/** Missing PCM on scene return is not silence. Hold briefly, but never retain stale audio forever. */
internal class SpectrumHandoff {
    private var waitingSeconds = 0f

    fun shouldHold(playing: Boolean, available: Boolean, seconds: Float): Boolean {
        if (!playing || available) {
            waitingSeconds = 0f
            return false
        }
        if (seconds.isFinite()) waitingSeconds += seconds.coerceAtLeast(0f)
        return waitingSeconds < 1f
    }
}
