package com.example.shiro

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class GlassPlatformKind { ISLAND, CUSHION, KEY, RIBBON }

/** Heights are in dp above the baseline. Rendering and collisions use these same line segments. */
internal data class GlassPiece(
    val id: Int, val kind: GlassPlatformKind, val left: Float, val right: Float,
    val top: FloatArray, val bottom: FloatArray, val level: Float, val pressure: Float,
    val rise: Float = 0f
) {
    val step = (right - left) / (top.size - 1)
    fun heightAt(x: Float): Float {
        val position = ((x - left) / step).coerceIn(0f, top.lastIndex.toFloat())
        val index = position.toInt().coerceAtMost(top.lastIndex - 1)
        return top[index] + (top[index + 1] - top[index]) * (position - index)
    }
}

internal data class GlassScene(val pieces: List<GlassPiece>, val phase: Float, val energy: Float)

/** Six platforms, a ribbon and eight decorative motes; advanced by the existing scene clock. */
internal class GlassSceneMotion(private val width: Float) {
    private val pressure = FloatArray(7)
    private val speed = FloatArray(7)
    private val load = FloatArray(7)
    private val touch = FloatArray(7) { 0.5f }
    private var previous: GlassScene? = null
    private var phase = 0f

    fun contact(id: Int, x: Float, impact: Float, weight: Float) {
        val index = id + 1
        if (index !in pressure.indices) return
        val piece = previous?.pieces?.getOrNull(index) ?: return
        touch[index] = ((x - piece.left) / (piece.right - piece.left)).coerceIn(0f, 1f)
        load[index] = maxOf(load[index], weight.coerceIn(0f, 1f) * 0.18f)
        if (impact >= 35f) {
            pressure[index] = maxOf(pressure[index], (impact / 340f).coerceIn(0.15f, 1f))
            speed[index] = 0f
        }
    }

    fun advance(bands: FloatArray, dynamics: MusicDynamics, playing: Boolean, seconds: Float, amplitude: Float): GlassScene {
        val dt = seconds.coerceIn(0f, 1f / 30f)
        val energy = if (bands.isEmpty()) 0f else bands.average().toFloat().coerceIn(0f, 1f)
        if (playing) phase = (phase + dt * (0.7f + dynamics.tempoDrive * 1.3f)) % (2f * PI.toFloat())
        for (i in pressure.indices) {
            speed[i] += ((load[i] - pressure[i]) * 95f - speed[i] * 13f) * dt
            pressure[i] = (pressure[i] + speed[i] * dt).coerceIn(0f, 1f)
            load[i] = 0f
        }
        fun bandAt(fraction: Float): Float {
            if (bands.isEmpty()) return 0f
            val position = fraction.coerceIn(0f, 1f) * bands.lastIndex
            val index = position.toInt()
            val blend = (1f - cos((position - index) * PI.toFloat())) * 0.5f
            return bands[index] + (bands[minOf(index + 1, bands.lastIndex)] - bands[index]) * blend
        }
        val pieces = ArrayList<GlassPiece>(7)
        val ribbon = FloatArray(49) { i ->
            val u = i / 48f
            val level = bandAt(u)
            val dent = pressure[0] * 6f * exp(-((u - touch[0]) * width / 28f).let { it * it })
            (level * amplitude * 0.16f + energy * 5f * (1f + sin(u * 8f + phase)) - dent).coerceAtLeast(0f)
        }
        pieces += GlassPiece(-1, GlassPlatformKind.RIBBON, 0f, width, ribbon, FloatArray(49), energy, pressure[0])
        for (i in 0 until 6) {
            val kind = GlassPlatformKind.entries[i % 3]
            var sum = 0f
            var peak = 0f
            val from = i * bands.size / 6
            val until = (i + 1) * bands.size / 6
            for (b in from until until) { sum += bands[b]; peak = maxOf(peak, bands[b]) }
            val level = (sum / (until - from).coerceAtLeast(1) * 0.55f + peak * 0.45f).coerceIn(0f, 1f)
            val visibility = (level / 0.08f).coerceIn(0f, 1f)
            val left = (i + 0.1f) * width / 6f
            val right = (i + 0.9f) * width / 6f
            val push = pressure[i + 1]
            val lift = level * amplitude * (if (kind == GlassPlatformKind.CUSHION) 0.48f else 0.72f)
            val tilt = if (kind == GlassPlatformKind.ISLAND)
                (sin(phase * 1.8f + i) * (2f + dynamics.tempoDrive * 3f) + (touch[i + 1] - 0.5f) * push * 8f) * visibility
            else 0f
            val top = FloatArray(17)
            val bottom = FloatArray(17)
            for (p in top.indices) {
                val u = p / 16f
                val arch = sqrt((1f - (u * 2f - 1f).let { it * it }).coerceAtLeast(0f))
                val base = (lift - push * 7f * visibility).coerceAtLeast(0f)
                when (kind) {
                    GlassPlatformKind.ISLAND -> {
                        top[p] = base + (5f * arch + tilt * (u * 2f - 1f)) * visibility
                        bottom[p] = base + (-5f * arch + tilt * (u * 2f - 1f)) * visibility
                    }
                    GlassPlatformKind.CUSHION -> {
                        val localDent = exp(-((u - touch[i + 1]) / 0.2f).let { it * it })
                        top[p] = base + arch * (18f + level * 10f - push * 13f * localDent) * visibility
                        bottom[p] = base - arch * 3f * visibility
                    }
                    GlassPlatformKind.KEY -> {
                        val bevel = minOf(u, 1f - u, 0.08f) / 0.08f
                        top[p] = base + bevel * 2f * visibility
                        bottom[p] = base - bevel * 11f * visibility
                    }
                    GlassPlatformKind.RIBBON -> Unit
                }
                top[p] = top[p].coerceAtLeast(0f)
                bottom[p] = bottom[p].coerceIn(0f, top[p])
            }
            val old = previous?.pieces?.getOrNull(i + 1)?.top
            var rise = 0f
            if (old != null) for (p in top.indices) rise = maxOf(rise, top[p] - old[p])
            pieces += GlassPiece(i, kind, left, right, top, bottom, level, push, rise)
        }
        return GlassScene(pieces, phase, energy).also { previous = it }
    }
}
