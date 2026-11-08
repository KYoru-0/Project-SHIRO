package com.example.shiro

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class ShiroEntranceTest {
    @Test fun freshDropStartsInACompactGroupAtTopCenterWithoutOverlapping() {
        val bodies = createShiroFamilies(400f, 20f)
        positionShirosForDrop(bodies, 400f, 600f, 20f, Random(42))
        val originals = bodies.filter { it.active }
        assertEquals(5, originals.size)
        assertTrue(originals.all { it.generation == 0 && it.sizeScale == 1f })
        assertTrue(originals.all { it.x in 140f..260f && it.height in 520f..600f && it.velocityY < 0f })
        assertEquals(200f, originals.map { it.x }.average().toFloat(), 0.001f)
        assertTrue(originals.take(3).all { it.height > originals.last().height })
        val hull = CatOutlineTest().artworkHull()
        val shapes = originals.map { deformCatOutline(hull, 75f, 50f, 0f,
            (it.velocityX / 180f + it.spin / 500f).coerceIn(-1f, 1f), it.rotation) }
        originals.forEachIndexed { i, body ->
            assertTrue(body.height + shapes[i].top <= 600f)
            for (j in i + 1 until originals.size) {
                assertNull(catOverlap(shapes[i], body.x, body.height,
                    shapes[j], originals[j].x, originals[j].height))
            }
        }
        assertEquals(5, originals.map { it.velocityY }.distinct().size)
        assertEquals(5, originals.map { it.rotation }.distinct().size)
        assertTrue(bodies.filter { !it.active }.all { it.height < 20f && it.velocityY == 0f })
    }

    @Test fun dropUsesGravityAndBouncesOnTheExistingSurface() {
        val bodies = createShiroFamilies(400f, 20f)
        positionShirosForDrop(bodies, 400f, 600f, 20f, Random(7))
        val cat = bodies.first()
        val start = cat.height
        repeat(30) { cat.step(0f, 0f, CatPhysicsClock.STEP_SECONDS, 20f) }
        assertTrue(cat.height < start - 20f)
        var landed = false
        repeat(240) {
            val impact = cat.step(0f, 0f, CatPhysicsClock.STEP_SECONDS, 20f)
            if (impact > 70f) {
                landed = true
                assertTrue(cat.velocityY > 0f)
            }
            assertTrue(cat.height >= 20f)
        }
        assertTrue(landed)
    }

    @Test fun newLaunchesKeepTheCenteredGroupButVaryItsMotion() {
        val first = createShiroFamilies(400f, 20f)
        val second = createShiroFamilies(400f, 20f)
        positionShirosForDrop(first, 400f, 600f, 20f, Random(1))
        positionShirosForDrop(second, 400f, 600f, 20f, Random(2))
        assertEquals(first.map { it.x }, second.map { it.x })
        assertNotEquals(first.map { it.velocityY }, second.map { it.velocityY })
        assertEquals(5, second.count { it.active })
    }
}
