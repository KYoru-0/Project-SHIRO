package com.example.shiro

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Use only the extracted cover color and its light/dark shades, with no added hue shifts. */
internal fun stageGradientColors(colors: PlayerColors): List<Color> = listOf(
    colors.artworkSeed, lerp(colors.artworkSeed, Color.White, 0.65f),
    lerp(colors.artworkSeed, Color.Black, 0.22f))

/** No decorative overlay while paused/silent; the music envelope eases its opacity to zero. */
internal fun stageGradientOpacity(frame: MusicBackdropFrame): Float =
    (frame.energy * 0.68f + frame.pulse * 0.12f).coerceIn(0f, 0.8f)

/** A fixed, small mesh bends the gradient itself, including its transparent edges. */
internal class GradientSheet {
    val vertices = FloatArray((COLUMNS + 1) * 4)

    fun update(frame: MusicBackdropFrame, layer: Int, liquid: Float, circle: Boolean,
        aspect: Float = 1f, centerY: Float = 0.5f) {
        val phase = frame.phase * (3.0 + layer * 0.35) + layer * 1.8
        val bend = frame.energy * (0.09f + liquid * 0.12f) + frame.pulse * 0.08f
        val half = 0.115f + liquid * 0.025f + frame.bass * 0.015f
        fun band(position: Float): Float {
            val levels = frame.spectrum ?: return 0f
            if (levels.isEmpty()) return 0f
            val at = if (circle) ((position % 1f + 1f) % 1f) * levels.size
                else position.coerceIn(0f, 1f) * (levels.size - 1)
            val index = at.toInt().coerceIn(0, levels.lastIndex)
            val next = if (circle) (index + 1) % levels.size else (index + 1).coerceAtMost(levels.lastIndex)
            val mix = at - index
            return levels[index] + (levels[next] - levels[index]) * mix
        }
        for (column in 0..COLUMNS) {
            // The duplicate final Circle column closes both geometry and frequency sampling exactly.
            val u = if (circle && column == COLUMNS) 0f else column.toFloat() / COLUMNS
            val x = -0.18f + 1.36f * u
            val at = if (circle) u else x
            val level = band(at)
            val broad = (band(at - 0.06f) + level * 2f + band(at + 0.06f)) * 0.25f
            val driven = level + (broad - level) * liquid
            val wave = sin(phase - at * PI * (if (circle) 4.0 else 2.0)).toFloat()
            val top = column * 2
            val bottom = (COLUMNS + 1 + column) * 2
            if (circle) {
                val angle = -PI / 2.0 - frame.rotation - u * PI * 2.0
                val nx = cos(angle).toFloat(); val ny = sin(angle).toFloat() * aspect
                val radius = (0.19f + layer * 0.17f + driven * 0.20f + frame.bass * 0.055f +
                    wave * bend * (0.16f + liquid * 0.32f)).coerceAtLeast(half + 0.025f)
                vertices[top] = 0.5f + nx * (radius - half)
                vertices[top + 1] = centerY + ny * (radius - half)
                vertices[bottom] = 0.5f + nx * (radius + half)
                vertices[bottom + 1] = centerY + ny * (radius + half)
            } else {
                val y = 0.40f + layer * 0.23f - driven * (0.36f - liquid * 0.10f) -
                    frame.bass * 0.08f + wave * bend * (0.3f + liquid * 0.7f)
                vertices[top] = x; vertices[top + 1] = y - half
                vertices[bottom] = x; vertices[bottom + 1] = y + half
            }
        }
    }

    companion object { const val COLUMNS = 32 }
}

/** Three translucent sheets, with one tiny cached gradient texture and reused vertex buffers. */
@Composable
internal fun StageGradient(colors: PlayerColors, mode: VisualizerMode, layout: VisualizerLayout,
    frames: State<MusicBackdropFrame>, modifier: Modifier) {
    val liquid = animateFloatAsState(if (mode == VisualizerMode.LIQUID) 1f else 0f,
        tween(900), label = "Gradient wave style")
    val circle = animateFloatAsState(if (layout == VisualizerLayout.CIRCLE) 1f else 0f,
        tween(900), label = "Gradient layout")
    val texture = remember {
        // One white alpha strip is tinted for each layer. It is never recreated per frame.
        createBitmap(1, 64).apply {
            for (y in 0 until height) {
                val weight = sin(PI * y / (height - 1)).toFloat()
                this[0, y] = Color.White.copy(alpha = weight * weight).toArgb()
            }
        }
    }
    Box(modifier.drawWithCache {
        val floorSheets = Array(3) { GradientSheet() }
        val circleSheets = Array(3) { GradientSheet() }
        val paints = stageGradientColors(colors).map { color ->
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = PorterDuffColorFilter(color.toArgb(), PorterDuff.Mode.SRC_IN)
            }
        }
        onDrawBehind {
            if (size.minDimension <= 0f) return@onDrawBehind
            val frame = frames.value
            val fluid = liquid.value
            val round = circle.value
            val opacity = stageGradientOpacity(frame)
            if (opacity < 0.005f) return@onDrawBehind
            val centerY = if (frame.circleHeight > 0f) 1f - frame.circleHeight.dp.toPx() / size.height else 0.5f
            clipRect {
                fun layer(i: Int, circular: Boolean, alpha: Float) {
                    if (alpha < 0.005f) return
                    val sheet = if (circular) circleSheets[i] else floorSheets[i]
                    sheet.update(frame, i, fluid, circular, size.width / size.height, centerY)
                    val paint = paints[i]
                    paint.alpha = (alpha * 255f).toInt()
                    withTransform({ scale(size.width, size.height, pivot = Offset.Zero) }) {
                        drawContext.canvas.nativeCanvas.drawBitmapMesh(texture, GradientSheet.COLUMNS, 1,
                            sheet.vertices, 0, null, 0, paint)
                    }
                }
                // Crossfade layouts instead of twisting a flat mesh into a self-crossing ring.
                for (i in floorSheets.indices) {
                    layer(i, false, opacity * (1f - round))
                    layer(i, true, opacity * round)
                }
            }
        }
    })
}
