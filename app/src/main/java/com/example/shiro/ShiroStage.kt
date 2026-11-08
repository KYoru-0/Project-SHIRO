package com.example.shiro

import android.animation.ValueAnimator

import androidx.compose.animation.core.animate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlin.math.sin

private data class CatPose(val x: Float = 0f, val height: Float = 0f, val rotation: Float = 0f, val compression: Float = 0f, val bend: Float = 0f, val contact: WaveContact? = null)

/** One scene clock drives the selected visualizer and the full-detail jelly cats. */
@Composable
fun ShiroStage(
    isPlaying: Boolean,
    isBlinking: Boolean,
    isSleepy: Boolean,
    sleepProgress: Float,
    leftEyeColor: Color,
    rightEyeColor: Color,
    spectrum: FloatArray,
    dynamics: MusicDynamics = MusicDynamics(),
    artwork: TrackArtwork? = null,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    revealOnEntry: Boolean = true,
    onRevealStarted: () -> Unit = {},
    visualizerMode: VisualizerMode = VisualizerMode.SPECTRUM,
    visualizerLayout: VisualizerLayout = VisualizerLayout.FLOOR,
    trackKey: String? = null,
    state: ShiroStageState = remember { ShiroStageState() },
    spectrumAvailable: Boolean = true
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val circular = visualizerLayout == VisualizerLayout.CIRCLE
    val night by animateFloatAsState(sleepProgress.coerceIn(0f, 1f), tween(900), label = "Bedtime lighting")
    val currentNight by rememberUpdatedState(night)
    val currentDynamics by rememberUpdatedState(dynamics)
    val circularDrift = state.circularDrift
    val displayed = rememberSmoothSpectrum(spectrum, dynamics, isPlaying, isVisible, state, spectrumAvailable)
    val peaks = rememberSpectrumPeaks(displayed, isPlaying, isVisible && visualizerMode == VisualizerMode.SPECTRUM, state)
    val sprites = rememberJellySprites(leftEyeColor, rightEyeColor)
    val reveal = state.reveal
    LaunchedEffect(isVisible) {
        if (!isVisible || reveal.floatValue >= 1f) return@LaunchedEffect
        if (!revealOnEntry) { reveal.floatValue = 1f; return@LaunchedEffect }
        onRevealStarted()
        try {
            animate(0f, 1f, animationSpec = tween(SHIRO_REVEAL_MILLIS, easing = LinearEasing)) { value, _ ->
                reveal.floatValue = value
            }
        } finally {
            // Interrupted entrances finish silently rather than replaying after a menu or backgrounding.
            reveal.floatValue = 1f
        }
    }
    val splashColor by animateColorAsState(artwork?.colors?.get(1) ?: Color(0xFF0284C7),
        tween(600), label = "Landing effect color")
    val reflection = rememberJellyReflection(splashColor)
    val contactShadow = remember(splashColor) {
        Brush.radialGradient(listOf(lerp(splashColor, Color.Black, 0.78f), Color.Transparent),
            center = Offset.Zero, radius = 1f)
    }
    BoxWithConstraints(modifier.clipToBounds()) {
        val waveHeight = if (maxHeight < 300.dp) 160.dp else 220.dp
        val catWidth = minOf(maxWidth * 0.16f, 78.dp)
        val catHeight = catWidth / 1.5f
        val widthDp = maxWidth.value
        val glassMotion = remember(widthDp) { GlassSceneMotion(widthDp.coerceAtLeast(1f)) }
        val liquidMotion = remember(widthDp, visualizerMode) { LiquidWaveMotion(widthDp.coerceAtLeast(1f)) }
        val visualMotion = remember(visualizerMode, visualizerLayout) { VisualizerMotion() }
        val visualFrame = remember(visualMotion) { mutableStateOf(VisualizerFrame()) }
        val circularMotion = remember(widthDp, maxHeight, visualizerMode, visualizerLayout) {
            CircularSpectrumMotion(visualizerMode, widthDp, maxHeight.value)
        }
        val circularScene = remember(circularMotion) { mutableStateOf<CircularScene?>(null) }
        val glassScene = remember(glassMotion, visualizerMode) { mutableStateOf(GlassScene(emptyList(), 0f, 0f)) }
        val radius = catHeight.value * 0.4f
        fun bend(body: WaveCatBody) = (body.velocityX / 180f + body.spin / 500f).coerceIn(-1f, 1f)
        fun outline(body: WaveCatBody) = body.outlineCache.get(sprites.hull,
            catWidth.value * body.sizeScale, catHeight.value * body.sizeScale,
            body.compression, bend(body), body.rotation, body.contact)
        val ceiling = maxHeight.value
        if (widthDp <= 0f || ceiling <= 0f) return@BoxWithConstraints
        val bodies = state.bodiesFor(widthDp, ceiling, radius)
        val beatBounce = remember(bodies) { CatBeatBounce() }
        val centerField = remember(bodies, ceiling) { CenterCatField(widthDp, ceiling, bodies.size) }
        fun sampleSurface(body: WaveCatBody, shape: CatOutline, amplitude: Float): CatWaveSample =
            when {
                circular -> CatWaveSample(shape.support, shape.supportPoint, 0f,
                    CatPoint(body.x + shape.supportPoint.x, 0f))
                visualizerMode.hasCurvedSurface -> body.glassSampler.sample(shape, glassScene.value, body.x, body.height)
                else -> sampleCatWave(shape, displayed.value, body.x, widthDp, amplitude, body.waveSampler)
            }
        var populationVersion by remember(bodies) { mutableIntStateOf(0) }
        val activeIndices = remember(bodies, populationVersion) { bodies.indices.filter { bodies[it].active } }
        val poses = remember(bodies, widthDp, ceiling) { bodies.map {
            mutableStateOf(CatPose(it.x, it.height, it.rotation, it.compression, bend(it), it.contact))
        } }
        val outlines = remember(bodies, sprites, widthDp, ceiling) { bodies.map { outline(it) }.toMutableList() }
        val impacts = remember(bodies, ceiling) { SpectrumImpacts(bodies.size) }
        val splashes = remember(impacts) { mutableStateOf<List<SpectrumSplash>>(emptyList()) }
        var tapVersion by remember { mutableIntStateOf(0) }
        fun publishPose(index: Int) {
            val body = bodies[index]
            poses[index].value = CatPose(body.x, body.height, body.rotation, body.compression,
                bend(body), body.contact)
        }
        fun nudge(index: Int) {
            if (reveal.floatValue < 1f) return
            val body = bodies[index]
            if (!body.active || body.isHeld) return
            body.tap(if (body.x < widthDp / 2f) 1f else -1f)
            tapVersion++
        }
        fun fitToStage(index: Int) {
            val body = bodies[index]
            val amplitude = (waveHeight.value - 12f) * (1f - currentNight * 0.22f)
            body.contain(widthDp, ceiling, outline(body))
            val shape = outline(body)
            body.height = maxOf(body.height,
                sampleSurface(body, shape, amplitude).centerHeight)
            body.contain(widthDp, ceiling, shape)
            outlines[index] = outline(body)
            publishPose(index)
        }
        fun split(index: Int): Boolean {
            if (reveal.floatValue < 1f) return false
            val children = splitShiro(bodies, index, catWidth.value) ?: return false
            children.forEach { fitToStage(it) }
            populationVersion++
            tapVersion++
            return true
        }
        fun merge(index: Int): Int? {
            bodies.indices.forEach { if (bodies[it].active) outlines[it] = outline(bodies[it]) }
            val merged = mergeDraggedShiro(bodies, index, outlines) ?: return null
            fitToStage(merged)
            populationVersion++
            tapVersion++
            return merged
        }
        LaunchedEffect(lifecycleOwner, isVisible, isPlaying, isSleepy, bodies, ceiling, populationVersion) {
            if (!isVisible || isPlaying || isSleepy) return@LaunchedEffect
            // delay() keeps running when the Compose frame clock pauses, so gate it directly.
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // Let the spectrum descend and the entrance finish before the first idle hop.
                delay(1800L)
                var turn = 0
                while (isActive) {
                    val index = activeIndices[turn % activeIndices.size]
                    val body = bodies[index]
                    if (body.active && reveal.floatValue >= 1f && displayed.value.all { it < 0.001f } &&
                        body.isSettled(outline(body).support)) {
                        val direction = when {
                            body.x < widthDp * 0.2f -> 1f
                            body.x > widthDp * 0.8f -> -1f
                            turn % 2 == 0 -> 1f
                            else -> -1f
                        }
                        body.idleHop(direction)
                        tapVersion++
                    }
                    turn++
                    delay(800L + turn % SHIRO_ORIGINAL_COUNT * 60L)
                }
            }
        }
        LaunchedEffect(isVisible, isPlaying, waveHeight, bodies, ceiling, sprites, tapVersion, populationVersion, visualizerMode, visualizerLayout, trackKey) {
            if (!isVisible || widthDp <= 0f || radius <= 0f) {
                // Freeze the scene while hidden. Returning resumes it with a fresh frame clock.
                return@LaunchedEffect
            }
            if (!circular) { centerField.reset(); circularDrift.stop() }
            var lastFrame = 0L
            val physicsClock = CatPhysicsClock()
            while (isActive) {
                val now = withFrameNanos { it }
                val elapsed = if (lastFrame == 0L) 16_666_667L else (now - lastFrame).coerceAtLeast(0L)
                val dt = (elapsed / 1_000_000_000f).coerceAtMost(1f / 30f)
                lastFrame = now
                impacts.advance(dt)
                val bands = displayed.value
                var gradientRotation: Float? = null
                var gradientCircleHeight: Float? = null
                val amplitude = (waveHeight.value - 12f) * (1f - currentNight * 0.22f)
                if (circular) {
                    val frame = visualMotion.advance(bands, currentDynamics, isPlaying, dt, false)
                    centerField.advance(frame, isPlaying && reveal.floatValue >= 1f, currentNight, dt)
                    val visibleFrame = frame.copy(fieldStrength = centerField.strength)
                    val rotation = circularDrift.advance(trackKey, frame.energy, isPlaying, dt,
                        motionEnabled = ValueAnimator.areAnimatorsEnabled())
                    val scene = circularMotion.advance(visibleFrame, dt, rotation)
                    gradientRotation = rotation.angle
                    gradientCircleHeight = scene.bounds.height
                    centerField.useSurface(scene)
                    circularScene.value = scene
                    visualFrame.value = visibleFrame
                    if (isPlaying && reveal.floatValue >= 1f) centerField.burst(bodies, frame.onset * (1f - currentNight))
                } else if (visualizerMode == VisualizerMode.PLAYGROUND) {
                    glassScene.value = glassMotion.advance(bands, currentDynamics, isPlaying, dt, amplitude)
                } else if (visualizerMode != VisualizerMode.SPECTRUM) {
                    val frame = visualMotion.advance(bands, currentDynamics, isPlaying, dt, visualizerMode == VisualizerMode.RIPPLES)
                    visualFrame.value = frame.copy(fieldStrength = centerField.strength)
                    if (visualizerMode == VisualizerMode.LIQUID) {
                        glassScene.value = liquidMotion.advance(frame, dt, amplitude)
                    } else if (visualizerMode.isAmbient && isPlaying && reveal.floatValue >= 1f) {
                        beatBounce.trigger(bodies, outlines, frame.onset * (1f - currentNight), widthDp)
                    }
                }
                val impactDrive = if (isPlaying) currentDynamics.impactDrive * (1f - currentNight) else 0f
                state.backdrop.value = state.backdropMotion.advance(bands, currentDynamics, isPlaying, dt,
                    motionEnabled = ValueAnimator.areAnimatorsEnabled(),
                    rotation = gradientRotation, circleHeight = gradientCircleHeight)
                // Briefly show the gathered group, then release it without accumulating catch-up steps.
                val steps = if (reveal.floatValue * SHIRO_REVEAL_MILLIS < SHIRO_GATHER_MILLIS) 0
                    else physicsClock.steps(elapsed)
                repeat(steps) {
                    activeIndices.forEach { index ->
                        val body = bodies[index]
                        if (!body.active) return@forEach
                        if (body.isHeld) {
                            outlines[index] = outline(body)
                            return@forEach
                        }
                        val shape = outline(body)
                        val center = body.x / widthDp
                        val bodyRadius = radius * body.sizeScale
                        val spread = bodyRadius / widthDp
                        val wave = sampleSurface(body, shape, amplitude)
                        val slope = when {
                            circular -> 0f
                            visualizerMode.hasCurvedSurface -> -wave.normalX / wave.normalY.coerceAtLeast(0.01f)
                            visualizerMode.isAmbient -> 0f
                            else -> (spectrumBandAt(bands, center + spread) -
                                spectrumBandAt(bands, center - spread)) * amplitude / (2f * bodyRadius)
                        }
                        val contact = when {
                            circular -> waveContactAt(shape.supportPoint, body.rotation,
                                catWidth.value * body.sizeScale, catHeight.value * body.sizeScale, bands.size, widthDp)
                            visualizerMode.hasCurvedSurface -> waveContactAt(
                                CatPoint(wave.hit.x - body.x, wave.hit.y - wave.centerHeight), body.rotation,
                                catWidth.value * body.sizeScale, catHeight.value * body.sizeScale, bands.size, widthDp,
                                wave.normalX, wave.normalY, wave.contactWidth)
                            visualizerMode.isAmbient -> waveContactAt(shape.supportPoint, body.rotation,
                                catWidth.value * body.sizeScale, catHeight.value * body.sizeScale, bands.size, widthDp)
                            else -> {
                                val slot = widthDp / bands.size
                                val hitBar = (wave.hit.x / slot).toInt().coerceIn(0, bands.lastIndex)
                                val contactX = (body.x + wave.point.x).coerceIn(
                                    (hitBar + SPECTRUM_BAR_INSET) * slot, (hitBar + 1f - SPECTRUM_BAR_INSET) * slot)
                                waveContactAt(CatPoint(contactX - body.x, wave.hit.y - wave.centerHeight), body.rotation,
                                    catWidth.value * body.sizeScale, catHeight.value * body.sizeScale, bands.size, widthDp)
                            }
                        }
                        val gravity = if (circular)
                            centerField.pull(index, body, shape, CatPhysicsClock.STEP_SECONDS) else 1f
                        val landing = body.step(wave.centerHeight - shape.support, slope, CatPhysicsClock.STEP_SECONDS,
                            shape.support, wave.surfaceHeight, contact, impactDrive, gravity)
                        impacts.hit(index, wave.hit, landing)
                        if (landing > 0f || body.height <= wave.centerHeight + 2f) {
                            if (visualizerMode == VisualizerMode.PLAYGROUND) {
                                glassMotion.contact(wave.surfaceId, wave.hit.x, landing, body.sizeScale * body.sizeScale)
                            } else if (!circular && visualizerMode == VisualizerMode.LIQUID && wave.surfaceId == -1) {
                                liquidMotion.contact(wave.hit.x, landing, body.sizeScale * body.sizeScale)
                            }
                        }
                        outlines[index] = outline(body)
                        if (landing > 70f) {
                            val pressed = sampleSurface(body, outlines[index], amplitude)
                            // The compressed silhouette remains on the bar, rather than hovering above it.
                            body.height = maxOf(outlines[index].support, pressed.centerHeight)
                        }
                    }
                    resolveCatContacts(bodies, outlines)
                    // Wall reflection can change the bend; recheck the reflected outline as well.
                    repeat(2) { pass ->
                        activeIndices.forEach { index ->
                            val body = bodies[index]
                            if (!body.active) return@forEach
                            outlines[index] = outline(body)
                            if (circular) {
                                val hit = centerField.resolve(body, outlines[index], catWidth.value * body.sizeScale,
                                    catHeight.value * body.sizeScale, deform = pass == 0)
                                if (pass == 0 && hit != null) {
                                    impacts.hit(index, hit.hit, hit.impact)
                                    circularMotion.contact(hit.hit.x - centerField.bounds.x, hit.hit.y - centerField.bounds.height,
                                        hit.impact, body.sizeScale * body.sizeScale)
                                }
                                outlines[index] = outline(body)
                            }
                            body.contain(widthDp, ceiling, outlines[index])
                        }
                    }
                }
                activeIndices.forEach { publishPose(it) }
                splashes.value = impacts.snapshot()
                // Sleep between occasional idle hops once all physics and effects have settled.
                if (reveal.floatValue >= 1f && !isPlaying && state.backdropMotion.isSettled && circularDrift.isSettled && centerField.strength == 0f && impacts.isEmpty && visualFrame.value.ripples.isEmpty() && bands.all { it < 0.001f } &&
                    activeIndices.all { bodies[it].isSettled(outlines[it].support) }) break
            }
        }

        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.verticalGradient(listOf(Color.Transparent,
                Color(0xFF47618A).copy(alpha = night * 0.24f), Color.Transparent)))
        }
        if (circular) CircularMusicStage(circularScene, peaks, splashColor, night, Modifier.matchParentSize())
        else when (visualizerMode) {
            VisualizerMode.SPECTRUM -> SpectrumBars(displayed, peaks, night, isPlaying, artwork?.colors,
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(waveHeight))
            VisualizerMode.LIQUID -> LiquidWaves(glassScene, splashColor, night, Modifier.matchParentSize())
            VisualizerMode.PLAYGROUND -> GlassPlayground(glassScene, splashColor, night, Modifier.matchParentSize())
            else -> MusicVisuals(visualizerMode, visualFrame, splashColor, night, Modifier.matchParentSize())
        }
        Canvas(Modifier.matchParentSize()) {
            if (circular || visualizerMode != VisualizerMode.SPECTRUM) {
                for (index in activeIndices) {
                    val pose = poses[index].value
                    val shape = outlines[index]
                    val foot = pose.height - shape.support
                    var piece: GlassPiece? = null
                    var surface = 0f
                    for (candidate in if (circular) emptyList() else glassScene.value.pieces) {
                        if (pose.x < candidate.left || pose.x > candidate.right || candidate.level < 0.001f) continue
                        val height = candidate.heightAt(pose.x)
                        if (height > surface && height <= foot + 3f) { piece = candidate; surface = height }
                    }
                    val reach = catHeight.value * bodies[index].sizeScale * 1.6f
                    val proximity = (1f - (foot - surface).coerceAtLeast(0f) / reach).coerceIn(0f, 1f)
                    val alpha = proximity * proximity * 0.24f
                    if (alpha < 0.005f) continue
                    val radiusX = (shape.right - shape.left).dp.toPx() * 0.42f
                    val radiusY = 2.dp.toPx() * bodies[index].sizeScale
                    val x = pose.x.dp.toPx()
                    val y = size.height - surface.dp.toPx() - if (surface == 0f) 0.8.dp.toPx() else 0f
                    clipRect(left = (piece?.left ?: 0f).dp.toPx(), right = (piece?.right ?: widthDp).dp.toPx()) {
                        withTransform({ translate(x, y); scale(radiusX, radiusY, pivot = Offset.Zero) }) {
                            drawCircle(contactShadow, radius = 1f, center = Offset.Zero, alpha = alpha)
                        }
                    }
                }
                return@Canvas
            }
            val bands = displayed.value
            if (bands.isEmpty()) return@Canvas
            val slot = size.width / bands.size
            val amplitude = (waveHeight.toPx() - 12.dp.toPx()) * (1f - night * 0.22f)
            for (index in activeIndices) {
                val pose = poses[index].value
                val shape = outlines[index]
                val centerX = (pose.x + (shape.left + shape.right) * 0.5f).dp.toPx()
                val foot = (pose.height - shape.support).dp.toPx()
                val reach = catHeight.toPx() * bodies[index].sizeScale * 1.6f
                val radiusX = (shape.right - shape.left).dp.toPx() * 0.48f
                val first = ((centerX - radiusX) / slot).toInt().coerceIn(0, bands.lastIndex)
                val last = ((centerX + radiusX) / slot).toInt().coerceIn(0, bands.lastIndex)
                // Project onto nearby bar tops, clipped inside their faces so gaps stay clear.
                // Cached outline bounds suffice for lighting; collision geometry is untouched.
                for (bar in first..last) {
                    val height = bands[bar] * amplitude
                    val proximity = (1f - (foot - height).coerceAtLeast(0f) / reach).coerceIn(0f, 1f)
                    val alpha = proximity * proximity * 0.24f
                    if (alpha < 0.005f || radiusX <= 0f) continue
                    val baseline = height < 1.dp.toPx()
                    val top = size.height - height
                    val y = if (baseline) size.height - 0.8.dp.toPx() else top + 0.6.dp.toPx()
                    val radiusY = (if (baseline) 1.6f else 3.2f).dp.toPx() * bodies[index].sizeScale
                    clipRect(left = (bar + SPECTRUM_BAR_INSET) * slot,
                        top = if (baseline) 0f else top,
                        right = (bar + 1f - SPECTRUM_BAR_INSET) * slot, bottom = size.height) {
                        withTransform({
                            translate(centerX, y)
                            scale(radiusX, radiusY, pivot = Offset.Zero)
                        }) { drawCircle(contactShadow, radius = 1f, center = Offset.Zero, alpha = alpha) }
                    }
                }
            }
        }
        SpectrumSplashLayer(splashes, splashColor, Modifier.matchParentSize())
        activeIndices.forEach { index ->
            val body = bodies[index]
            val bodyWidth = catWidth * body.sizeScale
            val bodyHeight = catHeight * body.sizeScale
            key(index) {
                WaveCat(
                    pose = poses[index],
                    isBlinking = isBlinking, isSleepy = isSleepy,
                    sprites = sprites,
                    reflection = reflection,
                    night = night,
                    onNudge = { nudge(index) },
                    onSplit = if (body.generation < SHIRO_MAX_GENERATION) ({ split(index); Unit }) else null,
                    modifier = Modifier.align(Alignment.BottomStart).size(bodyWidth, bodyHeight)
                        .graphicsLayer {
                            val pose = poses[index].value
                            transformOrigin = TransformOrigin(0.5f, 0.55f)
                            alpha = (reveal.floatValue * 12f).coerceAtMost(1f)
                            translationX = (pose.x - bodyWidth.value / 2f).dp.toPx()
                            translationY = (bodyHeight.value * 0.45f - pose.height).dp.toPx()
                            rotationZ = pose.rotation
                        }
                )
            }
        }
        val density = LocalDensity.current.density
        Box(Modifier.matchParentSize().pointerInput(bodies, sprites, density, ceiling, isVisible, visualizerMode, visualizerLayout) {
            if (!isVisible) return@pointerInput
            val taps = ShiroTapSequence()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (reveal.floatValue < 1f) return@awaitEachGesture
                val x = down.position.x / density
                val y = ceiling - down.position.y / density
                val hit = bodies.indices.reversed().firstOrNull { index ->
                    val body = bodies[index]
                    if (!body.active) return@firstOrNull false
                    val pose = poses[index].value
                    val shape = body.outlineCache.get(sprites.hull,
                        catWidth.value * body.sizeScale, catHeight.value * body.sizeScale,
                        pose.compression, pose.bend, pose.rotation, pose.contact)
                    shape.contains(x - pose.x, y - pose.height)
                }
                val second = taps.secondTarget(hit, down.position.x, down.position.y, down.uptimeMillis,
                    viewConfiguration.doubleTapMinTimeMillis, viewConfiguration.doubleTapTimeoutMillis,
                    viewConfiguration.touchSlop * 2f)?.takeIf { bodies[it].active }
                taps.clear()
                var index = second ?: hit ?: return@awaitEachGesture
                val secondTap = second != null
                // Claim a cat or its recent tap target; other empty space stays scrollable.
                down.consume()
                var body = bodies[index]
                var grabOffset = Offset(body.x - x, body.height - y)
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var dragging = false
                var hasMerged = false
                body.grab()
                publishPose(index)
                tapVersion++
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragging = true
                        val velocity = tracker.calculateVelocity()
                        change.consume()
                        if (dragging) {
                            body.dragTo(change.position.x / density + grabOffset.x,
                                ceiling - change.position.y / density + grabOffset.y,
                                velocity.x / density, -velocity.y / density)
                            body.contain(widthDp, ceiling, outline(body))
                            if (!hasMerged) {
                                val merged = merge(index)
                                if (merged != null) {
                                    index = merged
                                    body = bodies[index]
                                    body.grab()
                                    fitToStage(index)
                                    grabOffset = Offset(body.x - change.position.x / density,
                                        body.height - (ceiling - change.position.y / density))
                                    hasMerged = true
                                }
                            }
                            publishPose(index)
                        }
                        if (!change.pressed) {
                            if (dragging) {
                                body.release(velocity.x / density, -velocity.y / density)
                                if (hasMerged) body.wobble(0.4f)
                            }
                            else if (secondTap) {
                                body.release()
                                if (!split(index)) nudge(index)
                            } else {
                                body.release()
                                nudge(index)
                                publishPose(index)
                                taps.record(index, change.position.x, change.position.y, change.uptimeMillis)
                            }
                            tapVersion++
                            break
                        }
                    }
                } finally {
                    // Rotation, a menu, or gesture cancellation drops the cat without a surprise fling.
                    if (body.isHeld) { body.release(); tapVersion++ }
                }
            }
        })
    }
}

@Composable
private fun WaveCat(
    pose: State<CatPose>, isBlinking: Boolean, isSleepy: Boolean,
    sprites: JellySprites, reflection: IntArray, night: Float,
    onNudge: () -> Unit, onSplit: (() -> Unit)?, modifier: Modifier
) {
    JellyCat(
        sprites = sprites,
        reflection = reflection,
        isBlinking = isBlinking, isSleepy = isSleepy,
        jelly = { pose.value.compression },
        bend = { pose.value.bend },
        contact = { pose.value.contact },
        onTap = onNudge,
        onSplit = onSplit,
        modifier = modifier.graphicsLayer {
            alpha = 1f - night * 0.1f
        }
    )
}

@Composable
private fun rememberSmoothSpectrum(spectrum: FloatArray, dynamics: MusicDynamics,
    isPlaying: Boolean, isVisible: Boolean, state: ShiroStageState, spectrumAvailable: Boolean): State<FloatArray> {
    val displayed = state.displayed
    val resumeRamp = remember { SpectrumResumeRamp() }
    val latestBands by rememberUpdatedState(spectrum)
    val latestDynamics by rememberUpdatedState(dynamics)
    val latestAvailable by rememberUpdatedState(spectrumAvailable)
    val emptyBands = remember { FloatArray(SpectrumFrame.BAND_COUNT) }
    LaunchedEffect(isPlaying, isVisible) {
        if (!isVisible) {
            resumeRamp.reset()
            return@LaunchedEffect
        }
        var lastFrame = 0L
        val handoff = SpectrumHandoff()
        resumeRamp.reset()
        // One loop reads the latest audio target; a new FFT frame no longer restarts an animation.
        while (isActive) {
            val now = withFrameNanos { it }
            val elapsed = if (lastFrame == 0L) 1f / 60f else (now - lastFrame) / 1_000_000_000f
            val dt = elapsed.coerceIn(0f, 1f / 30f)
            lastFrame = now
            // Physics and liquid motion still run against the retained surface during this handoff.
            if (handoff.shouldHold(isPlaying, latestAvailable, elapsed)) continue
            val bands = if (latestAvailable) latestBands else emptyBands
            val maximumRise = resumeRamp.maximumRise(isPlaying, bands, dt)
            displayed.value = advanceMusicSpectrum(displayed.value, bands, latestDynamics, isPlaying, dt, maximumRise)
            if (!isPlaying && displayed.value.all { it == 0f }) break
        }
    }
    return displayed
}

@Composable
private fun rememberSpectrumPeaks(displayed: State<FloatArray>, isPlaying: Boolean, isVisible: Boolean,
    state: ShiroStageState): State<FloatArray> {
    val tracker = state.peakTracker
    val peaks = state.peaks
    LaunchedEffect(isVisible, isPlaying) {
        if (!isVisible) {
            return@LaunchedEffect
        }
        var lastFrame = 0L
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = if (lastFrame == 0L) 0f else (now - lastFrame) / 1_000_000_000f
            lastFrame = now
            tracker.step(displayed.value, dt)
            peaks.value = tracker.levels.copyOf()
            if (!isPlaying && displayed.value.all { it < 0.001f } && tracker.levels.all { it < 0.001f }) break
        }
    }
    return peaks
}

@Composable
private fun SpectrumBars(
    displayed: State<FloatArray>, peaks: State<FloatArray>, night: Float,
    isPlaying: Boolean, colors: List<Color>?, modifier: Modifier
) {
    val palette = colors ?: listOf(Color(0xFF7DD3FC), Color(0xFF0284C7), Color(0xFF075985))
    val top by animateColorAsState(palette[0], tween(600), label = "Spectrum top color")
    val middle by animateColorAsState(palette[1], tween(600), label = "Spectrum middle color")
    val bottom by animateColorAsState(palette[2], tween(600), label = "Spectrum bottom color")
    Box(modifier.semantics {
        contentDescription = if (isPlaying) "Shiros riding the spectrum bars, bass to treble" else "Shiros on the paused spectrum"
    }.drawWithCache {
        val slot = size.width / SpectrumFrame.BAND_COUNT
        val barWidth = slot * (1f - 2f * SPECTRUM_BAR_INSET)
        val shade = lerp(middle, Color(0xFF26364E), night * 0.3f)
        val glassColors = listOf(
            lerp(top, Color.White, 0.14f).copy(alpha = 0.84f),
            shade.copy(alpha = 0.64f),
            lerp(bottom, Color(0xFF26364E), night * 0.3f).copy(alpha = 0.76f)
        )
        // A shared horizontal lighting profile gives each column depth, without extrusion
        // beyond the exact rectangles used by cat collisions. No per-bar blur or shader creation.
        val bevel = Brush.horizontalGradient(
            0f to Color.White.copy(alpha = 0.38f),
            0.18f to Color.White.copy(alpha = 0.09f),
            0.48f to Color.Transparent,
            0.74f to Color.Transparent,
            1f to lerp(shade, Color.Black, 0.45f).copy(alpha = 0.24f),
            startX = 0f, endX = barWidth.coerceAtLeast(1f)
        )
        val lip = lerp(top, Color.White, 0.72f).copy(alpha = 0.8f)
        onDrawBehind {
            if (size.width <= 0f || size.height <= 0f) return@onDrawBehind
            val amplitude = (size.height - 12.dp.toPx()).coerceAtLeast(0f) * (1f - night * 0.22f)
            val bands = displayed.value
            val held = peaks.value
            val tallest = (bands.maxOrNull() ?: 0f) * amplitude
            val fill = Brush.verticalGradient(glassColors,
                startY = size.height - tallest.coerceAtLeast(1f), endY = size.height)
            bands.forEachIndexed { index, level ->
                val left = (index + SPECTRUM_BAR_INSET) * slot
                val peak = held.getOrElse(index) { 0f }
                // Peaks remain full-height translucent bars, with no detached caps.
                if (peak > 0.001f) {
                    val peakHeight = peak * amplitude
                    val alpha = 0.28f * (peak / 0.04f).coerceAtMost(1f)
                    drawRect(shade.copy(alpha = alpha),
                        topLeft = Offset(left, size.height - peakHeight), size = Size(barWidth, peakHeight))
                }
                val height = level * amplitude
                if (height > 0f) {
                    val y = size.height - height
                    drawRect(fill, topLeft = Offset(left, y), size = Size(barWidth, height))
                    withTransform({ translate(left, y) }) {
                        drawRect(bevel, size = Size(barWidth, height))
                        drawRect(lip, size = Size(barWidth, minOf(1.dp.toPx(), height)))
                    }
                }
            }
        }
    })
}

@Preview(name = "Five cats on the wave", widthDp = 380, heightDp = 390, showBackground = true)
@Composable
private fun ShiroStagePreview() {
    ShiroStage(
        isPlaying = true, isBlinking = false, isSleepy = false, sleepProgress = 0f,
        leftEyeColor = Color(0xFF23465B), rightEyeColor = Color(0xFF23465B),
        spectrum = FloatArray(24) { 0.15f + (sin(it * 0.8f) + 1f) * 0.3f },
        revealOnEntry = false,
        modifier = Modifier.fillMaxSize().background(Color(0xFFE8F4F8))
    )
}
