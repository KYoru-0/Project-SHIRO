package com.example.shiro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TrackArtwork(val image: ImageBitmap, val colors: List<Color>)

@Composable
fun rememberTrackArtwork(uri: Uri?): TrackArtwork? {
    val context = LocalContext.current.applicationContext
    // Hold the current cover until the next load resolves, then crossfade directly to it (or no art).
    // LaunchedEffect cancellation prevents an older load from replacing the latest selection.
    var artwork by remember { mutableStateOf<TrackArtwork?>(null) }
    LaunchedEffect(uri) {
        artwork = if (uri == null) null else withContext(Dispatchers.IO) { loadTrackArtwork(context, uri) }
    }
    return artwork
}

private fun loadTrackArtwork(context: Context, uri: Uri): TrackArtwork? = try {
    val retriever = MediaMetadataRetriever()
    val bytes = try {
        retriever.setDataSource(context, uri)
        retriever.embeddedPicture
    } finally {
        retriever.release()
    }
    if (bytes == null) null else {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1024) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        bitmap?.let {
            val thumbnail = Bitmap.createScaledBitmap(it, 48, 48, true)
            val pixels = IntArray(48 * 48)
            thumbnail.getPixels(pixels, 0, 48, 0, 0, 48, 48)
            if (thumbnail !== it) thumbnail.recycle()
            val colors = artworkPalette(pixels).map { color -> Color(color) }
            // Display the decoded cover directly. The UI supplies opacity; no blur or tiny upscale.
            TrackArtwork(it.asImageBitmap(), colors)
        }
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    // Missing artwork, broken metadata or revoked file access must not interrupt playback.
    null
}
