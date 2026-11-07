package com.example.shiro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val LocalPlayerColors = compositionLocalOf { playerColors(Color(0xFF0284C7)) }
private val LocalGlassOverlay = compositionLocalOf { false }

/** Color animation is observed here, away from the playback/playlist state owner. */
@Composable
internal fun ShiroGlassTheme(colorState: State<PlayerColors>? = null, content: @Composable () -> Unit) {
    val colors = colorState?.value ?: LocalPlayerColors.current
    val scheme = remember(colors) {
        lightColorScheme(
            primary = colors.accent, onPrimary = Color.White,
            primaryContainer = colors.selected, onPrimaryContainer = colors.text,
            secondary = colors.accent, onSecondary = Color.White,
            secondaryContainer = colors.selected, onSecondaryContainer = colors.text,
            tertiary = colors.secondaryText, onTertiary = Color.White,
            tertiaryContainer = colors.selected, onTertiaryContainer = colors.text,
            background = colors.background, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.background, onSurfaceVariant = colors.secondaryText,
            surfaceTint = Color.Transparent,
            surfaceBright = colors.surface, surfaceDim = colors.background,
            surfaceContainerLowest = colors.surface, surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface,
            surfaceContainerHighest = colors.background,
            outline = colors.accent.copy(alpha = 0.35f), outlineVariant = colors.accent.copy(alpha = 0.12f),
            inverseSurface = colors.text, inverseOnSurface = colors.surface, inversePrimary = colors.selected
        )
    }
    CompositionLocalProvider(LocalPlayerColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

internal fun PlayerColors.glassTint(dense: Boolean = false, selected: Boolean = false): Color =
    (if (selected) this.selected else if (dense) background else surface)
        .copy(alpha = if (dense) 1f else 0.08f)

internal fun glassHighlight(dense: Boolean = false): Color =
    Color.White.copy(alpha = if (dense) 1f else 0.20f)

/** Dense overlays block the screen beneath; ordinary cards retain translucent tint and highlights. */
internal fun Modifier.glassPanel(
    colors: PlayerColors,
    shape: Shape = RoundedCornerShape(20.dp),
    dense: Boolean = false,
    selected: Boolean = false,
    elevation: Dp = 0.dp,
    softHighlight: Boolean = false
): Modifier = shadow(elevation, shape, clip = false,
    ambientColor = colors.text.copy(alpha = 0.14f), spotColor = colors.text.copy(alpha = 0.14f))
    .clip(shape)
    .background(if (softHighlight) Brush.verticalGradient(listOf(
        Color.White.copy(alpha = 0.10f), colors.surface.copy(alpha = 0.04f)
    )) else Brush.linearGradient(listOf(
        glassHighlight(dense), colors.glassTint(dense, selected)
    )))
    .border(1.dp, if (softHighlight) Brush.verticalGradient(listOf(
        Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.12f)
    )) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.88f),
        colors.accent.copy(alpha = if (selected) 0.32f else 0.1f))), shape)

/** Soft color underneath glass; gradients are cached and do not add animation or bitmap work. */
internal fun Modifier.glassBackdrop(colors: PlayerColors): Modifier = drawWithCache {
    val base = Brush.linearGradient(listOf(colors.selected, colors.backdrop),
        start = Offset.Zero, end = Offset(size.width, size.height * 0.55f))
    val glow = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.58f), Color.Transparent),
        center = Offset(size.width * 0.12f, size.height * 0.18f), radius = size.maxDimension.coerceAtLeast(1f) * 0.7f)
    val reflection = Brush.linearGradient(
        0f to Color.Transparent, 0.34f to Color.Transparent,
        0.52f to Color.White.copy(alpha = 0.24f), 0.70f to Color.Transparent, 1f to Color.Transparent,
        start = Offset.Zero, end = Offset(size.width, size.height * 0.4f))
    onDrawBehind { drawRect(base); drawRect(glow); drawRect(reflection) }
}

@Composable
internal fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    dense: Boolean = false,
    selected: Boolean = false,
    elevation: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val colors = LocalPlayerColors.current
    if (!dense && LocalGlassOverlay.current) {
        // Menu rows sit on an opaque panel: another reflection/shadow layer looks like a stripe.
        Surface(modifier.border(1.dp, colors.accent.copy(alpha = 0.10f), shape),
            shape = shape, color = lerp(colors.surface, colors.background, 0.4f),
            contentColor = colors.text, tonalElevation = 0.dp, content = content)
        return
    }
    Surface(modifier.glassPanel(colors, shape, dense, selected, elevation),
        shape = shape, color = Color.Transparent, contentColor = colors.text) {
        CompositionLocalProvider(LocalGlassOverlay provides (dense || LocalGlassOverlay.current), content = content)
    }
}

@Composable
internal fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null
) {
    val colors = LocalPlayerColors.current
    val shape = RoundedCornerShape(28.dp)
    AlertDialog(onDismissRequest = onDismissRequest, title = title, text = text,
        confirmButton = confirmButton, dismissButton = dismissButton, shape = shape,
        modifier = Modifier.glassPanel(colors, shape, dense = true),
        containerColor = Color.Transparent, tonalElevation = 0.dp,
        titleContentColor = colors.text, textContentColor = colors.secondaryText,
        iconContentColor = colors.accent)
}
