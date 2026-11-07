package com.example.shiro

import kotlin.math.sqrt

/** Exact edge/segment contact with the rendered profiles; no invisible boxes around curved pieces. */
internal class GlassSurfaceSampler {
    fun sample(shape: CatOutline, scene: GlassScene, x: Float, height: Float): CatWaveSample {
        var floor = shape.support
        var hitX = x
        var hitY = 0f
        var slope = 0f
        var id = -2
        var contactWidth = 0f
        for (piece in scene.pieces) {
            if (piece.level < 0.001f || x + shape.right < piece.left || x + shape.left > piece.right) continue
            var required = Float.NEGATIVE_INFINITY
            var px = x
            var py = 0f
            var gradient = 0f
            for (i in shape.points.indices) {
                val a = shape.points[i]
                val b = shape.points[if (i == shape.points.lastIndex) 0 else i + 1]
                val left = maxOf(x + minOf(a.x, b.x), piece.left)
                val right = minOf(x + maxOf(a.x, b.x), piece.right)
                if (left > right) continue
                val first = ((left - piece.left) / piece.step).toInt().coerceIn(0, piece.top.lastIndex - 1)
                val last = ((right - piece.left) / piece.step).toInt().coerceIn(0, piece.top.lastIndex - 1)
                for (segment in first..last) {
                    val start = piece.left + segment * piece.step
                    val from = maxOf(left, start)
                    val to = minOf(right, start + piece.step)
                    val m = (piece.top[segment + 1] - piece.top[segment]) / piece.step
                    // The difference of two line segments reaches its maximum at an endpoint.
                    for (end in 0..1) {
                        val at = if (end == 0) from else to
                        val bodyY = if (kotlin.math.abs(b.x - a.x) < 0.0001f) minOf(a.y, b.y)
                            else a.y + (b.y - a.y) * (at - x - a.x) / (b.x - a.x)
                        val surface = piece.top[segment] + (at - start) * m
                        if (surface - bodyY > required) {
                            required = surface - bodyY
                            px = at; py = surface; gradient = m
                        }
                    }
                }
            }
            // Floating platforms catch from above. Cats can fall through the gaps and travel
            // underneath; a platform that rises into a resting cat still carries it upward.
            val canLand = piece.kind == GlassPlatformKind.RIBBON || height >= required - piece.rise - 2f
            if (canLand && required > floor) {
                floor = required; hitX = px; hitY = py; slope = gradient; id = piece.id
                contactWidth = if (piece.kind == GlassPlatformKind.RIBBON) 18f else piece.right - piece.left
            }
        }
        val length = sqrt(1f + slope * slope)
        return CatWaveSample(floor, CatPoint(hitX - x, hitY - floor), hitY, CatPoint(hitX, hitY),
            id, -slope / length, 1f / length, contactWidth)
    }
}
