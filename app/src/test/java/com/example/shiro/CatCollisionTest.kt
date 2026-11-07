package com.example.shiro

import java.io.File
import kotlin.math.sin
import kotlin.random.Random
import kotlin.system.measureNanoTime
import org.junit.Assert.*
import org.junit.Test

class CatCollisionTest {
    private fun rectangle(left: Float, bottom: Float, right: Float, top: Float) = CatOutline(listOf(
        CatPoint(left, bottom), CatPoint(right, bottom), CatPoint(right, top), CatPoint(left, top)))

    @Test fun edgeCrossingsContainmentAndBoundaryContactsRemainCorrect() {
        val horizontal = rectangle(-12f, -2f, 12f, 2f)
        val vertical = rectangle(-2f, -12f, 2f, 12f)
        // No vertex of either rectangle lies inside the other.
        assertNotNull(catOverlap(horizontal, 0f, 0f, vertical, 0f, 0f))
        assertNotNull(catOverlap(rectangle(-10f, -10f, 10f, 10f), 0f, 0f, vertical, 0f, 0f))
        assertNull(catOverlap(horizontal, 0f, 0f, horizontal, 0f, 4f))
        assertNull(catOverlap(horizontal, 0f, 0f, vertical, 14.01f, 0f))
        val small = rectangle(-1f, -1f, 1f, 1f)
        val push = catOverlap(horizontal, 0f, 0f, small, 8f, 0.5f)!!
        assertNull(catOverlap(horizontal, 0f, 0f, small, 8f + push.x * 1.001f, 0.5f + push.y * 1.001f))
    }

    @Test fun fullOutlineContactMatchesPreviousDetectorAcrossSquishSizesAndRotations() {
        val hull = CatOutlineTest().artworkHull()
        val shapes = List(60) { i ->
            val width = when (i % 3) { 0 -> 64f; 1 -> 64f * 0.70710678f; else -> 32f }
            deformCatOutline(hull, width, width / 1.5f, -0.75f + i % 5 * 0.4875f,
                sin(i * 0.7f), i * 37f, WaveContact(0.52f, 0.92f, 0f, -1f, 0.11f))
        }
        val random = Random(20261108)
        var hits = 0
        var misses = 0
        repeat(4000) { sample ->
            val a = shapes[random.nextInt(shapes.size)]
            val b = shapes[random.nextInt(shapes.size)]
            val x = random.nextFloat() * 80f - 40f
            val y = random.nextFloat() * 80f - 40f
            val previous = referenceCatOverlap(a, 0f, 0f, b, x, y)
            val current = catOverlap(a, 0f, 0f, b, x, y)
            assertEquals("Contact changed at sample $sample ($x, $y)", previous != null, current != null)
            if (current == null) misses++ else {
                hits++
                assertTrue(current.x.isFinite() && current.y.isFinite())
                // A separating push must actually separate even rotated, deeply dented bodies.
                assertNull("Push leaves overlap at sample $sample", catOverlap(a, 0f, 0f, b,
                    x + current.x * 1.001f, y + current.y * 1.001f))
            }
        }
        assertTrue(hits > 300)
        assertTrue(misses > 2000)
    }

    @Test fun crowdedTwentyCatCollisionWorkloadPreservesContactsAndReportsCost() {
        val hull = CatOutlineTest().artworkHull()
        data class Scene(val outlines: List<CatOutline>, val xs: FloatArray, val ys: FloatArray)
        fun scenes() = List(24) { frame ->
            Scene(List(20) { i ->
                deformCatOutline(hull, 32f, 32f / 1.5f, 0.7f * sin(i + frame * 0.17f),
                    sin(i * 0.4f), i * 31f + frame, WaveContact(0.52f, 0.92f, 0f, -1f, 0.11f))
            }, FloatArray(20) { 260f + it % 5 * 8f }, FloatArray(20) { 70f + it / 5 * 7f })
        }
        fun check(scenes: List<Scene>, optimized: Boolean): Int {
            var contacts = 0
            for (scene in scenes) for (a in 0 until 20) for (b in a + 1 until 20) {
                val result = if (optimized) catOverlap(scene.outlines[a], scene.xs[a], scene.ys[a],
                    scene.outlines[b], scene.xs[b], scene.ys[b])
                else referenceCatOverlap(scene.outlines[a], scene.xs[a], scene.ys[a],
                    scene.outlines[b], scene.xs[b], scene.ys[b])
                if (result != null) contacts++
            }
            return contacts
        }
        val warmup = scenes()
        repeat(3) { check(warmup, false); check(warmup, true) }
        val oldTimes = mutableListOf<Long>()
        val newTimes = mutableListOf<Long>()
        repeat(5) {
            // Fresh outlines include lazy collision-cache construction in the measured cost.
            val scenes = scenes()
            var oldHits: Int
            var newHits: Int
            oldTimes += measureNanoTime { oldHits = check(scenes, false) }
            newTimes += measureNanoTime { newHits = check(scenes, true) }
            assertEquals(oldHits, newHits)
            assertTrue(newHits > 500)
        }
        val before = oldTimes.sorted()[2] / 1_000_000.0 / 24
        val after = newTimes.sorted()[2] / 1_000_000.0 / 24
        val report = "20 crowded cats, 190 candidate pairs, fresh full-detail outlines per step.\n" +
            "Desktop JVM median collision cost: previous %.3f ms/step; optimized %.3f ms/step (%.1fx).\n"
                .format(before, after, before / after) +
            "This measures collision CPU work, not Android device frame rate.\n"
        File("build/reports/shiro-collision-performance.txt").apply { parentFile?.mkdirs(); writeText(report) }
        println(report)
    }

    @Test fun walkingProjectionKeepsTheSameExtremesAsScanningEveryVertex() {
        val hull = CatOutlineTest().artworkHull()
        val shapes = List(30) { i ->
            deformCatOutline(hull, if (i % 2 == 0) 32f else 64f, if (i % 2 == 0) 32f / 1.5f else 64f / 1.5f,
                -0.7f + i % 5 * 0.45f, sin(i * 0.6f), i * 29f,
                WaveContact(0.52f, 0.92f, 0f, -1f, 0.11f)).collisionShape()
        }
        for (shape in shapes) for (other in shapes) {
            var low = 0
            var high = 0
            for (axis in shape.axes.indices step 4) {
                val nx = shape.axes[axis]
                val ny = shape.axes[axis + 1]
                fun projection(index: Int) = other.vertices[index] * nx + other.vertices[index + 1] * ny
                val indices = other.vertices.indices step 2
                val expectedLow = indices.minBy { projection(it) }
                val expectedHigh = indices.maxBy { projection(it) }
                if (axis == 0) { low = expectedLow; high = expectedHigh }
                else {
                    low = advanceConvexSupport(other.vertices, low, nx, ny, maximum = false)
                    high = advanceConvexSupport(other.vertices, high, nx, ny, maximum = true)
                }
                assertEquals(projection(expectedLow), projection(low), 0f)
                assertEquals(projection(expectedHigh), projection(high), 0f)
            }
        }
    }
}
