package com.example.shiro

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

data class PlayerColors(
    val background: Color, val surface: Color, val accent: Color,
    val text: Color, val secondaryText: Color, val selected: Color, val backdrop: Color,
    val artworkSeed: Color
)

internal fun playerColors(seed: Color) = PlayerColors(
    background = lerp(seed, Color.White, 0.87f),
    surface = lerp(seed, Color.White, 0.96f),
    accent = lerp(seed, Color.Black, 0.5f),
    text = lerp(seed, Color.Black, 0.68f),
    secondaryText = lerp(seed, Color.Black, 0.55f),
    selected = lerp(seed, Color.White, 0.65f),
    backdrop = lerp(seed, Color.White, 0.42f),
    artworkSeed = seed
)

@Composable
fun rememberPlayerColors(artwork: TrackArtwork?): State<PlayerColors> {
    val seed = animateColorAsState(artwork?.colors?.get(1) ?: Color(0xFF0284C7),
        tween(600), label = "Player artwork colors")
    // Theme consumers read this state, so each color frame does not recompose the screen owner.
    return remember(seed) { derivedStateOf { playerColors(seed.value) } }
}
