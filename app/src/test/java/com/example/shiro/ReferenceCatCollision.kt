package com.example.shiro

import kotlin.math.abs
import kotlin.math.sqrt

private fun cross(a: CatPoint, b: CatPoint, c: CatPoint) =
    (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

internal fun CatOutline.referenceContains(x: Float, y: Float): Boolean {
    var inside = false
    val point = CatPoint(x, y)
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        if (abs(cross(a, b, point)) < 0.0001f && x in minOf(a.x, b.x)..maxOf(a.x, b.x) &&
            y in minOf(a.y, b.y)..maxOf(a.y, b.y)) return true
        if ((a.y > y) != (b.y > y) && x < a.x + (y - a.y) * (b.x - a.x) / (b.y - a.y)) inside = !inside
    }
    return inside
}

/** Conservative separation, gated by actual concave contours so empty dents cannot collide. */
internal fun referenceCatOverlap(a: CatOutline, ax: Float, ay: Float, b: CatOutline, bx: Float, by: Float): CatPoint? {
    if (ax + a.right <= bx + b.left || bx + b.right <= ax + a.left ||
        ay + a.top <= by + b.bottom || by + b.top <= ay + a.bottom) return null
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
    if (a.points.any { b.referenceContains(it.x + ax - bx, it.y + ay - by) } ||
        b.points.any { a.referenceContains(it.x + bx - ax, it.y + by - ay) }) return normal
    // Edge crossings also count, even if neither polygon has a vertex inside the other.
    for (i in a.points.indices) for (j in b.points.indices) {
        val p = a.points[i]
        val q = a.points[(i + 1) % a.points.size]
        val r = b.points[j].let { CatPoint(it.x + bx - ax, it.y + by - ay) }
        val s = b.points[(j + 1) % b.points.size].let { CatPoint(it.x + bx - ax, it.y + by - ay) }
        if (cross(p, q, r) * cross(p, q, s) < 0f && cross(r, s, p) * cross(r, s, q) < 0f) return normal
    }
    return null
}

