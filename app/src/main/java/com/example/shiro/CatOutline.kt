package com.example.shiro

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class CatPoint(val x: Float, val y: Float)

internal data class CatOutline(val points: List<CatPoint>) {
    val left: Float
    val right: Float
    val bottom: Float
    val top: Float
    val support: Float

    init {
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (i in points.indices) {
            val p = points[i]
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        left = if (minX.isFinite()) minX else 0f
        right = if (maxX.isFinite()) maxX else 0f
        bottom = if (minY.isFinite()) minY else 0f
        top = if (maxY.isFinite()) maxY else 0f
        support = -bottom
    }
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
    val rawHull = convexCatHull(edges)
    return simplifyHull(rawHull, targetCount = 14)
}

internal fun simplifyHull(points: List<CatPoint>, targetCount: Int = 14): List<CatPoint> {
    if (points.size <= targetCount) return points
    val step = points.size.toFloat() / targetCount
    val result = ArrayList<CatPoint>(targetCount)
    for (i in 0 until targetCount) {
        val index = (i * step).toInt().coerceIn(0, points.size - 1)
        result.add(points[index])
    }
    return convexCatHull(result)
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

private val scratchMesh = ThreadLocal.withInitial {
    FloatArray((JELLY_COLUMNS + 1) * (JELLY_ROWS + 1) * 2)
}

/** Samples the same triangle mesh as the renderer, then rotates into physics coordinates (Y up). */
internal fun deformCatOutline(
    hull: List<CatPoint>,
    width: Float,
    height: Float,
    jelly: Float,
    bend: Float,
    rotation: Float,
    contact: WaveContact? = null
): CatOutline {
    val mesh = scratchMesh.get()!!
    updateJellyMesh(mesh, width, height, jelly, bend, contact)
    val radians = Math.toRadians(rotation.toDouble())
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    val halfWidth = width * 0.5f
    val heightOffset = height * 0.55f

    val transformed = ArrayList<CatPoint>(hull.size)
    for (i in hull.indices) {
        val point = hull[i]
        val gx = point.x * JELLY_COLUMNS
        val gy = point.y * JELLY_ROWS
        val column = gx.toInt().coerceIn(0, JELLY_COLUMNS - 1)
        val row = gy.toInt().coerceIn(0, JELLY_ROWS - 1)
        val u = gx - column
        val v = gy - row
        val a = (row * (JELLY_COLUMNS + 1) + column) * 2
        val b = a + 2
        val d = a + (JELLY_COLUMNS + 1) * 2

        val interpX: Float
        val interpY: Float
        if (v >= u) {
            val wA = 1f - v
            val wD = v - u
            val wD2 = u
            interpX = mesh[a] * wA + mesh[d] * wD + mesh[d + 2] * wD2
            interpY = mesh[a + 1] * wA + mesh[d + 1] * wD + mesh[d + 3] * wD2
        } else {
            val wA = 1f - u
            val wB = u - v
            val wD2 = v
            interpX = mesh[a] * wA + mesh[b] * wB + mesh[d + 2] * wD2
            interpY = mesh[a + 1] * wA + mesh[b + 1] * wB + mesh[d + 3] * wD2
        }

        val x = interpX - halfWidth
        val y = interpY - heightOffset
        transformed.add(CatPoint(c * x - s * y, -(s * x + c * y)))
    }
    return CatOutline(transformed)
}

internal fun CatOutline.contains(x: Float, y: Float): Boolean {
    if (x < left || x > right || y < bottom || y > top) return false
    val count = points.size
    val point = CatPoint(x, y)
    for (i in 0 until count) {
        if (cross(points[i], points[(i + 1) % count], point) < -0.001f) return false
    }
    return true
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
    val points = shape.points
    val count = points.size
    for (index in 0 until count) {
        val p = points[index]
        val q = points[(index + 1) % count]
        if (p.y > shape.top * 0.5f && q.y > shape.top * 0.5f) continue

        val dx = q.x - p.x
        val dy = q.y - p.y
        val samples = kotlin.math.ceil(kotlin.math.abs(dx) / 4f).toInt().coerceAtLeast(1)
        for (sample in 0..samples) {
            val t = sample.toFloat() / samples
            val px = p.x + dx * t
            val py = p.y + dy * t
            val required = waveHeightAt(bands, (x + px) / width) * amplitude - py
            if (required > floor) {
                val scale = kotlin.math.exp((floor - required) / 2f)
                weightSum *= scale
                contactX *= scale
                contactY *= scale
                floor = required
            }
            val weight = kotlin.math.exp((required - floor) / 2f)
            weightSum += weight
            contactX += px * weight
            contactY += py * weight
        }
    }
    if (weightSum <= 0f) {
        return CatWaveSample(shape.bottom, CatPoint(0f, shape.bottom))
    }
    return CatWaveSample(floor, CatPoint(contactX / weightSum, contactY / weightSum))
}

/** Separating-axis test with early AABB rejection. Returns the smallest overlap directed from A toward B. */
internal fun catOverlap(a: CatOutline, ax: Float, ay: Float, b: CatOutline, bx: Float, by: Float): CatPoint? {
    if (ax + a.right < bx + b.left || bx + b.right < ax + a.left ||
        ay + a.top < by + b.bottom || by + b.top < ay + a.bottom) {
        return null
    }

    var least = Float.POSITIVE_INFINITY
    var normalX = 0f
    var normalY = 0f
    val outlines = arrayOf(a, b)
    for (o in 0..1) {
        val outline = outlines[o]
        val points = outline.points
        val count = points.size
        for (index in 0 until count) {
            val p = points[index]
            val q = points[(index + 1) % count]
            val edgeX = q.x - p.x
            val edgeY = q.y - p.y
            val length = sqrt(edgeX * edgeX + edgeY * edgeY)
            if (length < 0.0001f) continue
            var nx = -edgeY / length
            var ny = edgeX / length

            var aMin = Float.POSITIVE_INFINITY
            var aMax = Float.NEGATIVE_INFINITY
            for (i in a.points.indices) {
                val pt = a.points[i]
                val proj = (pt.x + ax) * nx + (pt.y + ay) * ny
                if (proj < aMin) aMin = proj
                if (proj > aMax) aMax = proj
            }

            var bMin = Float.POSITIVE_INFINITY
            var bMax = Float.NEGATIVE_INFINITY
            for (i in b.points.indices) {
                val pt = b.points[i]
                val proj = (pt.x + bx) * nx + (pt.y + by) * ny
                if (proj < bMin) bMin = proj
                if (proj > bMax) bMax = proj
            }

            if (aMax <= bMin || bMax <= aMin) return null
            val overlap = minOf(aMax - bMin, bMax - aMin)
            if (overlap < least) {
                least = overlap
                if ((bx - ax) * nx + (by - ay) * ny < 0f) {
                    nx = -nx
                    ny = -ny
                }
                normalX = nx * overlap
                normalY = ny * overlap
            }
        }
    }
    return CatPoint(normalX, normalY)
}
