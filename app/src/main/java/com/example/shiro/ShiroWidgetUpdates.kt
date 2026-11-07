package com.example.shiro

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.util.AtomicFile
import android.widget.RemoteViews
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Event-driven widget updates. One cached cover and two mascot poses; no background frame loop. */
@OptIn(UnstableApi::class)
internal object ShiroWidgetUpdates {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var update: Job? = null
    private var live: ShiroWidgetState? = null
    private var artwork: ByteArray? = null
    private var loadedUri: String? = null
    private var loadedData: ByteArray? = null
    private var cover: Cover? = null
    private val cats = arrayOfNulls<Bitmap>(2)
    private val cacheLock = Any()
    private data class Cover(val bitmap: Bitmap, val seed: Int)

    fun publish(context: Context, player: Player, recovering: Boolean = false) {
        val metadata = player.mediaMetadata
        val state = ShiroWidgetState(player.currentMediaItem?.mediaId.orEmpty(),
            metadata.title?.toString().orEmpty(), metadata.artist?.toString().orEmpty(),
            player.isPlaying, player.wantsPlayback() || (player.playWhenReady && recovering),
            player.playbackState == Player.STATE_BUFFERING || recovering,
            player.playerError != null && !recovering)
        val bytes = metadata.artworkData
        if (state == live && bytes.contentEquals(artwork)) return
        live = state
        artwork = bytes
        refresh(context)
    }

    fun stopped(context: Context) {
        live = live?.stopped()
        refresh(context)
    }

    fun connectionFailed(context: Context, finished: () -> Unit = {}) {
        live = (live ?: saved(context)).stopped().copy(error = true)
        refresh(context, finished)
    }

    private fun saved(context: Context): ShiroWidgetState {
        val prefs = context.getSharedPreferences("shiro_widget", Context.MODE_PRIVATE)
        val playback = context.getSharedPreferences("shiro_prefs", Context.MODE_PRIVATE)
        val uri = playback.getString("saved_track_uri", prefs.getString("uri", "")).orEmpty()
        val sameTrack = uri == prefs.getString("uri", "")
        // Never restore a stored "playing" flag after process death or reboot.
        return ShiroWidgetState(uri, playback.getString("saved_title", prefs.getString("title", "")).orEmpty(),
            if (sameTrack) prefs.getString("artist", "").orEmpty() else "")
    }

    fun refresh(context: Context, finished: () -> Unit = {}) {
        val app = context.applicationContext
        update?.cancel()
        update = scope.launch {
            try {
                val manager = AppWidgetManager.getInstance(app)
                val ids = manager.getAppWidgetIds(ComponentName(app, ShiroWidgetProvider::class.java))
                if (ids.isEmpty()) return@launch
                val state = live ?: saved(app)
                val bytes = artwork
                if (live != null) app.getSharedPreferences("shiro_widget", Context.MODE_PRIVATE).edit {
                    putString("uri", state.uri); putString("title", state.title); putString("artist", state.artist)
                }
                if (loadedUri != state.uri || !bytes.contentEquals(loadedData)) {
                    val next = try {
                        withContext(Dispatchers.IO) {
                            val bitmap = if (!state.hasTrack) null else if (bytes != null) decode(bytes) else readCover(app, state.uri)
                            ensureActive()
                            if (bitmap != null && bytes != null) saveCover(app, state.uri, bitmap)
                            bitmap?.let { Cover(it, palette(it)) }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null } // Missing/corrupt artwork cannot interrupt playback.
                    cover = next
                    loadedUri = state.uri
                    loadedData = bytes
                }
                manager.updateAppWidget(ids, views(app, state, cover))
            } finally { finished() }
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 256) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun palette(bitmap: Bitmap): Int {
        val thumb = bitmap.scale(48, 48)
        val pixels = IntArray(48 * 48)
        thumb.getPixels(pixels, 0, 48, 0, 0, 48, 48)
        if (thumb !== bitmap) thumb.recycle()
        return artworkPalette(pixels)[1]
    }

    // The key and image are one atomic payload, so interrupted writes cannot mix two songs.
    private fun cache(context: Context) = AtomicFile(File(context.cacheDir, "shiro-widget-cover"))
    private fun readCover(context: Context, uri: String): Bitmap? = synchronized(cacheLock) { try {
        DataInputStream(cache(context).openRead()).use {
            if (it.readUTF() == uri) BitmapFactory.decodeStream(it) else null
        }
    } catch (_: Exception) { null } }

    private fun saveCover(context: Context, uri: String, bitmap: Bitmap) = synchronized(cacheLock) {
        val file = cache(context)
        var stream: java.io.FileOutputStream? = null
        try {
            val output = file.startWrite()
            stream = output
            DataOutputStream(output).writeUTF(uri)
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            file.finishWrite(output)
        } catch (_: Exception) { stream?.let { file.failWrite(it) } }
    }

    private fun mascot(context: Context, playing: Boolean): Bitmap {
        val index = if (playing) 1 else 0
        cats[index]?.let { return it }
        val bitmap = createBitmap(192, 128)
        val canvas = Canvas(bitmap)
        canvas.rotate(if (playing) -9f else 0f, 96f, 100f)
        canvas.scale(if (playing) 0.97f else 1.04f, if (playing) 1.03f else 0.94f, 96f, 118f)
        ContextCompat.getDrawable(context, R.drawable.shiro_body_neutral)!!.mutate().apply {
            setBounds(0, 0, 192, 128); draw(canvas)
        }
        canvas.translate(5f, 0f)
        canvas.scale(0.6f, 0.6f, 96f, 128f * 0.5137f)
        ContextCompat.getDrawable(context, if (playing) R.drawable.shiro_eyes_neutral else R.drawable.shiro_eyes_closed)!!.mutate().apply {
            setBounds(0, 0, 192, 128); draw(canvas)
        }
        cats[index] = bitmap
        return bitmap
    }

    private fun views(context: Context, state: ShiroWidgetState, cover: Cover?): RemoteViews {
        val colors = playerColors(Color(cover?.seed ?: 0xFF0284C7.toInt()))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun control(action: WidgetAction) = PendingIntent.getBroadcast(context, action.ordinal + 1,
            Intent(context, ShiroWidgetProvider::class.java).setAction(WidgetControl.ACTION)
                .putExtra(WidgetControl.EXTRA, action.name), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return RemoteViews(context.packageName, R.layout.shiro_widget).apply {
            val status = when (state.status) {
                WidgetStatus.EMPTY -> R.string.widget_empty_status
                WidgetStatus.PLAYING -> R.string.widget_playing
                WidgetStatus.PAUSED -> R.string.widget_paused
                WidgetStatus.LOADING -> R.string.widget_loading
                WidgetStatus.WAITING -> R.string.widget_waiting
                WidgetStatus.ERROR -> R.string.widget_error
            }
            setTextViewText(R.id.widget_status, context.getString(status))
            setTextViewText(R.id.widget_title, state.title.ifBlank { context.getString(R.string.app_name) })
            setTextViewText(R.id.widget_artist, state.artist.ifBlank {
                context.getString(if (state.hasTrack) R.string.widget_open_player else R.string.widget_add_music)
            })
            val fontScale = context.resources.configuration.fontScale
            setInt(R.id.widget_title, "setMaxLines", if (fontScale > 1.25f) 1 else 2)
            setViewVisibility(R.id.widget_artist, if (fontScale > 1.6f) View.GONE else View.VISIBLE)
            setTextColor(R.id.widget_title, colors.text.toArgb())
            setTextColor(R.id.widget_artist, colors.secondaryText.toArgb())
            setTextColor(R.id.widget_status, colors.secondaryText.toArgb())
            setImageViewBitmap(R.id.widget_artwork, cover?.bitmap ?: mascot(context, false))
            setImageViewBitmap(R.id.widget_cat, mascot(context, state.playing))
            setContentDescription(R.id.widget_cat, context.getString(if (state.playing) R.string.widget_cat_awake else R.string.widget_cat_resting))
            val pause = state.primaryAction == WidgetAction.PAUSE
            setImageViewResource(R.id.widget_play, if (pause) R.drawable.ic_pause else R.drawable.ic_play)
            setContentDescription(R.id.widget_play, context.getString(if (pause) R.string.widget_pause else R.string.widget_play))
            for (id in listOf(R.id.widget_previous, R.id.widget_play, R.id.widget_next)) {
                setInt(id, "setColorFilter", colors.accent.toArgb())
            }
            for (id in listOf(R.id.widget_previous, R.id.widget_next)) {
                setBoolean(id, "setEnabled", state.hasTrack)
                setFloat(id, "setAlpha", if (state.hasTrack) 1f else 0.35f)
            }
            if (Build.VERSION.SDK_INT >= 31) setColorStateList(android.R.id.background,
                "setBackgroundTintList", ColorStateList.valueOf(colors.background.toArgb()))
            setOnClickPendingIntent(R.id.widget_metadata, open)
            setOnClickPendingIntent(R.id.widget_artwork, open)
            setOnClickPendingIntent(R.id.widget_cat, open)
            setOnClickPendingIntent(R.id.widget_play, if (state.hasTrack) control(state.primaryAction) else open)
            setOnClickPendingIntent(R.id.widget_previous, control(WidgetAction.PREVIOUS))
            setOnClickPendingIntent(R.id.widget_next, control(WidgetAction.NEXT))
        }
    }
}
