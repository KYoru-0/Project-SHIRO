package com.example.shiro

import android.content.ComponentName
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay

/** The UI connects to the service; disposing it never releases the service's player. */
@Composable
fun ShiroScreen(stageState: ShiroStageState = remember { ShiroStageState() }) {
    // Android Studio previews must not connect to a service.
    if (LocalInspectionMode.current) {
        ShiroLoadingScene()
        return
    }

    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var takingLonger by remember { mutableStateOf(false) }
    LaunchedEffect(controller, retry, failed) {
        takingLonger = false
        if (controller == null && !failed) {
            delay(10_000L)
            takingLonger = true
        }
    }
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

    Crossfade(targetState = controller != null, animationSpec = tween(220), label = "Player startup") { ready ->
        if (ready) controller?.let { ShiroScreen(it, stageState) }
        else ShiroLoadingScene(failed = failed, takingLonger = takingLonger, onRetry = { retry++ })
    }
}
