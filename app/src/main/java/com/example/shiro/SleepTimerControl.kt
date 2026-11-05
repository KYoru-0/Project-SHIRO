package com.example.shiro

import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(UnstableApi::class)
@Composable
fun SleepTimerControl(controller: MediaController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var deadline by remember(controller) { mutableLongStateOf(controller.sessionExtras.getLong(SleepTimer.DEADLINE)) }
    var endOfTrack by remember(controller) { mutableStateOf(controller.sessionExtras.getBoolean(SleepTimer.END_OF_TRACK)) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var hasTracks by remember(controller) { mutableStateOf(controller.mediaItemCount > 0) }

    fun refresh() {
        deadline = controller.sessionExtras.getLong(SleepTimer.DEADLINE)
        endOfTrack = controller.sessionExtras.getBoolean(SleepTimer.END_OF_TRACK)
        now = SystemClock.elapsedRealtime()
        hasTracks = controller.mediaItemCount > 0
    }
    LaunchedEffect(controller, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                refresh()
                delay(1_000L)
            }
        }
    }
    val active = deadline > 0L || endOfTrack
    val label = when {
        endOfTrack -> "Sleep: end of track"
        deadline > 0L -> "Sleep: ${formatTime((deadline - now).coerceAtLeast(0L))}"
        else -> "Sleep timer"
    }

    fun setTimer(minutes: Int) {
        error = null
        sending = true
        val result = controller.sendCustomCommand(SleepTimer.command, Bundle().apply { putInt(SleepTimer.MINUTES, minutes) })
        result.addListener({
            if (scope.isActive) {
                sending = false
                try {
                    if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                        refresh()
                        if (minutes != SleepTimer.EXTEND_FIVE) showDialog = false
                    } else {
                        refresh()
                        error = if (minutes == SleepTimer.EXTEND_FIVE) {
                            "That minute timer has ended or changed. Choose a duration to start a new one."
                        } else "Couldn't set the timer. Add a track and try again."
                    }
                } catch (_: Exception) {
                    error = "Couldn't reach the player. Close this dialog and try again."
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    TextButton(onClick = { refresh(); showDialog = true }) { Text(label, color = AccentColor) }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { if (!sending) showDialog = false },
            title = { Text("Sleep timer") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Pause music after a set time, or when the track finishes.")
                    Text("Music gently fades during the final 30 seconds. Shiro gets sleepy while the timer is on.")
                    if (active) Text(label)
                    if (deadline > 0L) {
                        TextButton(
                            onClick = { setTimer(SleepTimer.EXTEND_FIVE) },
                            enabled = deadline > now && hasTracks && !sending,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("+5 minutes") }
                    }
                    if (!hasTracks) Text("Add a music folder in Inputs first.")
                    for (minutes in listOf(15, 30, 60)) {
                        TextButton(onClick = { setTimer(minutes) }, enabled = hasTracks && !sending, modifier = Modifier.fillMaxWidth()) {
                            Text("$minutes minutes")
                        }
                    }
                    TextButton(onClick = { setTimer(SleepTimer.AT_TRACK_END) }, enabled = hasTracks && !sending, modifier = Modifier.fillMaxWidth()) {
                        Text("End of track")
                    }
                    Text("Minute timers keep counting while music is paused. If you skip a song, End of track follows the new song.")
                    error?.let { Text(it) }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }, enabled = !sending) { Text("Close") } },
            dismissButton = {
                if (active) TextButton(onClick = { setTimer(SleepTimer.CANCEL) }, enabled = !sending) { Text("Cancel timer") }
            }
        )
    }
}
