package com.example.shiro

import java.nio.ShortBuffer

/** Reuses one PCM window across decoder buffers, without boxing or removing list prefixes. */
internal class PcmWindowBuffer(windowSize: Int) {
    init { require(windowSize > 0) }
    private val samples = ShortArray(windowSize)
    private var filled = 0

    // The receiver consumes the window immediately; it must not retain the reused array.
    fun consume(input: ShortBuffer, onWindow: (ShortArray) -> Unit) {
        while (input.hasRemaining()) {
            val count = minOf(input.remaining(), samples.size - filled)
            input.get(samples, filled, count)
            filled += count
            if (filled == samples.size) {
                onWindow(samples)
                filled = 0
            }
        }
    }
}
