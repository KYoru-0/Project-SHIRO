package com.example.shiro

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max

/** Small 2D bodies. Distances are dp, height is measured upward from the wave baseline. */
internal class WaveCatBody(var x: Float, val generation: Int = 0, var active: Boolean = true) {
    init { require(generation in 0..SHIRO_MAX_GENERATION) }
    // Each split conserves area: two children have half their parent's mass each.
    val sizeScale = if (generation == 0) 1f else 0.70710678f
    var height = 0f
    var velocityX = 0f
    var velocityY = 0f
    var rotation = 0f
    var spin = 0f
    var compression = 0f
    var isHeld = false
        private set
    var contact: WaveContact? = null
        private set
    private var jellyVelocity = 0f
    private var previousFloor = Float.NaN
    val outlineCache = CatOutlineCache()
    val waveSampler = CatWaveSampler()
    val glassSampler = GlassSurfaceSampler()
    val radialSampler = RadialSurfaceSampler()

    fun resetForReuse() {
        active = true
        isHeld = false
        velocityX = 0f; velocityY = 0f; rotation = 0f; spin = 0f
        compression = 0f; jellyVelocity = 0f
        contact = null
        previousFloor = Float.NaN
    }

    fun grab() {
        isHeld = true
        velocityX = 0f; velocityY = 0f; spin = 0f
        jellyVelocity = 0f
        compression = -0.08f
        contact = null
        previousFloor = Float.NaN
    }

    fun dragTo(targetX: Float, targetHeight: Float, speedX: Float, speedY: Float) {
        if (!isHeld) return
        x = targetX; height = targetHeight
        velocityX = speedX.coerceIn(-450f, 450f)
        velocityY = speedY.coerceIn(-450f, 450f)
        compression = -0.08f - (hypot(velocityX, velocityY) / 1800f).coerceAtMost(0.3f)
    }

    fun release(speedX: Float = 0f, speedY: Float = 0f) {
        if (!isHeld) return
        isHeld = false
        val speed = hypot(speedX, speedY)
        val scale = if (speed > 450f) 450f / speed else 1f
        velocityX = speedX * scale; velocityY = speedY * scale
        spin = (velocityX * 0.7f).coerceIn(-240f, 240f)
        jellyVelocity = 0f
        previousFloor = Float.NaN
    }

    fun step(surface: Float, slope: Float, seconds: Float, radius: Float, waveHeight: Float = surface,
        waveContact: WaveContact? = null, impactDrive: Float = 0f, gravityScale: Float = 1f): Float {
        if (!active || isHeld) return 0f
        var landingSpeed = 0f
        val dt = seconds.coerceIn(0.001f, 1f / 60f)
        val floor = surface.coerceAtLeast(0f) + radius
        val floorSpeed = if (previousFloor.isNaN()) 0f else
            ((waveHeight - previousFloor) / dt).coerceIn(-240f, 240f)
        previousFloor = waveHeight
        val grounded = height <= floor + 2f
        if (grounded) velocityX -= slope.coerceIn(-2f, 2f) * 450f * dt
        velocityX *= exp(-(if (grounded) 1.2f else 0.18f) * dt)
        val maxSpeed = if (grounded) 180f else 450f
        velocityX = velocityX.coerceIn(-maxSpeed, maxSpeed)
        velocityY -= 720f * gravityScale.coerceIn(0f, 1f) * dt
        x += velocityX * dt
        height += velocityY * dt
        val restingDent = if (grounded && waveContact != null && waveHeight > 1f) 0.22f else 0f
        jellyVelocity += (-60f * (compression - restingDent) - 5.4f * jellyVelocity) * dt
        compression = (compression + jellyVelocity * dt).coerceIn(-0.75f, 1.20f)
        if (height <= floor) {
            val impact = (floorSpeed - velocityY).coerceAtLeast(0f)
            if (impact >= 35f) landingSpeed = impact
            height = floor
            val accent = if (waveHeight > 1f && impactDrive.isFinite()) impactDrive.coerceIn(0f, 1f) * 70f else 0f
            // A beat strengthens a real landing, without kicking held or airborne cats every frame.
            velocityY = if (impact > 70f) (floorSpeed.coerceAtLeast(0f) * 0.5f + impact * 0.48f + accent)
                .coerceAtMost(260f + accent) else 0f
            if (impact > 70f) {
                if (waveContact != null) {
                    // Dent immediately on contact, before the cat leaves the surface again.
                    compression = max(compression, (impact / 270f).coerceIn(0.3f, 1.20f))
                    jellyVelocity = 0f
                } else wobble(impact / 450f)
                contact = waveContact
            } else if (waveHeight > 1f && waveContact != null) contact = waveContact
            spin += (velocityX / radius.coerceAtLeast(1f) * 57.3f - spin) * (dt * 12f).coerceAtMost(1f)
        } else spin *= exp(-0.4f * dt)
        rotation = (rotation + spin * dt) % 360f
        return landingSpeed
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

    fun idleHop(direction: Float) {
        if (isHeld) return
        velocityX = direction.coerceIn(-1f, 1f) * 26f
        velocityY = 128f
        spin = direction.coerceIn(-1f, 1f) * 35f
        wobble(0.22f)
    }

    fun musicHop(strength: Float, direction: Float) {
        if (!active || isHeld) return
        val drive = strength.coerceIn(0f, 1f)
        velocityY = max(velocityY, 110f + drive * 100f)
        velocityX = (velocityX + direction * (10f + drive * 14f)).coerceIn(-60f, 60f)
        spin += direction * (15f + drive * 35f)
        wobble(0.2f + drive * 0.2f)
    }

    fun wobble(strength: Float, clearContact: Boolean = true) {
        if (clearContact) contact = null
        jellyVelocity = (jellyVelocity + strength.coerceIn(0f, 1f) * 9.5f).coerceAtMost(14.5f)
    }

    fun pressAgainst(surfaceContact: WaveContact, pressure: Float) {
        if (!active || isHeld) return
        contact = surfaceContact
        if (pressure > compression) { compression = pressure.coerceAtMost(1.20f); jellyVelocity = 0f }
    }

    fun stretchFromContact(strength: Float) {
        if (!active || isHeld) return
        compression = -0.16f - strength.coerceIn(0f, 1f) * 0.24f
        jellyVelocity = 0f
    }

    fun isSettled(radius: Float) = !isHeld && height <= radius + 0.1f && abs(velocityX) < 0.5f &&
        abs(velocityY) < 0.5f && abs(spin) < 1f && abs(compression) < 0.001f && abs(jellyVelocity) < 0.01f
}

internal fun resolveCatContacts(bodies: List<WaveCatBody>, outlines: List<CatOutline>) {
    for (i in bodies.indices) for (j in i + 1 until bodies.size) {
        val a = bodies[i]
        val b = bodies[j]
        if (!a.active || !b.active) continue
        val aWeight = if (a.isHeld) 0f else 1f / (a.sizeScale * a.sizeScale)
        val bWeight = if (b.isHeld) 0f else 1f / (b.sizeScale * b.sizeScale)
        val totalWeight = aWeight + bWeight
        if (totalWeight == 0f) continue
        val overlap = catOverlap(outlines[i], a.x, a.height, outlines[j], b.x, b.height) ?: continue
        val distance = hypot(overlap.x, overlap.y)
        if (distance < 0.0001f) continue
        val nx = overlap.x / distance
        val ny = overlap.y / distance
        val separation = distance / totalWeight
        a.x -= nx * separation * aWeight; a.height -= ny * separation * aWeight
        b.x += nx * separation * bWeight; b.height += ny * separation * bWeight
        val approaching = (b.velocityX - a.velocityX) * nx + (b.velocityY - a.velocityY) * ny
        if (approaching < 0f) {
            val impulse = -approaching * 1.44f / totalWeight
            if (!a.isHeld) a.wobble(-approaching / 400f, clearContact = false)
            if (!b.isHeld) b.wobble(-approaching / 400f, clearContact = false)
            a.velocityX -= nx * impulse * aWeight; a.velocityY -= ny * impulse * aWeight
            b.velocityX += nx * impulse * bWeight; b.velocityY += ny * impulse * bWeight
            a.spin -= ny * impulse * aWeight; b.spin += ny * impulse * bWeight
        }
    }
}

/** Each band occupies one slot; the inset is shared by the renderer and contact geometry. */
internal const val SPECTRUM_BAR_INSET = 0.08f

internal fun spectrumBandAt(bands: FloatArray, fraction: Float): Float {
    if (bands.isEmpty()) return 0f
    return bands[(fraction.coerceIn(0f, 1f) * bands.size).toInt().coerceAtMost(bands.lastIndex)]
}
