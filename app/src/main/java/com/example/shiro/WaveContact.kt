package com.example.shiro

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A snapshot of the wave at impact, expressed in the sprite's unrotated coordinates. */
internal data class WaveContact(
    val u: Float, val v: Float,
    val normalX: Float, val normalY: Float,
    val spread: Float
)

internal fun waveContactAt(
    point: CatPoint, catX: Float, rotation: Float, bodyWidth: Float, bodyHeight: Float,
    bands: FloatArray, sceneWidth: Float, amplitude: Float
): WaveContact {
    val contactX = catX + point.x
    val distance = bodyWidth * 0.18f
    val left = waveHeightAt(bands, (contactX - distance) / sceneWidth) * amplitude
    val middle = waveHeightAt(bands, contactX / sceneWidth) * amplitude
    val right = waveHeightAt(bands, (contactX + distance) / sceneWidth) * amplitude
    val slope = ((right - left) / (distance * 2f)).coerceIn(-3f, 3f)
    val normalLength = sqrt(1f + slope * slope)
    val nx = -slope / normalLength
    val ny = 1f / normalLength
    val angle = Math.toRadians(rotation.toDouble())
    val c = cos(angle).toFloat()
    val s = sin(angle).toFloat()
    val curvature = abs(middle - (left + right) / 2f) / bodyHeight
    return WaveContact(
        u = ((c * point.x - s * point.y) / bodyWidth + 0.5f).coerceIn(0f, 1f),
        v = ((-s * point.x - c * point.y) / bodyHeight + 0.55f).coerceIn(0f, 1f),
        normalX = c * nx - s * ny,
        normalY = -s * nx - c * ny,
        // A crest pokes a small area; a flat section presses across a broad area.
        spread = (0.34f / (1f + curvature * 10f)).coerceIn(0.11f, 0.34f)
    )
}
