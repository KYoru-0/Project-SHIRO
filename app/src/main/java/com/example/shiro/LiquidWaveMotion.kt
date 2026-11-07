package com.example.shiro

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

internal fun smoothVisualBand(bands: FloatArray, fraction: Float): Float {
    if (bands.isEmpty()) return 0f
    val position = fraction.coerceIn(0f, 1f) * bands.lastIndex
    val index = position.toInt()
    val mix = (1f - cos((position - index) * PI.toFloat())) * 0.5f
    fun level(i: Int) = bands[i].let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
    return level(index) + (level(minOf(index + 1, bands.lastIndex)) - level(index)) * mix
}

/** Local spring dents follow the front ribbon, which is also the cats' collision surface. */
internal class LiquidWaveMotion(private val width: Float) {
    private val dents = FloatArray(24)
    private val velocity = FloatArray(24)
    private val load = FloatArray(24)

    fun contact(x: Float, impact: Float, weight: Float) {
        val index = (x / width * dents.lastIndex).roundToInt().coerceIn(0, dents.lastIndex)
        load[index] = (load[index] + weight * 0.12f).coerceIn(0f, 0.5f)
        if (impact >= 35f) {
            dents[index] = maxOf(dents[index], (impact / 320f).coerceIn(0f, 1f))
            velocity[index] = 0f
        }
    }

    fun advance(frame: VisualizerFrame, seconds: Float, amplitude: Float): GlassScene {
        val dt = seconds.coerceIn(0f, 1f / 30f)
        for (i in dents.indices) {
            velocity[i] += ((load[i] - dents[i]) * 85f - velocity[i] * 12f) * dt
            dents[i] = (dents[i] + velocity[i] * dt).coerceIn(0f, 1f)
            load[i] = 0f
        }
        val top = FloatArray(97) { i ->
            val u = i / 96f
            val level = smoothVisualBand(frame.bands, u)
            val flow = sin(u * 2.0 * PI + frame.phase).toFloat()
            (amplitude * (level * 0.7f + frame.energy * (0.12f + flow * 0.06f)) -
                smoothVisualBand(dents, u) * 12f * (frame.energy * 8f).coerceAtMost(1f)).coerceIn(0f, amplitude)
        }
        val bottom = FloatArray(top.size) { (top[it] - 10f).coerceAtLeast(0f) }
        return GlassScene(listOf(GlassPiece(-1, GlassPlatformKind.RIBBON, 0f, width, top, bottom, frame.energy, 0f)),
            (frame.phase % (2.0 * PI)).toFloat(), frame.energy)
    }
}
