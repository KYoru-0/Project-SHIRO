package com.example.shiro

import kotlin.math.exp
import kotlin.math.hypot

internal const val JELLY_COLUMNS = 20
internal const val JELLY_ROWS = 16
internal const val JELLY_CORE_U = 0.52f
internal const val JELLY_CORE_V = 0.55f
internal const val JELLY_CORE_RADIUS = 0.19f // Core radius in sprite-height units.

/** Rest geometry depends only on sprite dimensions, not on its current dent or bend. */
internal class JellyRestMesh(val width: Float, val height: Float) {
    private val count = (JELLY_COLUMNS + 1) * (JELLY_ROWS + 1)
    val dx = FloatArray(count)
    val dy = FloatArray(count)
    val radius = FloatArray(count)
    val nx = FloatArray(count)
    val ny = FloatArray(count)
    init {
        for (row in 0..JELLY_ROWS) for (column in 0..JELLY_COLUMNS) {
            val index = row * (JELLY_COLUMNS + 1) + column
            dx[index] = column.toFloat() / JELLY_COLUMNS * width - width * JELLY_CORE_U
            dy[index] = row.toFloat() / JELLY_ROWS * height - height * JELLY_CORE_V
            radius[index] = hypot(dx[index], dy[index])
            if (radius[index] > 0f) {
                nx[index] = dx[index] / radius[index]
                ny[index] = dy[index] / radius[index]
            }
        }
    }
}

/** Reuse both the mesh storage and its fixed geometry for physics and bitmap drawing. */
internal class JellyMeshBuffer {
    private val vertices = FloatArray((JELLY_COLUMNS + 1) * (JELLY_ROWS + 1) * 2)
    private var rest: JellyRestMesh? = null
    private var jelly = Float.NaN
    private var bend = Float.NaN
    private var contact: WaveContact? = null

    fun update(width: Float, height: Float, jelly: Float, bend: Float, contact: WaveContact?): FloatArray {
        val dimensionsChanged = rest?.width != width || rest?.height != height
        if (dimensionsChanged) rest = JellyRestMesh(width, height)
        if (dimensionsChanged || this.jelly != jelly || this.bend != bend || this.contact != contact) {
            updateJellyMesh(vertices, width, height, jelly, bend, contact, rest)
            this.jelly = jelly; this.bend = bend; this.contact = contact
        }
        return vertices
    }
}

/** A firm circular core with a compressible outer shell. Radial compression preserves the core
 * and keeps the mapping ordered while allowing a deep, localized dent in the visible border.
 */
internal fun updateJellyMesh(vertices: FloatArray, width: Float, height: Float, jelly: Float, bend: Float,
    contact: WaveContact? = null, rest: JellyRestMesh? = null) {
    val squash = jelly.coerceIn(-0.75f, 1.20f)
    val lean = bend.coerceIn(-1f, 1f)
    val centerX = width * JELLY_CORE_U
    val centerY = height * JELLY_CORE_V
    val core = height * JELLY_CORE_RADIUS
    val contactX = if (contact == null) 0f else contact.u * width - centerX
    val contactY = if (contact == null) 1f else contact.v * height - centerY
    val contactRadius = hypot(contactX, contactY).coerceAtLeast(0.001f)
    val directionX = contactX / contactRadius
    val directionY = contactY / contactRadius
    val spread = if (contact == null) 0.5f else
        (contact.spread * width / contactRadius).coerceIn(0.38f, 0.85f)
    val globalStrength = if (contact == null) 0.32f else 0.08f
    val press = (squash.coerceAtLeast(0f) * 0.85f).coerceAtMost(0.94f)
    val rebound = (-squash).coerceAtLeast(0f) * 0.22f
    val spreadSquared = spread * spread
    val restDx = rest?.dx
    val restDy = rest?.dy
    val restRadius = rest?.radius
    val restNx = rest?.nx
    val restNy = rest?.ny
    var index = 0
    for (row in 0..JELLY_ROWS) {
        val v = row.toFloat() / JELLY_ROWS
        for (column in 0..JELLY_COLUMNS) {
            val u = column.toFloat() / JELLY_COLUMNS
            val vertex = index / 2
            val dx = if (restDx != null) restDx[vertex] else u * width - centerX
            val dy = if (restDy != null) restDy[vertex] else v * height - centerY
            val radius = if (restRadius != null) restRadius[vertex] else hypot(dx, dy)
            var mappedRadius = radius
            if (radius > core) {
                val nx = if (restNx != null) restNx[vertex] else dx / radius
                val ny = if (restNy != null) restNy[vertex] else dy / radius
                var shellScale = 1f + squash * globalStrength * (nx * nx - ny * ny) + 0.10f * lean * nx * ny
                if (contact != null) {
                    val distanceSquared = ((nx - directionX) * (nx - directionX) +
                        (ny - directionY) * (ny - directionY)) / spreadSquared
                    val influence = exp(-0.5f * distanceSquared)
                    // The center of the dent approaches the core; its shoulders bulge outward.
                    shellScale *= 1f - press * influence + press * 0.5f * distanceSquared * influence +
                        rebound * influence
                }
                mappedRadius = core + (radius - core) * minOf(1.65f, maxOf(0.06f, shellScale))
            }
            val scale = if (radius == 0f) 1f else mappedRadius / radius
            vertices[index++] = centerX + dx * scale
            vertices[index++] = centerY + dy * scale
        }
    }
}
