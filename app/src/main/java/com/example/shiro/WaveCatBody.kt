package com.example.shiro

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max

/** Small 2D bodies. Distances are dp, height is measured upward from the wave baseline. */
internal class WaveCatBody(var x: Float) {
    var height = 0f
    var velocityX = 0f
    var velocityY = 0f
    var rotation = 0f
    var spin = 0f
    var compression = 0f
    var contact: WaveContact? = null
        private set
    private var jellyVelocity = 0f
    private var previousFloor = Float.NaN

    fun step(surface: Float, slope: Float, seconds: Float, radius: Float, waveHeight: Float = surface,
        waveContact: WaveContact? = null) {
        val dt = seconds.coerceIn(0.001f, 1f / 60f)
        val floor = (surface - 2f).coerceAtLeast(0f) + radius
        val floorSpeed = if (previousFloor.isNaN()) 0f else
            ((waveHeight - previousFloor) / dt).coerceIn(-240f, 240f)
        previousFloor = waveHeight
        val grounded = height <= floor + 2f
        if (grounded) velocityX -= slope.coerceIn(-2f, 2f) * 450f * dt
        velocityX *= exp(-(if (grounded) 1.2f else 0.18f) * dt)
        velocityX = velocityX.coerceIn(-180f, 180f)
        velocityY -= 980f * dt
        x += velocityX * dt
        height += velocityY * dt
        jellyVelocity += (-70f * compression - 5.8f * jellyVelocity) * dt
        compression = (compression + jellyVelocity * dt).coerceIn(-0.45f, 0.70f)
        if (height <= floor) {
            val impact = (floorSpeed - velocityY).coerceAtLeast(0f)
            height = floor
            velocityY = if (impact > 80f) (floorSpeed.coerceAtLeast(0f) * 0.5f + impact * 0.48f).coerceAtMost(260f) else 0f
            if (impact > 80f) {
                wobble(impact / 600f)
                contact = waveContact
            }
            spin += (velocityX / radius.coerceAtLeast(1f) * 57.3f - spin) * (dt * 12f).coerceAtMost(1f)
        } else spin *= exp(-6.0f * dt)
        rotation = (rotation + spin * dt) % 360f
    }

    fun contain(width: Float, ceiling: Float, outline: CatOutline) {
        val left = -outline.left
        val right = width - outline.right
        if (x < left) { x = left; wobble(abs(velocityX) / 400f); velocityX = abs(velocityX) * 0.5f }
        if (x > right) { x = right; wobble(abs(velocityX) / 400f); velocityX = -abs(velocityX) * 0.5f }
        val top = (ceiling - outline.top).coerceAtLeast(outline.support)
        if (height > top) { height = top; wobble(abs(velocityY) / 400f); velocityY = -abs(velocityY) * 0.4f }
        if (height < outline.support) { height = outline.support; velocityY = max(0f, velocityY) }
    }

    fun tap(direction: Float) {
        velocityX += direction * 55f
        velocityY = max(velocityY, 220f)
        spin += direction * 100f
        wobble(0.6f)
    }

    fun wobble(strength: Float) {
        contact = null
        jellyVelocity = (jellyVelocity + strength.coerceIn(0f, 1f) * 4f).coerceAtMost(8f)
    }

    fun isSettled(radius: Float) = height <= radius + 0.1f && abs(velocityX) < 0.5f &&
        abs(velocityY) < 0.5f && abs(spin) < 1f && abs(compression) < 0.001f && abs(jellyVelocity) < 0.01f
}

internal fun resolveCatContacts(bodies: List<WaveCatBody>, outlines: List<CatOutline>) {
    for (i in bodies.indices) for (j in i + 1 until bodies.size) {
        val a = bodies[i]
        val b = bodies[j]
        val overlap = catOverlap(outlines[i], a.x, a.height, outlines[j], b.x, b.height) ?: continue
        val distance = hypot(overlap.x, overlap.y)
        if (distance < 0.0001f) continue
        val nx = overlap.x / distance
        val ny = overlap.y / distance
        val separation = distance / 2f
        a.x -= nx * separation; a.height -= ny * separation
        b.x += nx * separation; b.height += ny * separation
        val approaching = (b.velocityX - a.velocityX) * nx + (b.velocityY - a.velocityY) * ny
        if (approaching < 0f) {
            val impulse = -approaching * 0.72f
            a.wobble(-approaching / 400f); b.wobble(-approaching / 400f)
            a.velocityX -= nx * impulse; a.velocityY -= ny * impulse
            b.velocityX += nx * impulse; b.velocityY += ny * impulse
            a.spin -= ny * impulse * 0.2f
            b.spin += ny * impulse * 0.2f
            val avgSpin = (a.spin + b.spin) / 2f
            a.spin = a.spin * 0.6f + avgSpin * 0.4f
            b.spin = b.spin * 0.6f + avgSpin * 0.4f
        }
    }
}

/** Same smooth cubic interpolation used when drawing the wave. */
internal fun waveHeightAt(bands: FloatArray, fraction: Float): Float {
    if (bands.isEmpty()) return 0f
    val position = fraction.coerceIn(0f, 1f) * (bands.size - 1)
    val left = position.toInt()
    val right = (left + 1).coerceAtMost(bands.lastIndex)
    val t = position - left
    return bands[left] + (bands[right] - bands[left]) * t * t * (3f - 2f * t)
}
