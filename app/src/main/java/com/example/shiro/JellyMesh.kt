package com.example.shiro

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.exp

internal const val JELLY_COLUMNS = 12
internal const val JELLY_ROWS = 8

/** Non-affine deformation: belly bulge, upper-body lag and a damped stretch after impact. */
internal fun updateJellyMesh(vertices: FloatArray, width: Float, height: Float, jelly: Float, bend: Float,
    contact: WaveContact? = null) {
    val squash = jelly.coerceIn(-0.75f, 1.20f)
    val lean = bend.coerceIn(-1f, 1f)
    var index = 0
    for (row in 0..JELLY_ROWS) {
        val v = row.toFloat() / JELLY_ROWS
        val envelope = sin(PI * v).toFloat()
        for (column in 0..JELLY_COLUMNS) {
            val u = column.toFloat() / JELLY_COLUMNS
            val generalSquash = squash * if (contact == null) 1f else 0.3f
            var x = width * (u + (u - 0.5f) * 0.45f * generalSquash * envelope +
                0.085f * lean * envelope * sin(PI * u).toFloat())
            var y = height * (v + 0.36f * generalSquash * envelope * (0.72f - v) +
                0.045f * lean * sin(2 * PI * u).toFloat() * envelope)
            if (contact != null) {
                val dx = (u - contact.u) * width / height
                val dy = v - contact.v
                val tangent = -contact.normalY * dx + contact.normalX * dy
                val depth = contact.normalX * dx + contact.normalY * dy
                val widthAlongSurface = contact.spread * width / height
                val influence = exp(-0.5f * (tangent * tangent / (widthAlongSurface * widthAlongSurface) +
                    depth * depth / (0.38f * 0.38f)))
                val push = height * 0.21f * squash * influence
                x += contact.normalX * push
                y += contact.normalY * push
            }
            // Keep the face a little firmer while the ears and belly deform more freely.
            val faceX = (u - 0.52f) / 0.12f
            val faceY = (v - 0.52f) / 0.16f
            val softness = 1f - 0.3f * exp(-0.5f * (faceX * faceX + faceY * faceY))
            vertices[index++] = u * width + (x - u * width) * softness
            vertices[index++] = v * height + (y - v * height) * softness
        }
    }
}
