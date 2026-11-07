package com.example.shiro

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

private const val SPLASH_LIFETIME = 0.55f
internal data class SpectrumSplash(val x: Float, val height: Float, val strength: Float, val age: Float = 0f)

/** A small bounded set of landing effects, updated by the existing foreground physics loop. */
internal class SpectrumImpacts(catCount: Int) {
    private val cooldowns = FloatArray(catCount)
    private val splashes = mutableListOf<SpectrumSplash>()
    val isEmpty get() = splashes.isEmpty()
    fun snapshot(): List<SpectrumSplash> = splashes.toList()

    fun hit(cat: Int, point: CatPoint, speed: Float) {
        // Ignore resting jitter and the bare baseline when no spectrum bar is visible.
        if (speed < 35f || point.y <= 1f || cooldowns[cat] > 0f) return
        cooldowns[cat] = 0.18f
        if (splashes.size == 12) splashes.removeAt(0)
        splashes.add(SpectrumSplash(point.x, point.y, (speed / 400f).coerceIn(0.18f, 1f)))
    }

    fun advance(seconds: Float) {
        for (i in cooldowns.indices) cooldowns[i] = (cooldowns[i] - seconds).coerceAtLeast(0f)
        for (i in splashes.indices.reversed()) {
            val splash = splashes[i]
            val age = splash.age + seconds
            if (age >= SPLASH_LIFETIME) splashes.removeAt(i) else splashes[i] = splash.copy(age = age)
        }
    }

    fun clear() { splashes.clear(); cooldowns.fill(0f) }
}

@Composable
internal fun SpectrumSplashLayer(splashes: State<List<SpectrumSplash>>, color: Color, modifier: Modifier) {
    val ink = remember(color) { lerp(color, Color.Black, 0.25f) }
    val light = remember(color) { lerp(color, Color.White, 0.78f) }
    val droplet = remember(color) {
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.9f), light.copy(alpha = 0.65f), ink.copy(alpha = 0.38f)),
            center = Offset(-0.3f, -0.35f), radius = 1.4f)
    }
    val landingLight = remember(color) {
        Brush.radialGradient(listOf(light.copy(alpha = 0.8f), Color.Transparent), center = Offset.Zero, radius = 1f)
    }
    Canvas(modifier) {
        for (splash in splashes.value) {
            val progress = (splash.age / SPLASH_LIFETIME).coerceIn(0f, 1f)
            val fade = (1f - progress) * (1f - progress)
            val center = Offset(splash.x.dp.toPx(), size.height - splash.height.dp.toPx())
            val expansion = 1f - (1f - progress) * (1f - progress)
            val radius = (4f + expansion * (10f + splash.strength * 14f)).dp.toPx()
            val ringHeight = radius * 0.28f
            val flash = (1f - splash.age / 0.16f).coerceAtLeast(0f)
            val flashWidth = (7f + splash.strength * 12f).dp.toPx()
            val flashHeight = 2.4.dp.toPx()
            if (flash > 0f) withTransform({
                translate(center.x, center.y)
                scale(flashWidth, flashHeight, pivot = Offset.Zero)
            }) { drawCircle(landingLight, radius = 1f, center = Offset.Zero, alpha = flash) }
            drawOval(light.copy(alpha = fade * 0.7f),
                topLeft = center - Offset(radius, ringHeight), size = Size(radius * 2f, ringHeight * 2f),
                style = Stroke(1.dp.toPx()))
            // Four tiny droplets arc outward and disappear before cluttering the bars below.
            for (drop in 0..3) {
                val direction = (drop - 1.5f) / 1.5f
                val vx = direction * (22f + splash.strength * 35f)
                val vy = (42f + splash.strength * 48f) * if (drop == 1 || drop == 2) 1f else 0.75f
                val lift = vy * splash.age - 100f * splash.age * splash.age
                if (lift < 0f) continue
                val position = center + Offset((vx * splash.age).dp.toPx(), -lift.dp.toPx())
                val dotRadius = ((1.2f + splash.strength * 1.1f) * (1f - progress * 0.5f)).dp.toPx()
                withTransform({
                    translate(position.x, position.y)
                    scale(dotRadius, dotRadius, pivot = Offset.Zero)
                }) { drawCircle(droplet, radius = 1f, center = Offset.Zero, alpha = fade) }
            }
        }
    }
}
