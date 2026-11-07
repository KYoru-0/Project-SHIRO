package com.example.shiro

import org.junit.Assert.*
import org.junit.Test

class WaveContactTest {
    private val width = 60f
    private val height = 40f

    @Test fun narrowerBarsProduceMoreLocalizedContact() {
        val point = CatPoint(0f, -15f)
        val broad = waveContactAt(point, 0f, width, height, 8, 360f)
        val narrow = waveContactAt(point, 0f, width, height, 24, 360f)
        assertTrue(narrow.spread < broad.spread)
        assertEquals(0f, broad.normalX, 0.001f)
        assertEquals(-1f, broad.normalY, 0.001f)
    }

    @Test fun flatBarTopPushesUpwardInTheCatsRotatedCoordinates() {
        val upright = waveContactAt(CatPoint(0f, -15f), 0f, width, height, 24, 360f)
        assertEquals(0f, upright.normalX, 0.001f)
        assertEquals(-1f, upright.normalY, 0.001f)
        val rotated = waveContactAt(CatPoint(0f, -15f), 90f, width, height, 24, 360f)
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
        val row = JELLY_ROWS * 7 / 8
        val left = (row * (JELLY_COLUMNS + 1) + JELLY_COLUMNS / 4) * 2 + 1
        val right = (row * (JELLY_COLUMNS + 1) + JELLY_COLUMNS * 3 / 4) * 2 + 1
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
        assertTrue("The dent must be visible on the impact frame", cat.compression > 0.7f)
        cat.step(0f, 0f, 1f / 120f, 20f, 0f, later)
        assertEquals(first, cat.contact)
    }
}
