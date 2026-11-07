package com.example.shiro

import java.util.ArrayDeque

/**
 * Small bounded queue between the FFT worker and UI clock. Neither method runs on the audio
 * thread. The head's time check and removal are atomic with overflow trimming, so the UI can
 * never accidentally display an unchecked future frame when the worker drops an older one.
 */
internal class SpectrumFrameQueue(private val capacity: Int = 96) {
    private data class TimedFrame(val timeUs: Long, val generation: Long, val frame: SpectrumFrame)
    private val pending = ArrayDeque<TimedFrame>()
    private var displayed: TimedFrame? = null

    init { require(capacity > 0) }

    @Synchronized
    fun offer(timeUs: Long, generation: Long, frame: SpectrumFrame) {
        pending.addLast(TimedFrame(timeUs, generation, frame))
        while (pending.size > capacity) pending.removeFirst()
    }

    @Synchronized
    fun currentFrame(positionUs: Long, generation: Long): SpectrumFrame {
        while (pending.isNotEmpty()) {
            val next = pending.first
            if (next.generation != generation) { pending.removeFirst(); continue }
            if (next.timeUs > positionUs) break
            displayed = pending.removeFirst()
        }
        val current = displayed
        return if (current != null && current.generation == generation &&
            positionUs >= current.timeUs && positionUs - current.timeUs <= 250_000L) {
            current.frame
        } else SpectrumFrame.silence()
    }

    @Synchronized
    fun clear() {
        pending.clear()
        displayed = null
    }
}
