package com.example.shiro

import kotlin.math.cos
import kotlin.math.sin

/** A snapshot of spectrum contact, expressed in the sprite's unrotated coordinates. */
internal data class WaveContact(
    val u: Float, val v: Float,
    val normalX: Float, val normalY: Float,
    val spread: Float
)

internal fun waveContactAt(
    point: CatPoint, rotation: Float, bodyWidth: Float, bodyHeight: Float,
    barCount: Int, sceneWidth: Float, normalX: Float = 0f, normalY: Float = 1f,
    contactWidth: Float = sceneWidth / barCount.coerceAtLeast(1) * (1f - 2f * SPECTRUM_BAR_INSET)
): WaveContact {
    val angle = Math.toRadians(rotation.toDouble())
    val c = cos(angle).toFloat()
    val s = sin(angle).toFloat()
    return WaveContact(
        u = ((c * point.x - s * point.y) / bodyWidth + 0.5f).coerceIn(0f, 1f),
        v = ((-s * point.x - c * point.y) / bodyHeight + 0.55f).coerceIn(0f, 1f),
        normalX = c * normalX - s * normalY,
        normalY = -s * normalX - c * normalY,
        spread = (contactWidth / (2f * bodyWidth)).coerceIn(0.11f, 0.34f)
    )
}
