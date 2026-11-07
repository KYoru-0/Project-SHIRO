package com.example.shiro

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal const val SHIRO_REVEAL_MILLIS = 1100

/** Five 650ms pops, spaced 100ms apart, fit inside one short entrance. */
internal fun shiroRevealPhase(progress: Float, index: Int): Float =
    ((progress * SHIRO_REVEAL_MILLIS - index * 100f) / 650f).coerceIn(0f, 1f)

internal fun shiroRevealScale(phase: Float): Float {
    val t = phase.coerceIn(0f, 1f) - 1f
    return 0.2f + 0.8f * (1f + 2.2f * t * t * t + 1.2f * t * t)
}

@Composable
internal fun ShiroEntranceSmoke(progress: State<Float>, origins: Array<CatPoint?>, modifier: Modifier) {
    Canvas(modifier) {
        if (progress.value >= 1f) return@Canvas
        origins.forEachIndexed { index, origin ->
            if (origin == null) return@forEachIndexed
            val phase = shiroRevealPhase(progress.value, index)
            if (phase <= 0f || phase >= 1f) return@forEachIndexed
            val alpha = 0.28f * (1f - phase) * (1f - phase)
            for (puff in 0..4) {
                val side = puff - 2f
                val center = Offset((origin.x + side * (3f + phase * 7f)).dp.toPx(),
                    size.height - (origin.y + 4f + phase * (12f + (puff % 2) * 7f)).dp.toPx())
                val radius = (6f + phase * 9f).dp.toPx()
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = alpha),
                    Color(0xFF85929D).copy(alpha = alpha * 0.4f), Color.Transparent), center, radius),
                    radius = radius, center = center)
            }
        }
    }
}
