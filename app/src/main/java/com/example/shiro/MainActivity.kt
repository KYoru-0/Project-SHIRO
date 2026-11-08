package com.example.shiro

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.animation.Crossfade
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.example.shiro.ui.theme.ProjectSHIROTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.lifecycle.repeatOnLifecycle
import kotlin.random.Random
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val stageState = ViewModelProvider(this)[ShiroSessionViewModel::class.java].stage
        this.setContent {
            ProjectSHIROTheme {
                ShiroGlassTheme { ShiroScreen(stageState) }
            }
        }
    }
}

enum class SquishDirection {
    LEFT, CENTER, RIGHT
}

enum class DanceMode {
    SWAY, SQUISH, OFF
}

enum class Screen {
    MAIN, CUSTOMIZE, PLAYLIST
}

data class Track(
    val uri: Uri,
    val title: String,
    val fileName: String,
    val artist: String = "",
    val composer: String = "",
    val sourceSize: Long? = null,
    val sourceModified: Long? = null
)

data class FolderItem(
    val uri: Uri,
    val name: String
)

fun savePreferences(
    context: Context,
    folders: List<FolderItem>,
    isHetero: Boolean,
    leftColor: Color,
    rightColor: Color,
    bpm: Float,
    danceMode: DanceMode
) {
    val prefs = context.getSharedPreferences("shiro_prefs", Context.MODE_PRIVATE)
    val folderData = folders.joinToString("|||") { "${it.uri}||${it.name}" }

    prefs.edit()
        .putString("folder_data", folderData)
        .putBoolean("is_hetero", isHetero)
        .putInt("left_eye_color", leftColor.toArgb())
        .putInt("right_eye_color", rightColor.toArgb())
        .putFloat("bpm", bpm)
        .putString("dance_mode", danceMode.name)
        .apply()
}

fun clearSavedTrackPreferences(context: Context) {
    val prefs = context.getSharedPreferences("shiro_prefs", Context.MODE_PRIVATE)
    prefs.edit()
        .putString("saved_track_uri", "")
        .putString("saved_file_name", "")
        .putString("saved_title", "")
        .putLong("saved_position_ms", 0L)
        .putLong("saved_duration_ms", 0L)
        .commit()
}

fun extractTrackMetadata(context: Context, uri: Uri, fallbackName: String, retriever: MediaMetadataRetriever,
    sourceSize: Long? = null, sourceModified: Long? = null): Track {
    var title: String? = null
    var artist = ""
    var composer = ""
    var metadataRead = false

    try {
        retriever.setDataSource(context, uri)
        title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val extractedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        val albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
        artist = when {
            !extractedArtist.isNullOrBlank() -> extractedArtist.trim()
            !albumArtist.isNullOrBlank() -> albumArtist.trim()
            else -> ""
        }
        composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)?.trim() ?: ""
        metadataRead = true
    } catch (_: Exception) {}

    val cleanTitle = if (!title.isNullOrBlank()) title.trim() else fallbackName.substringBeforeLast(".")

    return Track(
        uri = uri,
        title = cleanTitle,
        fileName = fallbackName,
        artist = artist,
        composer = composer,
        sourceSize = sourceSize.takeIf { metadataRead },
        sourceModified = sourceModified.takeIf { metadataRead }
    )
}

fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = cursor.getString(index)
                    }
                }
            }
        } catch (_: Exception) {}
    }
    if (name == null) {
        name = uri.path?.substringAfterLast('/')
    }
    return name
}

fun getDisplayPathFromUri(uri: Uri): String {
    try {
        val docId = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (_: Exception) {
            try {
                DocumentsContract.getDocumentId(uri)
            } catch (_: Exception) {
                null
            }
        } ?: uri.lastPathSegment ?: uri.path ?: uri.toString()

        val decodedDocId = Uri.decode(docId)
        if (decodedDocId.startsWith("primary:")) {
            val relativePath = decodedDocId.substringAfter("primary:").trimStart('/')
            return if (relativePath.isEmpty()) "/storage/emulated/0" else "/storage/emulated/0/$relativePath"
        } else if (":" in decodedDocId) {
            val parts = decodedDocId.split(":", limit = 2)
            val volume = parts[0]
            val rel = parts[1].trimStart('/')
            return if (rel.isEmpty()) "/storage/$volume" else "/storage/$volume/$rel"
        }
        return Uri.decode(uri.path ?: docId)
    } catch (_: Exception) {
        return Uri.decode(uri.toString())
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

@Composable
fun ColorSpectrumBar(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val spectrumBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color.Red,
                Color.Yellow,
                Color.Green,
                Color.Cyan,
                Color.Blue,
                Color.Magenta,
                Color.Red
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(spectrumBrush)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    val hue = fraction * 360f
                    val hsv = floatArrayOf(hue, 0.85f, 0.85f)
                    onColorSelected(Color(android.graphics.Color.HSVToColor(hsv)))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    val hue = fraction * 360f
                    val hsv = floatArrayOf(hue, 0.85f, 0.85f)
                    onColorSelected(Color(android.graphics.Color.HSVToColor(hsv)))
                }
            }
    )
}

@OptIn(UnstableApi::class)
@Composable
fun ShiroScreen(exoPlayer: MediaController, stageState: ShiroStageState = remember { ShiroStageState() }) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

    var currentScreen by remember { mutableStateOf(Screen.MAIN) }

    val cachedTracks = remember(exoPlayer) {
        if (exoPlayer.mediaItemCount > 0) {
            List(exoPlayer.mediaItemCount) { exoPlayer.getMediaItemAt(it).toTrack() }
        } else loadPlaylistCache(context)
    }
    val initialIndex = remember {
        if (cachedTracks.isNotEmpty()) {
            val idx = cachedTracks.indexOfFirst { it.uri.toString() == exoPlayer.currentMediaItem?.mediaId }
            if (idx != -1) idx else 0
        } else -1
    }

    val tracks = remember { mutableStateListOf<Track>().apply { addAll(cachedTracks) } }
    val inputFolders = remember { mutableStateListOf<FolderItem>() }
    var currentTrackIndex by remember { mutableIntStateOf(initialIndex) }
    val artwork = rememberTrackArtwork(tracks.getOrNull(currentTrackIndex)?.uri)
    val playerColorState = rememberPlayerColors(artwork)
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var showPause by remember { mutableStateOf(exoPlayer.wantsPlayback()) }
    var isBuffering by remember { mutableStateOf(exoPlayer.playbackState == Player.STATE_BUFFERING) }
    var playbackPosition by remember { mutableLongStateOf(exoPlayer.currentPosition.coerceAtLeast(0L)) }
    var duration by remember { mutableLongStateOf(exoPlayer.duration.coerceAtLeast(0L)) }

    var isShuffleOn by remember { mutableStateOf(exoPlayer.shuffleModeEnabled) }
    var isRepeatOneOn by remember { mutableStateOf(exoPlayer.repeatMode == Player.REPEAT_MODE_ONE) }

    var danceMode by remember { mutableStateOf(DanceMode.SWAY) }
    var bpm by remember { mutableFloatStateOf(120f) }

    var isHeterochromia by remember { mutableStateOf(false) }
    var leftEyeColor by remember { mutableStateOf(Color(0xFF201C1C)) }
    var rightEyeColor by remember { mutableStateOf(Color(0xFF201C1C)) }

    var isBlinking by remember { mutableStateOf(false) }
    var isSleepy by remember(exoPlayer) {
        mutableStateOf(exoPlayer.sessionExtras.getLong(SleepTimer.DEADLINE) > 0L ||
            exoPlayer.sessionExtras.getBoolean(SleepTimer.END_OF_TRACK) ||
            exoPlayer.sessionExtras.getBoolean(SleepTimer.FINISHED))
    }
    var squishDirection by remember { mutableStateOf(SquishDirection.CENTER) }

    var isMenuOpen by remember { mutableStateOf(false) }
    var visualizerMode by remember {
        mutableStateOf(VisualizerMode.fromPreference(
            context.getSharedPreferences("shiro_prefs", Context.MODE_PRIVATE).getString("visualizer_mode", null)))
    }
    var visualizerLayout by remember {
        val prefs = context.getSharedPreferences("shiro_prefs", Context.MODE_PRIVATE)
        mutableStateOf(VisualizerLayout.fromPreference(prefs.getString("visualizer_layout", null),
            prefs.getString("visualizer_mode", null)))
    }
    var isInputsPopupOpen by remember { mutableStateOf(false) }
    var favoriteIds by remember { mutableStateOf(loadFavoriteIds(context)) }
    var unavailableIds by remember(exoPlayer) {
        mutableStateOf(exoPlayer.sessionExtras.getStringArrayList(PlaybackRecovery.UNAVAILABLE_IDS).orEmpty().toSet())
    }
    var recoveryMessage by remember(exoPlayer) {
        mutableStateOf(exoPlayer.sessionExtras.getString(PlaybackRecovery.MESSAGE).orEmpty())
    }
    fun toggleFavorite(track: Track) {
        val id = track.uri.toString()
        favoriteIds = if (id in favoriteIds) favoriteIds - id else favoriteIds + id
        saveFavoriteIds(context, favoriteIds)
    }
    var scanError by remember { mutableStateOf<String?>(null) }
    var cacheError by remember { mutableStateOf(false) }
    val cacheSaveLock = remember { Mutex() }
    var cacheSaveVersion by remember { mutableLongStateOf(0L) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var scanGeneration by remember { mutableLongStateOf(0L) }

    var isRestored by remember { mutableStateOf(false) }

    val isAutoSwaying = isPlaying && danceMode == DanceMode.SWAY

    val squishProgress = remember { Animatable(0f) }
    var squishJob by remember { mutableStateOf<Job?>(null) }

    fun triggerSquish(direction: SquishDirection = SquishDirection.CENTER) {
        squishJob?.cancel()
        squishJob = scope.launch {
            if (squishProgress.value > 0.01f) {
                squishProgress.animateTo(0f, tween(40, easing = FastOutSlowInEasing))
            }
            squishDirection = direction
            squishProgress.animateTo(1f, tween(70, easing = FastOutSlowInEasing))
            squishProgress.animateTo(0f, tween(110, easing = FastOutSlowInEasing))
        }
    }

    val permissionToRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_AUDIO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun persistPlaylist() {
        val snapshot = tracks.toList()
        val version = ++cacheSaveVersion
        scope.launch {
            cacheSaveLock.withLock {
                if (version != cacheSaveVersion) return@withLock
                val saved = withContext(Dispatchers.IO) { savePlaylistCache(context, snapshot) }
                if (version == cacheSaveVersion) cacheError = !saved
            }
        }
    }

    fun restoreTrackPosition(allScannedTracks: List<Track>) {
        val prefs = context.getSharedPreferences("shiro_prefs", Context.MODE_PRIVATE)
        val savedFileName = prefs.getString("saved_file_name", "") ?: ""
        val savedPositionMs = prefs.getLong("saved_position_ms", 0L)

        if (allScannedTracks.isNotEmpty()) {
            val liveId = exoPlayer.currentMediaItem?.mediaId
            val liveIndex = allScannedTracks.indexOfFirst { it.uri.toString() == liveId }
            val savedUri = prefs.getString("saved_track_uri", "")
            val savedIndex = if (!savedUri.isNullOrBlank()) {
                allScannedTracks.indexOfFirst { it.uri.toString() == savedUri }.takeIf { it >= 0 }
            } else {
                // Filename matching is only for legacy saves, never for a removed URI.
                allScannedTracks.indexOfFirst { it.fileName == savedFileName }.takeIf { it >= 0 }
            }
            val index = liveIndex.takeIf { it >= 0 } ?: savedIndex ?: 0
            val position = if (liveIndex >= 0) exoPlayer.currentPosition else if (savedIndex != null) savedPositionMs else 0L
            val sameQueue = exoPlayer.mediaItemCount == allScannedTracks.size && allScannedTracks.indices.all {
                exoPlayer.getMediaItemAt(it).mediaId == allScannedTracks[it].uri.toString()
            }
            if (!sameQueue) {
                // Keep the live track and position when folders are added or rescanned.
                if (liveIndex < 0) exoPlayer.pause()
                exoPlayer.setMediaItems(allScannedTracks.map { it.toMediaItem() }, index, position.coerceAtLeast(0L))
                exoPlayer.prepare()
            } else {
                // Refresh notification metadata without resetting the song or shuffle order.
                allScannedTracks.forEachIndexed { itemIndex, track ->
                    val metadata = exoPlayer.getMediaItemAt(itemIndex).mediaMetadata
                    // File fingerprints belong to the scan cache, not to playback metadata updates.
                    if (metadata.title?.toString().orEmpty() != track.title ||
                        metadata.artist?.toString().orEmpty() != track.artist ||
                        metadata.composer?.toString().orEmpty() != track.composer ||
                        metadata.extras?.getString("file_name").orEmpty() != track.fileName) {
                        exoPlayer.replaceMediaItem(itemIndex, track.toMediaItem())
                    }
                }
            }
            currentTrackIndex = index
            playbackPosition = position.coerceAtLeast(0L)
        } else {
            currentTrackIndex = -1
            playbackPosition = 0L
            duration = 0L
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            clearSavedTrackPreferences(context)
        }
        isRestored = true
    }

    // Every entry point uses a folder snapshot and the same generation guard.
    // Scan failures preserve retained tracks; explicit folder removals take effect immediately.
    fun reloadMusicList() {
        val generation = ++scanGeneration
        scanJob?.cancel()
        scanError = null
        val folders = inputFolders.toList()
        if (folders.isEmpty()) {
            tracks.clear()
            restoreTrackPosition(emptyList())
            persistPlaylist()
            return
        }
        val retainedTracks = tracks.filter { track -> folders.any { track.belongsToFolder(it.uri) } }
        if (retainedTracks.size != tracks.size) {
            tracks.clear()
            tracks.addAll(retainedTracks)
            restoreTrackPosition(tracks)
            persistPlaylist()
        }
        val trackSnapshot = tracks.toList()
        scanJob = scope.launch {
            var scanningFolder = ""
            val scannedTracks = try {
                withContext(Dispatchers.IO) {
                    val cachedByUri = loadPlaylistCache(context).associateBy { it.uri.toString() }.toMutableMap()
                    for (track in trackSnapshot) {
                        val key = track.uri.toString()
                        if ((track.sourceSize != null && track.sourceModified != null) || key !in cachedByUri) {
                            cachedByUri[key] = track
                        }
                    }
                    val result = mutableListOf<Track>()
                    for (folder in folders) {
                        scanningFolder = folder.name
                        result.addAll(scanAudioFilesFromFolder(context, folder.uri, cachedByUri))
                    }
                    result.distinctBy { it.uri }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == scanGeneration && folders == inputFolders.toList()) {
                    scanError = "Couldn't read $scanningFolder. Existing tracks from selected folders have been kept. " +
                        "Reconnect the storage or reselect the folder in Inputs, then retry."
                }
                return@launch
            }
            if (generation != scanGeneration || folders != inputFolders.toList()) return@launch
            // No suspension between checking the generation and committing the result.
            tracks.clear()
            tracks.addAll(scannedTracks)
            restoreTrackPosition(tracks)
            persistPlaylist()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) reloadMusicList()
        else scanError = "Music access wasn't granted. Your previous playlist is unchanged. " +
            "Reselect a folder in Inputs or retry."
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("shiro_prefs", Context.MODE_PRIVATE)

        val folderData = prefs.getString("folder_data", "") ?: ""
        if (folderData.isNotBlank()) {
            val loadedFolders = folderData.split("|||").mapNotNull { item ->
                val parts = item.split("||")
                if (parts.size == 2) {
                    val uri = Uri.parse(parts[0])
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}
                    FolderItem(uri = uri, name = parts[1])
                } else null
            }
            inputFolders.clear()
            inputFolders.addAll(loadedFolders)
        }

        isHeterochromia = prefs.getBoolean("is_hetero", false)
        leftEyeColor = Color(prefs.getInt("left_eye_color", Color(0xFF201C1C).toArgb()))
        rightEyeColor = Color(prefs.getInt("right_eye_color", Color(0xFF201C1C).toArgb()))
        bpm = prefs.getFloat("bpm", 120f)
        val danceModeName = prefs.getString("dance_mode", DanceMode.SWAY.name) ?: DanceMode.SWAY.name
        danceMode = try { DanceMode.valueOf(danceModeName) } catch (_: Exception) { DanceMode.SWAY }
        isShuffleOn = exoPlayer.shuffleModeEnabled
        isRepeatOneOn = exoPlayer.repeatMode == Player.REPEAT_MODE_ONE
        isRestored = true

        if (inputFolders.isEmpty()) {
            reloadMusicList()
        } else {
            if (tracks.isNotEmpty()) restoreTrackPosition(tracks)
            if (ContextCompat.checkSelfPermission(context, permissionToRequest) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                reloadMusicList()
            } else {
                permissionLauncher.launch(permissionToRequest)
            }
        }
    }

    // The service alone persists playback; the screen only saves appearance and input folders.
    LaunchedEffect(isRestored, inputFolders.toList(), isHeterochromia, leftEyeColor, rightEyeColor, bpm, danceMode) {
        if (isRestored) {
            savePreferences(context, inputFolders.toList(), isHeterochromia, leftEyeColor, rightEyeColor, bpm, danceMode)
        }
    }

    val characterVisible = currentScreen == Screen.CUSTOMIZE ||
        (pagerState.currentPage == 0 && !isMenuOpen && !isInputsPopupOpen)
    LaunchedEffect(lifecycleOwner, isPlaying, characterVisible) {
        if (!characterVisible) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!isPlaying) {
                try {
                    while (isActive) {
                        delay(Random.nextLong(3_000L, 7_001L))
                        isBlinking = true
                        delay(130L)
                        isBlinking = false
                    }
                } finally {
                    isBlinking = false
                }
            }
        }
    }

    fun playTrack(index: Int) {
        if (index in tracks.indices) {
            restoreTrackPosition(tracks)
            exoPlayer.seekToDefaultPosition(index)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    DisposableEffect(exoPlayer) {
        fun updateFromPlayer() {
            currentTrackIndex = tracks.indexOfFirst { it.uri.toString() == exoPlayer.currentMediaItem?.mediaId }
            isPlaying = exoPlayer.isPlaying
            showPause = exoPlayer.wantsPlayback()
            isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING
            isShuffleOn = exoPlayer.shuffleModeEnabled
            isRepeatOneOn = exoPlayer.repeatMode == Player.REPEAT_MODE_ONE
            playbackPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            duration = exoPlayer.duration.coerceAtLeast(0L)
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                updateFromPlayer()
            }
        }
        exoPlayer.addListener(listener)
        updateFromPlayer()
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(lifecycleOwner, exoPlayer) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                playbackPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                duration = exoPlayer.duration.coerceAtLeast(0L)
                showPause = exoPlayer.wantsPlayback()
                unavailableIds = exoPlayer.sessionExtras.getStringArrayList(PlaybackRecovery.UNAVAILABLE_IDS).orEmpty().toSet()
                recoveryMessage = exoPlayer.sessionExtras.getString(PlaybackRecovery.MESSAGE).orEmpty()
                isSleepy = exoPlayer.sessionExtras.getLong(SleepTimer.DEADLINE) > 0L ||
                    exoPlayer.sessionExtras.getBoolean(SleepTimer.END_OF_TRACK) ||
                    exoPlayer.sessionExtras.getBoolean(SleepTimer.FINISHED)
                delay(250L)
            }
        }
    }

    fun addFolderUri(uri: Uri) {
        val folderName = DocumentFile.fromTreeUri(context, uri)?.name
            ?: getFileNameFromUri(context, uri)
            ?: "Folder ${inputFolders.size + 1}"

        if (inputFolders.none { it.uri == uri }) {
            inputFolders.add(FolderItem(uri = uri, name = folderName))
        }

        reloadMusicList()
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            addFolderUri(uri)
        }
    }





    ShiroGlassTheme(playerColorState) {
    Box(Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().then(
            if (currentScreen == Screen.CUSTOMIZE) Modifier.clearAndSetSemantics {} else Modifier
        )) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (page == 0) {
                  val playerColors = LocalPlayerColors.current
                  BoxWithConstraints(Modifier.fillMaxSize().background(
                      Brush.verticalGradient(listOf(playerColors.selected, playerColors.backdrop))
                  )) {
                    // Short/landscape windows scroll instead of collapsing the cat to zero height.
                    val extraTextHeight = 160.dp * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)
                    val pageHeight = maxOf(maxHeight, 780.dp + extraTextHeight)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .height(pageHeight)
                            .navigationBarsPadding()
                            .padding(bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                      // This shared container ends at the song details, so artwork cannot spill below it.
                      Box(Modifier.weight(1f).fillMaxWidth()) {
                        Crossfade(targetState = artwork, animationSpec = tween(600),
                            modifier = Modifier.matchParentSize(), label = "Album cover crossfade") { cover ->
                            if (cover != null) Image(
                                bitmap = cover.image,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                alpha = 0.28f,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        StageGradient(playerColors, visualizerMode, visualizerLayout, stageState.backdrop,
                            Modifier.matchParentSize())
                        Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .statusBarsPadding(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassIconButton(
                                onClick = { isMenuOpen = true },
                                modifier = Modifier.glassPanel(playerColors, CircleShape, dense = true, elevation = 2.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_menu),
                                    contentDescription = "Menu",
                                    tint = playerColors.accent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            ConnectedShiroStage(
                                stageState = stageState,
                                controller = exoPlayer,
                                trackKey = tracks.getOrNull(currentTrackIndex)?.uri?.toString(),
                                visualizerMode = visualizerMode,
                                visualizerLayout = visualizerLayout,
                                visible = currentScreen == Screen.MAIN && pagerState.currentPage == 0 &&
                                    !isMenuOpen && !isInputsPopupOpen,
                                isPlaying = isPlaying,
                                isBlinking = isBlinking,
                                isSleepy = isSleepy,
                                leftEyeColor = leftEyeColor,
                                rightEyeColor = if (isHeterochromia) rightEyeColor else leftEyeColor,
                                artwork = artwork,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        }
                      }

                        Column(
                            // Use the page's continuous backdrop through details, controls and bottom padding.
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val hasTracks = tracks.isNotEmpty()
                            val activeIndex = if (hasTracks) currentTrackIndex.coerceIn(0, tracks.size - 1) else -1

                            val currentTrack = tracks.getOrNull(activeIndex)
                            TrackDetails(
                                track = currentTrack, index = activeIndex, total = tracks.size,
                                loading = isBuffering && showPause,
                                isFavorite = currentTrack?.uri?.toString() in favoriteIds,
                                onToggleFavorite = { currentTrack?.let { toggleFavorite(it) } }
                            )

                            if (recoveryMessage.isNotBlank()) {
                                Text(recoveryMessage, color = playerColors.accent, fontSize = 12.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                                    .glassPanel(playerColors, RoundedCornerShape(28.dp), elevation = 3.dp, softHighlight = true)
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                                Slider(
                                    value = playbackPosition.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                                    onValueChange = {
                                        playbackPosition = it.toLong()
                                        exoPlayer.seekTo(it.toLong())
                                    },
                                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = playerColors.accent,
                                        activeTrackColor = playerColors.accent,
                                        inactiveTrackColor = playerColors.accent.copy(alpha = 0.09f),
                                        activeTickColor = playerColors.surface,
                                        inactiveTickColor = playerColors.accent
                                    ),
                                    modifier = Modifier.height(24.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatTime(playbackPosition),
                                        fontSize = 11.sp,
                                        color = playerColors.accent
                                    )
                                    Text(
                                        text = formatTime(duration),
                                        fontSize = 11.sp,
                                        color = playerColors.accent
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StageControlButton(colors = playerColors,
                                    onClick = { exoPlayer.shuffleModeEnabled = !exoPlayer.shuffleModeEnabled },
                                    enabled = tracks.isNotEmpty(),
                                    selected = isShuffleOn
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_shuffle),
                                        contentDescription = "Shuffle",
                                        tint = if (tracks.isEmpty()) {
                                            playerColors.accent.copy(alpha = 0.2f)
                                        } else if (isShuffleOn) {
                                            playerColors.accent
                                        } else {
                                            playerColors.accent.copy(alpha = 0.4f)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                StageControlButton(colors = playerColors,
                                    onClick = {
                                        if (tracks.isNotEmpty()) {
                                            val currentPos = exoPlayer.currentPosition
                                            if (currentPos > 1000L) {
                                                exoPlayer.seekTo(0L)
                                                playbackPosition = 0L
                                            } else {
                                                exoPlayer.seekToPreviousMediaItem()
                                                exoPlayer.prepare()
                                                exoPlayer.play()
                                            }
                                        }
                                    },
                                    enabled = tracks.isNotEmpty()
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_prev),
                                        contentDescription = "Previous Track",
                                        tint = if (tracks.isNotEmpty()) playerColors.accent else playerColors.accent.copy(alpha = 0.4f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                StagePlayButton(colors = playerColors,
                                    onClick = {
                                        if (tracks.isNotEmpty()) {
                                            if (exoPlayer.wantsPlayback()) {
                                                exoPlayer.pause()
                                            } else {
                                                if (currentTrackIndex < 0) playTrack(0) else {
                                                    exoPlayer.prepare()
                                                    exoPlayer.play()
                                                }
                                            }
                                        }
                                    },
                                    enabled = tracks.isNotEmpty()
                                ) {
                                    Icon(
                                        painter = painterResource(id = if (showPause) R.drawable.ic_pause else R.drawable.ic_play),
                                        contentDescription = if (showPause) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                StageControlButton(colors = playerColors,
                                    onClick = {
                                        if (tracks.isNotEmpty()) {
                                            exoPlayer.seekToNextMediaItem()
                                            exoPlayer.prepare()
                                            exoPlayer.play()
                                        }
                                    },
                                    enabled = tracks.isNotEmpty()
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_next),
                                        contentDescription = "Next Track",
                                        tint = if (tracks.isNotEmpty()) playerColors.accent else playerColors.accent.copy(alpha = 0.4f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                StageControlButton(colors = playerColors,
                                    onClick = {
                                        exoPlayer.repeatMode = if (exoPlayer.repeatMode == Player.REPEAT_MODE_ONE) {
                                            Player.REPEAT_MODE_ALL
                                        } else Player.REPEAT_MODE_ONE
                                    },
                                    enabled = tracks.isNotEmpty(),
                                    selected = isRepeatOneOn
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_repeat_one),
                                        contentDescription = "Repeat One",
                                        tint = if (tracks.isEmpty()) {
                                            playerColors.accent.copy(alpha = 0.2f)
                                        } else if (isRepeatOneOn) {
                                            playerColors.accent
                                        } else {
                                            playerColors.accent.copy(alpha = 0.4f)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        }
                    }
                  }
                } else {
                    PlaylistScreen(
                        tracks = tracks,
                        favoriteIds = favoriteIds,
                        unavailableIds = unavailableIds,
                        onToggleFavorite = { toggleFavorite(it) },
                        currentTrackIndex = currentTrackIndex,
                        onTrackSelected = { index ->
                            playTrack(index)
                            scope.launch { pagerState.animateScrollToPage(0) }
                        },
                        onBack = {
                            scope.launch { pagerState.animateScrollToPage(0) }
                        }
                    )
                }
            }

            if (isMenuOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPlayerColors.current.text.copy(alpha = 0.28f))
                        .clickable { isMenuOpen = false }
                )
            }

            AnimatedVisibility(
                visible = isMenuOpen,
                enter = slideInHorizontally { -it } + fadeIn(),
                exit = slideOutHorizontally { -it } + fadeOut(),
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.8f)
                    .align(Alignment.CenterStart)
            ) {
                GlassSurface(
                    shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                    dense = true,
                    elevation = 8.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Project:SHIRO",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = LocalPlayerColors.current.text,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        GlassSurface(
                            shape = RoundedCornerShape(18.dp),
                            elevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SleepTimerControl(exoPlayer, Modifier.fillMaxWidth())
                        }

                        GlassSurface(shape = RoundedCornerShape(18.dp), elevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()) {
                            VisualizerModeControl(visualizerMode, visualizerLayout) { selected, layout ->
                                visualizerMode = selected
                                visualizerLayout = layout
                                context.getSharedPreferences("shiro_prefs", Context.MODE_PRIVATE).edit {
                                    putString("visualizer_mode", selected.name)
                                    putString("visualizer_layout", layout.name)
                                }
                                isMenuOpen = false
                            }
                        }

                        GlassSurface(
                            shape = RoundedCornerShape(18.dp),
                            elevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassClickable {
                                    isMenuOpen = false
                                    scope.launch { pagerState.animateScrollToPage(1) }
                                }
                        ) {
                            Text(
                                text = "Playlist",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = LocalPlayerColors.current.text,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        GlassSurface(
                            shape = RoundedCornerShape(18.dp),
                            elevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassClickable { addShiroWidget(context) }
                        ) {
                            Text(stringResource(R.string.widget_add), fontSize = 16.sp,
                                fontWeight = FontWeight.Bold, color = LocalPlayerColors.current.text,
                                modifier = Modifier.padding(16.dp))
                        }

                        GlassSurface(
                            shape = RoundedCornerShape(18.dp),
                            elevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassClickable {
                                    isMenuOpen = false
                                    isInputsPopupOpen = true
                                }
                        ) {
                            Text(
                                text = "Inputs",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = LocalPlayerColors.current.text,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        GlassSurface(
                            shape = RoundedCornerShape(18.dp),
                            elevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassClickable {
                                    isMenuOpen = false
                                    currentScreen = Screen.CUSTOMIZE
                                }
                        ) {
                            Text(
                                text = "Customize Shiro",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = LocalPlayerColors.current.text,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                    }
                }
            }

            if (isInputsPopupOpen) {
                GlassAlertDialog(
                    onDismissRequest = { isInputsPopupOpen = false },
                    title = {
                        Text(
                            text = "Input Folders",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = LocalPlayerColors.current.text
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (inputFolders.isEmpty()) {
                                Text(
                                    text = "No input folders added yet.",
                                    fontSize = 13.sp,
                                    color = LocalPlayerColors.current.secondaryText,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(inputFolders, key = { it.uri.toString() }) { folder ->
                                        GlassSurface(
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "📁 ${folder.name}",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = LocalPlayerColors.current.text,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = getDisplayPathFromUri(folder.uri),
                                                        fontSize = 10.sp,
                                                        color = LocalPlayerColors.current.secondaryText,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                GlassIconButton(
                                                    onClick = {
                                                            inputFolders.remove(folder)
                                                            // Persist selection before caching removal; failed cache saves must not resurrect it.
                                                            savePreferences(context, inputFolders.toList(), isHeterochromia, leftEyeColor, rightEyeColor, bpm, danceMode)
                                                            reloadMusicList()
                                                    }
                                                ) {
                                                    Icon(painterResource(R.drawable.ic_close),
                                                        contentDescription = "Remove ${folder.name}",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            GlassButton(
                                onClick = { folderPickerLauncher.launch(null) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LocalPlayerColors.current.accent),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("+ Add Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    confirmButton = {
                        GlassButton(
                            onClick = {
                                isInputsPopupOpen = false
                                reloadMusicList()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LocalPlayerColors.current.accent)
                        ) {
                            Text("Reload", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        GlassTextButton(onClick = { isInputsPopupOpen = false }) {
                            Text("Close", color = LocalPlayerColors.current.secondaryText)
                        }
                    }
                )
            }
        }
    if (currentScreen == Screen.CUSTOMIZE) {
        // The retained page underneath must not receive taps through empty areas of Customize.
        Box(Modifier.matchParentSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        })
        CustomizeScreen(
            onBack = { currentScreen = Screen.MAIN },
            isHeterochromia = isHeterochromia,
            onHeterochromiaChange = { isHeterochromia = it },
            leftEyeColor = leftEyeColor,
            onLeftEyeColorChange = { leftEyeColor = it },
            rightEyeColor = rightEyeColor,
            onRightEyeColorChange = { rightEyeColor = it },
            isBlinking = isBlinking,
            isSleepy = isSleepy,
            squishProgress = squishProgress.value,
            squishDirection = squishDirection,
            isAutoSwaying = isAutoSwaying,
            swaySkew = 0f,
            onCharacterTap = { triggerSquish(SquishDirection.CENTER) }
        )
    }
    }
    if (cacheError) {
        GlassAlertDialog(
            onDismissRequest = { cacheError = false },
            title = { Text("Couldn't save the playlist") },
            text = { Text("Your current playlist still works, but its latest changes may not be restored after closing Shiro. Free some device storage, then retry.") },
            confirmButton = { GlassTextButton(onClick = { persistPlaylist() }) { Text("Retry save") } },
            dismissButton = { GlassTextButton(onClick = { cacheError = false }) { Text("Close") } }
        )
    }
    // Compose after Inputs so a failed scan's retry dialog is visible above it.
    scanError?.let { message ->
        GlassAlertDialog(
            onDismissRequest = { scanError = null },
            title = { Text("Couldn't refresh music") },
            text = { Text(message) },
            confirmButton = { GlassTextButton(onClick = { reloadMusicList() }) { Text("Retry") } },
            dismissButton = { GlassTextButton(onClick = { scanError = null }) { Text("Close") } }
        )
    }
    }
}

@Composable
fun Modifier.drawVerticalScrollbar(
    state: LazyListState,
    color: Color = LocalPlayerColors.current.text,
    thickness: Dp = 14.dp,
    paddingEnd: Dp = 6.dp,
    touchWidth: Dp = 48.dp
): Modifier {
    var isDragging by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val alpha = if (isDragging) 1.0f else 0.6f
    val currentColor = color.copy(alpha = alpha)

    return this
        .pointerInput(state) {
            val touchWidthPx = touchWidth.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.position.x >= size.width - touchWidthPx) {
                    isDragging = true
                    val pointerId = down.id
                    var dragJob: Job? = null

                    val updateScroll: (Float) -> Unit = { y ->
                        val layoutInfo = state.layoutInfo
                        val totalItems = layoutInfo.totalItemsCount
                        val visibleItems = layoutInfo.visibleItemsInfo
                        if (totalItems > 0 && visibleItems.isNotEmpty()) {
                            val viewportHeight = size.height.toFloat()
                            val firstVisible = visibleItems.first()
                            val lastVisible = visibleItems.last()
                            val visibleItemCount = (lastVisible.index - firstVisible.index + 1).coerceAtLeast(1)
                            val estimatedTotalHeight = visibleItems.sumOf { it.size } * (totalItems.toFloat() / visibleItemCount)
                            if (estimatedTotalHeight > viewportHeight) {
                                val minBarHeight = 44.dp.toPx()
                                val barHeight = (viewportHeight * (viewportHeight / estimatedTotalHeight)).coerceIn(minBarHeight, viewportHeight)
                                val maxBarY = (viewportHeight - barHeight).coerceAtLeast(1f)
                                val targetBarY = (y - barHeight / 2f).coerceIn(0f, maxBarY)
                                val progress = targetBarY / maxBarY
                                val maxScrollableUnits = (totalItems - visibleItemCount).coerceAtLeast(1)
                                val exactPosition = progress * maxScrollableUnits
                                val targetIndex = exactPosition.toInt().coerceIn(0, totalItems - 1)
                                val offsetFraction = exactPosition - targetIndex
                                val avgItemSize = visibleItems.sumOf { it.size } / visibleItems.size
                                val targetOffset = (offsetFraction * avgItemSize).roundToInt()
                                dragJob?.cancel()
                                dragJob = coroutineScope.launch {
                                    state.scrollToItem(targetIndex, targetOffset)
                                }
                            }
                        }
                    }

                    updateScroll(down.position.y)

                    while (true) {
                        val event = awaitPointerEvent()
                        val dragChange = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!dragChange.pressed) break
                        dragChange.consume()
                        updateScroll(dragChange.position.y)
                    }
                    isDragging = false
                }
            }
        }
        .drawWithContent {
            drawContent()
            if (!state.canScrollForward && !state.canScrollBackward) return@drawWithContent
            val layoutInfo = state.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            if (totalItems == 0 || visibleItems.isEmpty()) return@drawWithContent

            val firstVisible = visibleItems.first()
            val lastVisible = visibleItems.last()
            val visibleItemCount = (lastVisible.index - firstVisible.index + 1).coerceAtLeast(1)

            val estimatedTotalHeight = visibleItems.sumOf { it.size } * (totalItems.toFloat() / visibleItemCount)
            val viewportHeight = size.height
            if (estimatedTotalHeight <= viewportHeight) return@drawWithContent

            val minBarHeight = 44.dp.toPx()
            val barHeight = (viewportHeight * (viewportHeight / estimatedTotalHeight)).coerceIn(minBarHeight, viewportHeight)

            val firstItemOffset = (-firstVisible.offset).coerceAtLeast(0)
            val maxScrollableItems = (totalItems - visibleItemCount).coerceAtLeast(1)
            val progress = (firstVisible.index.toFloat() + (firstItemOffset.toFloat() / firstVisible.size.coerceAtLeast(1))) / maxScrollableItems
            val barY = (viewportHeight - barHeight) * progress.coerceIn(0f, 1f)
            val barX = size.width - paddingEnd.toPx() - thickness.toPx()

            drawRoundRect(
                color = currentColor,
                topLeft = Offset(barX, barY),
                size = Size(thickness.toPx(), barHeight),
                cornerRadius = CornerRadius(thickness.toPx() / 2, thickness.toPx() / 2)
            )
        }
}

@Composable
fun Modifier.drawVerticalScrollbar(
    state: ScrollState,
    color: Color = LocalPlayerColors.current.text,
    thickness: Dp = 14.dp,
    paddingEnd: Dp = 6.dp,
    touchWidth: Dp = 48.dp
): Modifier {
    var isDragging by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val alpha = if (isDragging) 1.0f else 0.6f
    val currentColor = color.copy(alpha = alpha)

    return this
        .pointerInput(state) {
            val touchWidthPx = touchWidth.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.position.x >= size.width - touchWidthPx) {
                    isDragging = true
                    val pointerId = down.id
                    var dragJob: Job? = null

                    val updateScroll: (Float) -> Unit = { y ->
                        if (state.maxValue > 0) {
                            val viewportHeight = size.height.toFloat()
                            val totalHeight = state.maxValue + viewportHeight
                            val minBarHeight = 44.dp.toPx()
                            val barHeight = (viewportHeight * (viewportHeight / totalHeight)).coerceIn(minBarHeight, viewportHeight)
                            val maxBarY = (viewportHeight - barHeight).coerceAtLeast(1f)
                            val targetBarY = (y - barHeight / 2f).coerceIn(0f, maxBarY)
                            val progress = targetBarY / maxBarY
                            val targetScroll = (progress * state.maxValue).roundToInt().coerceIn(0, state.maxValue)
                            dragJob?.cancel()
                            dragJob = coroutineScope.launch {
                                state.scrollTo(targetScroll)
                            }
                        }
                    }

                    updateScroll(down.position.y)

                    while (true) {
                        val event = awaitPointerEvent()
                        val dragChange = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!dragChange.pressed) break
                        dragChange.consume()
                        updateScroll(dragChange.position.y)
                    }
                    isDragging = false
                }
            }
        }
        .drawWithContent {
            drawContent()
            if (!state.canScrollForward && !state.canScrollBackward) return@drawWithContent
            if (state.maxValue <= 0) return@drawWithContent

            val viewportHeight = size.height
            val totalHeight = state.maxValue + viewportHeight
            val minBarHeight = 44.dp.toPx()
            val barHeight = (viewportHeight * (viewportHeight / totalHeight)).coerceIn(minBarHeight, viewportHeight)

            val progress = state.value.toFloat() / state.maxValue.toFloat()
            val barY = (viewportHeight - barHeight) * progress.coerceIn(0f, 1f)
            val barX = size.width - paddingEnd.toPx() - thickness.toPx()

            drawRoundRect(
                color = currentColor,
                topLeft = Offset(barX, barY),
                size = Size(thickness.toPx(), barHeight),
                cornerRadius = CornerRadius(thickness.toPx() / 2, thickness.toPx() / 2)
            )
        }
}

@Composable
fun PlaylistScreen(
    tracks: List<Track>,
    favoriteIds: Set<String>,
    unavailableIds: Set<String>,
    onToggleFavorite: (Track) -> Unit,
    currentTrackIndex: Int,
    onTrackSelected: (Int) -> Unit,
    onBack: () -> Unit
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    val trackSnapshot = tracks.toList()
    val filteredTracks = remember(trackSnapshot, searchQuery, favoriteIds, favoritesOnly) {
        val query = searchQuery.trim()
        trackSnapshot.filter { track ->
            (!favoritesOnly || track.uri.toString() in favoriteIds) &&
                (query.isBlank() || listOf(track.title, track.artist, track.composer, track.fileName)
                    .any { it.contains(query, ignoreCase = true) })
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        } else {
            searchQuery = ""
            isSearchFocused = false
        }
    }

    LaunchedEffect(isSearchFocused) {
        if (!isSearchFocused && isSearchActive) {
            isSearchActive = false
        }
    }

    val showCloseIcon = isSearchFocused && searchQuery.isNotEmpty()
    val actionIconRes = if (showCloseIcon) R.drawable.ic_close else R.drawable.ic_search

    Column(
        modifier = Modifier
            .fillMaxSize()
            .glassBackdrop(LocalPlayerColors.current)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search tracks...",
                            fontSize = 14.sp,
                            color = LocalPlayerColors.current.secondaryText
                        )
                    },
                    trailingIcon = {
                        GlassIconButton(
                            onClick = {
                                if (showCloseIcon) {
                                    searchQuery = ""
                                } else {
                                    isSearchActive = false
                                    focusManager.clearFocus()
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(id = actionIconRes),
                                contentDescription = if (showCloseIcon) "Clear Search" else "Close Search",
                                tint = LocalPlayerColors.current.text,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = LocalPlayerColors.current.surface.copy(alpha = 0.92f),
                        unfocusedContainerColor = LocalPlayerColors.current.surface.copy(alpha = 0.72f),
                        disabledContainerColor = LocalPlayerColors.current.surface.copy(alpha = 0.5f),
                        focusedBorderColor = LocalPlayerColors.current.accent,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedTextColor = LocalPlayerColors.current.text,
                        unfocusedTextColor = LocalPlayerColors.current.text
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            isSearchFocused = focusState.isFocused
                        }
                )
            } else {
                GlassSurface(
                    shape = CircleShape,
                    elevation = 2.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .glassClickable(shape = CircleShape) { onBack() }
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_back),
                            contentDescription = "Back",
                            tint = LocalPlayerColors.current.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Playlist",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = LocalPlayerColors.current.text
                    )
                    Text(
                        text = "${tracks.size} tracks",
                        fontSize = 12.sp,
                        color = LocalPlayerColors.current.secondaryText
                    )
                }

                GlassIconButton(
                    onClick = { isSearchActive = true }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = "Search",
                        tint = LocalPlayerColors.current.text,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        val chipColors = FilterChipDefaults.filterChipColors(
            containerColor = LocalPlayerColors.current.surface.copy(alpha = 0.6f),
            selectedContainerColor = LocalPlayerColors.current.selected.copy(alpha = 0.85f),
            labelColor = LocalPlayerColors.current.text,
            selectedLabelColor = LocalPlayerColors.current.text
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassFilterChip(selected = !favoritesOnly, onClick = { favoritesOnly = false },
                colors = chipColors, shape = RoundedCornerShape(16.dp), label = { Text("All tracks") })
            GlassFilterChip(selected = favoritesOnly, onClick = { favoritesOnly = true },
                colors = chipColors, shape = RoundedCornerShape(16.dp), label = { Text("Favorites") })
        }
        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (tracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tracks in playlist.\nOpen menu ➔ Inputs to add music folders.",
                        fontSize = 14.sp,
                        color = LocalPlayerColors.current.secondaryText,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (filteredTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            favoritesOnly && tracks.none { it.uri.toString() in favoriteIds } ->
                                "No favorites yet. Tap a heart to save a song."
                            favoritesOnly -> "No favorites match your search."
                            else -> "No tracks match your search."
                        },
                        fontSize = 14.sp,
                        color = LocalPlayerColors.current.secondaryText,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = filteredTracks.indexOfFirst {
                        it.uri == tracks.getOrNull(currentTrackIndex)?.uri
                    }.let { (it - 2).coerceAtLeast(0) }
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawVerticalScrollbar(listState)
                ) {
                    itemsIndexed(filteredTracks, key = { _, track -> track.uri.toString() }) { _, track ->
                        val originalIndex = tracks.indexOf(track)
                        val isCurrent = originalIndex == currentTrackIndex
                        val displayIndex = originalIndex + 1

                        GlassSurface(
                            shape = RoundedCornerShape(18.dp),
                            selected = isCurrent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .glassClickable {
                                    if (originalIndex != -1) {
                                        onTrackSelected(originalIndex)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = if (isCurrent) "▶" else "$displayIndex",
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrent) LocalPlayerColors.current.text else LocalPlayerColors.current.secondaryText,
                                    modifier = Modifier.width(32.dp),
                                    textAlign = TextAlign.Center
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = track.title,
                                        fontSize = 15.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                        color = LocalPlayerColors.current.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (track.artist.isNotBlank()) {
                                        Text(
                                            text = "Artist: ${track.artist}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = LocalPlayerColors.current.secondaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (track.composer.isNotBlank()) {
                                        Text(
                                            text = "Composer: ${track.composer}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = LocalPlayerColors.current.secondaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (track.uri.toString() in unavailableIds) {
                                        Text("Unavailable · tap to retry", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                    }
                                }
                                if (favoritesOnly) {
                                    GlassIconButton(
                                        onClick = { onToggleFavorite(track) },
                                        modifier = Modifier.glassPanel(LocalPlayerColors.current, CircleShape, selected = true)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_close),
                                            contentDescription = "Remove ${track.title} from favorites",
                                            tint = LocalPlayerColors.current.accent
                                        )
                                    }
                                } else {
                                    FavoriteButton(track.uri.toString() in favoriteIds, track.title) {
                                        onToggleFavorite(track)
                                    }
                                }

                            }
                        }

                    }
                }
            }

            if (isSearchActive && isSearchFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.1f))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                focusManager.clearFocus()
                            }
                        }
                )
            }
        }
    }
}

@Composable
fun CustomizeScreen(
    onBack: () -> Unit,
    isHeterochromia: Boolean,
    onHeterochromiaChange: (Boolean) -> Unit,
    leftEyeColor: Color,
    onLeftEyeColorChange: (Color) -> Unit,
    rightEyeColor: Color,
    onRightEyeColorChange: (Color) -> Unit,
    isBlinking: Boolean,
    isSleepy: Boolean,
    squishProgress: Float,
    squishDirection: SquishDirection,
    isAutoSwaying: Boolean,
    swaySkew: Float,
    onCharacterTap: () -> Unit
) {
    var isEyeColorPopupOpen by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .glassBackdrop(LocalPlayerColors.current)
            .navigationBarsPadding()
            .drawVerticalScrollbar(scrollState)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassSurface(
                shape = CircleShape,
                elevation = 2.dp,
                modifier = Modifier
                    .size(48.dp)
                    .glassClickable(shape = CircleShape) { onBack() }
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "Back",
                        tint = LocalPlayerColors.current.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "Customize Shiro",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = LocalPlayerColors.current.text
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassSurface(
                shape = RoundedCornerShape(18.dp),
                elevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .glassClickable { isEyeColorPopupOpen = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Eye Color",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = LocalPlayerColors.current.text
                        )
                        Text(
                            text = if (isHeterochromia) "Heterochromia On" else "Single Eye Color",
                            fontSize = 12.sp,
                            color = LocalPlayerColors.current.secondaryText
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(leftEyeColor, CircleShape)
                                .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                        )
                        if (isHeterochromia) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(rightEyeColor, CircleShape)
                                    .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .glassPanel(LocalPlayerColors.current, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            ShiroCharacter(
                isBlinking = isBlinking,
                isSleepy = isSleepy,
                squishProgress = squishProgress,
                squishDirection = squishDirection,
                isAutoSwaying = isAutoSwaying,
                swaySkew = swaySkew,
                leftEyeColor = leftEyeColor,
                rightEyeColor = if (isHeterochromia) rightEyeColor else leftEyeColor,
                onTap = onCharacterTap,
                eyeScale = 0.6f,
                eyeOffsetX = 5.dp,
                eyeOffsetY = 0.dp,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1536f / 1024f)
            )
        }
    }

    if (isEyeColorPopupOpen) {
        var activeEyeTab by remember { mutableIntStateOf(0) } // 0 = Left, 1 = Right
        val currentTargetColor = if (!isHeterochromia) {
            leftEyeColor
        } else {
            if (activeEyeTab == 0) leftEyeColor else rightEyeColor
        }

        fun updateTargetColor(newColor: Color) {
            if (!isHeterochromia) {
                onLeftEyeColorChange(newColor)
                onRightEyeColorChange(newColor)
            } else {
                if (activeEyeTab == 0) onLeftEyeColorChange(newColor) else onRightEyeColorChange(newColor)
            }
        }

        GlassAlertDialog(
            onDismissRequest = { isEyeColorPopupOpen = false },
            title = {
                Text(
                    text = "Eye Color Customization",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalPlayerColors.current.text
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Heterochromia",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = LocalPlayerColors.current.text
                        )

                        GlassSwitch(
                            checked = isHeterochromia,
                            onCheckedChange = {
                                onHeterochromiaChange(it)
                                if (!it) onRightEyeColorChange(leftEyeColor)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = LocalPlayerColors.current.accent
                            )
                        )
                    }

                    if (isHeterochromia) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlassButton(
                                onClick = { activeEyeTab = 0 },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (activeEyeTab == 0) LocalPlayerColors.current.accent else LocalPlayerColors.current.selected.copy(alpha = 0.72f),
                                    contentColor = if (activeEyeTab == 0) Color.White else LocalPlayerColors.current.text
                                ),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("Left Eye", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            GlassButton(
                                onClick = { activeEyeTab = 1 },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (activeEyeTab == 1) LocalPlayerColors.current.accent else LocalPlayerColors.current.selected.copy(alpha = 0.72f),
                                    contentColor = if (activeEyeTab == 1) Color.White else LocalPlayerColors.current.text
                                ),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("Right Eye", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        text = if (isHeterochromia) {
                            if (activeEyeTab == 0) "Left Eye Color Spectrum" else "Right Eye Color Spectrum"
                        } else "Eye Color Spectrum",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = LocalPlayerColors.current.secondaryText
                    )

                    ColorSpectrumBar(
                        selectedColor = currentTargetColor,
                        onColorSelected = { updateTargetColor(it) }
                    )

                    var rText by remember(currentTargetColor) { mutableStateOf((currentTargetColor.red * 255).roundToInt().toString()) }
                    var gText by remember(currentTargetColor) { mutableStateOf((currentTargetColor.green * 255).roundToInt().toString()) }
                    var bText by remember(currentTargetColor) { mutableStateOf((currentTargetColor.blue * 255).roundToInt().toString()) }

                    fun updateRgb(rStr: String, gStr: String, bStr: String) {
                        rText = rStr.filter { it.isDigit() }.take(3)
                        gText = gStr.filter { it.isDigit() }.take(3)
                        bText = bStr.filter { it.isDigit() }.take(3)

                        val r = rText.toIntOrNull()?.coerceIn(0, 255) ?: 0
                        val g = gText.toIntOrNull()?.coerceIn(0, 255) ?: 0
                        val b = bText.toIntOrNull()?.coerceIn(0, 255) ?: 0

                        updateTargetColor(Color(r, g, b))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(currentTargetColor, shape = CircleShape)
                                .border(2.dp, Color.Gray.copy(alpha = 0.4f), CircleShape)
                        )

                        OutlinedTextField(
                            value = rText,
                            onValueChange = { updateRgb(it, gText, bText) },
                            label = { Text("R", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp)
                        )

                        OutlinedTextField(
                            value = gText,
                            onValueChange = { updateRgb(rText, it, bText) },
                            label = { Text("G", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp)
                        )

                        OutlinedTextField(
                            value = bText,
                            onValueChange = { updateRgb(rText, gText, it) },
                            label = { Text("B", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp)
                        )
                    }
                }
            },
            confirmButton = {
                GlassButton(
                    onClick = { isEyeColorPopupOpen = false },
                    colors = ButtonDefaults.buttonColors(containerColor = LocalPlayerColors.current.accent)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun ShiroCharacter(
    isBlinking: Boolean,
    squishProgress: Float,
    squishDirection: SquishDirection,
    modifier: Modifier = Modifier,
    isSleepy: Boolean = false,
    isAutoSwaying: Boolean = false,
    swaySkew: Float = 0f,
    leftEyeColor: Color = Color(0xFF201C1C),
    rightEyeColor: Color = Color(0xFF201C1C),
    onTap: () -> Unit = {},
    eyeScale: Float = 0.6f,
    eyeOffsetX: Dp = 5.dp,
    eyeOffsetY: Dp = 0.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val showClosedEyes = isBlinking || isPressed || (squishProgress > 0.1f)
    val sleepyEyeScale by animateFloatAsState(
        targetValue = if (isSleepy) 0.45f else 1f,
        animationSpec = tween(durationMillis = 600),
        label = "Sleepy eyes"
    )
    val eyeHeight = if (showClosedEyes) eyeScale else eyeScale * sleepyEyeScale

    val bodyScaleY = 1f - squishProgress * 0.22f
    val bodyScaleX = 1f + squishProgress * 0.16f

    val squishSkewAmount = when (squishDirection) {
        SquishDirection.LEFT -> 0.32f * squishProgress
        SquishDirection.RIGHT -> -0.32f * squishProgress
        SquishDirection.CENTER -> 0f
    }
    val totalSkewAmount = squishSkewAmount + swaySkew

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onTap
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val pivotX = size.width * 0.5f
                    val pivotY = size.height * 0.922f

                    drawIntoCanvas { canvas ->
                        canvas.save()
                        canvas.nativeCanvas.translate(pivotX, pivotY)
                        canvas.nativeCanvas.skew(totalSkewAmount, 0f)
                        canvas.nativeCanvas.scale(bodyScaleX, bodyScaleY)
                        canvas.nativeCanvas.translate(-pivotX, -pivotY)

                        drawContent()
                        canvas.restore()
                    }
                }
        ) {
            Image(
                painter = painterResource(id = R.drawable.shiro_body_neutral),
                contentDescription = "Shiro Body",
                modifier = Modifier.fillMaxSize()
            )

            // Right Eye (character's right / viewer's left)
            Image(
                painter = painterResource(
                    id = if (showClosedEyes) {
                        R.drawable.shiro_eyes_closed_right
                    } else {
                        R.drawable.shiro_eyes_neutral_right
                    }
                ),
                contentDescription = "Shiro Right Eye",
                colorFilter = ColorFilter.tint(rightEyeColor),
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = eyeOffsetX, y = eyeOffsetY)
                    .graphicsLayer(
                        scaleX = eyeScale,
                        scaleY = eyeHeight,
                        transformOrigin = TransformOrigin(0.5f, 0.5137f)
                    )
            )

            // Left Eye (character's left / viewer's right)
            Image(
                painter = painterResource(
                    id = if (showClosedEyes) {
                        R.drawable.shiro_eyes_closed_left
                    } else {
                        R.drawable.shiro_eyes_neutral_left
                    }
                ),
                contentDescription = "Shiro Left Eye",
                colorFilter = ColorFilter.tint(leftEyeColor),
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = eyeOffsetX, y = eyeOffsetY)
                    .graphicsLayer(
                        scaleX = eyeScale,
                        scaleY = eyeHeight,
                        transformOrigin = TransformOrigin(0.5f, 0.5137f)
                    )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ShiroScreenPreview() {
    ProjectSHIROTheme {
        ShiroGlassTheme { ShiroScreen() }
    }
}
