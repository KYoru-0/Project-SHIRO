package com.example.shiro

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

internal data class CircularRotation(val angle: Float = 0f, val speed: Float = 0f)

/** One continuous angle for both circle styles; randomness only selects occasional destinations. */
internal class CircularDrift(private val random: Random = Random.Default) {
    private var track: String? = null
    private var angle = random.nextDouble(2.0 * PI)
    private var speed = 0f
    private var destination: Double? = null
    private var direction = if (random.nextBoolean()) 1f else -1f
    private var remaining = 16f + random.nextFloat() * 12f
    val isSettled get() = speed == 0f

    fun stop() { speed = 0f }

    fun advance(trackKey: String?, energy: Float, playing: Boolean, seconds: Float,
        motionEnabled: Boolean = true): CircularRotation {
        if (trackKey != null && trackKey != track) {
            val next = random.nextDouble(2.0 * PI)
            // A new track eases toward its starting angle instead of teleporting a live surface.
            if (track == null) angle = next else {
                val turn = ((next - angle) % (2.0 * PI) + 3.0 * PI) % (2.0 * PI) - PI
                destination = angle + turn
            }
            track = trackKey
            remaining = 16f + random.nextFloat() * 12f
        }
        if (!motionEnabled) { stop(); return CircularRotation(angle.toFloat()) }
        val dt = if (seconds.isFinite()) seconds.coerceIn(0f, 1f / 30f) else 0f
        val level = if (playing && energy.isFinite()) energy.coerceIn(0f, 1f) else 0f
        val moving = level > 0.001f
        if (moving) {
            remaining -= dt
            if (remaining <= 0f) {
                direction = -direction
                remaining = 16f + random.nextFloat() * 12f
            }
        }
        val distance = destination?.minus(angle)
        if (distance != null && abs(distance) < 0.04) destination = null
        val cruise = direction * (0.045f + level * 0.18f)
        val target = if (!moving) 0f else if (destination != null)
            (distance!! * 0.45).toFloat().coerceIn(-0.26f, 0.26f) else cruise
        speed += (target - speed) * (1f - exp(-dt / if (moving) 1.4f else 0.45f))
        if (!moving && abs(speed) < 0.0001f) speed = 0f
        angle += speed * dt
        // Keep precision on long sessions, shifting the destination by the same full turns.
        val turns = kotlin.math.floor(angle / (2.0 * PI)) * (2.0 * PI)
        angle -= turns
        destination = destination?.minus(turns)
        return CircularRotation(angle.toFloat(), speed)
    }
}
