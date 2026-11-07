package com.example.shiro

import org.junit.Assert.*
import org.junit.Test

class CatOutlineCacheTest {
    @Test fun cachedGeometryMatchesFullMeshAcrossSizesDentsAndRotations() {
        val hull = CatOutlineTest().artworkHull()
        val cache = CatOutlineCache()
        for (scale in listOf(1f, 0.70710678f, 0.5f)) {
            for (compression in listOf(-0.75f, 0f, 0.3f, 1.2f)) for (rotation in listOf(0f, 37f, 90f, 210f)) {
                for (contact in listOf(null, WaveContact(0.7f, 0.9f, 0f, -1f, 0.2f))) {
                    val expected = deformCatOutline(hull, 60f * scale, 40f * scale, compression, 0.4f, rotation, contact)
                    val actual = cache.get(hull, 60f * scale, 40f * scale, compression, 0.4f, rotation, contact)
                    assertEquals(expected, actual)
                    assertSame(actual, cache.get(hull, 60f * scale, 40f * scale, compression, 0.4f, rotation, contact))
                }
            }
        }
    }

    @Test fun replacingArtworkOrChangingBendInvalidatesTheCacheWithoutMutatingOldOutlines() {
        val hull = CatOutlineTest().artworkHull()
        val cache = CatOutlineCache()
        val rest = cache.get(hull, 60f, 40f, 0f, 0f, 0f, null)
        val original = rest.points.toList()
        assertNotSame(rest, cache.get(hull, 60f, 40f, 0f, 1f, 0f, null))
        val replacement = hull.map { CatPoint(it.x * 0.8f, it.y) }
        assertEquals(deformCatOutline(replacement, 60f, 40f, 0f, 0f, 0f),
            cache.get(replacement, 60f, 40f, 0f, 0f, 0f, null))
        assertEquals(original, rest.points)
    }
}
