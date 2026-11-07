package com.example.shiro

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val TURN = (2.0 * PI).toFloat()

/** Wrap the last frequency band back to the first without a seam in the liquid ring. */
internal fun circularBandAt(bands: FloatArray, fraction: Float): Float {
    if (bands.isEmpty()) return 0f
    val position = ((fraction % 1f + 1f) % 1f) * bands.size
    val index = position.toInt().coerceAtMost(bands.lastIndex)
    val mix = (1f - cos((position - index) * PI.toFloat())) * 0.5f
    return bands[index] * (1f - mix) + bands[(index + 1) % bands.size] * mix
}

/** Segment endpoints are shared verbatim by drawing and contact sampling, in local Y-up dp. */
internal data class CircularScene(val mode: VisualizerMode, val bounds: CenterFieldBounds,
    val amplitude: Float, val edges: FloatArray, val speeds: FloatArray, val alpha: Float,
    val angularSpeed: Float = 0f) {
    fun radiusAt(dx: Float, dy: Float): Float {
        val distance = hypot(dx, dy)
        if (distance < 0.001f) return bounds.radius
        val nx = dx / distance; val ny = dy / distance
        var radius = bounds.radius
        for (i in speeds.indices) {
            val j = i * 4
            val ax = edges[j]; val ay = edges[j + 1]
            val ex = edges[j + 2] - ax; val ey = edges[j + 3] - ay
            val denominator = nx * ey - ny * ex
            if (kotlin.math.abs(denominator) < 0.0001f) continue
            val along = (ax * ey - ay * ex) / denominator
            val edge = (ax * ny - ay * nx) / denominator
            if (along > radius && edge in 0f..1f) radius = along
        }
        return radius
    }
}

internal class CircularSpectrumMotion(private val mode: VisualizerMode, private val width: Float, private val height: Float) {
    private val count = if (mode == VisualizerMode.SPECTRUM) 24 else 96
    private val directions = FloatArray(count * 4) { j ->
        val segment = j / 4
        val end = (j % 4) / 2
        val inset = if (mode == VisualizerMode.SPECTRUM) SPECTRUM_BAR_INSET else 0f
        val at = (segment + if (end == 0) inset else 1f - inset) / count
        val angle = PI.toFloat() * 0.5f + TURN * at
        if (j % 2 == 0) cos(angle) else sin(angle)
    }
    private val previous = FloatArray(count) { Float.NaN }
    private val dents = FloatArray(24)
    private val velocity = FloatArray(24)
    private val load = FloatArray(24)
    private var angle = 0f

    fun contact(x: Float, y: Float, impact: Float, weight: Float) {
        if (mode != VisualizerMode.LIQUID) return
        val local = (atan2(y, x) - PI.toFloat() * 0.5f - angle) / TURN
        val fraction = (local % 1f + 1f) % 1f
        val index = (fraction * dents.size).roundToInt() % dents.size
        load[index] = (load[index] + weight * 0.12f).coerceAtMost(0.5f)
        if (impact >= 35f) { dents[index] = maxOf(dents[index], (impact / 320f).coerceAtMost(1f)); velocity[index] = 0f }
    }

    fun advance(frame: VisualizerFrame, seconds: Float, rotation: CircularRotation = CircularRotation()): CircularScene {
        val dt = seconds.coerceIn(0f, 1f / 30f)
        angle = rotation.angle
        val cosine = cos(angle); val sine = sin(angle)
        for (i in dents.indices) {
            velocity[i] += ((load[i] - dents[i]) * 85f - velocity[i] * 12f) * dt
            dents[i] = (dents[i] + velocity[i] * dt).coerceIn(0f, 1f)
            load[i] = 0f
        }
        val outer = centerFieldBounds(width, height, frame.fieldStrength)
        // Give the audio most of the radius; the old large core left too little room to move.
        val bounds = outer.copy(radius = outer.radius * 0.40f)
        val amplitude = outer.radius * 0.60f
        val edges = FloatArray(count * 4)
        val speeds = FloatArray(count)
        fun drive(level: Float) = (level + (level - frame.energy) * 0.45f).coerceIn(0f, 1f)
        fun radius(at: Float): Float {
            val level = drive(circularBandAt(frame.bands, at))
            val flow = sin(at * TURN + frame.phase).toFloat()
            val lift = amplitude * (level * 0.84f + frame.energy * (0.10f + flow * 0.06f)) -
                circularBandAt(dents, at) * 6f * (frame.energy * 8f).coerceAtMost(1f)
            return bounds.radius + lift.coerceIn(0f, amplitude)
        }
        for (i in 0 until count) {
            val first = if (mode == VisualizerMode.SPECTRUM)
                bounds.radius + amplitude * frame.bands.getOrElse(i) { 0f } else radius(i.toFloat() / count)
            val second = if (mode == VisualizerMode.SPECTRUM) first else radius((i + 1f) / count)
            val average = (first + second) * 0.5f
            speeds[i] = if (previous[i].isFinite() && dt > 0f) ((average - previous[i]) / dt).coerceIn(-180f, 180f) else 0f
            previous[i] = average
            val j = i * 4
            edges[j] = (directions[j] * cosine - directions[j + 1] * sine) * first
            edges[j + 1] = (directions[j] * sine + directions[j + 1] * cosine) * first
            edges[j + 2] = (directions[j + 2] * cosine - directions[j + 3] * sine) * second
            edges[j + 3] = (directions[j + 2] * sine + directions[j + 3] * cosine) * second
        }
        return CircularScene(mode, bounds, amplitude, edges, speeds,
            maxOf((frame.energy * 5f).coerceIn(0f, 1f), frame.fieldStrength), rotation.speed)
    }
}

internal data class RadialContact(val depth: Float, val point: CatPoint, val hit: CatPoint,
    val normalX: Float, val normalY: Float, val pushX: Float, val pushY: Float,
    val speed: Float, val impact: Float)

/** Rotate into a cat's radial frame, then intersect exact outline edges with nearby bar/wave edges. */
internal class RadialSurfaceSampler {
    private var xs = FloatArray(96)
    private var ys = FloatArray(96)

    fun sample(body: WaveCatBody, shape: CatOutline, scene: CircularScene): RadialContact? {
        val dx = body.x - scene.bounds.x; val dy = body.height - scene.bounds.height
        val distance = hypot(dx, dy)
        if (distance < scene.bounds.radius * 0.5f) return null
        val nx = dx / distance; val ny = dy / distance
        val tx = -ny; val ty = nx
        if (xs.size < shape.points.size) { xs = FloatArray(shape.points.size); ys = FloatArray(shape.points.size) }
        var left = Float.POSITIVE_INFINITY; var right = Float.NEGATIVE_INFINITY
        for (i in shape.points.indices) {
            val p = shape.points[i]
            xs[i] = p.x * tx + p.y * ty; ys[i] = p.x * nx + p.y * ny
            left = minOf(left, xs[i]); right = maxOf(right, xs[i])
        }
        var depth = -1f
        var hitX = 0f; var hitY = 0f; var pointY = 0f; var slope = 0f; var speed = 0f
        for (segment in scene.speeds.indices) {
            val j = segment * 4
            val ax = scene.edges[j] * tx + scene.edges[j + 1] * ty
            val ay = scene.edges[j] * nx + scene.edges[j + 1] * ny
            val bx = scene.edges[j + 2] * tx + scene.edges[j + 3] * ty
            val by = scene.edges[j + 2] * nx + scene.edges[j + 3] * ny
            // Only the outward-facing near side can support this cat.
            if (ay + by < scene.bounds.radius || bx <= ax + 0.001f || bx < left || ax > right) continue
            val m = (by - ay) / (bx - ax)
            for (i in shape.points.indices) {
                val next = if (i == shape.points.lastIndex) 0 else i + 1
                val from = maxOf(ax, minOf(xs[i], xs[next]))
                val to = minOf(bx, maxOf(xs[i], xs[next]))
                if (from > to) continue
                for (end in 0..1) {
                    val at = if (end == 0) from else to
                    val y = if (kotlin.math.abs(xs[next] - xs[i]) < 0.0001f) minOf(ys[i], ys[next])
                        else ys[i] + (ys[next] - ys[i]) * (at - xs[i]) / (xs[next] - xs[i])
                    val surface = ay + (at - ax) * m
                    val penetration = surface - y - distance
                    if (penetration > depth) {
                        depth = penetration; hitX = at; hitY = surface; pointY = y; slope = m; speed = scene.speeds[segment]
                    }
                }
            }
        }
        if (depth <= -1f) return null
        val length = sqrt(1f + slope * slope)
        val normalX = (nx - slope * tx) / length; val normalY = (ny - slope * ty) / length
        val localX = tx * hitX + nx * hitY; val localY = ty * hitX + ny * hitY
        // Rotating slopes have normal velocity too, even when their frequency levels stay constant.
        val rotationSpeed = scene.angularSpeed * (-localY * normalX + localX * normalY)
        val normalSpeed = (speed / length + rotationSpeed).coerceIn(-220f, 220f)
        return RadialContact(depth, CatPoint(tx * hitX + nx * pointY, ty * hitX + ny * pointY),
            CatPoint(scene.bounds.x + tx * hitX + nx * hitY, scene.bounds.height + ty * hitX + ny * hitY),
            normalX, normalY, nx, ny, normalSpeed,
            (normalSpeed - body.velocityX * normalX - body.velocityY * normalY).coerceAtLeast(0f))
    }
}
