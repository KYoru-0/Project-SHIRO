package com.example.shiro

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** The renderer and both center modes use this same rim, in dp (or pixels when drawing). */
internal data class CenterFieldBounds(val x: Float, val height: Float, val radius: Float)

internal fun centerFieldBounds(width: Float, height: Float, strength: Float) = CenterFieldBounds(
    width * 0.5f, height * 0.54f,
    minOf(width * 0.43f, height * 0.35f) * (0.76f + strength * 0.14f)
)

/** Shared pull/swirl/release for circular Spectrum and Liquid, on the existing physics clock. */
internal class CenterCatField(private val width: Float, private val height: Float, capacity: Int) {
    var strength = 0f
        private set
    var bounds = centerFieldBounds(width, height, 0f)
        private set
    private var bass = 0f
    private var cursor = 0
    private val released = FloatArray(capacity)
    private var surface: CircularScene? = null

    fun useSurface(scene: CircularScene) { surface = scene; bounds = scene.bounds }

    fun reset() {
        strength = 0f; bass = 0f
        released.fill(0f)
        surface = null
        bounds = centerFieldBounds(width, height, 0f)
    }

    fun advance(frame: VisualizerFrame, playing: Boolean, sleep: Float, seconds: Float) {
        val dt = seconds.coerceIn(0f, 1f / 30f)
        val target = if (playing) (frame.energy * 5f).coerceIn(0f, 1f) * (1f - sleep.coerceIn(0f, 1f)) else 0f
        strength += (target - strength) * (1f - exp(-dt / if (target > strength) 0.45f else 0.55f))
        if (target == 0f && strength < 0.001f) strength = 0f
        bass = frame.bass
        bounds = centerFieldBounds(width, height, strength)
        for (i in released.indices) released[i] = (released[i] - dt).coerceAtLeast(0f)
    }

    /** Returns the remaining gravity. Held cats stay under the finger, including inside the rim. */
    fun pull(index: Int, body: WaveCatBody, shape: CatOutline, seconds: Float): Float {
        if (!body.active || body.isHeld || strength == 0f) return 1f
        val influence = strength * (1f - released[index] / 0.65f)
        val dx = body.x - bounds.x
        val dy = body.height - bounds.height
        val distance = hypot(dx, dy)
        val nx = if (distance > 0.001f) dx / distance else cos(index * 2.4f)
        val ny = if (distance > 0.001f) dy / distance else sin(index * 2.4f)
        var extent = 0f
        for (point in shape.points) extent = maxOf(extent, -point.x * nx - point.y * ny)
        val radialSpeed = body.velocityX * nx + body.velocityY * ny
        val tangentSpeed = -body.velocityX * ny + body.velocityY * nx
        val mobility = 1f + (1f - body.sizeScale) * 0.5f
        // Centripetal acceleration keeps the swirl against the rim instead of drifting away.
        val rim = surface?.radiusAt(dx, dy) ?: bounds.radius
        val radial = (-(distance - rim - extent + 1.5f) * (10f + bass * 6f) -
            radialSpeed * 5f - tangentSpeed * tangentSpeed / distance.coerceAtLeast(20f)).coerceIn(-1000f, 1000f)
        val tangent = ((45f + bass * 65f) * mobility - tangentSpeed) * 3f
        val dt = seconds.coerceIn(0f, CatPhysicsClock.STEP_SECONDS)
        body.velocityX += (nx * radial - ny * tangent) * influence * mobility * dt
        body.velocityY += (ny * radial + nx * tangent) * influence * mobility * dt
        body.spin += (tangentSpeed * 0.6f - body.spin) * influence * dt * 2f
        limitSpeed(body)
        // A burst releases suction, not buoyancy; gravity returns as the whole field fades.
        return 1f - strength
    }

    /** Fresh strong beats release at most three nearby cats, then give them time to travel outward. */
    fun burst(bodies: List<WaveCatBody>, onset: Float): Int {
        if (onset < 0.45f || strength < 0.45f || bodies.isEmpty()) return 0
        var count = 0
        var checked = 0
        while (checked < bodies.size && count < 3) {
            val index = cursor % bodies.size
            cursor = (cursor + 1) % bodies.size
            checked++
            val body = bodies[index]
            if (!body.active || body.isHeld || released[index] > 0f) continue
            val dx = body.x - bounds.x
            val dy = body.height - bounds.height
            val distance = hypot(dx, dy)
            val rim = surface?.radiusAt(dx, dy) ?: bounds.radius
            if (distance < bounds.radius * 0.7f || distance > rim + 45f) continue
            val impulse = (110f + onset.coerceIn(0f, 1f) * 130f) * (1f + (1f - body.sizeScale) * 0.5f)
            body.velocityX += dx / distance * impulse
            body.velocityY += dy / distance * impulse
            body.stretchFromContact(onset)
            released[index] = 0.65f
            limitSpeed(body)
            count++
        }
        return count
    }

    /** Contact uses the closest point on the actual deformed outline, not a box or sprite radius. */
    fun resolve(body: WaveCatBody, shape: CatOutline, bodyWidth: Float, bodyHeight: Float, deform: Boolean = true): RadialContact? {
        if (!body.active || body.isHeld || strength < 0.02f) return null
        resolveCore(body, shape, bodyWidth, bodyHeight, deform)
        val scene = surface ?: return null
        val hit = body.radialSampler.sample(body, shape, scene) ?: return null
        resolveContact(body, hit.point.x, hit.point.y, hit.normalX, hit.normalY, hit.depth,
            bodyWidth, bodyHeight, deform, hit.pushX, hit.pushY, hit.speed)
        return hit
    }

    private fun resolveCore(body: WaveCatBody, shape: CatOutline, bodyWidth: Float, bodyHeight: Float, deform: Boolean) {
        if (!body.active || body.isHeld || strength < 0.02f) return
        val dx = body.x - bounds.x
        val dy = body.height - bounds.height
        val distance = hypot(dx, dy)
        val reach = maxOf(maxOf(-shape.left, shape.right), maxOf(-shape.bottom, shape.top)) * 1.42f
        if (distance > bounds.radius + reach + 2f) return
        var px = 0f
        var py = 0f
        var closestSquared = Float.POSITIVE_INFINITY
        if (distance < bounds.radius) {
            // A cat dragged through the object escapes smoothly when released, without teleporting.
            val nx = if (distance > 0.001f) dx / distance else 0f
            val ny = if (distance > 0.001f) dy / distance else -1f
            var projection = Float.POSITIVE_INFINITY
            for (point in shape.points) {
                val dot = point.x * nx + point.y * ny
                if (dot < projection) { projection = dot; px = point.x; py = point.y }
            }
            resolveContact(body, px, py, nx, ny, bounds.radius - distance - projection, bodyWidth, bodyHeight, deform)
            return
        }
        for (i in shape.points.indices) {
            val a = shape.points[i]
            val b = shape.points[if (i == shape.points.lastIndex) 0 else i + 1]
            val ex = b.x - a.x
            val ey = b.y - a.y
            val lengthSquared = ex * ex + ey * ey
            val t = if (lengthSquared > 0f) (-(dx + a.x) * ex - (dy + a.y) * ey) / lengthSquared else 0f
            val x = a.x + ex * t.coerceIn(0f, 1f)
            val y = a.y + ey * t.coerceIn(0f, 1f)
            val squared = (dx + x) * (dx + x) + (dy + y) * (dy + y)
            if (squared < closestSquared) { closestSquared = squared; px = x; py = y }
        }
        val nearest = sqrt(closestSquared).coerceAtLeast(0.001f)
        resolveContact(body, px, py, (dx + px) / nearest, (dy + py) / nearest,
            bounds.radius - nearest, bodyWidth, bodyHeight, deform)
    }

    private fun resolveContact(body: WaveCatBody, px: Float, py: Float, nx: Float, ny: Float,
        depth: Float, bodyWidth: Float, bodyHeight: Float, deform: Boolean,
        pushX: Float = nx, pushY: Float = ny, surfaceSpeed: Float = 0f) {
        if (depth < -1f) return
        val incoming = surfaceSpeed - (body.velocityX * nx + body.velocityY * ny)
        if (depth > 0f) {
            val correction = minOf(depth, if (surface == null) 2f else 4f) * strength
            body.x += pushX * correction; body.height += pushY * correction
            if (incoming > 0f) {
                body.velocityX += nx * incoming * strength
                body.velocityY += ny * incoming * strength
                if (deform && incoming > 70f && surfaceSpeed > 0f) {
                    body.velocityX += nx * incoming * 0.25f * strength
                    body.velocityY += ny * incoming * 0.25f * strength
                }
                limitSpeed(body)
            }
        }
        if (deform && incoming > -10f) {
            val contact = waveContactAt(CatPoint(px, py), body.rotation, bodyWidth, bodyHeight,
                24, width, nx, ny, bodyWidth * 0.5f)
            body.pressAgainst(contact, (0.2f + incoming.coerceAtLeast(0f) / 300f).coerceAtMost(0.95f) * strength)
        }
    }

    private fun limitSpeed(body: WaveCatBody) {
        val speed = hypot(body.velocityX, body.velocityY)
        if (speed > 360f) { body.velocityX *= 360f / speed; body.velocityY *= 360f / speed }
    }
}
