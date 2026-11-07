package com.example.shiro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatPhysicsClockTest {
    @Test fun displayRefreshAndNanosecondRoundingDoNotIncreasePhysicsRate() {
        for (refresh in listOf(30, 60, 90, 120, 144, 240)) {
            val clock = CatPhysicsClock()
            var previous = 0L
            var steps = 0
            for (frame in 1..refresh * 5) {
                val now = frame * 1_000_000_000L / refresh
                val count = clock.steps(now - previous)
                assertTrue(count in 0..4)
                steps += count
                previous = now
            }
            assertEquals("$refresh Hz display", 600, steps)
        }
    }

    @Test fun slightlyLateSixtyHertzFramesDoNotCreateAThirdStepEveryFrame() {
        val clock = CatPhysicsClock()
        repeat(600) { assertEquals(2, clock.steps(16_666_668L)) }
    }

    @Test fun aStallHasBoundedCatchUpAndNoLingeringBacklog() {
        val clock = CatPhysicsClock()
        assertEquals(4, clock.steps(Long.MAX_VALUE))
        assertEquals(0, clock.steps(0L))
        assertEquals(0, clock.steps(-1L))
        repeat(10) { assertEquals(1, clock.steps(8_333_334L)) }
    }
}
