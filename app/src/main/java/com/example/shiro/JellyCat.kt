package com.example.shiro

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.core.content.ContextCompat

internal data class JellySprites(val open: Bitmap, val closed: Bitmap, val sleepy: Bitmap, val hull: List<CatPoint>)

/** All cats share three cached composites; the material and eyes deform with the body. */
@Composable
internal fun rememberJellySprites(left: Color, right: Color): JellySprites {
    val context = LocalContext.current
    return remember(context, left, right) {
        val body = Bitmap.createBitmap(384, 256, Bitmap.Config.ARGB_8888)
        val bodyCanvas = AndroidCanvas(body)
        ContextCompat.getDrawable(context, R.drawable.shiro_body_neutral)!!.mutate().apply {
            setBounds(0, 0, 384, 256)
            draw(bodyCanvas)
        }
        // Derive collisions from the original silhouette, before any cosmetic lighting.
        val hull = opaqueCatHull(body.width, body.height) { x, y -> body.getPixel(x, y) ushr 24 }
        shadeJellyBody(bodyCanvas)
        fun composite(closed: Boolean, sleepy: Boolean): Bitmap {
            val bitmap = body.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = AndroidCanvas(bitmap)
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
        val sprites = JellySprites(composite(false, false), composite(true, false), composite(false, true), hull)
        body.recycle()
        sprites
    }
}

/** SRC_ATOP preserves the body's alpha, including its antialiased edge and transparent padding. */
private fun shadeJellyBody(canvas: AndroidCanvas) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        shader = LinearGradient(120f, 70f, 278f, 238f,
            intArrayOf(0x00FFFFFF, 0x0C8297AA, 0x6B70849C), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, 384f, 256f, paint)
    paint.shader = RadialGradient(164f, 105f, 132f,
        intArrayOf(0xD9FFFFFF.toInt(), 0x42FFFFFF, 0x00FFFFFF),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, 384f, 256f, paint)
    // Broad curved reflections remain readable on the smallest descendants.
    paint.shader = LinearGradient(119f, 90f, 152f, 173f,
        0xC7FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
    canvas.drawOval(115f, 100f, 143f, 180f, paint)
    paint.shader = null
    paint.color = 0xBFFFFFFF.toInt()
    canvas.drawOval(141f, 65f, 152f, 81f, paint)
    paint.color = 0x52FFFFFF
    canvas.drawOval(213f, 213f, 267f, 222f, paint)
}

/** One shared vertex tint follows artwork transitions without rebuilding any body bitmaps. */
@Composable
internal fun rememberJellyReflection(color: Color): IntArray = remember(color) {
    IntArray((JELLY_COLUMNS + 1) * (JELLY_ROWS + 1)) { index ->
        val v = (index / (JELLY_COLUMNS + 1)).toFloat() / JELLY_ROWS
        val reflection = ((v - 0.58f) / 0.35f).coerceIn(0f, 1f) * 0.18f
        lerp(Color.White, color, reflection).toArgb()
    }
}

@Composable
internal fun JellyCat(
    sprites: JellySprites, reflection: IntArray, isBlinking: Boolean, isSleepy: Boolean,
    jelly: () -> Float, bend: () -> Float, contact: () -> WaveContact?, onTap: () -> Unit, modifier: Modifier,
    onSplit: (() -> Unit)? = null
) {
    val mesh = remember { JellyMeshBuffer() }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    // Scene-level touch picking follows the outline; semantics and keyboard activation stay accessible.
    Canvas(modifier.semantics {
        contentDescription = "Jelly Shiro, tap to bounce or drag and release to toss"
        role = Role.Button
        onClick { onTap(); true }
        if (onSplit != null) customActions = listOf(CustomAccessibilityAction("Split into two smaller Shiros") {
            onSplit(); true
        })
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
        val vertices = mesh.update(size.width, size.height, deformation, bend(), contact())
        drawIntoCanvas { it.nativeCanvas.drawBitmapMesh(bitmap, JELLY_COLUMNS, JELLY_ROWS, vertices, 0, reflection, 0, paint) }
    }
}
