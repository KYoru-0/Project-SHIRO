package com.example.shiro

import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.system.measureNanoTime
import org.junit.Assert.*
import org.junit.Test

class CenterCatFieldTest {
    private val music = VisualizerFrame(FloatArray(24) { 0.6f }, energy = 0.6f, bass = 0.7f)
    private val dt = CatPhysicsClock.STEP_SECONDS
    private val square = CatOutline(listOf(CatPoint(-8f, -8f), CatPoint(8f, -8f), CatPoint(8f, 8f), CatPoint(-8f, 8f)))

    private fun chargedField(count: Int = 10) = CenterCatField(400f, 500f, count).apply {
        repeat(180) { advance(music, true, 0f, 1f / 60f) }
    }

    @Test fun bothModesShareTheRimAndEaseForcesAcrossPlaybackAndSleep() {
        val field = CenterCatField(400f, 500f, 10)
        field.advance(music, true, 0f, 1f / 60f)
        assertTrue(field.strength in 0.01f..0.05f)
        val cat = WaveCatBody(200f).apply { height = 8f }
        field.pull(0, cat, square, dt)
        assertTrue(hypot(cat.velocityX, cat.velocityY) < 1f)
        repeat(180) { field.advance(music, true, 0f, 1f / 60f) }
        assertTrue(field.strength > 0.99f)
        val dp = field.bounds
        val pixels = centerFieldBounds(400f * 2.75f, 500f * 2.75f, field.strength)
        assertEquals(dp.x, pixels.x / 2.75f, 0.001f)
        assertEquals(dp.height, pixels.height / 2.75f, 0.001f)
        assertEquals(dp.radius, pixels.radius / 2.75f, 0.001f)
        field.advance(VisualizerFrame(), false, 0f, 1f / 60f)
        assertTrue(field.strength > 0.9f)
        repeat(300) { field.advance(VisualizerFrame(), false, 0f, 1f / 60f) }
        assertEquals(0f, field.strength, 0f)
        repeat(180) { field.advance(music, true, 1f, 1f / 60f) }
        assertEquals(0f, field.strength, 0f)
        field.advance(music, true, 0f, 1f / 60f)
        field.reset()
        assertEquals(0f, field.strength, 0f)
    }

    @Test fun suctionLiftsCatsFromTheFloorAndSwirlsThemAroundTheCenter() {
        val field = CenterCatField(400f, 500f, 1)
        val cat = WaveCatBody(200f).apply { height = 8f }
        var lifted = false
        var side = false
        var above = false
        repeat(1200) {
            field.advance(music, true, 0f, dt)
            val gravity = field.pull(0, cat, square, dt)
            cat.step(0f, 0f, dt, square.support, gravityScale = gravity)
            field.resolve(cat, square, 24f, 16f)
            cat.contain(400f, 500f, square)
            lifted = lifted || cat.height > 80f
            side = side || abs(cat.x - field.bounds.x) > field.bounds.radius * 0.8f
            above = above || cat.height > field.bounds.height + field.bounds.radius * 0.8f
            assertTrue(cat.x.isFinite() && cat.height.isFinite())
        }
        assertTrue("The field must lift, circulate and carry cats above the artwork", lifted && side && above)
    }

    @Test fun beatsReleaseOnlyThreeNearbyCatsAndGiveThemTimeToEscape() {
        val field = chargedField()
        val rim = field.bounds
        val cats = List(10) { i -> WaveCatBody(rim.x + cos(i * PI / 5).toFloat() * (rim.radius + 8f), 1).apply {
            height = rim.height + sin(i * PI / 5).toFloat() * (rim.radius + 8f)
        } }
        cats[0].grab()
        cats[1].active = false
        cats[2].x = -1000f
        assertEquals(0, field.burst(cats, 0.3f))
        assertEquals(3, field.burst(cats, 1f))
        val launched = cats.indices.filter { hypot(cats[it].velocityX, cats[it].velocityY) > 0f }.toSet()
        assertEquals(3, launched.size)
        assertFalse(launched.any { it < 3 })
        assertTrue(launched.all { cats[it].compression < 0f })
        val copy = cats[launched.first()]
        val velocityX = copy.velocityX
        val velocityY = copy.velocityY
        assertEquals(1f - field.strength, field.pull(launched.first(), copy, square, dt), 0f)
        assertEquals(velocityX, copy.velocityX, 0f)
        assertEquals(velocityY, copy.velocityY, 0f)
        repeat(12) { field.burst(cats, 1f) }
        assertEquals(0, field.burst(cats, 1f))
        assertTrue(cats.all { hypot(it.velocityX, it.velocityY) <= 360.001f })

        val large = WaveCatBody(rim.x + rim.radius + 8f).apply { height = rim.height }
        val small = WaveCatBody(large.x, 1).apply { height = large.height }
        val second = chargedField(2)
        assertEquals(2, second.burst(listOf(large, small), 1f))
        assertTrue(small.velocityX > large.velocityX)
    }

    @Test fun contactsMatchTheDeformedBorderAndDentTowardTheRim() {
        val hull = CatOutlineTest().artworkHull()
        val field = chargedField(1)
        repeat(64) { i ->
            val shape = deformCatOutline(hull, 48f, 32f, sin(i * 0.4f), cos(i * 0.3f), i * 29f)
            val angle = i * PI / 32
            val distance = field.bounds.radius + (i % 5) * 4f
            val cat = WaveCatBody(field.bounds.x + cos(angle).toFloat() * distance).apply {
                height = field.bounds.height + sin(angle).toFloat() * distance
            }
            var closest = Float.POSITIVE_INFINITY
            // Independent dense edge samples approximate the true nearest point, including dents.
            for (j in shape.points.indices) {
                val a = shape.points[j]
                val b = shape.points[(j + 1) % shape.points.size]
                for (k in 0..100) {
                    val x = cat.x + a.x + (b.x - a.x) * k / 100f - field.bounds.x
                    val y = cat.height + a.y + (b.y - a.y) * k / 100f - field.bounds.height
                    closest = minOf(closest, sqrt(x * x + y * y))
                }
            }
            val beforeX = cat.x
            val beforeY = cat.height
            field.resolve(cat, shape, 48f, 32f, deform = false)
            val moved = hypot(cat.x - beforeX, cat.height - beforeY)
            assertEquals((field.bounds.radius - closest).coerceIn(0f, 2f) * field.strength, moved, 0.02f)
        }
        val cat = WaveCatBody(field.bounds.x + field.bounds.radius + 6f).apply { height = field.bounds.height }
        field.resolve(cat, square, 24f, 16f)
        assertTrue(cat.compression > 0.15f)
        assertNotNull(cat.contact)
        assertTrue(cat.contact!!.u < 0.3f)
        assertEquals(1f, cat.contact!!.normalX, 0.01f)
    }

    @Test fun draggingOverridesTheFieldAndReleaseInsideTheCoreDoesNotTeleport() {
        val field = chargedField(1)
        val cat = WaveCatBody(field.bounds.x).apply { height = field.bounds.height; grab() }
        repeat(30) {
            field.pull(0, cat, square, dt)
            field.resolve(cat, square, 24f, 16f)
            field.burst(listOf(cat), 1f)
        }
        assertEquals(field.bounds.x, cat.x, 0f)
        assertEquals(field.bounds.height, cat.height, 0f)
        assertEquals(0f, cat.velocityX, 0f)
        assertNull(cat.contact)
        cat.release()
        repeat(240) {
            val x = cat.x; val y = cat.height
            val gravity = field.pull(0, cat, square, dt)
            cat.step(0f, 0f, dt, square.support, gravityScale = gravity)
            field.resolve(cat, square, 24f, 16f)
            cat.contain(400f, 500f, square)
            assertTrue(hypot(cat.x - x, cat.height - y) < 6f)
        }
        assertTrue(hypot(cat.x - field.bounds.x, cat.height - field.bounds.height) > field.bounds.radius)
    }

    @Test fun tenFullDetailCatsStayBoundedAndSettleAfterPausing() {
        val hull = CatOutlineTest().artworkHull()
        val cats = List(10) { i -> WaveCatBody(30f + i * 38f, 1).apply { height = 15f } }
        val childWidth = 64f * cats[0].sizeScale
        fun shape(cat: WaveCatBody) = cat.outlineCache.get(hull, childWidth, childWidth / 1.5f, cat.compression,
            (cat.velocityX / 180f + cat.spin / 500f).coerceIn(-1f, 1f), cat.rotation, cat.contact)
        val outlines = cats.map { shape(it) }.toMutableList()
        val field = CenterCatField(400f, 500f, cats.size)
        val clock = CatPhysicsClock()
        val lifted = BooleanArray(cats.size)
        val sectors = BooleanArray(8)
        var peakSpeed = 0f
        val nanos = measureNanoTime {
            repeat(960) { frame ->
                val playing = frame < 480
                field.advance(if (playing) music else VisualizerFrame(), playing, 0f, 1f / 60f)
                if (playing && frame % 22 == 0) assertTrue(field.burst(cats, 0.8f) <= 3)
                repeat(clock.steps(16_666_667L)) {
                    for (i in cats.indices) {
                        val cat = cats[i]
                        val current = shape(cat)
                        val gravity = field.pull(i, cat, current, dt)
                        cat.step(0f, 0f, dt, current.support, gravityScale = gravity)
                        outlines[i] = shape(cat)
                    }
                    resolveCatContacts(cats, outlines)
                    repeat(2) { pass -> for (i in cats.indices) {
                        val cat = cats[i]
                        field.resolve(cat, shape(cat), childWidth, childWidth / 1.5f, deform = pass == 0)
                        outlines[i] = shape(cat)
                        cat.contain(400f, 500f, outlines[i])
                    } }
                }
                for (i in cats.indices) {
                    val cat = cats[i]
                    val current = shape(cat)
                    assertTrue(cat.x.isFinite() && cat.height.isFinite() && cat.compression.isFinite())
                    assertTrue(cat.x + current.left >= -0.1f && cat.x + current.right <= 400.1f)
                    assertTrue(cat.height + current.top <= 500.1f && cat.height + current.bottom >= -0.1f)
                    peakSpeed = maxOf(peakSpeed, hypot(cat.velocityX, cat.velocityY))
                    if (playing && cat.height > 80f) {
                        lifted[i] = true
                        val angle = atan2(cat.height - field.bounds.height, cat.x - field.bounds.x)
                        sectors[((angle + PI) / (2 * PI) * 8).toInt().coerceIn(0, 7)] = true
                    }
                }
            }
        }
        assertTrue("All 10 cats should participate", lifted.all { it })
        assertTrue("Cats should circulate instead of collecting under the object: ${sectors.toList()}", sectors.count { it } >= 7)
        assertEquals(0f, field.strength, 0f)
        assertTrue(cats.all { it.height <= shape(it).support + 1f && abs(it.velocityY) < 1f })
        File("build/reports/shiro-center-field-performance.txt").apply {
            parentFile?.mkdirs()
            writeText("10 full-detail cats, 8 seconds of music and 8 seconds of pause.\n" +
                "All cats lifted; visited ${sectors.count { it }}/8 angular sectors; peak speed %.1f dp/s.\n".format(peakSpeed) +
                "Desktop simulation including outline updates, core/cat collisions and checks: %.3f ms/frame.\n".format(nanos / 960_000_000.0) +
                "This is not Android rendering or device FPS.\n")
        }
    }
}
