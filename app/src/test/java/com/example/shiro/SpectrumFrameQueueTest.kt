package com.example.shiro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumFrameQueueTest {
    @Test fun missingAndExpiredFramesStayDistinctFromRealSilenceIncludingDuringFades() {
        val queue = SpectrumFrameQueue()
        assertFalse(queue.currentFrame(0L, 1).available)
        assertFalse(SpectrumFrame.unavailable().attenuated(0f).available)
        queue.offer(10L, 1, SpectrumFrame.silence())
        assertTrue(queue.currentFrame(10L, 1).available)
        assertTrue(queue.currentFrame(10L, 1).attenuated(0f).available)
        assertFalse(queue.currentFrame(250_011L, 1).available)
    }
    private fun frame(level: Float) = SpectrumFrame(FloatArray(SpectrumFrame.BAND_COUNT) { level }, level)

    @Test fun waitsForAudibleClockAndExpiresStaleFrames() {
        val queue = SpectrumFrameQueue()
        queue.offer(100_000L, 1, frame(0.7f))
        assertEquals(0f, queue.currentFrame(99_999L, 1).bassEnergy, 0f)
        assertEquals(0.7f, queue.currentFrame(100_000L, 1).bassEnergy, 0f)
        assertEquals(0f, queue.currentFrame(350_001L, 1).bassEnergy, 0f)
    }

    @Test fun overflowNeverDisplaysAnUnreachedFutureFrame() {
        val queue = SpectrumFrameQueue(capacity = 2)
        queue.offer(10L, 1, frame(0.1f))
        queue.offer(20L, 1, frame(0.2f))
        queue.offer(30L, 1, frame(0.3f))
        assertEquals(0f, queue.currentFrame(15L, 1).bassEnergy, 0f)
        assertEquals(0.2f, queue.currentFrame(25L, 1).bassEnergy, 0f)
        queue.offer(40L, 1, frame(0.4f))
        queue.offer(50L, 1, frame(0.5f))
        assertEquals(0.2f, queue.currentFrame(35L, 1).bassEnergy, 0f)
        assertEquals(0.4f, queue.currentFrame(40L, 1).bassEnergy, 0f)
    }

    @Test fun seekGenerationRejectsBothDisplayedAndQueuedOldAudio() {
        val queue = SpectrumFrameQueue()
        queue.offer(100L, 1, frame(0.5f))
        queue.offer(200L, 1, frame(0.8f))
        assertEquals(0.5f, queue.currentFrame(100L, 1).bassEnergy, 0f)
        assertEquals(0f, queue.currentFrame(200L, 2).bassEnergy, 0f)
        queue.offer(500L, 2, frame(0.3f))
        assertEquals(0.3f, queue.currentFrame(500L, 2).bassEnergy, 0f)
        queue.clear()
        assertEquals(0f, queue.currentFrame(500L, 2).bassEnergy, 0f)
    }
}
