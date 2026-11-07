package com.example.shiro

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

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

/** All background stops stay in the extracted cover hue and preserve control contrast. */
internal data class BackdropLights(val light: Color, val shade: Color, val pearl: Color)

internal fun backdropLights(colors: PlayerColors) =
    BackdropLights(colors.selected, colors.backdrop, colors.surface)

/** One continuous backdrop. Cached gradient brushes move; controls only read normal player state. */
@Composable
internal fun MusicBackdrop(colors: PlayerColors, mode: VisualizerMode, layout: VisualizerLayout,
    frames: State<MusicBackdropFrame>, modifier: Modifier) {
    val liquid = animateFloatAsState(if (mode == VisualizerMode.LIQUID) 1f else 0f,
        tween(900), label = "Backdrop wave style")
    val circle = animateFloatAsState(if (layout == VisualizerLayout.CIRCLE) 1f else 0f,
        tween(900), label = "Backdrop layout")
    Box(modifier.drawWithCache {
        val base = Brush.verticalGradient(listOf(colors.selected, colors.backdrop))
        val lights = backdropLights(colors)
        fun pool(color: Color) = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)),
            center = Offset.Zero, radius = 1f)
        val light = pool(lights.light)
        val shade = pool(lights.shade)
        val pearl = pool(lights.pearl)
        onDrawBehind {
            drawRect(base)
            if (size.minDimension <= 0f) return@onDrawBehind
            val frame = frames.value
            val activity = frame.energy
            if (activity < 0.005f) return@onDrawBehind
            val fluid = liquid.value
            val round = circle.value
            val sway = sin(frame.phase).toFloat()
            val drift = cos(frame.phase * 0.73).toFloat()
            val flowX = size.width * (0.5f + sway * (0.22f + fluid * 0.12f))
            val flowY = size.height * (0.65f - frame.bass * 0.18f + drift * fluid * 0.14f)
            val centerX = flowX + (size.width * (0.5f + sway * 0.18f) - flowX) * round
            val centerY = flowY + (size.height * 0.36f + drift * size.width * 0.08f - flowY) * round
            val radialSize = size.width * (0.68f + frame.bass * 0.18f + frame.pulse * 0.36f)
            val floorWidth = size.width * (0.95f + frame.pulse * 0.25f)
            val radiusX = floorWidth + (radialSize - floorWidth) * round
            val floorHeight = size.height * (0.42f + fluid * 0.16f + frame.pulse * 0.16f)
            val radiusY = floorHeight + (radialSize - floorHeight) * round
            // Local pools expand on beats; the whole page never switches brightness at once.
            withTransform({ translate(centerX, centerY); scale(radiusX, radiusY, pivot = Offset.Zero) }) {
                drawCircle(light, radius = 1f, center = Offset.Zero,
                    alpha = activity * (0.22f + frame.energy * (0.24f + sway * 0.12f) + frame.pulse * 0.2f))
            }
            val tintX = size.width - centerX + size.width * 0.12f * drift * fluid
            val tintY = size.height * (0.88f - round * 0.32f) - sway * size.height * (0.06f + fluid * 0.08f)
            withTransform({ translate(tintX, tintY); scale(radiusX * 1.2f, radiusY * 1.15f, pivot = Offset.Zero) }) {
                drawCircle(shade, radius = 1f, center = Offset.Zero,
                    alpha = activity * (0.18f + frame.energy * (0.24f - sway * 0.12f) + frame.pulse * 0.15f))
            }
            withTransform({
                translate(centerX, centerY + (1f - round) * size.height * (0.12f - frame.pulse * 0.12f))
                scale(radiusX * 0.72f, radiusY * 0.72f, pivot = Offset.Zero)
            }) {
                drawCircle(pearl, radius = 1f, center = Offset.Zero, alpha = activity * (0.06f + frame.pulse * 0.34f))
            }
        }
    })
}
