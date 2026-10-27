package com.example.shiro

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.shiro.ui.theme.ProjectSHIROTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        this.setContent {
            ProjectSHIROTheme {
                ShiroScreen()
            }
        }
    }
}

val LightBlue = Color(0xFFE0F2FE)
val ButtonColor = Color(0xFF38BDF8)
val AccentColor = Color(0xFF0284C7)

enum class SquishDirection {
    LEFT, CENTER, RIGHT
}

enum class DanceMode {
    SWAY, SQUISH, OFF
}

enum class Screen {
    MAIN, CUSTOMIZE
}

data class Track(
    val uri: Uri,
    val title: String,
    val fileName: String,
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
    danceMode: DanceMode,
    fileName: String,
    positionMs: Long
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
        .putString("saved_file_name", fileName)
        .putLong("saved_position_ms", positionMs)
        .apply()
}

fun scanAudioFilesFromFolder(context: Context, treeUri: Uri): List<Track> {
    val audioTracks = mutableListOf<Track>()
    val rootFile = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()

    fun traverse(dir: DocumentFile) {
        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                traverse(file)
            } else if (file.isFile) {
                val name = file.name ?: ""
                val lowerName = name.lowercase()
                val mime = file.type ?: ""
                if (lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") ||
                    mime.startsWith("audio/mpeg") || mime.startsWith("audio/wav") || mime.startsWith("audio/x-wav")) {
                    val title = name.substringBeforeLast(".")
                    audioTracks.add(Track(uri = file.uri, title = title, fileName = name))
                }
            }
        }
    }

    traverse(rootFile)
    return audioTracks.sortedBy { it.fileName }
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

@Composable
fun ShiroScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(Screen.MAIN) }

    val tracks = remember { mutableStateListOf<Track>() }
    val inputFolders = remember { mutableStateListOf<FolderItem>() }
    var currentTrackIndex by remember { mutableIntStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    var danceMode by remember { mutableStateOf(DanceMode.SWAY) }
    var bpm by remember { mutableFloatStateOf(120f) }

    var isHeterochromia by remember { mutableStateOf(false) }
    var leftEyeColor by remember { mutableStateOf(Color(0xFF201C1C)) }
    var rightEyeColor by remember { mutableStateOf(Color(0xFF201C1C)) }

    var isBlinking by remember { mutableStateOf(false) }
    var squishDirection by remember { mutableStateOf(SquishDirection.CENTER) }

    var isMenuOpen by remember { mutableStateOf(false) }
    var isInputsPopupOpen by remember { mutableStateOf(false) }

    var isRestored by remember { mutableStateOf(false) }

    val isAutoSquishing = isPlaying && danceMode == DanceMode.SQUISH
    val isAutoSwaying = isPlaying && danceMode == DanceMode.SWAY

    val squishDurationSec = (0.3f - (bpm - 60f) * (0.2f / 120f)).coerceIn(0.1f, 0.3f)

    val squishProgress = remember { Animatable(0f) }
    val swaySkew = remember { Animatable(0f) }
    var squishJob by remember { mutableStateOf<Job?>(null) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    val permissionToRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_AUDIO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun restoreTrackPosition(allScannedTracks: List<Track>) {
        val prefs = context.getSharedPreferences("shiro_prefs", Context.MODE_PRIVATE)
        val savedFileName = prefs.getString("saved_file_name", "") ?: ""
        val savedPositionMs = prefs.getLong("saved_position_ms", 0L)

        if (allScannedTracks.isNotEmpty()) {
            val foundIndex = allScannedTracks.indexOfFirst { it.fileName == savedFileName }
            if (foundIndex != -1) {
                currentTrackIndex = foundIndex
                val track = allScannedTracks[foundIndex]
                exoPlayer.setMediaItem(MediaItem.fromUri(track.uri))
                exoPlayer.prepare()
                exoPlayer.seekTo(savedPositionMs)
                playbackPosition = savedPositionMs
            } else {
                currentTrackIndex = 0
                val track = allScannedTracks[0]
                exoPlayer.setMediaItem(MediaItem.fromUri(track.uri))
                exoPlayer.prepare()
                exoPlayer.seekTo(0L)
                playbackPosition = 0L
            }
        } else {
            currentTrackIndex = -1
            playbackPosition = 0L
            duration = 0L
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
        isRestored = true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && inputFolders.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val allScannedTracks = mutableListOf<Track>()
                for (folder in inputFolders) {
                    val folderTracks = scanAudioFilesFromFolder(context, folder.uri)
                    allScannedTracks.addAll(folderTracks)
                }
                val distinctTracks = allScannedTracks.distinctBy { it.uri }
                withContext(Dispatchers.Main) {
                    tracks.clear()
                    tracks.addAll(distinctTracks)
                    restoreTrackPosition(tracks)
                }
            }
        }
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

        if (ContextCompat.checkSelfPermission(context, permissionToRequest) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(permissionToRequest)
        } else if (inputFolders.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val allScannedTracks = mutableListOf<Track>()
                for (folder in inputFolders) {
                    val folderTracks = scanAudioFilesFromFolder(context, folder.uri)
                    allScannedTracks.addAll(folderTracks)
                }
                val distinctTracks = allScannedTracks.distinctBy { it.uri }
                withContext(Dispatchers.Main) {
                    tracks.clear()
                    tracks.addAll(distinctTracks)
                    restoreTrackPosition(tracks)
                }
            }
        } else {
            isRestored = true
        }
    }

    val currentFileName = if (currentTrackIndex in tracks.indices) tracks[currentTrackIndex].fileName else ""

    LaunchedEffect(isRestored, inputFolders.toList(), isHeterochromia, leftEyeColor, rightEyeColor, bpm, danceMode, currentFileName, playbackPosition) {
        if (isRestored && currentFileName.isNotEmpty()) {
            savePreferences(
                context,
                inputFolders,
                isHeterochromia,
                leftEyeColor,
                rightEyeColor,
                bpm,
                danceMode,
                currentFileName,
                playbackPosition
            )
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                    playbackPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                }
                if (playbackState == Player.STATE_ENDED) {
                    if (tracks.isNotEmpty() && currentTrackIndex < tracks.size - 1) {
                        currentTrackIndex++
                        val track = tracks[currentTrackIndex]
                        exoPlayer.setMediaItem(MediaItem.fromUri(track.uri))
                        exoPlayer.prepare()
                        exoPlayer.play()
                    } else {
                        isPlaying = false
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            playbackPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(200L)
        }
    }

    fun playTrack(index: Int) {
        if (index in tracks.indices) {
            currentTrackIndex = index
            val track = tracks[index]
            exoPlayer.setMediaItem(MediaItem.fromUri(track.uri))
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    fun reloadMusicList() {
        scope.launch(Dispatchers.IO) {
            val allScannedTracks = mutableListOf<Track>()
            for (folder in inputFolders) {
                val folderTracks = scanAudioFilesFromFolder(context, folder.uri)
                allScannedTracks.addAll(folderTracks)
            }
            val distinctTracks = allScannedTracks.distinctBy { it.uri }
            withContext(Dispatchers.Main) {
                tracks.clear()
                tracks.addAll(distinctTracks)
                restoreTrackPosition(tracks)
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

        scope.launch(Dispatchers.IO) {
            val newTracks = scanAudioFilesFromFolder(context, uri)
            withContext(Dispatchers.Main) {
                val existingUris = tracks.map { it.uri }.toSet()
                val uniqueNewTracks = newTracks.filter { it.uri !in existingUris }
                tracks.addAll(uniqueNewTracks)
                if (tracks.isNotEmpty() && currentTrackIndex < 0) {
                    restoreTrackPosition(tracks)
                }
            }
        }
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

    LaunchedEffect(isAutoSquishing) {
        if (isAutoSquishing) {
            val directions = listOf(
                SquishDirection.CENTER,
                SquishDirection.LEFT,
                SquishDirection.CENTER,
                SquishDirection.RIGHT
            )
            var directionIndex = 0
            try {
                while (isActive) {
                    val currentBpm = bpm
                    val currentDurationSec = (0.3f - (currentBpm - 60f) * (0.2f / 120f)).coerceIn(0.1f, 0.3f)
                    val totalMs = (currentDurationSec * 1000f).roundToInt().coerceAtLeast(60)
                    val compressMs = (totalMs * 0.35f).roundToInt().coerceAtLeast(20)
                    val reboundMs = (totalMs - compressMs).coerceAtLeast(20)

                    val beatPeriodMs = (60000f / currentBpm).roundToInt()
                    val restDelayMs = (beatPeriodMs - totalMs).coerceAtLeast(0)

                    squishDirection = directions[directionIndex]

                    squishProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = compressMs, easing = FastOutSlowInEasing)
                    )
                    squishProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = reboundMs, easing = FastOutSlowInEasing)
                    )

                    directionIndex = (directionIndex + 1) % directions.size

                    if (restDelayMs > 0) {
                        delay(restDelayMs.toLong())
                    }
                }
            } finally {
                squishDirection = SquishDirection.CENTER
                squishProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing)
                )
            }
        } else {
            squishDirection = SquishDirection.CENTER
            squishProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(isAutoSwaying) {
        if (isAutoSwaying) {
            try {
                var swayLeft = true
                while (isActive) {
                    val currentBpm = bpm
                    val beatPeriodMs = (60000f / currentBpm).roundToInt().coerceAtLeast(100)
                    val targetSkew = if (swayLeft) 0.18f else -0.18f

                    swaySkew.animateTo(
                        targetValue = targetSkew,
                        animationSpec = tween(
                            durationMillis = beatPeriodMs,
                            easing = FastOutSlowInEasing
                        )
                    )

                    swayLeft = !swayLeft
                }
            } finally {
                swaySkew.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing)
                )
            }
        } else {
            swaySkew.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing)
            )
        }
    }

    fun triggerSquish(direction: SquishDirection = SquishDirection.CENTER) {
        squishJob?.cancel()
        squishJob = scope.launch {
            if (squishProgress.value > 0.01f) {
                val quickReturnMaxMs = (squishDurationSec * 200f).roundToInt().coerceIn(40, 90)
                val returnMs = (squishProgress.value * quickReturnMaxMs).roundToInt().coerceAtLeast(25)
                squishProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = returnMs, easing = FastOutSlowInEasing)
                )
            }
            squishDirection = direction

            val totalMs = (squishDurationSec * 1000f).roundToInt().coerceAtLeast(60)
            val compressMs = (totalMs * 0.35f).roundToInt().coerceAtLeast(30)
            val reboundMs = (totalMs - compressMs).coerceAtLeast(30)

            squishProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = compressMs, easing = FastOutSlowInEasing)
            )
            squishProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = reboundMs, easing = FastOutSlowInEasing)
            )
        }
    }

    if (currentScreen == Screen.CUSTOMIZE) {
        CustomizeScreen(
            onBack = { currentScreen = Screen.MAIN },
            isHeterochromia = isHeterochromia,
            onHeterochromiaChange = { isHeterochromia = it },
            leftEyeColor = leftEyeColor,
            onLeftEyeColorChange = { leftEyeColor = it },
            rightEyeColor = rightEyeColor,
            onRightEyeColorChange = { rightEyeColor = it },
            isBlinking = isBlinking,
            squishProgress = squishProgress.value,
            squishDirection = squishDirection,
            isAutoSwaying = isAutoSwaying,
            swaySkew = swaySkew.value,
            onCharacterTap = {
                if (!isAutoSquishing && !isAutoSwaying) {
                    triggerSquish(SquishDirection.CENTER)
                }
            }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightBlue)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ButtonColor,
                        modifier = Modifier.clickable { isMenuOpen = true }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "☰",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    ShiroCharacter(
                        isBlinking = isBlinking,
                        squishProgress = squishProgress.value,
                        squishDirection = squishDirection,
                        isAutoSwaying = isAutoSwaying,
                        swaySkew = swaySkew.value,
                        leftEyeColor = leftEyeColor,
                        rightEyeColor = if (isHeterochromia) rightEyeColor else leftEyeColor,
                        onTap = {
                            if (!isAutoSquishing && !isAutoSwaying) {
                                triggerSquish(SquishDirection.CENTER)
                            }
                        },
                        eyeScale = 0.6f,
                        eyeOffsetX = 5.dp,
                        eyeOffsetY = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .aspectRatio(1536f / 1024f)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val hasTracks = tracks.isNotEmpty()
                    val activeIndex = if (hasTracks) currentTrackIndex.coerceIn(0, tracks.size - 1) else -1

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (hasTracks && activeIndex in tracks.indices) tracks[activeIndex].title else "No Tracks Loaded",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0369A1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (hasTracks) "Track ${activeIndex + 1} of ${tracks.size}" else "Open menu (☰) ➔ Inputs to add music folders",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0284C7).copy(alpha = 0.8f)
                            )
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth(0.95f)) {
                        Slider(
                            value = playbackPosition.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                            onValueChange = {
                                playbackPosition = it.toLong()
                                exoPlayer.seekTo(it.toLong())
                            },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = ButtonColor,
                                activeTrackColor = ButtonColor
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
                                color = Color(0xFF0369A1)
                            )
                            Text(
                                text = formatTime(duration),
                                fontSize = 11.sp,
                                color = Color(0xFF0369A1)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    val prevIndex = if (currentTrackIndex > 0) currentTrackIndex - 1 else tracks.size - 1
                                    playTrack(prevIndex)
                                }
                            },
                            enabled = tracks.isNotEmpty()
                        ) {
                            Text(
                                text = "⏮",
                                fontSize = 24.sp,
                                color = if (tracks.isNotEmpty()) Color(0xFF0369A1) else Color(0xFF0369A1).copy(alpha = 0.4f)
                            )
                        }

                        Button(
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    if (isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        if (currentTrackIndex < 0) playTrack(0) else exoPlayer.play()
                                    }
                                }
                            },
                            enabled = tracks.isNotEmpty(),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlaying) Color(0xFFEF4444) else AccentColor,
                                disabledContainerColor = AccentColor.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Text(
                                text = if (isPlaying) "⏸" else "▶",
                                fontSize = 22.sp,
                                color = Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    val nextIndex = (currentTrackIndex + 1) % tracks.size
                                    playTrack(nextIndex)
                                }
                            },
                            enabled = tracks.isNotEmpty()
                        ) {
                            Text(
                                text = "⏭",
                                fontSize = 24.sp,
                                color = if (tracks.isNotEmpty()) Color(0xFF0369A1) else Color(0xFF0369A1).copy(alpha = 0.4f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(0.95f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dance:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0369A1))

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                onClick = { danceMode = DanceMode.SWAY },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (danceMode == DanceMode.SWAY) AccentColor else ButtonColor.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Sway", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { danceMode = DanceMode.SQUISH },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (danceMode == DanceMode.SQUISH) AccentColor else ButtonColor.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Squish", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { danceMode = DanceMode.OFF },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (danceMode == DanceMode.OFF) AccentColor else ButtonColor.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Off", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(0.95f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dance Tempo: ${bpm.roundToInt()} BPM",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1)
                        )
                    }

                    Slider(
                        value = bpm,
                        onValueChange = { bpm = it },
                        valueRange = 60f..240f,
                        colors = SliderDefaults.colors(
                            thumbColor = ButtonColor,
                            activeTrackColor = ButtonColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .height(24.dp)
                    )
                }
            }

            if (isMenuOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
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
                Surface(
                    color = Color(0xFFF0F9FF),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Project SHIRO",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        HorizontalDivider(color = Color(0xFFBAE6FD))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    isMenuOpen = false
                                    isInputsPopupOpen = true
                                }
                        ) {
                            Text(
                                text = "Inputs",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0369A1),
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    isMenuOpen = false
                                    currentScreen = Screen.CUSTOMIZE
                                }
                        ) {
                            Text(
                                text = "Customize Shiro",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0369A1),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }

            if (isInputsPopupOpen) {
                AlertDialog(
                    onDismissRequest = { isInputsPopupOpen = false },
                    title = {
                        Text(
                            text = "Input Folders",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1)
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
                                    color = Color.Gray,
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
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFE0F2FE),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "📁 ${folder.name}",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF0369A1),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "❌",
                                                    fontSize = 14.sp,
                                                    modifier = Modifier
                                                        .clickable { inputFolders.remove(folder) }
                                                        .padding(4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { folderPickerLauncher.launch(null) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ButtonColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("+ Add Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                isInputsPopupOpen = false
                                reloadMusicList()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                        ) {
                            Text("Reload", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { isInputsPopupOpen = false }) {
                            Text("Close", color = Color.Gray)
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
    squishProgress: Float,
    squishDirection: SquishDirection,
    isAutoSwaying: Boolean,
    swaySkew: Float,
    onCharacterTap: () -> Unit
) {
    var isEyeColorPopupOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBlue)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ButtonColor,
                modifier = Modifier.clickable { onBack() }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "← Back",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "Customize Shiro",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0369A1)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isEyeColorPopupOpen = true }
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
                            color = Color(0xFF0369A1)
                        )
                        Text(
                            text = if (isHeterochromia) "Heterochromia On" else "Single Eye Color",
                            fontSize = 12.sp,
                            color = Color.Gray
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
                .height(240.dp),
            contentAlignment = Alignment.Center
        ) {
            ShiroCharacter(
                isBlinking = isBlinking,
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

        AlertDialog(
            onDismissRequest = { isEyeColorPopupOpen = false },
            title = {
                Text(
                    text = "Eye Color Customization",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0369A1)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
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
                            color = Color(0xFF0369A1)
                        )

                        Switch(
                            checked = isHeterochromia,
                            onCheckedChange = {
                                onHeterochromiaChange(it)
                                if (!it) onRightEyeColorChange(leftEyeColor)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentColor
                            )
                        )
                    }

                    if (isHeterochromia) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { activeEyeTab = 0 },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (activeEyeTab == 0) AccentColor else ButtonColor.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Text("Left Eye", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { activeEyeTab = 1 },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (activeEyeTab == 1) AccentColor else ButtonColor.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.weight(1f).height(36.dp)
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
                        color = Color(0xFF0284C7)
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
                Button(
                    onClick = { isEyeColorPopupOpen = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
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

    val showClosedEyes = isBlinking || isAutoSwaying || isPressed

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
                        scaleY = eyeScale,
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
                        scaleY = eyeScale,
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
        ShiroScreen()
    }
}
