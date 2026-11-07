package com.example.shiro

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import kotlin.system.measureNanoTime
import org.junit.Assert.*
import org.junit.Test

class CircularSpectrumTest {
    private val frame = VisualizerFrame(FloatArray(24) { 0.6f }, energy = 0.6f, bass = 0.7f, fieldStrength = 1f)
    private val dt = 1f / 60f

    @Test fun circleUsesMoreSpaceForMusicAndLiquidHasClearPeakToValleyMotion() {
        val bounds = centerFieldBounds(400f, 500f, 1f)
        val alternating = frame.copy(bands = FloatArray(24) { if (it % 2 == 0) 0.75f else 0.15f }, energy = 0.45f)
        for (mode in VisualizerMode.available) {
            val scene = CircularSpectrumMotion(mode, 400f, 500f).advance(alternating, dt)
            assertTrue("Audio must get more radial room than the core", scene.amplitude > scene.bounds.radius)
            assertEquals(bounds.radius, scene.bounds.radius + scene.amplitude, 0.001f)
            val radii = scene.speeds.indices.map { hypot(scene.edges[it * 4], scene.edges[it * 4 + 1]) }
            assertTrue(radii.all { it >= scene.bounds.radius - 0.001f && it <= bounds.radius + 0.001f })
            val range = radii.max() - radii.min()
            assertTrue("${mode.title}: visible contrast between quiet and loud bands", range > 50f)
            if (mode == VisualizerMode.LIQUID) {
                // The previous 40% radial budget and unexpanded band range barely moved ~32dp here.
                val previousRange = bounds.radius * 0.4f * (0.84f * 0.6f + 0.12f * alternating.energy)
                assertTrue("Liquid should have roughly twice the earlier radial excursion", range > previousRange * 1.8f)
            }
        }
    }

    @Test fun circularBarsHaveRealGapsAndTheSameEdgesForDrawingAndContact() {
        val scene = CircularSpectrumMotion(VisualizerMode.SPECTRUM, 400f, 500f)
            .advance(frame.copy(bands = FloatArray(24) { 1f }), dt)
        val tiny = CatOutline(listOf(CatPoint(-0.3f, -0.3f), CatPoint(0.3f, -0.3f), CatPoint(0.3f, 0.3f), CatPoint(-0.3f, 0.3f)))
        val sampler = RadialSurfaceSampler()
        assertEquals(24, scene.speeds.size)
        for (i in 0 until 24) {
            val angle = PI / 2 + (i + 0.5) * PI / 12
            val nx = cos(angle).toFloat(); val ny = sin(angle).toFloat()
            val rim = scene.radiusAt(nx, ny)
            assertEquals((scene.bounds.radius + scene.amplitude) * cos((1f - 2f * SPECTRUM_BAR_INSET) * PI / 24).toFloat(), rim, 0.001f)
            val cat = WaveCatBody(scene.bounds.x + nx * rim).apply { height = scene.bounds.height + ny * rim }
            assertTrue(sampler.sample(cat, tiny, scene)!!.depth > 0.2f)
            val gapAngle = PI / 2 + i * PI / 12
            val gx = cos(gapAngle).toFloat(); val gy = sin(gapAngle).toFloat()
            assertEquals(scene.bounds.radius, scene.radiusAt(gx, gy), 0.001f)
            cat.x = scene.bounds.x + gx * (scene.bounds.radius + 4f)
            cat.height = scene.bounds.height + gy * (scene.bounds.radius + 4f)
            assertNull("No invisible bridge over bar gap $i", sampler.sample(cat, tiny, scene))
        }
    }

    @Test fun liquidWrapsWithoutASeamAndLocalDentsRecover() {
        val motion = CircularSpectrumMotion(VisualizerMode.LIQUID, 400f, 500f)
        val before = motion.advance(frame, dt)
        val saved = before.edges.copyOf()
        motion.contact(0f, 100f, 300f, 1f)
        val dented = motion.advance(frame, dt)
        assertTrue(before.radiusAt(0f, 1f) > dented.radiusAt(0f, 1f) + 2f)
        assertEquals(before.radiusAt(0f, -1f), dented.radiusAt(0f, -1f), 0.01f)
        assertEquals(dented.edges[0], dented.edges[dented.edges.size - 2], 0.001f)
        assertEquals(dented.edges[1], dented.edges.last(), 0.001f)
        assertEquals(circularBandAt(frame.bands, -0.00001f), circularBandAt(frame.bands, 0.99999f), 0.001f)
        var recovered = dented
        repeat(240) { recovered = motion.advance(frame, dt) }
        assertArrayEquals(saved, before.edges, 0f)
        assertArrayEquals(before.edges, recovered.edges, 0.01f)
        val silent = motion.advance(VisualizerFrame(), dt)
        assertEquals(0f, silent.alpha, 0f)
        for (i in silent.speeds.indices) assertEquals(silent.bounds.radius, hypot(silent.edges[i * 4], silent.edges[i * 4 + 1]), 0.001f)
        assertTrue(silent.speeds.all { abs(it) <= 180f })
    }

    @Test fun rotationCarriesTheSameBandsAndLiquidDentsWithoutMovingTheCenter() {
        val angle = 1.13f
        for (mode in VisualizerMode.available) {
            val motion = CircularSpectrumMotion(mode, 400f, 500f)
            val audio = frame.copy(bands = FloatArray(24) { 0.4f + it * 0.02f })
            val first = motion.advance(audio, dt)
            val rotated = motion.advance(audio, dt, CircularRotation(angle, 0.2f))
            assertEquals(first.bounds, rotated.bounds)
            assertEquals(0.2f, rotated.angularSpeed, 0f)
            for (j in first.edges.indices step 2) {
                assertEquals(first.edges[j] * cos(angle) - first.edges[j + 1] * sin(angle), rotated.edges[j], 0.001f)
                assertEquals(first.edges[j] * sin(angle) + first.edges[j + 1] * cos(angle), rotated.edges[j + 1], 0.001f)
            }
            if (mode == VisualizerMode.LIQUID) {
                motion.contact(-sin(angle) * 100f, cos(angle) * 100f, 300f, 1f)
                val nextAngle = angle + 0.1f
                val dented = motion.advance(audio, dt, CircularRotation(nextAngle, 0.2f))
                val untouched = CircularSpectrumMotion(mode, 400f, 500f).advance(audio, dt, CircularRotation(nextAngle))
                val x = -sin(nextAngle); val y = cos(nextAngle)
                assertTrue(untouched.radiusAt(x, y) > dented.radiusAt(x, y) + 2f)
                assertEquals(untouched.radiusAt(-x, -y), dented.radiusAt(-x, -y), 0.001f)
            }
        }
    }

    private fun edgeDistance(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val ex = bx - ax; val ey = by - ay
        val t = (((x - ax) * ex + (y - ay) * ey) / (ex * ex + ey * ey).coerceAtLeast(0.00001f)).coerceIn(0f, 1f)
        return hypot(x - ax - ex * t, y - ay - ey * t)
    }

    @Test fun rotatedDeformedCatsContactActualRenderedSegments() {
        val hull = CatOutlineTest().artworkHull()
        for (mode in VisualizerMode.available) {
            val scene = CircularSpectrumMotion(mode, 400f, 500f)
                .advance(frame.copy(bands = FloatArray(24) { 0.55f + 0.35f * sin(it * 0.6f) }), dt,
                    CircularRotation(1.137f, 0.2f))
            val sampler = RadialSurfaceSampler()
            var hits = 0
            repeat(80) { i ->
                val shape = deformCatOutline(hull, 45f, 30f, sin(i * 0.2f), cos(i * 0.3f), i * 23f)
                val angle = i * PI / 40
                val nx = cos(angle).toFloat(); val ny = sin(angle).toFloat()
                val rim = scene.radiusAt(nx, ny)
                val cat = WaveCatBody(scene.bounds.x + nx * (rim + 2f)).apply { height = scene.bounds.height + ny * (rim + 2f) }
                val hit = sampler.sample(cat, shape, scene) ?: return@repeat
                hits++
                val x = hit.hit.x - scene.bounds.x; val y = hit.hit.y - scene.bounds.height
                val onSurface = scene.speeds.indices.minOf { j -> edgeDistance(x, y,
                    scene.edges[j * 4], scene.edges[j * 4 + 1], scene.edges[j * 4 + 2], scene.edges[j * 4 + 3]) }
                assertTrue(onSurface < 0.002f)
                val onCat = shape.points.indices.minOf { j ->
                    val a = shape.points[j]; val b = shape.points[(j + 1) % shape.points.size]
                    edgeDistance(hit.point.x, hit.point.y, a.x, a.y, b.x, b.y)
                }
                assertTrue(onCat < 0.002f)
                assertEquals(1f, hypot(hit.normalX, hit.normalY), 0.001f)
                assertEquals("Contact velocity includes the rotating surface",
                    0.2f * (-y * hit.normalX + x * hit.normalY), hit.speed, 0.001f)
                if (hit.depth > 0f) {
                    cat.x += hit.pushX * (hit.depth + 0.1f)
                    cat.height += hit.pushY * (hit.depth + 0.1f)
                    assertTrue((sampler.sample(cat, shape, scene)?.depth ?: -1f) < 0.01f)
                }
            }
            assertTrue("${mode.title}: the contact checks must exercise the rotated bodies", hits > 50)
        }
    }

    @Test fun bothCircularModesMoveTenCatsAndReleaseThemOnPause() {
        val hull = CatOutlineTest().artworkHull()
        val preview = BufferedImage(800, 500, BufferedImage.TYPE_INT_RGB)
        val report = StringBuilder("Circular mode simulations: 10 full-detail cats, 8 seconds music + 8 seconds pause.\n")
        for ((modeIndex, mode) in VisualizerMode.available.withIndex()) {
            val field = CenterCatField(400f, 500f, 10)
            val motion = CircularSpectrumMotion(mode, 400f, 500f)
            val drift = CircularDrift(Random(17))
            val cats = List(10) { i -> WaveCatBody(25f + i * 38f, 1).apply { height = 14f } }
            val bodyWidth = 64f * cats[0].sizeScale
            fun outline(cat: WaveCatBody) = cat.outlineCache.get(hull, bodyWidth, bodyWidth / 1.5f, cat.compression,
                (cat.velocityX / 180f + cat.spin / 500f).coerceIn(-1f, 1f), cat.rotation, cat.contact)
            val shapes = cats.map { outline(it) }.toMutableList()
            val lifted = BooleanArray(10)
            var contacts = 0
            val nanos = measureNanoTime {
                repeat(960) { frameIndex ->
                    val playing = frameIndex < 480
                    val audio = if (playing) frame.copy(bands = FloatArray(24) { 0.5f + 0.35f * sin(it * 0.5f + frameIndex * 0.06f) },
                        phase = frameIndex * 0.01) else VisualizerFrame()
                    field.advance(audio, playing, 0f, dt)
                    val rotation = drift.advance(if (frameIndex < 240) "first" else "next", audio.energy, playing, dt)
                    val scene = motion.advance(audio.copy(fieldStrength = field.strength), dt, rotation)
                    field.useSurface(scene)
                    if (playing && frameIndex % 24 == 0) field.burst(cats, 0.8f)
                    repeat(2) {
                        for (i in cats.indices) {
                            val cat = cats[i]
                            val shape = outline(cat)
                            val gravity = field.pull(i, cat, shape, CatPhysicsClock.STEP_SECONDS)
                            cat.step(0f, 0f, CatPhysicsClock.STEP_SECONDS, shape.support, gravityScale = gravity)
                            shapes[i] = outline(cat)
                        }
                        resolveCatContacts(cats, shapes)
                        repeat(2) { pass -> for (i in cats.indices) {
                            val cat = cats[i]
                            val hit = field.resolve(cat, outline(cat), bodyWidth, bodyWidth / 1.5f, pass == 0)
                            if (hit != null && pass == 0) {
                                contacts++
                                motion.contact(hit.hit.x - scene.bounds.x, hit.hit.y - scene.bounds.height, hit.impact, 0.5f)
                            }
                            shapes[i] = outline(cat)
                            cat.contain(400f, 500f, shapes[i])
                        } }
                    }
                    for (i in cats.indices) {
                        val cat = cats[i]; val shape = outline(cat)
                        assertTrue(cat.x.isFinite() && cat.height.isFinite())
                        assertTrue(cat.x + shape.left >= -0.1f && cat.x + shape.right <= 400.1f)
                        assertTrue(cat.height + shape.bottom >= -0.1f && cat.height + shape.top <= 500.1f)
                        if (cat.height > 85f) lifted[i] = true
                    }
                    if (frameIndex == 360) drawPreview(preview, modeIndex * 400, scene, cats, cats.map { outline(it) })
                }
            }
            assertTrue("${mode.title}: all cats must lift", lifted.all { it })
            assertTrue("${mode.title}: cats must interact with the audio edges", contacts > 20)
            assertEquals(0f, field.strength, 0f)
            assertTrue(drift.isSettled)
            assertTrue(cats.all { it.height < outline(it).support + 1f && abs(it.velocityY) < 1f })
            report.append("${mode.title}: $contacts audio-edge contacts; desktop simulation %.3f ms/frame.\n".format(nanos / 960_000_000.0))
        }
        report.append("Includes cat geometry, field and collision work; excludes Android rendering. Not device FPS.\n")
        File("build/reports/shiro-circular-performance.txt").apply { parentFile?.mkdirs(); writeText(report.toString()) }
        ImageIO.write(preview, "png", File("build/reports/shiro-circular-physics-preview.png"))
    }

    private fun drawPreview(image: BufferedImage, offset: Int, scene: CircularScene, cats: List<WaveCatBody>, shapes: List<CatOutline>) {
        val g = image.createGraphics()
        g.translate(offset, 0)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x727B81); g.fillRect(0, 0, 400, 500)
        g.color = Color.WHITE; g.drawString("${scene.mode.title} — physics geometry, 6 seconds", 12, 22)
        g.stroke = BasicStroke(1.5f)
        for (i in scene.speeds.indices) {
            val j = i * 4; val ax = scene.edges[j]; val ay = scene.edges[j + 1]
            val bx = scene.edges[j + 2]; val by = scene.edges[j + 3]
            val ar = hypot(ax, ay); val br = hypot(bx, by)
            val path = Path2D.Float()
            path.moveTo(scene.bounds.x + ax * scene.bounds.radius / ar, 500 - scene.bounds.height - ay * scene.bounds.radius / ar)
            path.lineTo(scene.bounds.x + ax, 500 - scene.bounds.height - ay)
            path.lineTo(scene.bounds.x + bx, 500 - scene.bounds.height - by)
            path.lineTo(scene.bounds.x + bx * scene.bounds.radius / br, 500 - scene.bounds.height - by * scene.bounds.radius / br)
            path.closePath()
            g.color = Color(0x9FD9ED); g.fill(path)
        }
        for (i in cats.indices) {
            val path = Path2D.Float()
            shapes[i].points.forEachIndexed { j, p ->
                val x = cats[i].x + p.x; val y = 500 - cats[i].height - p.y
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.closePath(); g.color = Color.WHITE; g.fill(path); g.color = Color(0x384853); g.draw(path)
        }
        g.dispose()
    }
}
