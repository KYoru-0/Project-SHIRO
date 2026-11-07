package com.example.shiro

import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random
import kotlin.system.measureNanoTime
import org.junit.Assert.*
import org.junit.Test

class GlassSceneTest {
    private val music = MusicDynamics(-6f, 1f, 190f, 1f)
    private fun smallCat() = CatOutline(listOf(CatPoint(-2f, -2f), CatPoint(2f, -2f), CatPoint(2f, 2f), CatPoint(-2f, 2f)))

    @Test fun catsLandOnTheActualCurveCanPassUnderIslandsAndFallThroughGaps() {
        val dome = GlassPiece(0, GlassPlatformKind.CUSHION, 40f, 60f,
            floatArrayOf(50f, 60f, 50f), floatArrayOf(45f, 45f, 45f), 1f, 0f)
        val scene = GlassScene(listOf(dome), 0f, 1f)
        val sampler = GlassSurfaceSampler()
        val atEdge = sampler.sample(smallCat(), scene, 42f, 100f)
        assertEquals(56f, atEdge.centerHeight, 0.001f) // Bounding box would incorrectly require 62.
        assertEquals(44f, atEdge.hit.x, 0.001f)
        assertTrue(atEdge.normalX < 0f)
        assertEquals(2f, sampler.sample(smallCat(), scene, 42f, 10f).centerHeight, 0f)
        assertEquals(2f, sampler.sample(smallCat(), scene, 30f, 100f).centerHeight, 0f)
        val rising = scene.copy(pieces = listOf(dome.copy(top = floatArrayOf(54f, 64f, 54f), rise = 4f)))
        assertEquals(60f, sampler.sample(smallCat(), rising, 42f, 56f).centerHeight, 0.001f)
        val contact = waveContactAt(atEdge.point, 0f, 4f, 4f, 24, 100f,
            atEdge.normalX, atEdge.normalY, atEdge.contactWidth)
        assertTrue(contact.normalX < 0f && contact.normalY < 0f)
        assertEquals(1f, contact.normalX * contact.normalX + contact.normalY * contact.normalY, 0.001f)
    }

    @Test fun cushionsDentLocallyKeysDepressAndBothRecoverWithoutChangingOldSnapshots() {
        val motion = GlassSceneMotion(400f)
        val bands = FloatArray(24) { 0.65f }
        val before = motion.advance(bands, music, false, 0f, 208f)
        val cushion = before.pieces[2]
        val key = before.pieces[3]
        val saved = cushion.top.copyOf()
        motion.contact(cushion.id, (cushion.left + cushion.right) / 2f, 300f, 1f)
        motion.contact(key.id, (key.left + key.right) / 2f, 300f, 1f)
        val dented = motion.advance(bands, music, false, 1f / 60f, 208f)
        val centerDrop = cushion.top[8] - dented.pieces[2].top[8]
        val edgeDrop = cushion.top[2] - dented.pieces[2].top[2]
        assertTrue(centerDrop > edgeDrop + 5f)
        assertTrue(key.top[8] > dented.pieces[3].top[8] + 3f)
        var recovered = dented
        repeat(180) { recovered = motion.advance(bands, music, false, 1f / 60f, 208f) }
        assertArrayEquals(cushion.top, recovered.pieces[2].top, 0.01f)
        assertArrayEquals(key.top, recovered.pieces[3].top, 0.01f)
        assertArrayEquals(saved, cushion.top, 0f)
    }

    @Test fun repeatedImpactsStayBoundedAndPauseCollapsesEverySurface() {
        val motion = GlassSceneMotion(400f)
        var scene = motion.advance(FloatArray(24) { 1f }, music, true, 0f, 208f)
        repeat(240) { frame ->
            for (piece in scene.pieces) motion.contact(piece.id, piece.left, 10_000f, 100f)
            scene = motion.advance(FloatArray(24) { 0.5f + sin(frame * 0.2f + it) * 0.5f }, music, true, 1f / 30f, 208f)
            for (piece in scene.pieces) for (i in piece.top.indices) {
                assertTrue(piece.top[i].isFinite() && piece.top[i] in 0f..208f)
                assertTrue(piece.bottom[i] in 0f..piece.top[i])
                assertTrue(piece.pressure in 0f..1f)
            }
        }
        val paused = motion.advance(FloatArray(24), music, false, 1f / 60f, 208f)
        assertEquals(scene.phase, paused.phase, 0f)
        assertEquals(0f, paused.energy, 0f)
        assertTrue(paused.pieces.all { it.top.all { height -> height == 0f } })
        assertEquals(VisualizerMode.SPECTRUM, VisualizerMode.fromPreference("old-or-unknown-setting"))
        assertEquals(VisualizerMode.SPECTRUM, VisualizerMode.fromPreference("PLAYGROUND"))
    }

    @Test fun deformedCatContactsAgreeWithDenseIndependentSampling() {
        val hull = CatOutlineTest().artworkHull()
        val motion = GlassSceneMotion(400f)
        val scene = motion.advance(FloatArray(24) { 0.4f + sin(it * 0.7f) * 0.3f }, music, true, 1f / 60f, 208f)
        val sampler = GlassSurfaceSampler()
        val random = Random(172)
        repeat(100) { index ->
            val width = if (index % 2 == 0) 64f else 32f
            val shape = deformCatOutline(hull, width, width / 1.5f,
                random.nextFloat() * 1.95f - 0.75f, random.nextFloat() * 2f - 1f, random.nextFloat() * 360f)
            val x = 15f + random.nextFloat() * 370f
            val actual = sampler.sample(shape, scene, x, 500f)
            var expected = shape.support
            for (i in shape.points.indices) {
                val a = shape.points[i]
                val b = shape.points[(i + 1) % shape.points.size]
                for (step in 0..100) {
                    val t = step / 100f
                    val worldX = x + a.x + (b.x - a.x) * t
                    val localY = a.y + (b.y - a.y) * t
                    for (piece in scene.pieces) if (worldX >= piece.left && worldX <= piece.right) {
                        expected = max(expected, piece.heightAt(worldX) - localY)
                    }
                }
            }
            assertTrue("Exact contact must enclose the sampled outline", actual.centerHeight >= expected - 0.001f)
            assertEquals(expected, actual.centerHeight, 0.08f)
        }
    }

    @Test fun twentyFullDetailCatsUseBoundedSurfaceWork() {
        val hull = CatOutlineTest().artworkHull()
        val shapes = List(20) { i -> deformCatOutline(hull, 32f, 32f / 1.5f, sin(i * 0.5f), sin(i * 0.3f), i * 31f) }
        val samplers = List(20) { GlassSurfaceSampler() }
        val motion = GlassSceneMotion(400f)
        val bands = FloatArray(24) { 0.6f }
        val scene = motion.advance(bands, music, true, 1f / 60f, 208f)
        fun sampleFrame() {
            for (i in shapes.indices) {
                val sample = samplers[i].sample(shapes[i], scene, 18f + i * 19f, 200f)
                assertTrue(sample.centerHeight.isFinite() && abs(sample.normalX) <= 1f)
            }
        }
        repeat(100) { sampleFrame() }
        val millis = List(5) { measureNanoTime { repeat(300) { sampleFrame() } } / 300_000_000.0 }.sorted()[2]
        File("build/reports/shiro-glass-surface-performance.txt").apply {
            parentFile?.mkdirs()
            writeText("20 full 96-point cat outlines, seven glass surfaces.\nMedian desktop contact sampling: %.3f ms/frame.\n".format(millis) +
                "This excludes rendering and cat-to-cat resolution; it is not Android frame timing.\n")
        }
    }
}
