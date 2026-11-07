package com.example.shiro

import kotlin.math.exp

internal data class VisualRipple(val x: Float, val y: Float, val strength: Float, val age: Float = 0f)

internal data class VisualizerFrame(
    val bands: FloatArray = FloatArray(24), val phase: Double = 0.0,
    val energy: Float = 0f, val bass: Float = 0f, val treble: Float = 0f,
    val pulse: Float = 0f, val onset: Float = 0f, val ripples: List<VisualRipple> = emptyList(),
    val fieldStrength: Float = 0f
)

/** Audio-driven motion on the stage's existing clock, with a bounded ring pool. */
internal class VisualizerMotion {
    private var phase = 0.0
    private var pulse = 0f
    private var previousBeat = 0f
    private var cooldown = 0f
    private var beatNumber = 0
    private val ripples = ArrayList<VisualRipple>(6)

    fun reset() {
        pulse = 0f; previousBeat = 0f; cooldown = 0f
        ripples.clear()
    }

    fun advance(bands: FloatArray, dynamics: MusicDynamics, playing: Boolean, seconds: Float, rings: Boolean): VisualizerFrame {
        val dt = if (seconds.isFinite()) seconds.coerceIn(0f, 1f / 30f) else 0f
        fun mean(from: Int, until: Int): Float {
            var sum = 0f
            for (i in from until until) if (bands[i].isFinite()) sum += bands[i].coerceIn(0f, 1f)
            return sum / (until - from).coerceAtLeast(1)
        }
        val energy = mean(0, bands.size)
        val bass = mean(0, (bands.size / 4).coerceAtLeast(1).coerceAtMost(bands.size))
        val treble = mean(bands.size * 3 / 4, bands.size)
        val beat = if (playing && dynamics.beatPulse.isFinite()) dynamics.beatPulse.coerceIn(0f, 1f) else 0f
        pulse += (beat - pulse) * (1f - exp(-dt / 0.09f))
        if (playing && energy > 0.001f) phase += dt * (0.35 + dynamics.tempoDrive * 0.8 + energy * 0.25)
        cooldown = (cooldown - dt).coerceAtLeast(0f)
        var onset = 0f
        if (beat >= 0.55f && previousBeat < 0.55f && cooldown == 0f && energy > 0.015f) {
            onset = ((0.3f + bass * 0.7f) * beat).coerceIn(0f, 1f)
            cooldown = 0.18f
            if (rings) {
                if (ripples.size == 6) ripples.removeAt(0)
                ripples += VisualRipple(if (beatNumber % 2 == 0) 0.35f else 0.65f,
                    if (beatNumber % 3 == 0) 0.43f else 0.53f, onset)
            }
            beatNumber++
        }
        previousBeat = beat
        if (!rings) ripples.clear()
        for (i in ripples.indices.reversed()) {
            val aged = ripples[i].copy(age = ripples[i].age + dt)
            if (aged.age >= 1.8f) ripples.removeAt(i) else ripples[i] = aged
        }
        return VisualizerFrame(bands, phase, energy, bass, treble, pulse, onset, ripples.toList())
    }
}

/** Retained for disabled background styles; center modes now use CenterCatField instead. */
internal class CatBeatBounce {
    private var cursor = 0
    fun trigger(bodies: List<WaveCatBody>, outlines: List<CatOutline>, strength: Float, width: Float): Int {
        if (strength <= 0f || !strength.isFinite() || bodies.isEmpty()) return 0
        var bounced = 0
        var checked = 0
        while (checked < bodies.size && bounced < 3) {
            val index = cursor % bodies.size
            cursor = (cursor + 1) % bodies.size
            checked++
            val body = bodies[index]
            if (!body.active || body.isHeld || body.height > outlines[index].support + 2f || body.velocityY > 20f) continue
            body.musicHop(strength, if (body.x < width * 0.5f) 1f else -1f)
            bounced++
        }
        return bounced
    }
}
