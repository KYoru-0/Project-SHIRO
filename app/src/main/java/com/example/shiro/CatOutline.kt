package com.example.shiro

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class CatPoint(val x: Float, val y: Float)
internal data class CatOutline(val points: List<CatPoint>) {
    val left = points.minOf { it.x }
    val right = points.maxOf { it.x }
    val bottom = points.minOf { it.y }
    val top = points.maxOf { it.y }
    val support get() = -bottom
}

/** Excludes transparent padding and the faint painted glow. Coordinates are normalized. */
internal fun opaqueCatHull(width: Int, height: Int, alpha: (Int, Int) -> Int): List<CatPoint> {
    val edges = mutableListOf<CatPoint>()
    for (y in 0 until height) {
        val first = (0 until width).firstOrNull { alpha(it, y) >= 200 } ?: continue
        val last = (width - 1 downTo first).first { alpha(it, y) >= 200 }
        edges.add(CatPoint(first.toFloat() / width, y.toFloat() / height))
        edges.add(CatPoint((last + 1f) / width, (y + 1f) / height))
    }
    return convexCatHull(edges)
}

private fun cross(a: CatPoint, b: CatPoint, c: CatPoint) =
    (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

internal fun convexCatHull(points: List<CatPoint>): List<CatPoint> {
    val sorted = points.distinct().sortedWith(compareBy<CatPoint> { it.x }.thenBy { it.y })
    require(sorted.size >= 3)
    fun half(input: List<CatPoint>): MutableList<CatPoint> {
        val result = mutableListOf<CatPoint>()
        input.forEach { point ->
            while (result.size >= 2 && cross(result[result.lastIndex - 1], result.last(), point) <= 0f) result.removeAt(result.lastIndex)
            result.add(point)
        }
        result.removeAt(result.lastIndex)
        return result
    }
    return half(sorted) + half(sorted.asReversed())
}

/** Samples the same triangle mesh as the renderer, then rotates into physics coordinates (Y up). */
internal fun deformCatOutline(hull: List<CatPoint>, width: Float, height: Float,
    jelly: Float, bend: Float, rotation: Float, contact: WaveContact? = null): CatOutline {
    val mesh = FloatArray((JELLY_COLUMNS + 1) * (JELLY_ROWS + 1) * 2)
    updateJellyMesh(mesh, width, height, jelly, bend, contact)
    val radians = Math.toRadians(rotation.toDouble())
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    val transformed = hull.map { point ->
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
        CatPoint(c * x - s * y, -(s * x + c * y))
    }
    return CatOutline(convexCatHull(transformed))
}

internal fun CatOutline.contains(x: Float, y: Float): Boolean {
    val point = CatPoint(x, y)
    return points.indices.all { cross(points[it], points[(it + 1) % points.size], point) >= -0.001f }
}

/** Lowest safe center height when the silhouette meets the sampled wave. */
internal data class CatWaveSample(val centerHeight: Float, val point: CatPoint)

internal fun catWaveCenterHeight(shape: CatOutline, bands: FloatArray, x: Float, width: Float, amplitude: Float): Float =
    sampleCatWave(shape, bands, x, width, amplitude).centerHeight

internal fun sampleCatWave(shape: CatOutline, bands: FloatArray, x: Float, width: Float, amplitude: Float): CatWaveSample {
    var floor = Float.NEGATIVE_INFINITY
    var weightSum = 0f
    var contactX = 0f
    var contactY = 0f
    shape.points.forEachIndexed { index, p ->
        val q = shape.points[(index + 1) % shape.points.size]
        val samples = kotlin.math.ceil(kotlin.math.abs(q.x - p.x) / 3f).toInt().coerceAtLeast(1)
        for (sample in 0..samples) {
            val t = sample.toFloat() / samples
            val px = p.x + (q.x - p.x) * t
            val py = p.y + (q.y - p.y) * t
            val required = waveHeightAt(bands, (x + px) / width) * amplitude - py
            if (required > floor) {
                val scale = kotlin.math.exp((floor - required) / 2f)
                weightSum *= scale; contactX *= scale; contactY *= scale
                floor = required
            }
            val weight = kotlin.math.exp((required - floor) / 2f)
            weightSum += weight
            contactX += px * weight
            contactY += py * weight
        }
    }
    return CatWaveSample(floor, CatPoint(contactX / weightSum, contactY / weightSum))
}

/** Separating-axis test. Returns the smallest overlap directed from A toward B. */
internal fun catOverlap(a: CatOutline, ax: Float, ay: Float, b: CatOutline, bx: Float, by: Float): CatPoint? {
    var least = Float.POSITIVE_INFINITY
    var normal = CatPoint(0f, 0f)
    for (outline in listOf(a, b)) for (index in outline.points.indices) {
        val p = outline.points[index]
        val q = outline.points[(index + 1) % outline.points.size]
        val length = sqrt((q.x - p.x) * (q.x - p.x) + (q.y - p.y) * (q.y - p.y))
        if (length < 0.0001f) continue
        var nx = -(q.y - p.y) / length
        var ny = (q.x - p.x) / length
        var aMin = Float.POSITIVE_INFINITY; var aMax = Float.NEGATIVE_INFINITY
        var bMin = Float.POSITIVE_INFINITY; var bMax = Float.NEGATIVE_INFINITY
        for (point in a.points) {
            val projection = (point.x + ax) * nx + (point.y + ay) * ny
            aMin = minOf(aMin, projection); aMax = maxOf(aMax, projection)
        }
        for (point in b.points) {
            val projection = (point.x + bx) * nx + (point.y + by) * ny
            bMin = minOf(bMin, projection); bMax = maxOf(bMax, projection)
        }
        if (aMax <= bMin || bMax <= aMin) return null
        val overlap = minOf(aMax - bMin, bMax - aMin)
        if (overlap < least) {
            least = overlap
            if ((bx - ax) * nx + (by - ay) * ny < 0f) { nx = -nx; ny = -ny }
            normal = CatPoint(nx * overlap, ny * overlap)
        }
    }
    return normal
}
