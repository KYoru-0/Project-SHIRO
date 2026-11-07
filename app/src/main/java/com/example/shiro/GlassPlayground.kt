package com.example.shiro

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
internal fun GlassPlayground(scene: State<GlassScene>, color: Color, night: Float, modifier: Modifier) {
    val body = remember { Path() }
    val outline = remember { Path() }
    val rim = remember { Path() }
    val light = lerp(color, Color.White, 0.78f)
    val shade = lerp(color, Color.Black, 0.36f)
    val face = remember(color) {
        Brush.verticalGradient(listOf(light.copy(alpha = 0.66f), color.copy(alpha = 0.24f), shade.copy(alpha = 0.42f)),
            startY = 0f, endY = 1f)
    }
    val mote = remember(color) {
        Brush.radialGradient(listOf(light, Color.Transparent), center = Offset.Zero, radius = 1f)
    }
    Canvas(modifier.semantics {
        contentDescription = "Glass playground: floating islands, jelly cushions, piano keys and a liquid ribbon"
    }) {
        val frame = scene.value
        for (piece in frame.pieces) {
            if (piece.level < 0.001f) continue
            val alpha = (piece.level / 0.08f).coerceIn(0f, 1f) * (1f - night * 0.3f)
            val high = piece.top.maxOrNull() ?: 0f
            val low = piece.bottom.minOrNull() ?: 0f
            val span = (high - low).coerceAtLeast(0.01f)
            val width = (piece.right - piece.left).dp.toPx()
            val left = piece.left.dp.toPx()
            body.reset(); outline.reset(); rim.reset()
            for (i in piece.top.indices) {
                val u = i.toFloat() / piece.top.lastIndex
                val x = left + u * width
                val y = size.height - piece.top[i].dp.toPx()
                if (i == 0) {
                    body.moveTo(u, (high - piece.top[i]) / span)
                    outline.moveTo(x, y); rim.moveTo(x, y)
                } else {
                    body.lineTo(u, (high - piece.top[i]) / span)
                    outline.lineTo(x, y); rim.lineTo(x, y)
                }
            }
            for (i in piece.bottom.indices.reversed()) {
                val u = i.toFloat() / piece.bottom.lastIndex
                body.lineTo(u, (high - piece.bottom[i]) / span)
                outline.lineTo(left + u * width, size.height - piece.bottom[i].dp.toPx())
            }
            body.close(); outline.close()
            val y = size.height - high.dp.toPx()
            val depth = span.dp.toPx()
            // One normalized shader is shared by every piece, including its rounded/dented outline.
            withTransform({ translate(left, y); scale(width, depth, pivot = Offset.Zero) }) {
                drawPath(body, face, alpha = alpha)
            }
            drawPath(outline, light.copy(alpha = alpha * 0.3f), style = Stroke(0.7.dp.toPx()))
            drawPath(rim, light.copy(alpha = alpha * (0.72f + piece.pressure * 0.28f)), style = Stroke(1.5.dp.toPx()))
            if (piece.kind == GlassPlatformKind.KEY) {
                // Inset key marker stays on the moving key face, rather than floating above it.
                val markY = size.height - piece.heightAt((piece.left + piece.right) * 0.5f).dp.toPx() + minOf(4.dp.toPx(), depth * 0.32f)
                drawRoundRect(lerp(shade, light, piece.level).copy(alpha = alpha * 0.65f), Offset(left + width * 0.36f, markY),
                    Size(width * 0.28f, minOf(2.dp.toPx(), depth * 0.15f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
            }
        }
        // Eight small motes share this frame; no particle engine, extra clock, or blur pass.
        val glow = (frame.energy * 0.5f).coerceAtMost(0.3f) * (1f - night)
        if (glow > 0.002f) repeat(8) { i ->
            val x = size.width * ((i * 0.618f + 0.15f) % 1f) + sin(frame.phase + i) * 5.dp.toPx()
            val lift = (28f + i * 17f + frame.energy * 65f + sin(frame.phase * 0.7f + i * 2f) * 8f).dp.toPx()
            val radius = (3f + i % 3).dp.toPx()
            withTransform({ translate(x, size.height - lift); scale(radius, radius, pivot = Offset.Zero) }) {
                drawCircle(mote, radius = 1f, center = Offset.Zero, alpha = glow)
            }
        }
    }
}
