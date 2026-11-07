package com.example.shiro

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.abs

internal data class CatPoint(val x: Float, val y: Float)
internal data class CatOutline(val points: List<CatPoint>) {
    val left = points.minOf { it.x }
    val right = points.maxOf { it.x }
    val supportPoint = points.minBy { it.y }
    val bottom = supportPoint.y
    val top = points.maxOf { it.y }
    val support get() = -bottom
    private var cachedCollision: CatCollisionShape? = null
    // Only overlapping cats need this; reuse it when several neighbors touch the same outline.
    fun collisionShape(): CatCollisionShape = cachedCollision
        ?: CatCollisionShape(points).also { cachedCollision = it }
}

/** Trace the star-shaped artwork from its core, preserving the ears and other concavities.
 * Coordinates are normalized; the alpha threshold excludes transparent padding and glow.
 */
internal fun opaqueCatHull(width: Int, height: Int, alpha: (Int, Int) -> Int): List<CatPoint> {
    val centerX = width * JELLY_CORE_U
    val centerY = height * JELLY_CORE_V
    return List(96) { ray ->
        val angle = ray * 2.0 * PI / 96
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        var radius = hypot(width.toFloat(), height.toFloat())
        while (radius > 0f) {
            val x = centerX + dx * radius
            val y = centerY + dy * radius
            if (x >= 0f && x < width && y >= 0f && y < height && alpha(x.toInt(), y.toInt()) >= 200) break
            radius -= 0.5f
        }
        CatPoint((centerX + dx * radius) / width, (centerY + dy * radius) / height)
    }
}

/** Samples the same triangle mesh as the renderer, then rotates into physics coordinates (Y up). */
internal fun deformCatOutline(hull: List<CatPoint>, width: Float, height: Float,
    jelly: Float, bend: Float, rotation: Float, contact: WaveContact? = null,
    mesh: FloatArray = FloatArray((JELLY_COLUMNS + 1) * (JELLY_ROWS + 1) * 2)): CatOutline {
    updateJellyMesh(mesh, width, height, jelly, bend, contact)
    return outlineFromMesh(hull, width, height, rotation, mesh)
}

internal fun outlineFromMesh(hull: List<CatPoint>, width: Float, height: Float,
    rotation: Float, mesh: FloatArray): CatOutline {
    val radians = Math.toRadians(rotation.toDouble())
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    val transformed = ArrayList<CatPoint>(hull.size)
    for (index in hull.lastIndex downTo 0) {
        val point = hull[index]
        val gx = point.x * JELLY_COLUMNS
        val gy = point.y * JELLY_ROWS
        val column = gx.toInt().coerceAtMost(JELLY_COLUMNS - 1)
        val row = gy.toInt().coerceAtMost(JELLY_ROWS - 1)
        val u = gx - column
        val v = gy - row
        val a = (row * (JELLY_COLUMNS + 1) + column) * 2
        val b = a + 2
        val d = a + (JELLY_COLUMNS + 1) * 2
        // Android's drawBitmapMesh splits each quad along its top-left/bottom-right diagonal.
        fun interpolate(axis: Int): Float = if (v >= u) {
            mesh[a + axis] * (1f - v) + mesh[d + axis] * (v - u) + mesh[d + 2 + axis] * u
        } else {
            mesh[a + axis] * (1f - u) + mesh[b + axis] * (u - v) + mesh[d + 2 + axis] * v
        }
        val x = interpolate(0) - width * 0.5f
        val y = interpolate(1) - height * 0.55f
        transformed.add(CatPoint(c * x - s * y, -(s * x + c * y)))
    }
    // Rebuilding a convex hull here would erase the very dent the renderer just made.
    return CatOutline(transformed)
}

internal fun CatOutline.contains(x: Float, y: Float): Boolean {
    if (x < left || x > right || y < bottom || y > top) return false
    var inside = false
    for (i in points.indices) {
        val a = points[i]
        val b = points[if (i == points.lastIndex) 0 else i + 1]
        if (abs((b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x)) < 0.0001f &&
            ((x >= a.x && x <= b.x) || (x >= b.x && x <= a.x)) &&
            ((y >= a.y && y <= b.y) || (y >= b.y && y <= a.y))) return true
        if ((a.y > y) != (b.y > y) && x < a.x + (y - a.y) * (b.x - a.x) / (b.y - a.y)) inside = !inside
    }
    return inside
}

/** Lowest safe center height when the silhouette meets the sampled wave. */
internal data class CatWaveSample(val centerHeight: Float, val point: CatPoint, val surfaceHeight: Float,
    val hit: CatPoint, val surfaceId: Int = -2, val normalX: Float = 0f, val normalY: Float = 1f,
    val contactWidth: Float = 0f)

internal fun catWaveCenterHeight(shape: CatOutline, bands: FloatArray, x: Float, width: Float, amplitude: Float): Float =
    sampleCatWave(shape, bands, x, width, amplitude).centerHeight

internal fun sampleCatWave(shape: CatOutline, bands: FloatArray, x: Float, width: Float, amplitude: Float,
    sampler: CatWaveSampler = CatWaveSampler()): CatWaveSample = sampler.sample(shape, bands, x, width, amplitude)
