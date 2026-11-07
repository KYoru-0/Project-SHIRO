package com.example.shiro

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

/** Startup feedback only: no music analysis, physics, fake percentage, or minimum waiting time. */
@Composable
internal fun ShiroLoadingScene(failed: Boolean = false, takingLonger: Boolean = false,
    onRetry: () -> Unit = {}) {
    val colors = LocalPlayerColors.current
    val phase = remember { Animatable(0f) }
    val lifecycle = LocalLifecycleOwner.current
    val preview = LocalInspectionMode.current
    LaunchedEffect(lifecycle, failed, preview) {
        phase.snapTo(0f)
        if (failed || preview) return@LaunchedEffect
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive && ValueAnimator.areAnimatorsEnabled()) {
                phase.snapTo(0f)
                phase.animateTo(1f, tween(2600, easing = LinearEasing))
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().glassBackdrop(colors).safeDrawingPadding()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .heightIn(min = maxHeight).padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Box(Modifier.widthIn(max = 280.dp).fillMaxWidth().height(216.dp).drawWithCache {
                val glow = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.72f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.45f), radius = size.width * 0.5f)
                val plinth = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.8f), colors.selected))
                onDrawBehind {
                    drawRect(glow)
                    val width = size.width * 0.5f
                    val left = (size.width - width) * 0.5f
                    drawOval(colors.accent.copy(alpha = 0.07f), Offset(left - 5.dp.toPx(), size.height * 0.8f),
                        Size(width + 10.dp.toPx(), 18.dp.toPx()))
                    drawOval(plinth, Offset(left, size.height * 0.78f), Size(width, 16.dp.toPx()))
                    drawOval(Color.White.copy(alpha = 0.9f), Offset(left, size.height * 0.78f),
                        Size(width, 16.dp.toPx()), style = Stroke(1.dp.toPx()))
                }
            }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(240.dp, 160.dp).offset(y = (-10).dp).graphicsLayer {
                    val lift = (1f - kotlin.math.cos(phase.value * 2f * PI).toFloat()) * 0.5f
                    translationY = -lift * 9.dp.toPx()
                    scaleX = 1f - lift * 0.025f
                    scaleY = 1f + lift * 0.025f
                }) {
                    Image(painterResource(R.drawable.shiro_body_neutral), contentDescription = null, modifier = Modifier.fillMaxSize())
                    for (closed in listOf(false, true)) {
                        Image(painterResource(if (closed) R.drawable.shiro_eyes_closed else R.drawable.shiro_eyes_neutral),
                            contentDescription = null, colorFilter = ColorFilter.tint(colors.text),
                            modifier = Modifier.fillMaxSize().offset(x = 6.dp).graphicsLayer {
                                alpha = if (closed == (phase.value in 0.88f..0.93f)) 1f else 0f
                                scaleX = 0.6f; scaleY = 0.6f
                                transformOrigin = TransformOrigin(0.5f, 0.5137f)
                            })
                    }
                }
            }
            Text(stringResource(R.string.app_name), color = colors.text, fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 5.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(if (failed) R.string.startup_failed else if (takingLonger)
                R.string.startup_slow else R.string.startup_loading),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = colors.secondaryText, fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            if (!failed) Canvas(Modifier.width(76.dp).height(28.dp).semantics {
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            }) {
                val slot = size.width / 5f
                for (i in 0..4) {
                    val wave = (sin(phase.value * 2 * PI - i * 0.7).toFloat() + 1f) * 0.5f
                    val height = size.height * (0.25f + wave * 0.65f)
                    drawRoundRect(colors.accent.copy(alpha = 0.35f + wave * 0.5f),
                        Offset(slot * (i + 0.5f) - 2.dp.toPx(), (size.height - height) * 0.5f),
                        Size(4.dp.toPx(), height), CornerRadius(2.dp.toPx()))
                }
            }
            if (failed || takingLonger) {
                Text(stringResource(R.string.startup_retry_hint), color = colors.secondaryText,
                    fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                GlassButton(onClick = onRetry) { Text(stringResource(R.string.startup_retry)) }
            }
        }
    }
}

@Preview(name = "Startup", widthDp = 393, heightDp = 852)
@Preview(name = "Startup landscape", widthDp = 640, heightDp = 360, fontScale = 1.5f)
@Composable
private fun LoadingPreview() { ShiroGlassTheme { ShiroLoadingScene() } }

@Preview(name = "Startup retry", widthDp = 393, heightDp = 852)
@Composable
private fun LoadingErrorPreview() { ShiroGlassTheme { ShiroLoadingScene(failed = true) } }
