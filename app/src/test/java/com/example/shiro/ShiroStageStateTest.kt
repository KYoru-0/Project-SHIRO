package com.example.shiro

import org.junit.Assert.*
import org.junit.Test

class ShiroStageStateTest {
    @Test fun interruptedDropResumesFromItsCurrentPositionInsteadOfTheTop() {
        val state = ShiroStageState()
        val bodies = state.bodiesFor(400f, 600f, 20f)
        assertTrue(bodies.filter { it.active }.all { it.height > 520f && it.velocityY < 0f })
        bodies[0].apply { height = 400f; velocityY = -220f }
        state.reveal.floatValue = 0.4f
        val returned = state.bodiesFor(400f, 600f, 20f)
        assertSame(bodies, returned)
        assertEquals(400f, returned[0].height, 0f)
        assertEquals(-220f, returned[0].velocityY, 0f)
        assertEquals(0.4f, state.reveal.floatValue, 0f)
    }

    @Test fun returningToTheStagePreservesSplitCatsMotionAndEntrance() {
        val state = ShiroStageState()
        val bodies = state.bodiesFor(400f, 600f, 20f)
        state.reveal.floatValue = 1f
        val children = splitShiro(bodies, 0, 64f)!!
        bodies[children[0]].apply { x = 172f; height = 250f; rotation = 137f; velocityX = 22f }
        repeat(4) {
            val returned = state.bodiesFor(400f, 600f, 20f)
            assertSame(bodies, returned)
            assertEquals(6, returned.count { it.active })
            assertEquals(172f, returned[children[0]].x, 0f)
            assertEquals(137f, returned[children[0]].rotation, 0f)
            assertEquals(22f, returned[children[0]].velocityX, 0f)
            assertEquals(1f, state.reveal.floatValue, 0f)
        }
    }

    @Test fun resizedStagePreservesPopulationAndScalesPositionsOnce() {
        val state = ShiroStageState()
        val bodies = state.bodiesFor(400f, 600f, 20f)
        bodies[0].apply { x = 100f; height = 150f; rotation = 70f }
        val resized = state.bodiesFor(600f, 400f, 24f)
        assertSame(bodies, resized)
        assertEquals(150f, resized[0].x, 0.001f)
        assertEquals(100f, resized[0].height, 0.001f)
        assertEquals(70f, resized[0].rotation, 0f)
        state.bodiesFor(600f, 400f, 24f)
        assertEquals(150f, resized[0].x, 0.001f)
    }

    @Test fun freshSessionStartsFiveOriginalsAndNewEntrance() {
        val previous = ShiroStageState()
        previous.reveal.floatValue = 1f
        splitShiro(previous.bodiesFor(400f, 600f, 20f), 0, 64f)
        val fresh = ShiroStageState()
        assertEquals(0f, fresh.reveal.floatValue, 0f)
        assertEquals(5, fresh.bodiesFor(400f, 600f, 20f).count { it.active && it.generation == 0 })
    }
}
