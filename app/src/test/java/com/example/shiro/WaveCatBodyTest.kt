package com.example.shiro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WaveCatBodyTest {
    private val square = CatOutline(listOf(CatPoint(-20f, -20f), CatPoint(20f, -20f), CatPoint(20f, 20f), CatPoint(-20f, 20f)))
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

    @Test fun interpolationMatchesCurveAndStaysWithinBandHeights() {
        val bands = floatArrayOf(0f, 1f)
        assertEquals(0.5f, waveHeightAt(bands, 0.5f), 0.001f)
        repeat(101) { assertTrue(waveHeightAt(bands, it / 100f) in 0f..1f) }
        assertEquals(0f, waveHeightAt(bands, -1f), 0f)
        assertEquals(1f, waveHeightAt(bands, 2f), 0f)
    }
}
