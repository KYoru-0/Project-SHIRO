package com.example.shiro

internal enum class WidgetStatus { EMPTY, PLAYING, PAUSED, LOADING, WAITING, ERROR }
internal enum class WidgetAction { PLAY, PAUSE, PREVIOUS, NEXT }

/** Playback intent and audible playback stay separate, just as they do in the main player. */
internal data class ShiroWidgetState(
    val uri: String = "", val title: String = "", val artist: String = "",
    val playing: Boolean = false, val wantsPlayback: Boolean = false,
    val buffering: Boolean = false, val error: Boolean = false
) {
    val hasTrack get() = uri.isNotEmpty()
    val status get() = when {
        !hasTrack -> WidgetStatus.EMPTY
        error -> WidgetStatus.ERROR
        playing -> WidgetStatus.PLAYING
        buffering && wantsPlayback -> WidgetStatus.LOADING
        wantsPlayback -> WidgetStatus.WAITING
        else -> WidgetStatus.PAUSED
    }
    val primaryAction get() = if (hasTrack && wantsPlayback && !error) WidgetAction.PAUSE else WidgetAction.PLAY
    fun stopped() = copy(playing = false, wantsPlayback = false, buffering = false)
}
