package com.example.shiro

import kotlin.math.abs
import kotlin.math.sqrt

/** Cached convex envelope used only to find a separating push, never as a cat's hitbox. */
internal class CatCollisionShape(points: List<CatPoint>) {
    val vertices: FloatArray
    // Each axis stores its normal and this shape's projection interval.
    val axes: FloatArray

    init {
        val sorted = points.toTypedArray().apply {
            sortWith { a, b -> if (a.x == b.x) a.y.compareTo(b.y) else a.x.compareTo(b.x) }
        }
        val hull = ArrayList<CatPoint>(points.size * 2)
        fun turn(a: CatPoint, b: CatPoint, c: CatPoint) =
            (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        for (point in sorted) {
            while (hull.size >= 2 && turn(hull[hull.size - 2], hull.last(), point) <= 0f) {
                hull.removeAt(hull.lastIndex)
            }
            hull.add(point)
        }
        val lowerSize = hull.size
        for (i in sorted.lastIndex - 1 downTo 0) {
            val point = sorted[i]
            while (hull.size > lowerSize && turn(hull[hull.size - 2], hull.last(), point) <= 0f) {
                hull.removeAt(hull.lastIndex)
            }
            hull.add(point)
        }
        if (hull.size > 1) hull.removeAt(hull.lastIndex)
        vertices = FloatArray(hull.size * 2)
        axes = FloatArray(hull.size * 4)
        for (i in hull.indices) {
            vertices[i * 2] = hull[i].x
            vertices[i * 2 + 1] = hull[i].y
        }
        for (i in hull.indices) {
            val p = hull[i]
            val q = hull[if (i == hull.lastIndex) 0 else i + 1]
            val dx = q.x - p.x
            val dy = q.y - p.y
            val length = sqrt(dx * dx + dy * dy)
            if (length < 0.0001f) continue
            val nx = -dy / length
            val ny = dx / length
            var low = Float.POSITIVE_INFINITY
            var high = Float.NEGATIVE_INFINITY
            for (j in hull.indices) {
                val projection = vertices[j * 2] * nx + vertices[j * 2 + 1] * ny
                if (projection < low) low = projection
                if (projection > high) high = projection
            }
            axes[i * 4] = nx; axes[i * 4 + 1] = ny
            axes[i * 4 + 2] = low; axes[i * 4 + 3] = high
        }
    }
}

/** Full concave contours decide contact. The cached envelope only supplies the separation vector. */
internal fun catOverlap(a: CatOutline, ax: Float, ay: Float, b: CatOutline, bx: Float, by: Float): CatPoint? {
    if (ax + a.right <= bx + b.left || bx + b.right <= ax + a.left ||
        ay + a.top <= by + b.bottom || by + b.top <= ay + a.bottom) return null
    val dx = bx - ax
    val dy = by - ay
    if (!contoursTouch(a, b, dx, dy)) return null
    val aShape = a.collisionShape()
    val bShape = b.collisionShape()
    var least = Float.POSITIVE_INFINITY
    var normalX = 0f
    var normalY = 0f
    for (side in 0..1) {
        val shape = if (side == 0) aShape else bShape
        val other = if (side == 0) bShape else aShape
        val offsetX = if (side == 0) dx else -dx
        val offsetY = if (side == 0) dy else -dy
        var axis = 0
        var lowVertex = 0
        var highVertex = 0
        while (axis < shape.axes.size) {
            var nx = shape.axes[axis]
            var ny = shape.axes[axis + 1]
            if (nx == 0f && ny == 0f) { axis += 4; continue }
            val low = shape.axes[axis + 2]
            val high = shape.axes[axis + 3]
            var otherLow = Float.POSITIVE_INFINITY
            var otherHigh = Float.NEGATIVE_INFINITY
            if (axis == 0) {
                var vertex = 0
                while (vertex < other.vertices.size) {
                    val projection = other.vertices[vertex] * nx + other.vertices[vertex + 1] * ny
                    if (projection < otherLow) { otherLow = projection; lowVertex = vertex }
                    if (projection > otherHigh) { otherHigh = projection; highVertex = vertex }
                    vertex += 2
                }
            } else {
                // Both hulls and their normals are ordered around the perimeter. As the normal
                // turns, its extreme vertices only move forward: no full scan for every axis.
                lowVertex = advanceConvexSupport(other.vertices, lowVertex, nx, ny, maximum = false)
                highVertex = advanceConvexSupport(other.vertices, highVertex, nx, ny, maximum = true)
                otherLow = other.vertices[lowVertex] * nx + other.vertices[lowVertex + 1] * ny
                otherHigh = other.vertices[highVertex] * nx + other.vertices[highVertex + 1] * ny
            }
            val shift = offsetX * nx + offsetY * ny
            otherLow += shift; otherHigh += shift
            if (high <= otherLow || otherHigh <= low) return null
            val forward = high - otherLow
            val backward = otherHigh - low
            val overlap = if (forward < backward) forward else backward
            if (overlap < least) {
                least = overlap
                val flip = if (forward == backward) dx * nx + dy * ny < 0f
                    else if (side == 0) backward < forward else forward < backward
                if (flip) { nx = -nx; ny = -ny }
                normalX = nx * overlap; normalY = ny * overlap
            }
            axis += 4
        }
    }
    return if (least.isFinite()) CatPoint(normalX, normalY) else null
}

internal fun advanceConvexSupport(vertices: FloatArray, start: Int, nx: Float, ny: Float, maximum: Boolean): Int {
    var index = start
    var value = vertices[index] * nx + vertices[index + 1] * ny
    var visited = 0
    while (visited < vertices.size) {
        val next = if (index + 2 == vertices.size) 0 else index + 2
        val candidate = vertices[next] * nx + vertices[next + 1] * ny
        if (if (maximum) candidate < value else candidate > value) break
        index = next
        value = candidate
        visited += 2
    }
    return index
}

private fun contoursTouch(a: CatOutline, b: CatOutline, dx: Float, dy: Float): Boolean {
    // Without crossing edges, one vertex per polygon is enough to test containment.
    val firstA = a.points[0]
    val firstB = b.points[0]
    if (b.contains(firstA.x - dx, firstA.y - dy) || a.contains(firstB.x + dx, firstB.y + dy)) return true
    for (i in a.points.indices) {
        val p = a.points[i]
        val q = a.points[if (i == a.points.lastIndex) 0 else i + 1]
        val left = if (p.x < q.x) p.x else q.x
        val right = if (p.x > q.x) p.x else q.x
        val bottom = if (p.y < q.y) p.y else q.y
        val top = if (p.y > q.y) p.y else q.y
        if (right < b.left + dx || left > b.right + dx || top < b.bottom + dy || bottom > b.top + dy) continue
        for (j in b.points.indices) {
            val r = b.points[j]
            val s = b.points[if (j == b.points.lastIndex) 0 else j + 1]
            val rx = r.x + dx; val ry = r.y + dy
            val sx = s.x + dx; val sy = s.y + dy
            // Reject separated edge boxes before doing any intersection math; allocate no points.
            if ((rx < left && sx < left) || (rx > right && sx > right) ||
                (ry < bottom && sy < bottom) || (ry > top && sy > top)) continue
            val pr = (q.x - p.x) * (ry - p.y) - (q.y - p.y) * (rx - p.x)
            val ps = (q.x - p.x) * (sy - p.y) - (q.y - p.y) * (sx - p.x)
            val rp = (sx - rx) * (p.y - ry) - (sy - ry) * (p.x - rx)
            val rq = (sx - rx) * (q.y - ry) - (sy - ry) * (q.x - rx)
            if (pr * ps < 0f && rp * rq < 0f) return true
            if (abs(pr) < 0.0001f && rx >= left && rx <= right && ry >= bottom && ry <= top) return true
            if (abs(ps) < 0.0001f && sx >= left && sx <= right && sy >= bottom && sy <= top) return true
            if (abs(rp) < 0.0001f && between(p.x, rx, sx) && between(p.y, ry, sy)) return true
            if (abs(rq) < 0.0001f && between(q.x, rx, sx) && between(q.y, ry, sy)) return true
        }
    }
    return false
}

private fun between(value: Float, a: Float, b: Float) =
    (value >= a && value <= b) || (value >= b && value <= a)
