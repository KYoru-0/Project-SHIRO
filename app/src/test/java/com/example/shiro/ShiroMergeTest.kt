package com.example.shiro

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ShiroMergeTest {
    private fun outlines(bodies: List<WaveCatBody>) = bodies.map {
        val r = 20f * it.sizeScale
        CatOutline(listOf(CatPoint(-r, -r), CatPoint(r, -r), CatPoint(r, r), CatPoint(-r, r)))
    }

    private fun touch(bodies: List<WaveCatBody>, first: Int, second: Int) {
        bodies.forEachIndexed { index, body -> body.x = 1000f + index * 100f; body.height = 50f }
        bodies[first].apply { x = 100f; grab(); dragTo(100f, 50f, 80f, 40f) }
        bodies[second].apply { x = 110f; velocityX = 0f; velocityY = 0f }
    }

    @Test fun differentOriginalsCanMergeAndSplitAgain() {
        val bodies = createShiroFamilies(400f, 20f)
        val leftChildren = splitShiro(bodies, 0, 60f)!!
        val rightChildren = splitShiro(bodies, SHIRO_FAMILY_SIZE, 60f)!!
        touch(bodies, leftChildren[0], rightChildren[0])
        val mergedIndex = mergeDraggedShiro(bodies, leftChildren[0], outlines(bodies))!!
        val merged = bodies[mergedIndex]
        assertEquals(0, merged.generation)
        assertEquals(1f, merged.sizeScale, 0f)
        assertEquals(105f, merged.x, 0f)
        assertEquals(40f, merged.velocityX, 0f)
        assertEquals(20f, merged.velocityY, 0f)
        assertFalse(bodies[leftChildren[0]].active)
        assertFalse(bodies[rightChildren[0]].active)
        assertFalse(bodies[leftChildren[0]].isHeld)
        assertEquals(6, bodies.count { it.active })
        assertNotNull(splitShiro(bodies, mergedIndex, 60f))
        assertEquals(7, bodies.count { it.active })
    }

    @Test fun passiveContactCancelledDragAndDistantCatsNeverMerge() {
        val bodies = createShiroFamilies(400f, 20f)
        val children = splitShiro(bodies, 0, 60f)!!
        touch(bodies, children[0], children[1])
        bodies[children[0]].release()
        assertNull(mergeDraggedShiro(bodies, children[0], outlines(bodies)))
        bodies[children[0]].grab()
        bodies[children[1]].x = 300f
        assertNull(mergeDraggedShiro(bodies, children[0], outlines(bodies)))
        assertEquals(6, bodies.count { it.active })
    }

    @Test fun differentSizesOriginalSizeAndHeldTargetsDoNotMerge() {
        val bodies = createShiroFamilies(400f, 20f)
        val children = splitShiro(bodies, 0, 60f)!!
        touch(bodies, children[0], SHIRO_FAMILY_SIZE)
        assertNull(mergeDraggedShiro(bodies, children[0], outlines(bodies)))
        touch(bodies, SHIRO_FAMILY_SIZE, SHIRO_FAMILY_SIZE * 2)
        assertNull(mergeDraggedShiro(bodies, SHIRO_FAMILY_SIZE, outlines(bodies)))
        touch(bodies, children[0], children[1])
        bodies[children[1]].grab()
        assertNull(mergeDraggedShiro(bodies, children[0], outlines(bodies)))
    }

    @Test fun overlappingImageBoundsWithoutTouchingSilhouettesDoNotMerge() {
        val bodies = createShiroFamilies(400f, 20f)
        val children = splitShiro(bodies, 0, 60f)!!
        touch(bodies, children[0], children[1])
        bodies[children[1]].apply { x = 115f; height = 65f }
        val triangle = CatOutline(listOf(CatPoint(-10f, -10f), CatPoint(10f, -10f), CatPoint(-10f, 10f)))
        val shapes = outlines(bodies).toMutableList()
        children.forEach { shapes[it] = triangle }
        assertNull(mergeDraggedShiro(bodies, children[0], shapes))
        assertEquals(6, bodies.count { it.active })
    }

    @Test fun tenCatsCanReturnToFiveAndRepeatWithoutExhaustingSlotsOrChangingMass() {
        val bodies = createShiroFamilies(400f, 20f)
        val random = Random(42)
        repeat(12) {
            bodies.indices.filter { bodies[it].active && bodies[it].generation == 0 }
                .forEach { assertNotNull(splitShiro(bodies, it, 60f)) }
            assertEquals(10, bodies.count { it.active })
            val pairs = bodies.indices.filter { bodies[it].active && bodies[it].generation == 1 }
                .shuffled(random).chunked(2)
            pairs.forEach { (first, second) ->
                touch(bodies, first, second)
                assertNotNull(mergeDraggedShiro(bodies, first, outlines(bodies)))
                assertEquals(5f, bodies.filter { it.active }.sumOf {
                    (it.sizeScale * it.sizeScale).toDouble()
                }.toFloat(), 0.0001f)
                assertTrue(bodies.filter { !it.active }.none { it.isHeld })
                assertTrue(bodies.count { it.active } in 5..10)
            }
            assertEquals(5, bodies.count { it.active })
            assertTrue(bodies.filter { it.active }.all { it.generation == 0 && !it.isHeld })
        }
    }

    @Test fun recycledCatLosesOldDentAndFitsInsideEdgesAtItsLargerSize() {
        val bodies = createShiroFamilies(400f, 20f)
        val parent = bodies[0]
        parent.height = 20f; parent.velocityY = -300f
        parent.step(30f, 0f, 1f / 120f, 20f, 30f, WaveContact(0.5f, 0.9f, 0f, -1f, 0.2f))
        assertNotNull(parent.contact)
        val children = splitShiro(bodies, 0, 60f)!!
        touch(bodies, children[0], children[1])
        val merged = bodies[mergeDraggedShiro(bodies, children[0], outlines(bodies))!!]
        assertNull(merged.contact)
        assertEquals(0f, merged.compression, 0f)
        val shape = outlines(bodies)[bodies.indexOf(merged)]
        merged.x = 398f; merged.height = 399f
        merged.contain(400f, 400f, shape)
        assertTrue(merged.x + shape.right <= 400f && merged.height + shape.top <= 400f)
    }
}
