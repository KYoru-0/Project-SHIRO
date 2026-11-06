package com.example.shiro

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class TrackHitData(
    val kicksMs: LongArray,
    val chunkTimesMs: LongArray,
    val chunkBalances: FloatArray,
    val estimatedBpm: Float
)

@OptIn(UnstableApi::class)
object TrackAudioAnalyzer {

    fun hasCache(context: Context, fileName: String): Boolean {
        val cacheFile = File(context.cacheDir, "hits_${fileName.hashCode()}.json")
        return cacheFile.exists() && cacheFile.length() > 0
    }

    fun loadCachedHitData(context: Context, fileName: String): TrackHitData? {
        return try {
            val cacheFile = File(context.cacheDir, "hits_${fileName.hashCode()}.json")
            if (!cacheFile.exists() || cacheFile.length() <= 0) return null
            val obj = JSONObject(cacheFile.readText())
            val kicksArr = obj.getJSONArray("kicks")
            val timesArr = obj.optJSONArray("chunkTimes") ?: JSONArray()
            val balancesArr = obj.optJSONArray("chunkBalances") ?: JSONArray()
            val bpm = obj.optDouble("bpm", 120.0).toFloat()

            val kicksList = LongArray(kicksArr.length()) { kicksArr.getLong(it) }
            val timesList = LongArray(timesArr.length()) { timesArr.getLong(it) }
            val balancesList = FloatArray(balancesArr.length()) { balancesArr.getDouble(it).toFloat() }

            TrackHitData(kicksList, timesList, balancesList, bpm)
        } catch (_: Exception) {
            null
        }
    }

    fun clearAllCache(context: Context) {
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("hits_") && file.name.endsWith(".json")) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun analyzeTrack(context: Context, uri: Uri, fileName: String): TrackHitData = withContext(Dispatchers.Default) {
        val cached = loadCachedHitData(context, fileName)
        if (cached != null) return@withContext cached

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val rawKicks = mutableListOf<Long>()
        val rawChunkTimes = mutableListOf<Long>()
        val rawChunkBalances = mutableListOf<Float>()

        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                return@withContext generateFallbackHitData(120f)
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false

            val pcmAccumulator = ArrayList<Short>(4096)
            val chunkSizeSamples = (sampleRate * 0.02f).toInt() * channelCount

            var currentSampleTimeUs = 0L

            val alphaLow = 0.035f  // ~250 Hz Low-Pass for kicks
            val alphaHigh = 0.35f  // ~2500 Hz High/Mid filter

            var lpLow = 0f
            var lpHigh = 0f

            var smoothELow = 0f
            var smoothEHigh = 0f

            var prevELow = 0f
            var movingAvgKick = 0f

            while (!isEOS) {
                val inIndex = codec.dequeueInputBuffer(5000L)
                if (inIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            val sampleTime = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 5000L)
                if (outIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val pcmShorts = ShortArray(shortBuffer.remaining())
                        shortBuffer.get(pcmShorts)

                        currentSampleTimeUs = bufferInfo.presentationTimeUs

                        for (s in pcmShorts) {
                            pcmAccumulator.add(s)
                        }

                        while (pcmAccumulator.size >= chunkSizeSamples) {
                            val chunkTimeMs = (currentSampleTimeUs / 1000L)

                            var sumLow = 0f
                            var sumHigh = 0f
                            var sampleCount = 0

                            var k = 0
                            while (k < chunkSizeSamples - 1) {
                                val rawSample = pcmAccumulator[k] / 32768f

                                lpLow += alphaLow * (rawSample - lpLow)
                                lpHigh += alphaHigh * (rawSample - lpHigh)

                                val sLow = lpLow
                                val sHigh = rawSample - lpHigh

                                sumLow += sLow * sLow
                                sumHigh += sHigh * sHigh
                                sampleCount++

                                k += channelCount
                            }

                            pcmAccumulator.subList(0, chunkSizeSamples).clear()

                            if (sampleCount > 0) {
                                val rawELow = sqrt((sumLow / sampleCount).toDouble()).toFloat()
                                val rawEHigh = sqrt((sumHigh / sampleCount).toDouble()).toFloat()

                                smoothELow = 0.6f * smoothELow + 0.4f * rawELow
                                smoothEHigh = 0.6f * smoothEHigh + 0.4f * rawEHigh

                                // 1. Low Band Kick Detection
                                val dLow = (smoothELow - prevELow).coerceAtLeast(0f)
                                prevELow = smoothELow

                                movingAvgKick = 0.92f * movingAvgKick + 0.08f * dLow
                                val kickThreshold = (movingAvgKick * 1.8f).coerceAtLeast(0.003f)

                                if (dLow > kickThreshold) {
                                    rawKicks.add(chunkTimeMs)
                                }

                                // 2. Frequency Balance (-1.0 for low/left, +1.0 for high/right)
                                val totalChunkEnergy = smoothELow + smoothEHigh + 0.0001f
                                val balance = ((smoothEHigh - smoothELow) / totalChunkEnergy).coerceIn(-1f, 1f)

                                rawChunkTimes.add(chunkTimeMs)
                                rawChunkBalances.add(balance)
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                }

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }

            val estimatedBpm = calculateBpmFromRawHits(rawKicks)

            val beatIntervalMs = (60000f / estimatedBpm).toLong().coerceIn(300L, 1000L)
            if (rawKicks.size < 10) {
                rawKicks.clear()
                var t = 0L
                while (t < 600000L) {
                    rawKicks.add(t)
                    t += beatIntervalMs
                }
            }

            val hitData = TrackHitData(
                kicksMs = rawKicks.toLongArray(),
                chunkTimesMs = rawChunkTimes.toLongArray(),
                chunkBalances = rawChunkBalances.toFloatArray(),
                estimatedBpm = estimatedBpm
            )

            saveHitDataToCache(context, fileName, hitData)
            hitData
        } catch (_: Exception) {
            generateFallbackHitData(120f)
        } finally {
            try { codec?.stop(); codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun generateFallbackHitData(bpm: Float): TrackHitData {
        val beatIntervalMs = (60000f / bpm).toLong()
        val kicks = mutableListOf<Long>()
        val chunkTimes = mutableListOf<Long>()
        val chunkBalances = mutableListOf<Float>()
        var t = 0L
        while (t < 600000L) {
            kicks.add(t)
            chunkTimes.add(t)
            chunkBalances.add(0f)
            t += 20L
        }
        return TrackHitData(kicks.toLongArray(), chunkTimes.toLongArray(), chunkBalances.toFloatArray(), bpm)
    }

    private fun calculateBpmFromRawHits(rawHits: List<Long>): Float {
        if (rawHits.size < 5) return 120f
        val intervals = mutableListOf<Long>()
        for (i in 1 until rawHits.size) {
            val delta = rawHits[i] - rawHits[i - 1]
            if (delta in 200L..1200L) {
                intervals.add(delta)
            }
        }
        if (intervals.size < 5) return 120f
        val sorted = intervals.sorted()
        val medianDelta = sorted[sorted.size / 2].toFloat()
        var rawBpm = 60000f / medianDelta
        while (rawBpm < 80f) rawBpm *= 2f
        while (rawBpm > 170f) rawBpm /= 2f
        return rawBpm
    }

    private fun saveHitDataToCache(context: Context, fileName: String, data: TrackHitData) {
        try {
            val cacheFile = File(context.cacheDir, "hits_${fileName.hashCode()}.json")
            val obj = JSONObject()
            obj.put("kicks", JSONArray(data.kicksMs.toList()))
            obj.put("chunkTimes", JSONArray(data.chunkTimesMs.toList()))
            obj.put("chunkBalances", JSONArray(data.chunkBalances.toList()))
            obj.put("bpm", data.estimatedBpm.toDouble())
            cacheFile.writeText(obj.toString())
        } catch (_: Exception) {}
    }
}

object TrackAnalysisWorker {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun processPlaylistInBackground(context: Context, tracks: List<Track>) {
        val appContext = context.applicationContext
        val trackList = tracks.toList()
        scope.launch {
            for (track in trackList) {
                if (!TrackAudioAnalyzer.hasCache(appContext, track.fileName)) {
                    TrackAudioAnalyzer.analyzeTrack(appContext, track.uri, track.fileName)
                }
            }
        }
    }
}
