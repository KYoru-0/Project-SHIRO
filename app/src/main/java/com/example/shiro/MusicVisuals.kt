package com.example.shiro

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun MusicVisuals(mode: VisualizerMode, frames: State<VisualizerFrame>, color: Color, night: Float, modifier: Modifier) {
    val geometry = remember { MusicVisualGeometry() }
    val path = remember { Path() }
    val dots = remember { FloatArray(MusicVisualGeometry.DOT_COUNT * 2) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND } }
    val light = lerp(color, Color.White, 0.7f)
    val trail = remember(color) { Brush.linearGradient(listOf(light, color, light)) }
    Canvas(modifier.semantics { contentDescription = "${mode.title}, reacting to the music" }) {
        val frame = frames.value
        val alpha = maxOf((frame.energy * 5f).coerceIn(0f, 1f), frame.fieldStrength) * (1f - night * 0.35f)
        if (alpha < 0.002f || size.minDimension <= 0f) return@Canvas
        val field = centerFieldBounds(size.width, size.height, frame.fieldStrength)
        val center = Offset(field.x, size.height - field.height)
        val radius = if (mode.hasCenterField) field.radius else minOf(size.width * 0.43f, size.height * 0.35f)
        if (mode.hasCenterField) {
            // This visible rim is the same circular boundary used by both modes' cat contacts.
            drawCircle(color.copy(alpha = alpha * 0.1f), radius, center, style = Stroke(5.dp.toPx()))
            drawCircle(light.copy(alpha = alpha * (0.5f + frame.pulse * 0.35f)), radius, center,
                style = Stroke(1.4.dp.toPx()))
        }
        fun line(count: Int, opacity: Float) {
            path.reset()
            for (i in 0 until count) {
                val x = center.x + geometry.points[i * 2] * radius
                val y = center.y + geometry.points[i * 2 + 1] * radius
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color.copy(alpha = alpha * opacity * 0.12f), style = Stroke(6.dp.toPx(), join = StrokeJoin.Round))
            drawPath(path, trail, alpha = alpha * opacity, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
        }
        when (mode) {
            VisualizerMode.HALO -> {
                line(geometry.halo(frame, false), 0.9f)
            }
            VisualizerMode.RIPPLES -> {
                // Quiet, audio-sized source rings remain visible between detected beats.
                for (side in 0..1) drawCircle(light.copy(alpha = alpha * 0.2f),
                    radius * (0.2f + frame.bass * 0.16f),
                    Offset(size.width * if (side == 0) 0.35f else 0.65f, size.height * 0.48f),
                    style = Stroke(1.dp.toPx()))
                for (ripple in frame.ripples) {
                    val progress = (ripple.age / 1.8f).coerceIn(0f, 1f)
                    val fade = (1f - progress) * (1f - progress) * alpha
                    val origin = Offset(size.width * ripple.x, size.height * ripple.y)
                    val spread = radius * (0.12f + progress * (1.1f + ripple.strength * 0.3f))
                    drawCircle(color.copy(alpha = fade * 0.12f), spread, origin, style = Stroke(5.dp.toPx()))
                    drawCircle(light.copy(alpha = fade * 0.85f), spread, origin, style = Stroke(1.5.dp.toPx()))
                    drawCircle(color.copy(alpha = fade * 0.24f), spread * 0.92f, origin, style = Stroke(0.8.dp.toPx()))
                }
            }
            VisualizerMode.ORBITS -> for (layer in 0..2) line(geometry.orbit(frame, layer), 0.7f - layer * 0.15f)
            VisualizerMode.RESONANCE -> {
                val count = geometry.resonance(frame)
                for (i in 0 until count) {
                    dots[i * 2] = center.x + geometry.points[i * 2] * radius
                    dots[i * 2 + 1] = center.y + geometry.points[i * 2 + 1] * radius
                }
                // Two batched point draws, not 192 individually animated composables or particles.
                drawIntoCanvas { canvas ->
                    paint.color = color.copy(alpha = alpha * 0.16f).toArgb()
                    paint.strokeWidth = 5.dp.toPx()
                    canvas.nativeCanvas.drawPoints(dots, 0, count * 2, paint)
                    paint.color = light.copy(alpha = alpha * 0.9f).toArgb()
                    paint.strokeWidth = 2.dp.toPx()
                    canvas.nativeCanvas.drawPoints(dots, 0, count * 2, paint)
                }
            }
            else -> Unit
        }
    }
}

@Composable
internal fun LiquidWaves(scene: State<GlassScene>, color: Color, night: Float, modifier: Modifier) {
    val body = remember { Path() }
    val rim = remember { Path() }
    val light = lerp(color, Color.White, 0.72f)
    val edge = remember(color) { Brush.horizontalGradient(listOf(light, color, light)) }
    Canvas(modifier.semantics { contentDescription = "Shiros riding layered liquid glass waves" }) {
        val piece = scene.value.pieces.firstOrNull() ?: return@Canvas
        val alpha = (scene.value.energy * 6f).coerceIn(0f, 1f) * (1f - night * 0.3f)
        if (alpha < 0.002f) return@Canvas
        for (layer in 2 downTo 0) {
            val scale = 1f - layer * 0.21f
            body.reset(); rim.reset()
            for (i in piece.top.indices) {
                val x = size.width * i / piece.top.lastIndex
                val y = size.height - (piece.top[i] * scale).dp.toPx()
                if (i == 0) { body.moveTo(x, y); rim.moveTo(x, y) }
                else { body.lineTo(x, y); rim.lineTo(x, y) }
            }
            for (i in piece.bottom.indices.reversed()) {
                body.lineTo(size.width * i / piece.bottom.lastIndex, size.height - (piece.bottom[i] * scale).dp.toPx())
            }
            body.close()
            val strength = if (layer == 0) 1f else 0.35f
            drawPath(body, light.copy(alpha = alpha * strength * 0.24f))
            drawPath(rim, edge, alpha = alpha * strength,
                style = Stroke(if (layer == 0) 2.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
