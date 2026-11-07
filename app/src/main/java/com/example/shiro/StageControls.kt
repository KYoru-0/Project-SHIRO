package com.example.shiro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp

/** Glass control with the same hit area and accessibility behavior as IconButton. */
@Composable
fun StageControlButton(
    onClick: () -> Unit,
    enabled: Boolean,
    colors: PlayerColors,
    selected: Boolean? = null,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(48.dp)
            .semantics { if (selected != null) this.selected = selected }
            .glassPressFeedback(interactionSource, enabled, CircleShape)
            .glassPanel(colors, CircleShape, selected = selected == true && enabled,
                elevation = if (pressed || !enabled) 0.dp else 2.dp)
    ) { content() }
}

@Composable
fun StagePlayButton(onClick: () -> Unit, enabled: Boolean, colors: PlayerColors, content: @Composable () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        modifier = Modifier.size(60.dp).glassPressFeedback(interactionSource, enabled, CircleShape)
            .shadow(if (pressed || !enabled) 2.dp else 6.dp, CircleShape,
                ambientColor = colors.text.copy(alpha = 0.2f), spotColor = colors.text.copy(alpha = 0.2f))
            .background(Brush.linearGradient(listOf(colors.accent, lerp(colors.accent, Color.Black, 0.16f))
                .map { it.copy(alpha = if (enabled) 1f else 0.4f) }), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
            .glassButtonFinish(CircleShape)
    ) { content() }
}
