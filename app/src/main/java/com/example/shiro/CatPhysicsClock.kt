package com.example.shiro

/** Fixed 120 Hz physics: carry fractional steps instead of rounding every display frame upward. */
internal class CatPhysicsClock {
    private var remainder = 0L

    fun steps(elapsedNanos: Long): Int {
        // Drop excess catch-up after a stall; never turn one slow frame into an unbounded backlog.
        val budget = (remainder + elapsedNanos.coerceIn(0L, 33_333_334L) * 120L)
            .coerceAtMost(4_000_000_000L)
        // Round to the closest step, retaining the signed fraction (including nanosecond jitter).
        val steps = ((budget + 500_000_000L) / 1_000_000_000L).toInt()
        remainder = budget - steps * 1_000_000_000L
        return steps
    }

    companion object {
        const val STEP_SECONDS = 1f / 120f
    }
}
