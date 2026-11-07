package com.example.shiro

import org.junit.Assert.*
import org.junit.Test

class WaveContactTest {
    private val width = 60f
    private val height = 40f

    @Test fun peaksProduceNarrowerContactThanFlatWave() {
        val flat = FloatArray(25) { 0.5f }
        val peak = FloatArray(25).apply { this[12] = 1f }
        val point = CatPoint(0f, -15f)
        val broad = waveContactAt(point, 180f, 0f, width, height, flat, 360f, 100f)
        val narrow = waveContactAt(point, 180f, 0f, width, height, peak, 360f, 100f)
        assertTrue(narrow.spread < broad.spread)
        assertEquals(0f, broad.normalX, 0.001f)
        assertEquals(-1f, broad.normalY, 0.001f)
    }

    @Test fun slopeAndCatRotationDetermineWhichSideIsPushed() {
        val rising = FloatArray(25) { it / 24f }
        val upright = waveContactAt(CatPoint(0f, -15f), 180f, 0f, width, height, rising, 360f, 100f)
        assertTrue(upright.normalX < 0f && upright.normalY < 0f)
        val rotated = waveContactAt(CatPoint(0f, -15f), 180f, 90f, width, height, FloatArray(25), 360f, 100f)
        assertTrue(rotated.u > 0.5f)
        assertEquals(-1f, rotated.normalX, 0.001f)
        assertEquals(0f, rotated.normalY, 0.001f)
    }

    @Test fun localImpactDeformsNearbyMeshMoreThanOppositeSide() {
        val contact = WaveContact(0.25f, 0.85f, 0f, -1f, 0.12f)
        val baseline = FloatArray((JELLY_COLUMNS + 1) * (JELLY_ROWS + 1) * 2)
        val pushed = baseline.copyOf()
        // Compare with general squash at the same 30% strength used for wave impacts.
        updateJellyMesh(baseline, width, height, 0.3f, 0f)
        updateJellyMesh(pushed, width, height, 1f, 0f, contact)
        val left = (7 * (JELLY_COLUMNS + 1) + 3) * 2 + 1
        val right = (7 * (JELLY_COLUMNS + 1) + 9) * 2 + 1
        assertTrue(baseline[left] - pushed[left] > 4f)
        assertTrue(baseline[left] - pushed[left] > (baseline[right] - pushed[right]) * 3f)
    }

    @Test fun contactIsCapturedOnImpactAndDoesNotFollowLaterAirborneCurve() {
        val cat = WaveCatBody(100f).apply { this.height = 20f }
        val first = WaveContact(0.3f, 0.9f, 0f, -1f, 0.12f)
        val later = first.copy(u = 0.8f)
        cat.step(0f, 0f, 1f / 120f, 20f)
        cat.step(40f, 0f, 1f / 120f, 20f, 40f, first)
        assertEquals(first, cat.contact)
        cat.step(0f, 0f, 1f / 120f, 20f, 0f, later)
        assertEquals(first, cat.contact)
    }
}
