package com.example.shiro

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

@Composable
internal fun CircularMusicStage(scenes: State<CircularScene?>, peaks: State<FloatArray>,
    color: Color, night: Float, modifier: Modifier) {
    val path = remember { Path() }
    val edge = remember { Path() }
    val light = lerp(color, Color.White, 0.72f)
    val dark = lerp(color, Color.Black, 0.25f)
    Canvas(modifier.semantics { contentDescription = "Circular music visualizer with Shiros riding its outer edge" }) {
        val scene = scenes.value ?: return@Canvas
        val trailAlpha = if (scene.mode == VisualizerMode.SPECTRUM) (peaks.value.maxOrNull() ?: 0f) * 0.5f else 0f
        val alpha = maxOf(scene.alpha, trailAlpha) * (1f - night * 0.3f)
        if (alpha < 0.002f) return@Canvas
        val center = Offset(scene.bounds.x.dp.toPx(), size.height - scene.bounds.height.dp.toPx())
        val base = scene.bounds.radius
        val maximum = (base + scene.amplitude).dp.toPx()
        val fill = Brush.radialGradient(listOf(dark.copy(alpha = 0.6f), color.copy(alpha = 0.65f), light.copy(alpha = 0.85f)),
            center = center, radius = maximum)
        fun point(x: Float, y: Float) = center + Offset(x.dp.toPx(), -y.dp.toPx())
        drawCircle(light.copy(alpha = alpha * 0.4f), base.dp.toPx(), center, style = Stroke(1.dp.toPx()))
        if (scene.mode == VisualizerMode.SPECTRUM) {
            for (i in scene.speeds.indices) {
                val j = i * 4
                val ax = scene.edges[j]; val ay = scene.edges[j + 1]
                val bx = scene.edges[j + 2]; val by = scene.edges[j + 3]
                val radius = hypot(ax, ay)
                val ux = ax / radius; val uy = ay / radius
                val vx = bx / radius; val vy = by / radius
                val start = point(ux * base, uy * base)
                val end = point(vx * base, vy * base)
                fun bar(top: Float) {
                    val p = point(ux * top, uy * top); val q = point(vx * top, vy * top)
                    path.reset(); path.moveTo(start.x, start.y); path.lineTo(p.x, p.y)
                    path.lineTo(q.x, q.y); path.lineTo(end.x, end.y); path.close()
                }
                val peak = peaks.value.getOrElse(i) { 0f }
                if (peak > 0.001f) {
                    bar(base + scene.amplitude * peak)
                    drawPath(path, color.copy(alpha = alpha * 0.25f * (peak / 0.04f).coerceAtMost(1f)))
                }
                if (radius > base + 0.01f) {
                    bar(radius)
                    drawPath(path, fill, alpha = alpha)
                    val topLeft = point(ax, ay); val topRight = point(bx, by)
                    drawLine(light.copy(alpha = alpha * 0.85f), topLeft, topRight, strokeWidth = 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = alpha * 0.3f), start, topLeft, strokeWidth = 0.7.dp.toPx())
                    drawLine(dark.copy(alpha = alpha * 0.3f), end, topRight, strokeWidth = 0.7.dp.toPx())
                }
            }
        } else {
            for (layer in 2 downTo 0) {
                val scale = 1f - layer * 0.25f
                path.reset(); edge.reset()
                for (i in scene.speeds.indices) {
                    val j = i * 4
                    val x = scene.edges[j]; val y = scene.edges[j + 1]
                    val radius = hypot(x, y)
                    val r = base + (radius - base) * scale
                    val p = point(x * r / radius, y * r / radius)
                    if (i == 0) { path.moveTo(p.x, p.y); edge.moveTo(p.x, p.y) }
                    else { path.lineTo(p.x, p.y); edge.lineTo(p.x, p.y) }
                }
                edge.close()
                val firstRadius = hypot(scene.edges[0], scene.edges[1])
                val firstScale = (base + (firstRadius - base) * scale) / firstRadius
                val first = point(scene.edges[0] * firstScale, scene.edges[1] * firstScale)
                path.lineTo(first.x, first.y)
                val innerFirstScale = maxOf(base, base + (firstRadius - base) * scale - 8f) / firstRadius
                val innerFirst = point(scene.edges[0] * innerFirstScale, scene.edges[1] * innerFirstScale)
                path.lineTo(innerFirst.x, innerFirst.y)
                for (i in scene.speeds.indices.reversed()) {
                    val j = i * 4
                    val x = scene.edges[j]; val y = scene.edges[j + 1]
                    val radius = hypot(x, y)
                    val inner = maxOf(base, base + (radius - base) * scale - 8f)
                    val p = point(x * inner / radius, y * inner / radius)
                    path.lineTo(p.x, p.y)
                }
                path.close()
                val opacity = alpha * if (layer == 0) 1f else 0.4f
                drawPath(path, fill, alpha = opacity * 0.5f)
                drawPath(edge, light.copy(alpha = opacity * 0.14f), style = Stroke(5.dp.toPx()))
                drawPath(edge, light.copy(alpha = opacity * 0.9f), style = Stroke(if (layer == 0) 1.8.dp.toPx() else 1.dp.toPx()))
            }
        }
    }
}
