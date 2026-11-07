package com.example.shiro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WaveCatBodyTest {
    private val square = CatOutline(listOf(CatPoint(-20f, -20f), CatPoint(20f, -20f), CatPoint(20f, 20f), CatPoint(-20f, 20f)))

    @Test fun retiredParentsDoNotMoveOrCollide() {
        val retired = WaveCatBody(100f, active = false).apply { height = 20f; velocityX = 200f }
        val child = WaveCatBody(110f, generation = 1).apply { height = 20f }
        retired.step(50f, 2f, 1f / 60f, 20f)
        resolveCatContacts(listOf(retired, child), listOf(square, square))
        assertEquals(100f, retired.x, 0f)
        assertEquals(20f, retired.height, 0f)
        assertEquals(110f, child.x, 0f)
        assertEquals(0f, child.velocityX, 0f)
    }

    @Test fun smallCatsTakeTheirShareOfCollisionByBodyArea() {
        val large = WaveCatBody(100f).apply { height = 40f; velocityX = 50f }
        val small = WaveCatBody(125f, generation = 1).apply { height = 40f; velocityX = -50f }
        val smallShape = CatOutline(square.points.map { CatPoint(it.x * small.sizeScale, it.y * small.sizeScale) })
        val momentum = large.velocityX + small.velocityX * 0.5f
        resolveCatContacts(listOf(large, small), listOf(square, smallShape))
        assertEquals((100f - large.x) * 2f, small.x - 125f, 0.001f)
        assertEquals(20f + smallShape.right, small.x - large.x, 0.001f)
        assertEquals(momentum, large.velocityX + small.velocityX * 0.5f, 0.001f)
        assertTrue(small.velocityX > large.velocityX)
    }

    @Test fun idleHopIsGentleTravelsSidewaysAndSettles() {
        val cat = WaveCatBody(100f).apply { height = 20f; idleHop(1f) }
        var peak = cat.height
        repeat(900) {
            cat.step(0f, 0f, 1f / 120f, 20f)
            peak = maxOf(peak, cat.height)
        }
        assertTrue(peak in 25f..35f)
        assertTrue(cat.x > 100f)
        assertTrue(cat.isSettled(20f))
        cat.grab()
        cat.idleHop(-1f)
        assertEquals(0f, cat.velocityX, 0f)
        assertEquals(0f, cat.velocityY, 0f)
        assertTrue(cat.isHeld)
    }

    @Test fun landingSignalOnlyFiresOnActualImpacts() {
        val cat = WaveCatBody(100f).apply { height = 200f; velocityY = -150f }
        assertEquals(0f, cat.step(50f, 0f, 1f / 120f, 20f), 0f)
        cat.height = 72f
        assertTrue(cat.step(50f, 0f, 1f / 120f, 20f) == 0f) // Still above the visible surface.
        cat.height = 70f
        assertTrue(cat.step(50f, 0f, 1f / 120f, 20f) >= 150f)
        cat.height = 70f; cat.velocityY = 0f
        assertEquals(0f, cat.step(50f, 0f, 1f / 120f, 20f), 0f)
        cat.grab()
        assertEquals(0f, cat.step(100f, 0f, 1f / 120f, 20f), 0f)
    }

    @Test fun draggingSuspendsGravityAndReleaseKeepsBoundedMomentum() {
        val cat = WaveCatBody(100f).apply { height = 100f; grab() }
        cat.dragTo(140f, 180f, 300f, 200f)
        cat.step(0f, 0f, 1f / 60f, 20f)
        assertEquals(140f, cat.x, 0f)
        assertEquals(180f, cat.height, 0f)
        assertTrue(cat.compression in -0.38f..-0.08f)
        assertFalse(cat.isSettled(200f))
        cat.release(3000f, 2000f)
        assertFalse(cat.isHeld)
        assertTrue(kotlin.math.hypot(cat.velocityX, cat.velocityY) <= 450.001f)
        cat.step(0f, 0f, 1f / 120f, 20f)
        assertTrue(cat.x > 140f && cat.height > 180f)
        assertTrue(cat.velocityX > 180f) // Airborne throws retain momentum beyond the walking limit.
    }

    @Test fun cancelledGrabDropsWithoutFlingingAndDoesNotStayHeld() {
        val cat = WaveCatBody(100f).apply { height = 100f; grab(); dragTo(150f, 200f, 400f, 400f) }
        cat.release()
        assertFalse(cat.isHeld)
        assertEquals(0f, cat.velocityX, 0f)
        assertEquals(0f, cat.velocityY, 0f)
        assertEquals(0f, cat.spin, 0f)
        cat.step(0f, 0f, 1f / 60f, 20f)
        assertTrue(cat.height < 200f)
    }

    @Test fun heldCatPushesOtherCatsWithoutBeingPulledAwayFromFinger() {
        val held = WaveCatBody(100f).apply { height = 40f; grab(); dragTo(100f, 40f, 80f, 0f) }
        val other = WaveCatBody(130f).apply { height = 40f }
        resolveCatContacts(listOf(held, other), listOf(square, square))
        assertEquals(100f, held.x, 0f)
        assertEquals(40f, held.height, 0f)
        assertEquals(140f, other.x, 0.001f)
        assertTrue(other.velocityX > 0f)
        held.contain(360f, 400f, square)
        held.dragTo(900f, 900f, 400f, 400f)
        held.contain(360f, 400f, square)
        assertTrue(held.x <= 340f && held.height <= 380f)
    }
    @Test fun jellyOscillatesAfterImpactAndSettles() {
        val cat = WaveCatBody(120f).apply { height = 20f; wobble(1f) }
        var compressed = false
        var stretched = false
        repeat(720) {
            cat.step(0f, 0f, 1f / 120f, 20f)
            compressed = compressed || cat.compression > 0.1f
            stretched = stretched || cat.compression < -0.02f
            assertTrue(cat.compression in -0.75f..1.20f)
        }
        assertTrue(compressed && stretched)
        assertTrue(cat.isSettled(20f))
    }

    @Test fun risingWaveLaunchesLighterCatsAboveTheSurface() {
        val cat = WaveCatBody(120f).apply { height = 20f }
        cat.step(0f, 0f, 1f / 120f, 20f)
        cat.step(40f, 0f, 1f / 120f, 20f)
        assertTrue(cat.velocityY > 100f)
        var peak = cat.height
        repeat(120) { cat.step(40f, 0f, 1f / 120f, 20f); peak = maxOf(peak, cat.height) }
        assertTrue(peak > 70f)
    }
    @Test fun waveLiftsCatAndGravityReturnsItToRest() {
        val cat = WaveCatBody(120f)
        repeat(120) { cat.step(100f, 0f, 1f / 120f, 20f) }
        assertTrue(cat.height >= 114f)
        repeat(720) { cat.step(0f, 0f, 1f / 120f, 20f) }
        assertEquals(20f, cat.height, 0.01f)
        assertTrue(cat.isSettled(20f))
    }

    @Test fun slopedWaveMovesAndRollsCat() {
        val cat = WaveCatBody(200f).apply { height = 20f }
        repeat(120) { cat.step(80f, 0.4f, 1f / 120f, 20f) }
        assertTrue(cat.x < 190f)
        assertTrue(abs(cat.rotation) > 5f)
        assertTrue(abs(cat.velocityX) <= 180f)
    }

    @Test fun edgesContainStrongImpulses() {
        val cat = WaveCatBody(-50f).apply { height = 500f; velocityX = -200f; velocityY = 300f }
        cat.contain(360f, 400f, square)
        assertEquals(20f, cat.x, 0f)
        assertEquals(380f, cat.height, 0f)
        assertTrue(cat.velocityX >= 0f && cat.velocityY <= 0f)
        cat.x = 500f
        cat.velocityX = 200f
        cat.height = -40f
        cat.contain(360f, 400f, square)
        assertEquals(340f, cat.x, 0f)
        assertEquals(20f, cat.height, 0f)
        assertTrue(cat.velocityX <= 0f)
    }

    @Test fun catsSeparateAndAbsorbCollisionEnergy() {
        val a = WaveCatBody(100f).apply { height = 20f; velocityX = 50f }
        val b = WaveCatBody(120f).apply { height = 20f; velocityX = -50f }
        resolveCatContacts(listOf(a, b), listOf(square, square))
        assertEquals(40f, b.x - a.x, 0.001f)
        assertTrue(a.velocityX < 0f && b.velocityX > 0f)
        assertTrue(abs(a.velocityX) < 50f && abs(b.velocityX) < 50f)
    }

    @Test fun spectrumKeepsIndividualBandHeightsAndClampsScreenEdges() {
        val bands = floatArrayOf(0f, 1f)
        assertEquals(0f, spectrumBandAt(bands, 0.49f), 0f)
        assertEquals(1f, spectrumBandAt(bands, 0.5f), 0f)
        assertEquals(0f, spectrumBandAt(bands, -1f), 0f)
        assertEquals(1f, spectrumBandAt(bands, 2f), 0f)
    }

    @Test fun singleAndEmptySpectraStayStable() {
        assertEquals(0f, spectrumBandAt(floatArrayOf(), 0.5f), 0f)
        assertEquals(0.7f, spectrumBandAt(floatArrayOf(0.7f), 0.9f), 0f)
    }
}
