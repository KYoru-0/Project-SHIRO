package com.example.shiro

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Reused, bounded point storage. Coordinates stay inside a normalized stage region. */
internal class MusicVisualGeometry {
    val points = FloatArray(DOT_COUNT * 2)
    private val angles = DoubleArray(LINE_POINTS) { it * 2.0 * PI / LINE_POINTS }
    private val unitX = FloatArray(LINE_POINTS) { cos(angles[it]).toFloat() }
    private val unitY = FloatArray(LINE_POINTS) { sin(angles[it]).toFloat() }

    fun halo(frame: VisualizerFrame, inner: Boolean): Int {
        for (i in angles.indices) {
            val band = smoothVisualBand(frame.bands, (1f - unitX[i]) * 0.5f)
            val radius = (0.76f + band * 0.18f + frame.pulse * 0.035f) * if (inner) 0.86f else 1f
            points[i * 2] = unitX[i] * radius
            points[i * 2 + 1] = unitY[i] * radius
        }
        return LINE_POINTS
    }

    fun orbit(frame: VisualizerFrame, layer: Int): Int {
        for (i in angles.indices) {
            val t = angles[i]
            points[i * 2] = (sin(t * (layer + 2) + frame.phase * 0.5) * (0.64 + frame.bass * 0.22) +
                sin(t * 5 + frame.phase) * frame.pulse * 0.035).toFloat()
            points[i * 2 + 1] = (sin(t * (layer + 3) + frame.phase * 0.35 + layer * 0.4) *
                (0.59 + frame.treble * 0.24)).toFloat()
        }
        return LINE_POINTS
    }

    fun resonance(frame: VisualizerFrame): Int {
        for (i in 0 until DOT_COUNT) {
            val ring = i / 64
            val t = i % 64 * 2.0 * PI / 64 + ring * 0.035
            val band = smoothVisualBand(frame.bands, (1.0 - cos(t)).toFloat() * 0.5f)
            val radius = 0.35 + ring * 0.16 + sin(t * 3 + frame.phase) * 0.10 +
                cos(t * 5 - frame.phase * 0.7) * (0.04 + frame.treble * 0.07) + band * 0.09
            val angle = t + frame.phase * 0.12
            points[i * 2] = (cos(angle) * radius).toFloat()
            points[i * 2 + 1] = (sin(angle) * radius).toFloat()
        }
        return DOT_COUNT
    }

    companion object {
        const val LINE_POINTS = 128
        const val DOT_COUNT = 192
    }
}
