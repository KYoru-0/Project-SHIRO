package com.example.shiro

import androidx.compose.animation.core.animate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

private data class CatPose(val x: Float = 0f, val height: Float = 0f, val rotation: Float = 0f, val compression: Float = 0f, val foot: Float = 0f, val bend: Float = 0f, val contact: WaveContact? = null)

private data class CatInstance(
    val body: WaveCatBody,
    val pose: MutableState<CatPose>,
    var outline: CatOutline,
    val blinkState: MutableState<Boolean>,
    var prevContact: WaveContact? = null
)

/** Five jelly cats share the same rendered wave used for contact physics. */
@Composable
fun ShiroStage(
    isPlaying: Boolean,
    isBlinking: Boolean,
    isSleepy: Boolean,
    sleepProgress: Float,
    leftEyeColor: Color,
    rightEyeColor: Color,
    spectrum: FloatArray,
    artwork: TrackArtwork? = null,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    shiroCount: Int = 5
) {
    val night by animateFloatAsState(sleepProgress.coerceIn(0f, 1f), tween(900), label = "Bedtime lighting")
    val currentNight by rememberUpdatedState(night)
    val displayed = rememberSmoothSpectrum(spectrum, isPlaying, isVisible)
    val sprites = rememberJellySprites(leftEyeColor, rightEyeColor)
    BoxWithConstraints(modifier.clipToBounds()) {
        artwork?.let {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .align(Alignment.Center)
            ) {
                Image(
                    bitmap = it.image,
                    contentDescription = "Album cover background",
                    contentScale = ContentScale.Crop,
                    alpha = 0.50f,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        val waveHeight = if (maxHeight < 300.dp) 160.dp else 220.dp
        val catWidth = minOf(maxWidth * 0.16f, 78.dp)
        val catHeight = catWidth / 1.5f
        val widthDp = maxWidth.value
        val radius = catHeight.value * 0.4f
        fun bend(body: WaveCatBody) = (body.velocityX / 180f + body.spin / 500f).coerceIn(-1f, 1f)
        fun outline(body: WaveCatBody) = deformCatOutline(sprites.hull, catWidth.value, catHeight.value,
            body.compression, bend(body), body.rotation, body.contact)
        val ceiling = maxHeight.value

        val catsState = remember(widthDp, ceiling) {
            val initialCats = List(shiroCount) { i ->
                val body = WaveCatBody(widthDp / 2f + (i - shiroCount / 2f) * 24f).apply {
                    height = ceiling + 80f + i * 25f
                    rotation = Random.nextFloat() * 80f - 40f
                    velocityX = (Random.nextFloat() - 0.5f) * 60f
                    spin = (Random.nextFloat() - 0.5f) * 120f
                }
                val out = deformCatOutline(sprites.hull, catWidth.value, catHeight.value, 0f, 0f, body.rotation, null)
                CatInstance(
                    body = body,
                    pose = mutableStateOf(CatPose(body.x, body.height, body.rotation)),
                    outline = out,
                    blinkState = mutableStateOf(false)
                )
            }
            mutableStateOf(initialCats)
        }

        LaunchedEffect(shiroCount, widthDp, ceiling) {
            val current = catsState.value
            if (current.size < shiroCount) {
                val added = List(shiroCount - current.size) { i ->
                    val idx = current.size + i
                    val body = WaveCatBody(widthDp / 2f + (Random.nextFloat() - 0.5f) * 40f).apply {
                        height = ceiling + 80f + idx * 25f
                        rotation = Random.nextFloat() * 80f - 40f
                        velocityX = (Random.nextFloat() - 0.5f) * 80f
                        spin = (Random.nextFloat() - 0.5f) * 150f
                    }
                    val out = deformCatOutline(sprites.hull, catWidth.value, catHeight.value, 0f, 0f, body.rotation, null)
                    CatInstance(
                        body = body,
                        pose = mutableStateOf(CatPose(body.x, body.height, body.rotation)),
                        outline = out,
                        blinkState = mutableStateOf(false)
                    )
                }
                catsState.value = current + added
            } else if (current.size > shiroCount) {
                catsState.value = current.take(shiroCount)
            }
        }

        val cats = catsState.value
        val scope = rememberCoroutineScope()

        // Auto-blink: all cats blink together on a synced 3–7 s timer.
        LaunchedEffect(cats) {
            while (isActive) {
                delay((3_000L..7_000L).random())
                cats.forEach { it.blinkState.value = true }
                delay(120L)
                cats.forEach { it.blinkState.value = false }
            }
        }

        var tapVersion by remember { mutableIntStateOf(0) }
        fun nudge(index: Int) {
            val cat = cats.getOrNull(index) ?: return
            cat.body.tap(if (cat.body.x < widthDp / 2f) 1f else -1f)
            tapVersion++
        }

        LaunchedEffect(isVisible, isPlaying, waveHeight, cats, ceiling, sprites, tapVersion) {
            if (!isVisible || widthDp <= 0f || radius <= 0f) return@LaunchedEffect
            var lastFrame = 0L
            while (isActive) {
                val now = withFrameNanos { it }
                val dt = (if (lastFrame == 0L) 1f / 60f else (now - lastFrame) / 1_000_000_000f).coerceAtMost(1f / 30f)
                lastFrame = now
                val bands = displayed.value
                val amplitude = (waveHeight.value - 2f) * (1f - currentNight * 0.22f)
                val steps = ceil(dt * 120f).toInt().coerceIn(1, 4)
                val currentBodies = cats.map { it.body }
                val currentOutlines = cats.map { it.outline }.toMutableList()
                repeat(steps) {
                    cats.forEachIndexed { index, cat ->
                        val body = cat.body
                        val shape = cat.outline
                        val center = body.x / widthDp
                        val spread = radius / widthDp
                        val surface = waveHeightAt(bands, center) * amplitude
                        val slope = (waveHeightAt(bands, center + spread) -
                            waveHeightAt(bands, center - spread)) * amplitude / (2f * radius)
                        val wave = sampleCatWave(shape, bands, body.x, widthDp, amplitude)
                        val contact = waveContactAt(wave.point, body.x, body.rotation, catWidth.value,
                            catHeight.value, bands, widthDp, amplitude)
                        body.step(wave.centerHeight - shape.support, slope, dt / steps, shape.support, surface, contact)
                        cat.outline = outline(body)
                        currentOutlines[index] = cat.outline
                        body.contain(widthDp, ceiling, cat.outline)
                    }
                    if (currentBodies.size == currentOutlines.size) {
                        resolveCatContacts(currentBodies, currentOutlines)
                    }
                }
                cats.forEach { cat ->
                    val body = cat.body
                    val shape = cat.outline
                    cat.pose.value = CatPose(body.x, body.height, body.rotation, body.compression,
                        body.height - shape.support, bend(body), body.contact)
                    val hadContact = cat.prevContact != null
                    val hasContact = body.contact != null
                    if (hasContact && !hadContact && !cat.blinkState.value) {
                        scope.launch {
                            cat.blinkState.value = true
                            delay(120L)
                            cat.blinkState.value = false
                        }
                    }
                    cat.prevContact = body.contact
                }
                if (!isPlaying && bands.all { it < 0.001f } &&
                    cats.all { it.body.isSettled(it.outline.support) }) break
            }
        }

        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.verticalGradient(listOf(Color.Transparent,
                Color(0xFF47618A).copy(alpha = night * 0.24f), Color.Transparent)))
        }
        SpectrumWave(
            displayed, cats.map { it.pose }, night, isPlaying, artwork?.colors,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(waveHeight)
        )
        cats.forEachIndexed { index, cat ->
            WaveCat(
                pose = cat.pose,
                isBlinking = isBlinking || cat.blinkState.value, isSleepy = isSleepy,
                sprites = sprites,
                night = night,
                onNudge = { nudge(index) },
                modifier = Modifier.align(Alignment.BottomStart).size(catWidth, catHeight)
                    .graphicsLayer {
                        val pose = cat.pose.value
                        transformOrigin = TransformOrigin(0.5f, 0.55f)
                        translationX = (pose.x - catWidth.value / 2f).dp.toPx()
                        translationY = (catHeight.value * 0.45f - pose.height).dp.toPx()
                        rotationZ = pose.rotation
                    }
            )
        }
        val density = LocalDensity.current.density
        Box(Modifier.matchParentSize().pointerInput(cats, sprites, density, ceiling) {
            detectTapGestures { position ->
                val x = position.x / density
                val y = ceiling - position.y / density
                cats.indices.reversed().firstOrNull { index ->
                    val pose = cats.getOrNull(index)?.pose?.value ?: return@firstOrNull false
                    val shape = deformCatOutline(sprites.hull, catWidth.value, catHeight.value,
                        pose.compression, pose.bend, pose.rotation, pose.contact)
                    shape.contains(x - pose.x, y - pose.height)
                }?.let { nudge(it) }
            }
        })
    }
}

@Composable
private fun WaveCat(
    pose: State<CatPose>, isBlinking: Boolean, isSleepy: Boolean,
    sprites: JellySprites, night: Float, onNudge: () -> Unit, modifier: Modifier
) {
    JellyCat(
        sprites = sprites,
        isBlinking = isBlinking, isSleepy = isSleepy,
        jelly = { pose.value.compression },
        bend = { pose.value.bend },
        contact = { pose.value.contact },
        onTap = onNudge,
        modifier = modifier.graphicsLayer {
            alpha = 1f - night * 0.1f
        }
    )
}

@Composable
private fun rememberSmoothSpectrum(spectrum: FloatArray, isPlaying: Boolean, isVisible: Boolean): State<FloatArray> {
    val displayed = remember { mutableStateOf(FloatArray(SpectrumFrame.BAND_COUNT)) }
    LaunchedEffect(spectrum, isPlaying, isVisible) {
        if (!isVisible) {
            displayed.value = FloatArray(SpectrumFrame.BAND_COUNT)
            return@LaunchedEffect
        }
        val start = displayed.value
        val target = FloatArray(SpectrumFrame.BAND_COUNT) { index ->
            val level = if (isPlaying) spectrum.getOrElse(index) { 0f } else 0f
            if (level.isFinite()) level.coerceIn(0f, 1f) else 0f
        }
        if (start.contentEquals(target)) return@LaunchedEffect
        animate(0f, 1f, animationSpec = tween(
            durationMillis = if (isPlaying) 90 else 1_100,
            easing = if (isPlaying) LinearEasing else LinearOutSlowInEasing
        )) { progress, _ ->
            displayed.value = FloatArray(target.size) { start[it] + (target[it] - start[it]) * progress }
        }
    }
    return displayed
}

@Composable
private fun SpectrumWave(
    displayed: State<FloatArray>, poses: List<State<CatPose>>, night: Float,
    isPlaying: Boolean, colors: List<Color>?, modifier: Modifier
) {
    val palette = colors ?: listOf(Color(0xFF7DD3FC), Color(0xFF0284C7), Color(0xFF075985))
    val top by animateColorAsState(palette[0], tween(600), label = "Wave top color")
    val middle by animateColorAsState(palette[1], tween(600), label = "Wave middle color")
    val bottom by animateColorAsState(palette[2], tween(600), label = "Wave bottom color")
    Canvas(modifier.semantics {
        contentDescription = if (isPlaying) "Three cats riding the music spectrum, bass to treble" else "Three cats resting on the paused spectrum"
    }) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val amplitude = (size.height - 2.dp.toPx()).coerceAtLeast(0f) * (1f - night * 0.22f)
        val bands = displayed.value
        val path = Path()
        // Sampling the smooth curve also lets each cat make a small local dent in it.
        for (point in 0..320) {
            val fraction = point / 320f
            var height = waveHeightAt(bands, fraction) * amplitude
            poses.forEach { poseState ->
                val pose = poseState.value
                val center = pose.x.dp.toPx() / size.width
                val surface = waveHeightAt(bands, center) * amplitude
                val foot = pose.foot.dp.toPx()
                val contact = (1f - (foot - (surface - 2.dp.toPx()).coerceAtLeast(0f)) / 12.dp.toPx()).coerceIn(0f, 1f)
                val distance = (fraction - center) / 0.035f
                height -= 2.dp.toPx() * contact * exp(-distance * distance * 0.5f)
            }
            val x = fraction * size.width
            val y = size.height - height.coerceAtLeast(0f)
            if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(top, middle, bottom).map {
            lerp(it, Color(0xFF26364E), night * 0.3f)
        }))
        // Only the open crest is outlined; width is two physical pixels, independent of density.
        drawPath(path, Color(0xFF0284C7), style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Preview(name = "Five cats on the wave", widthDp = 380, heightDp = 390, showBackground = true)
@Composable
private fun ShiroStagePreview() {
    ShiroStage(
        isPlaying = true, isBlinking = false, isSleepy = false, sleepProgress = 0f,
        leftEyeColor = Color(0xFF23465B), rightEyeColor = Color(0xFF23465B),
        spectrum = FloatArray(24) { 0.15f + (sin(it * 0.8f) + 1f) * 0.3f },
        modifier = Modifier.fillMaxSize().background(Color(0xFFE8F4F8))
    )
}
