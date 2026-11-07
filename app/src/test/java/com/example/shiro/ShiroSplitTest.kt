package com.example.shiro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiroSplitTest {
    @Test fun fiveFamiliesReachTenCatsAndChildrenCannotSplitAgain() {
        val bodies = createShiroFamilies(400f, 20f)
        assertEquals(5, bodies.count { it.active })
        val parents = bodies.indices.filter { bodies[it].active }
        parents.forEach { index ->
            assertNotNull(splitShiro(bodies, index, 60f))
            assertFalse(bodies[index].active)
            assertNull(splitShiro(bodies, index, 60f))
            // Splitting replaces, rather than duplicates, the parent's total body area.
            assertEquals(5f, bodies.filter { it.active }.sumOf {
                (it.sizeScale * it.sizeScale).toDouble()
            }.toFloat(), 0.0001f)
        }
        bodies.indices.forEach { assertNull(splitShiro(bodies, it, 60f)) }
        assertEquals(10, bodies.count { it.active })
        assertEquals(15, bodies.size) // Only five original and ten child slots are allocated.
        assertTrue(bodies.filter { it.active }.all { it.generation == 1 && it.sizeScale == 0.70710678f })
    }

    @Test fun childrenAppearAtTheirParentWithOpposingPopAndMatchingSize() {
        val bodies = createShiroFamilies(400f, 20f)
        val parent = bodies[0].apply {
            x = 200f; height = 160f; rotation = 90f; velocityX = 30f; velocityY = 120f
        }
        val children = splitShiro(bodies, 0, 60f)!!.map { bodies[it] }
        children.forEach { assertNull(splitShiro(bodies, bodies.indexOf(it), 60f)) }
        assertEquals(200f, (children[0].x + children[1].x) / 2f, 0.001f)
        assertTrue(children[0].x < parent.x && children[1].x > parent.x)
        assertTrue(children[0].velocityX < 0f && children[1].velocityX > 0f)
        assertEquals(30f, children.map { it.velocityX }.average().toFloat(), 0.001f)
        assertTrue(children.all { it.height == 163f && it.velocityY == 120f && !it.isHeld })
        assertEquals(90f, children.map { it.rotation }.average().toFloat(), 0.001f)
        children.forEach { child ->
            assertEquals(0.70710678f, child.sizeScale, 0.0001f)
            child.step(0f, 0f, 1f / 120f, 20f * child.sizeScale)
            assertTrue(child.height > 163f)
            assertTrue(child.compression > 0f)
        }
    }

    @Test fun splittingReleasesGrabbedParentAndLeavesOtherFamiliesAlone() {
        val bodies = createShiroFamilies(400f, 20f)
        bodies[SHIRO_FAMILY_SIZE].grab()
        assertNotNull(splitShiro(bodies, SHIRO_FAMILY_SIZE, 60f))
        assertFalse(bodies[SHIRO_FAMILY_SIZE].isHeld)
        assertFalse(bodies[SHIRO_FAMILY_SIZE].active)
        assertTrue(bodies[0].active)
        assertEquals(6, bodies.count { it.active })
        assertNull(splitShiro(bodies, -1, 60f))
        assertNull(splitShiro(bodies, bodies.size, 60f))
    }
}
