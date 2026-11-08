package com.example.shiro

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

/** Lives for this Activity session, rather than for a pager page or a foreground interval. */
class ShiroStageState {
    internal val reveal = mutableFloatStateOf(0f)
    internal val circularDrift = CircularDrift()
    internal val backdropMotion = MusicBackdropMotion()
    internal val backdrop = mutableStateOf(MusicBackdropFrame())
    internal val displayed = mutableStateOf(FloatArray(SpectrumFrame.BAND_COUNT))
    internal val audioFrame = mutableStateOf(SpectrumFrame.unavailable())
    internal val peakTracker = SpectrumPeaks(SpectrumFrame.BAND_COUNT)
    internal val peaks = mutableStateOf(FloatArray(SpectrumFrame.BAND_COUNT))
    private var bodies: List<WaveCatBody>? = null
    private var width = 0f
    private var height = 0f

    internal fun bodiesFor(width: Float, height: Float, radius: Float): List<WaveCatBody> {
        val existing = bodies
        if (existing == null) {
            return createShiroFamilies(width, radius).also {
                positionShirosForDrop(it, width, height, radius)
                bodies = it; this.width = width; this.height = height
            }
        }
        if (width > 0f && height > 0f && (this.width != width || this.height != height)) {
            for (body in existing) {
                body.x *= width / this.width.coerceAtLeast(1f)
                body.height *= height / this.height.coerceAtLeast(1f)
            }
            this.width = width; this.height = height
        }
        return existing
    }
}

class ShiroSessionViewModel : ViewModel() {
    val stage = ShiroStageState()
}
