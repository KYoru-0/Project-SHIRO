package com.example.shiro

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.min

/** Observes decoded PCM without changing its position, bytes, format, or playback timing. */
@OptIn(UnstableApi::class)
internal class SpectrumAudioSink(sink: AudioSink, private val spectrum: SpectrumEngine) : ForwardingAudioSink(sink) {
    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var lastBuffer: ByteBuffer? = null
    private var lastPresentationTimeUs = Long.MIN_VALUE

    // Offload skips decoded PCM. Keep the existing decoded playback path available to the scene.
    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport = AudioOffloadSupport.DEFAULT_UNSUPPORTED

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
        sampleRate = inputFormat.sampleRate
        channelCount = inputFormat.channelCount
        encoding = if (inputFormat.sampleMimeType == MimeTypes.AUDIO_RAW) inputFormat.pcmEncoding else C.ENCODING_INVALID
        lastBuffer = null
        // Encoded passthrough remains playable; unsupported PCM simply has no visualization.
        if (PcmSamples.bytesPerSample(encoding) == 0) spectrum.reset()
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        // DefaultAudioSink may retry a partly consumed buffer. Capture it only once.
        if (lastBuffer !== buffer || lastPresentationTimeUs != presentationTimeUs) {
            lastBuffer = buffer
            lastPresentationTimeUs = presentationTimeUs
            spectrum.offerPcm(buffer, presentationTimeUs, sampleRate, channelCount, encoding)
        }
        val handled = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        if (handled) lastBuffer = null
        return handled
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val positionUs = super.getCurrentPositionUs(sourceEnded)
        spectrum.playbackPositionUs.set(positionUs)
        return positionUs
    }

    override fun flush() {
        lastBuffer = null
        spectrum.reset()
        super.flush()
    }

    override fun reset() {
        lastBuffer = null
        spectrum.reset()
        super.reset()
    }
}

/** Absolute byte reads leave the decoder buffer untouched, including byte order. */
@OptIn(UnstableApi::class)
internal object PcmSamples {
    fun bytesPerSample(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_8BIT -> 1
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
        else -> 0
    }

    fun read(buffer: ByteBuffer, index: Int, encoding: Int): Float {
        fun byte(offset: Int) = buffer.get(index + offset).toInt() and 0xff
        return when (encoding) {
            C.ENCODING_PCM_8BIT -> (byte(0) - 128) / 128f
            C.ENCODING_PCM_16BIT -> ((byte(0) or (byte(1) shl 8)).toShort()) / 32768f
            C.ENCODING_PCM_16BIT_BIG_ENDIAN -> ((byte(1) or (byte(0) shl 8)).toShort()) / 32768f
            C.ENCODING_PCM_24BIT -> ((byte(0) shl 8 or (byte(1) shl 16) or (byte(2) shl 24)) shr 8) / 8388608f
            C.ENCODING_PCM_32BIT -> (byte(0) or (byte(1) shl 8) or (byte(2) shl 16) or (byte(3) shl 24)) / 2147483648f
            C.ENCODING_PCM_FLOAT -> Float.fromBits(byte(0) or (byte(1) shl 8) or (byte(2) shl 16) or (byte(3) shl 24))
            else -> 0f
        }
    }
}

/**
 * One producer (Media3 playback thread), one consumer (DSP worker). PCM copies use preallocated
 * slots and never wait: if the worker falls behind, visualization data is dropped, never audio.
 */
internal class SpectrumEngine : AutoCloseable {
    val playbackPositionUs = AtomicLong(Long.MIN_VALUE)
    private class PcmChunk {
        val samples = FloatArray(CHUNK_FRAMES * MAX_CHANNELS)
        var frames = 0
        var channels = 0
        var sampleRate = 0
        var timeUs = 0L
        var generation = 0L
    }
    private val chunks = Array(8) { PcmChunk() }
    @Volatile private var writeSequence = 0L
    @Volatile private var readSequence = 0L
    @Volatile private var closed = false
    private val available = Semaphore(0)
    private val ready = SpectrumFrameQueue()
    private val worker = Thread(::work, "Shiro spectrum").apply {
        isDaemon = true
        priority = Thread.MIN_PRIORITY
        start()
    }

    fun offerPcm(buffer: ByteBuffer, timeUs: Long, sampleRate: Int, channels: Int, encoding: Int) {
        if (closed || !PlaybackSpectrum.visible || sampleRate < 1000 || channels !in 1..MAX_CHANNELS) return
        val bytes = PcmSamples.bytesPerSample(encoding)
        if (bytes == 0) return
        val generation = PlaybackSpectrum.generation.get()
        val frameBytes = bytes * channels
        val totalFrames = buffer.remaining() / frameBytes
        var frameOffset = 0
        while (frameOffset < totalFrames) {
            if (writeSequence - readSequence >= chunks.size) return
            val chunk = chunks[(writeSequence % chunks.size).toInt()]
            chunk.frames = min(CHUNK_FRAMES, totalFrames - frameOffset)
            chunk.channels = channels
            chunk.sampleRate = sampleRate
            chunk.timeUs = timeUs + frameOffset * 1_000_000L / sampleRate
            chunk.generation = generation
            val byteStart = buffer.position() + frameOffset * frameBytes
            for (i in 0 until chunk.frames * channels) {
                chunk.samples[i] = PcmSamples.read(buffer, byteStart + i * bytes, encoding)
            }
            frameOffset += chunk.frames
            writeSequence++ // Volatile publication makes all slot writes visible to the worker.
            available.release()
        }
    }

    fun reset() {
        PlaybackSpectrum.clear()
        playbackPositionUs.set(Long.MIN_VALUE)
    }

    /** Uses the sink's audible clock, in the same timestamp domain as the captured PCM. */
    fun currentFrame(): SpectrumFrame = ready.currentFrame(
        playbackPositionUs.get(), PlaybackSpectrum.generation.get()
    )

    private fun work() {
        var analyzer: SpectrumAnalyzer? = null
        var sampleRate = 0
        var channels = 0
        var generation = -1L
        var expectedTimeUs = Long.MIN_VALUE
        while (!closed) {
            available.acquireUninterruptibly()
            if (closed) break
            while (readSequence < writeSequence) {
                val chunk = chunks[(readSequence % chunks.size).toInt()]
                if (PlaybackSpectrum.visible && chunk.generation == PlaybackSpectrum.generation.get()) {
                    if (sampleRate != chunk.sampleRate || channels != chunk.channels) {
                        sampleRate = chunk.sampleRate
                        channels = chunk.channels
                        analyzer = SpectrumAnalyzer(sampleRate, channels)
                        expectedTimeUs = Long.MIN_VALUE
                    }
                    if (generation != chunk.generation || expectedTimeUs == Long.MIN_VALUE ||
                        abs(chunk.timeUs - expectedTimeUs) > 10_000L) analyzer?.reset()
                    generation = chunk.generation
                    expectedTimeUs = chunk.timeUs + chunk.frames * 1_000_000L / sampleRate
                    for (i in 0 until chunk.frames) {
                        val frame = analyzer?.addFrame(chunk.samples, i * channels) ?: continue
                        // Label the window's center, not decode time; the UI waits for the sink
                        // clock to reach it. This compensates for ExoPlayer's queued audio.
                        val frameTimeUs = chunk.timeUs + (i - 1024L) * 1_000_000L / sampleRate
                        ready.offer(frameTimeUs, generation, frame)
                    }
                }
                readSequence++
            }
        }
        ready.clear()
    }

    override fun close() {
        closed = true
        available.release()
        PlaybackSpectrum.clear()
    }

    private companion object {
        const val CHUNK_FRAMES = 2048
        const val MAX_CHANNELS = 8
    }
}
