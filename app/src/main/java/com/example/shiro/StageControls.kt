package com.example.shiro

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp

/** A raised control with the same hit area and accessibility behavior as IconButton. */
@Composable
fun StageControlButton(
    onClick: () -> Unit,
    enabled: Boolean,
    selected: Boolean? = null,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(100), label = "Control press")
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(48.dp)
            .semantics { if (selected != null) this.selected = selected }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = if (pressed) 1.dp.toPx() else 0f
            }
            .shadow(if (pressed || !enabled) 1.dp else 4.dp, CircleShape,
                ambientColor = Color(0xFF5488AA), spotColor = Color(0xFF5488AA))
            .background(
                Brush.verticalGradient(
                    if (selected == true && enabled) listOf(Color(0xFFF5FCFF), Color(0xFFB9E7FA))
                    else listOf(Color(0xFFFEFFFF), Color(0xFFE2EFF7))
                ), CircleShape
            )
    ) { content() }
}

@Composable
fun StagePlayButton(onClick: () -> Unit, enabled: Boolean, content: @Composable () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(100), label = "Play press")
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentColor,
            disabledContainerColor = AccentColor.copy(alpha = 0.4f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 2.dp),
        modifier = Modifier.size(60.dp).graphicsLayer { scaleX = scale; scaleY = scale }
    ) { content() }
}
