package com.example.shiro

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.core.content.ContextCompat

internal data class JellySprites(val open: Bitmap, val closed: Bitmap, val sleepy: Bitmap, val hull: List<CatPoint>)

/** All five cats share these small cached composites; eyes and body deform together. */
@Composable
internal fun rememberJellySprites(left: Color, right: Color): JellySprites {
    val context = LocalContext.current
    return remember(context, left, right) {
        fun composite(closed: Boolean, sleepy: Boolean): Bitmap {
            val bitmap = Bitmap.createBitmap(384, 256, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(bitmap)
            ContextCompat.getDrawable(context, R.drawable.shiro_body_neutral)!!.mutate().apply {
                setBounds(0, 0, 384, 256)
                draw(canvas)
            }
            val eyes = listOf(
                (if (closed) R.drawable.shiro_eyes_closed_left else R.drawable.shiro_eyes_neutral_left) to left,
                (if (closed) R.drawable.shiro_eyes_closed_right else R.drawable.shiro_eyes_neutral_right) to right
            )
            eyes.forEach { (resource, color) ->
                canvas.save()
                canvas.translate(10f, 0f)
                canvas.scale(0.6f, if (sleepy && !closed) 0.27f else 0.6f, 192f, 256f * 0.5137f)
                ContextCompat.getDrawable(context, resource)!!.mutate().apply {
                    setTint(color.toArgb())
                    setBounds(0, 0, 384, 256)
                    draw(canvas)
                }
                canvas.restore()
            }
            return bitmap
        }
        val open = composite(false, false)
        val hull = opaqueCatHull(open.width, open.height) { x, y -> open.getPixel(x, y) ushr 24 }
        JellySprites(open, composite(true, false), composite(false, true), hull)
    }
}

@Composable
internal fun JellyCat(
    sprites: JellySprites, isBlinking: Boolean, isSleepy: Boolean,
    jelly: () -> Float, bend: () -> Float, contact: () -> WaveContact?, onTap: () -> Unit, modifier: Modifier
) {
    val vertices = remember { FloatArray((JELLY_COLUMNS + 1) * (JELLY_ROWS + 1) * 2) }
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix().apply {
                    set(floatArrayOf(
                        1.08f, 0f, 0f, 0f, 12f,
                        0f, 1.08f, 0f, 0f, 12f,
                        0f, 0f, 0f, 1.08f, 0f, 12f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                }
            )
        }
    }
    // Scene-level touch picking follows the outline; semantics and keyboard activation stay accessible.
    Canvas(modifier.semantics {
        contentDescription = "Jelly Shiro, tap to bounce"
        role = Role.Button
        onClick { onTap(); true }
    }.onKeyEvent {
        if (it.type == KeyEventType.KeyUp && (it.key == Key.Enter || it.key == Key.Spacebar)) {
            onTap(); true
        } else false
    }.focusable()) {
        val deformation = jelly()
        val bitmap = when {
            isBlinking || deformation > 0.4f -> sprites.closed
            isSleepy -> sprites.sleepy
            else -> sprites.open
        }
        updateJellyMesh(vertices, size.width, size.height, deformation, bend(), contact())
        drawIntoCanvas { it.nativeCanvas.drawBitmapMesh(bitmap, JELLY_COLUMNS, JELLY_ROWS, vertices, 0, null, 0, paint) }
    }
}
