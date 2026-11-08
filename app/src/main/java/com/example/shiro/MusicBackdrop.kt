package com.example.shiro

import kotlin.math.exp

data class MusicBackdropFrame(val phase: Double = 0.0, val energy: Float = 0f,
    val bass: Float = 0f, val pulse: Float = 0f, val spectrum: FloatArray? = null,
    val rotation: Float = 0f, val circleHeight: Float = 0f)

/** Continuous color flow and gentle musical swells on the existing stage clock. */
internal class MusicBackdropMotion {
    private var phase = 0.0
    private var energy = 0f
    private var bass = 0f
    private var pulse = 0f
    private var levels = FloatArray(24)
    private var lastRotation = 0f
    private var lastCircleHeight = 0f
    val isSettled get() = energy == 0f && bass == 0f && pulse == 0f && levels.all { it == 0f }

    fun reset(): MusicBackdropFrame {
        energy = 0f; bass = 0f; pulse = 0f
        levels.fill(0f)
        return MusicBackdropFrame(phase, rotation = lastRotation, circleHeight = lastCircleHeight)
    }

    fun advance(bands: FloatArray, dynamics: MusicDynamics, playing: Boolean, seconds: Float,
        motionEnabled: Boolean = true, rotation: Float? = null, circleHeight: Float? = null): MusicBackdropFrame {
        // Preserve the outgoing circle's pose while the layout crossfades to Floor.
        if (rotation != null && rotation.isFinite()) lastRotation = rotation
        if (circleHeight != null && circleHeight.isFinite()) lastCircleHeight = circleHeight
        if (!motionEnabled) return reset()
        val dt = if (seconds.isFinite()) seconds.coerceIn(0f, 1f / 30f) else 0f
        var sum = 0f; var low = 0f
        val lowCount = (bands.size / 4).coerceAtLeast(1)
        if (playing) for (i in bands.indices) {
            val level = bands[i].let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
            sum += level
            if (i < lowCount) low += level
        }
        val targetEnergy = (sum / bands.size.coerceAtLeast(1) * 2f).coerceAtMost(1f)
        val targetBass = (low / lowCount * 2f).coerceAtMost(1f)
        val beat = dynamics.beatPulse.let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
        fun follow(value: Float, target: Float, time: Float): Float {
            val next = value + (target - value) * (1f - exp(-dt / time))
            return if (target == 0f && next < 0.001f) 0f else next
        }
        if (bands.isNotEmpty() && levels.size != bands.size) levels = FloatArray(bands.size)
        for (i in levels.indices) {
            val raw = if (playing) bands.getOrElse(i) { 0f } else 0f
            val target = if (raw.isFinite()) raw.coerceIn(0f, 1f) else 0f
            levels[i] = follow(levels[i], target, if (target > levels[i]) 0.18f else 0.32f)
        }
        energy = follow(energy, targetEnergy, if (targetEnergy > energy) 0.65f else 0.28f)
        bass = follow(bass, targetBass, 0.8f)
        val targetPulse = if (playing) beat * targetEnergy else 0f
        pulse = follow(pulse, targetPulse, if (targetPulse > pulse) 0.24f else 0.65f)
        val tempo = dynamics.tempoDrive.let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
        phase += dt * energy * (0.18 + tempo * 0.22)
        // Drawing runs on the same UI thread and reads this fixed buffer; no spectrum copy per frame.
        return MusicBackdropFrame(phase, energy, bass, pulse,
            if (levels.any { it > 0f }) levels else null, lastRotation, lastCircleHeight)
    }
}
