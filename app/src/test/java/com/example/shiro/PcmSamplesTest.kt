package com.example.shiro

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmSamplesTest {
    @Test fun integerPcmReadsBothStereoChannelsWithoutConsumingBuffer() {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(0).putShort(16384).putShort(-32768).putShort(32767).flip()
        buffer.position(4)
        buffer.order(ByteOrder.BIG_ENDIAN)
        assertEquals(0.5f, PcmSamples.read(buffer, 2, C.ENCODING_PCM_16BIT), 0f)
        assertEquals(-1f, PcmSamples.read(buffer, 4, C.ENCODING_PCM_16BIT), 0f)
        assertEquals(4, buffer.position())
        assertEquals(8, buffer.limit())
        assertEquals(ByteOrder.BIG_ENDIAN, buffer.order())
    }

    @Test fun floatAndHighResolutionPcmDecodeWithoutChangingAudio() {
        val floatBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(-0.75f)
        assertEquals(-0.75f, PcmSamples.read(floatBuffer, 0, C.ENCODING_PCM_FLOAT), 0f)
        val pcm24 = ByteBuffer.wrap(byteArrayOf(0, 0, -128, 0, 0, 64))
        assertEquals(-1f, PcmSamples.read(pcm24, 0, C.ENCODING_PCM_24BIT), 0f)
        assertEquals(0.5f, PcmSamples.read(pcm24, 3, C.ENCODING_PCM_24BIT), 0f)
        val pcm32 = ByteBuffer.wrap(byteArrayOf(0, 0, 0, -128))
        assertEquals(-1f, PcmSamples.read(pcm32, 0, C.ENCODING_PCM_32BIT), 0f)
        assertEquals(0, PcmSamples.bytesPerSample(C.ENCODING_INVALID))
    }
}
