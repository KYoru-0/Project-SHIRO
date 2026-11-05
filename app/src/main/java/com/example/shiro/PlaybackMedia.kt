package com.example.shiro

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController

/** Buffering/suppressed playback still needs a Pause action; errors and ended queues need Play. */
fun Player.wantsPlayback(): Boolean = playWhenReady &&
    (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY)

/** Keep Pause available during the short delay before recovering a failed track. */
@androidx.annotation.OptIn(UnstableApi::class)
fun MediaController.wantsPlayback(): Boolean = (this as Player).wantsPlayback() ||
    (playWhenReady && sessionExtras.getBoolean(PlaybackRecovery.PENDING))

/** Stable URI identity prevents two files with the same name restoring the wrong song. */
fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(uri.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setComposer(composer)
            .setExtras(Bundle().apply { putString("file_name", fileName) })
            .build()
    )
    .build()

fun MediaItem.toTrack(): Track = Track(
    uri = Uri.parse(mediaId),
    title = mediaMetadata.title?.toString().orEmpty(),
    fileName = mediaMetadata.extras?.getString("file_name").orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    composer = mediaMetadata.composer?.toString().orEmpty()
)
