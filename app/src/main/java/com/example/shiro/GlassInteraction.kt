package com.example.shiro

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** The native control owns clicks, cancellation, focus and ripple; this only supplies its glass response. */
@Composable
internal fun Modifier.glassPressFeedback(
    interactions: InteractionSource,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(18.dp)
): Modifier {
    val pressed by interactions.collectIsPressedAsState()
    val pressure = animateFloatAsState(if (enabled && pressed) 1f else 0f,
        tween(if (pressed) 90 else 180), label = "Glass press")
    return graphicsLayer {
        val amount = pressure.value
        scaleX = 1f - amount * 0.04f
        scaleY = scaleX
        translationY = amount * 0.75.dp.toPx()
    }.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val shine = Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.08f)),
            start = Offset.Zero, end = Offset(size.width, size.height))
        val edge = Stroke(1.dp.toPx())
        onDrawWithContent {
            drawContent()
            val amount = pressure.value
            if (amount > 0f) {
                drawOutline(outline, shine, alpha = amount * 0.24f)
                drawOutline(outline, Color.White, alpha = amount * 0.65f, style = edge)
            }
        }
    }
}

/** A stationary reflection, rather than an endlessly animated shimmer. */
internal fun Modifier.glassButtonFinish(shape: Shape): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val shine = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.24f),
        0.48f to Color.White.copy(alpha = 0.025f), 1f to Color.Transparent)
    val rim = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.08f)))
    val edge = Stroke(1.dp.toPx())
    onDrawWithContent {
        drawContent()
        drawOutline(outline, shine)
        drawOutline(outline, rim, style = edge)
    }
}

@Composable
internal fun Modifier.glassClickable(
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(18.dp),
    onClick: () -> Unit
): Modifier {
    val interactions = remember { MutableInteractionSource() }
    return glassPressFeedback(interactions, enabled, shape)
        .clickable(interactions, LocalIndication.current, enabled, role = Role.Button, onClick = onClick)
}

@Composable
internal fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(18.dp),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit
) {
    val interactions = remember { MutableInteractionSource() }
    Button(onClick, enabled = enabled, shape = shape, colors = colors, interactionSource = interactions,
        modifier = Modifier.glassPressFeedback(interactions, enabled, shape).then(modifier)
            .heightIn(min = 48.dp).glassButtonFinish(shape),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
        content = content)
}

@Composable
internal fun GlassTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interactions = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    TextButton(onClick, enabled = enabled, shape = shape, contentPadding = contentPadding,
        interactionSource = interactions,
        modifier = Modifier.glassPressFeedback(interactions, enabled, shape).then(modifier).heightIn(min = 48.dp),
        content = content)
}

@Composable
internal fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactions = remember { MutableInteractionSource() }
    IconButton(onClick, enabled = enabled, interactionSource = interactions,
        modifier = Modifier.glassPressFeedback(interactions, enabled, CircleShape).then(modifier), content = content)
}

@Composable
internal fun GlassFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    colors: SelectableChipColors,
    shape: Shape = RoundedCornerShape(16.dp),
    label: @Composable () -> Unit
) {
    val interactions = remember { MutableInteractionSource() }
    FilterChip(selected, onClick, label, colors = colors, shape = shape, interactionSource = interactions,
        modifier = Modifier.glassPressFeedback(interactions, shape = shape).heightIn(min = 48.dp))
}

@Composable
internal fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, colors: SwitchColors) {
    val interactions = remember { MutableInteractionSource() }
    Switch(checked, onCheckedChange, colors = colors, interactionSource = interactions,
        modifier = Modifier.glassPressFeedback(interactions, shape = CircleShape))
}
