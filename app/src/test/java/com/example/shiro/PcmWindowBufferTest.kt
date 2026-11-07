package com.example.shiro

import java.nio.ShortBuffer
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PcmWindowBufferTest {
    @Test fun decoderChunkBoundariesPreserveEveryCompleteWindow() {
        val samples = ShortArray(25_003) { (it * 17).toShort() }
        for (windowSize in listOf(1, 441, 1764, 3840)) {
            val windows = PcmWindowBuffer(windowSize)
            val output = mutableListOf<ShortArray>()
            val random = Random(windowSize)
            var offset = 0
            var shared: ShortArray? = null
            while (offset < samples.size) {
                val count = minOf(random.nextInt(1, 4097), samples.size - offset)
                val input = ShortBuffer.wrap(samples, offset, count)
                windows.consume(input) { window ->
                    if (shared == null) shared = window else assertSame(shared, window)
                    output += window.copyOf()
                }
                assertFalse(input.hasRemaining())
                offset += count
            }
            assertEquals(samples.size / windowSize, output.size)
            output.forEachIndexed { index, window ->
                assertArrayEquals(samples.copyOfRange(index * windowSize, (index + 1) * windowSize), window)
            }
        }
    }

    @Test fun incompleteWindowWaitsForMorePcmAndEmptyBuffersDoNothing() {
        val windows = PcmWindowBuffer(4)
        val output = mutableListOf<ShortArray>()
        windows.consume(ShortBuffer.wrap(shortArrayOf(1, 2, 3))) { output += it.copyOf() }
        windows.consume(ShortBuffer.allocate(0)) { fail("An empty buffer cannot finish a window") }
        assertTrue(output.isEmpty())
        windows.consume(ShortBuffer.wrap(shortArrayOf(4, 5, 6, 7, 8))) { output += it.copyOf() }
        assertArrayEquals(shortArrayOf(1, 2, 3, 4), output[0])
        assertArrayEquals(shortArrayOf(5, 6, 7, 8), output[1])
    }
}
