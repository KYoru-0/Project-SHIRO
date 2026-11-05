package com.example.shiro

import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/** The UI connects to the service; disposing it never releases the service's player. */
@Composable
fun ShiroScreen() {
    // Android Studio previews must not connect to a service.
    if (LocalInspectionMode.current) {
        Column(Modifier.fillMaxSize().background(LightBlue), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            ShiroCharacter(false, 0f, SquishDirection.CENTER)
        }
        return
    }

    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    DisposableEffect(context, retry) {
        var disposed = false
        failed = false
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(mediaController: MediaController) {
                    if (!disposed) {
                        controller = null
                        failed = true
                    }
                }
            })
            .buildAsync()
        future.addListener({
            if (!disposed) {
                try { controller = future.get() }
                catch (_: Exception) { failed = true }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            controller = null
            MediaController.releaseFuture(future)
        }
    }

    val connectedController = controller
    if (connectedController != null) {
        ShiroScreen(connectedController)
    } else {
        Column(
            modifier = Modifier.fillMaxSize().background(LightBlue).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Text(if (failed) "Couldn't connect to the music player." else "Getting Shiro ready…")
            if (failed) Button(onClick = { retry++ }) { Text("Try again") }
        }
    }
}
