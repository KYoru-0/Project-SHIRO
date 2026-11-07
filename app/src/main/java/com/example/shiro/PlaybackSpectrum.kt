package com.example.shiro

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** Immutable snapshots: consumers must not modify [bands]. Levels are normalized to 0..1. */
data class SpectrumFrame(val bands: FloatArray, val bassEnergy: Float) {
    companion object {
        const val BAND_COUNT = 24
        fun silence() = SpectrumFrame(FloatArray(BAND_COUNT), 0f)
    }
}

/** In-process bridge between the playback service and the visible music scene. */
object PlaybackSpectrum {
    private val mutableFrames = MutableStateFlow(SpectrumFrame.silence())
    val frames: StateFlow<SpectrumFrame> = mutableFrames.asStateFlow()

    @Volatile internal var visible = false
        private set
    internal val generation = AtomicLong()
    @Volatile internal var onVisibilityChanged: (() -> Unit)? = null

    fun setVisible(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible
        clear()
        onVisibilityChanged?.invoke()
    }

    internal fun clear() {
        generation.incrementAndGet()
        mutableFrames.value = SpectrumFrame.silence()
    }

    internal fun publish(frame: SpectrumFrame) {
        if (visible) mutableFrames.value = frame
    }
}
