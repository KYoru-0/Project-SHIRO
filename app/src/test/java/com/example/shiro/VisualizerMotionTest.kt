package com.example.shiro

import java.io.File
import kotlin.math.abs
import kotlin.math.sin
import kotlin.system.measureNanoTime
import org.junit.Assert.*
import org.junit.Test

class VisualizerMotionTest {
    private val bands = FloatArray(24) { 0.6f }
    private val beat = MusicDynamics(-6f, 1f, 190f, 1f)

    @Test fun ringsAndBouncesRequireFreshAudibleBeatsAndExpireAfterPause() {
        val motion = VisualizerMotion()
        val first = motion.advance(bands, beat, true, 1f / 60f, true)
        assertTrue(first.onset > 0f)
        assertEquals(1, first.ripples.size)
        repeat(20) { assertEquals(0f, motion.advance(bands, beat, true, 1f / 60f, true).onset, 0f) }
        motion.advance(bands, beat.copy(beatPulse = 0f), true, 1f / 60f, true)
        assertTrue(motion.advance(bands, beat, true, 1f / 60f, true).onset > 0f)
        var paused = motion.advance(FloatArray(24), beat, false, 1f / 60f, true)
        val phase = paused.phase
        repeat(120) {
            paused = motion.advance(FloatArray(24), beat, false, 1f / 60f, true)
            assertEquals(0f, paused.onset, 0f)
            assertEquals(phase, paused.phase, 0.0)
        }
        assertTrue(paused.ripples.isEmpty())
        assertEquals(0f, paused.energy, 0f)
        motion.reset()
        assertEquals(0f, motion.advance(FloatArray(24), beat, true, 1f / 60f, true).onset, 0f)
        val empty = motion.advance(floatArrayOf(Float.NaN), beat, true, 1f / 60f, false)
        assertEquals(0f, empty.energy, 0f)
        assertTrue(empty.ripples.isEmpty())
    }

    @Test fun fastTracksCannotAccumulateUnboundedRings() {
        val motion = VisualizerMotion()
        var frame = VisualizerFrame()
        repeat(1200) { i ->
            frame = motion.advance(bands, beat.copy(beatPulse = if (i % 13 == 0) 1f else 0f), true, 1f / 60f, true)
            assertTrue(frame.ripples.size <= 6)
            assertTrue(frame.ripples.all { it.age in 0f..1.8f })
        }
        assertTrue(frame.ripples.isNotEmpty())
        motion.reset()
        assertTrue(motion.advance(bands, beat.copy(beatPulse = 0f), true, 1f / 60f, true).ripples.isEmpty())
    }

    @Test fun beatsRotateThroughGroundedCatsWithoutKickingHeldOrAirborneCats() {
        val shape = CatOutline(listOf(CatPoint(-3f, -5f), CatPoint(3f, -5f), CatPoint(3f, 5f), CatPoint(-3f, 5f)))
        val bodies = List(10) { WaveCatBody(20f + it * 38f, generation = 1).apply { height = 5f } }
        val shapes = List(10) { shape }
        bodies[0].grab()
        bodies[1].height = 100f
        bodies[2].active = false
        val bounce = CatBeatBounce()
        assertEquals(0, bounce.trigger(bodies, shapes, 0f, 400f))
        assertEquals(3, bounce.trigger(bodies, shapes, 1f, 400f))
        assertEquals(3, bodies.count { it.velocityY > 0f })
        assertEquals(0f, bodies[0].velocityY, 0f)
        assertEquals(0f, bodies[1].velocityY, 0f)
        val first = bodies.indices.filter { bodies[it].velocityY > 0f }.toSet()
        bodies.forEach { it.velocityY = 0f }
        assertEquals(3, bounce.trigger(bodies, shapes, 0.4f, 400f))
        val next = bodies.indices.filter { bodies[it].velocityY > 0f }.toSet()
        assertTrue(first.intersect(next).isEmpty())
        assertTrue(bodies.all { it.velocityY <= 210f && abs(it.velocityX) <= 60f })
    }

    @Test fun drawingsStayInsideTheStageRegionAndReuseTheirPointStorage() {
        val geometry = MusicVisualGeometry()
        val points = geometry.points
        repeat(240) { step ->
            val frame = VisualizerFrame(FloatArray(24) { (0.5f + sin(it + step * 0.1f) * 0.5f) },
                phase = step * 0.17, energy = 0.8f, bass = step % 10 / 9f, treble = step % 7 / 6f, pulse = step % 4 / 3f)
            val operations = listOf<() -> Int>({ geometry.halo(frame, false) }, { geometry.halo(frame, true) },
                { geometry.orbit(frame, 0) }, { geometry.orbit(frame, 1) }, { geometry.orbit(frame, 2) }, { geometry.resonance(frame) })
            for (operation in operations) {
                val count = operation()
                assertSame(points, geometry.points)
                assertTrue(count <= MusicVisualGeometry.DOT_COUNT)
                for (i in 0 until count * 2) assertTrue(points[i].isFinite() && abs(points[i]) <= 1f)
            }
        }
        geometry.halo(VisualizerFrame(bands = FloatArray(24)), false)
        val quiet = points.copyOf()
        geometry.halo(VisualizerFrame(bands = FloatArray(24) { 1f }, pulse = 1f), false)
        assertTrue(points[0] > quiet[0] + 0.15f)
    }

    @Test fun enabledChoicesRoundTripAndRetiredSettingsFallBack() {
        assertEquals(listOf(VisualizerMode.SPECTRUM, VisualizerMode.LIQUID), VisualizerMode.available)
        for (mode in VisualizerMode.available) assertEquals(mode, VisualizerMode.fromPreference(mode.name))
        assertEquals(VisualizerMode.SPECTRUM, VisualizerMode.fromPreference(null))
        assertEquals(VisualizerMode.SPECTRUM, VisualizerMode.fromPreference("PLAYGROUND"))
        assertEquals(VisualizerMode.SPECTRUM, VisualizerMode.fromPreference("RIPPLES"))
        assertEquals(VisualizerMode.SPECTRUM, VisualizerMode.fromPreference("ORBITS"))
        assertEquals(VisualizerMode.SPECTRUM, VisualizerMode.fromPreference("HALO"))
        assertEquals(VisualizerMode.SPECTRUM, VisualizerMode.fromPreference("RESONANCE"))
        assertEquals(VisualizerMode.SPECTRUM, VisualizerMode.fromPreference("unknown"))
        for (layout in VisualizerLayout.entries) assertEquals(layout, VisualizerLayout.fromPreference(layout.name))
        assertEquals(4, VisualizerMode.available.size * VisualizerLayout.entries.size)
        assertEquals(VisualizerLayout.FLOOR, VisualizerLayout.fromPreference(null))
        assertEquals(VisualizerLayout.CIRCLE, VisualizerLayout.fromPreference(null, "HALO"))
        assertEquals(VisualizerLayout.CIRCLE, VisualizerLayout.fromPreference(null, "RESONANCE"))
        assertEquals(VisualizerLayout.FLOOR, VisualizerLayout.fromPreference("FLOOR", "HALO"))
    }

    @Test fun boundedGeometryCpuCostCanBeComparedWithoutClaimingDeviceFps() {
        val geometry = MusicVisualGeometry()
        val frame = VisualizerFrame(bands, 1.0, 0.6f, 0.8f, 0.5f, 0.7f)
        fun drawGeometry() {
            geometry.halo(frame, false)
            repeat(3) { geometry.orbit(frame, it) }
            geometry.resonance(frame)
        }
        repeat(300) { drawGeometry() }
        val millis = List(5) { measureNanoTime { repeat(1000) { drawGeometry() } } / 1_000_000_000.0 }.sorted()[2]
        File("build/reports/shiro-visualizer-performance.txt").apply {
            parentFile?.mkdirs()
            writeText("Geometry for halo, all three orbits and 192 resonance dots combined: %.3f ms/frame on the desktop JVM.\n".format(millis) +
                "The app draws only the selected mode. This excludes Canvas rendering and physics; it is not device FPS.\n")
        }
    }
}
