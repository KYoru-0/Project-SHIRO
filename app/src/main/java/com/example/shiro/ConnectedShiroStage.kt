package com.example.shiro

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Keeps rapid spectrum updates inside the scene, away from playlist/player controls. */
@OptIn(UnstableApi::class)
@Composable
fun ConnectedShiroStage(
    controller: MediaController,
    visible: Boolean,
    isPlaying: Boolean,
    isBlinking: Boolean,
    isSleepy: Boolean,
    leftEyeColor: Color,
    rightEyeColor: Color,
    artwork: TrackArtwork? = null,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var frame by remember { mutableStateOf(SpectrumFrame(FloatArray(SpectrumFrame.BAND_COUNT), 0f)) }
    var sleepTarget by remember { mutableFloatStateOf(0f) }
    var foreground by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner, controller, visible) {
        if (!visible) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            foreground = true
            PlaybackSpectrum.setVisible(true)
            try {
                launch { PlaybackSpectrum.frames.collect { frame = it } }
                while (isActive) {
                    val extras = controller.sessionExtras
                    val timerActive = extras.getLong(SleepTimer.DEADLINE) > 0L ||
                        extras.getBoolean(SleepTimer.END_OF_TRACK)
                    sleepTarget = when {
                        extras.getBoolean(SleepTimer.FINISHED) -> 1f
                        timerActive -> 0.2f + 0.8f * extras.getFloat(SleepTimer.FADE_PROGRESS).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    delay(150L)
                }
            } finally {
                PlaybackSpectrum.setVisible(false)
                foreground = false
                frame = SpectrumFrame(FloatArray(24), 0f)
            }
        }
    }

    ShiroStage(
        isPlaying = isPlaying && visible && foreground,
        isBlinking = isBlinking,
        isSleepy = isSleepy || sleepTarget >= 0.99f,
        sleepProgress = sleepTarget,
        leftEyeColor = leftEyeColor,
        rightEyeColor = rightEyeColor,
        spectrum = frame.bands,
        artwork = artwork,
        isVisible = visible && foreground,
        modifier = modifier
    )
}
