package com.example.shiro

import org.junit.Assert.*
import org.junit.Test

class ShiroEntranceTest {
    @Test fun entranceStaggersAllFiveCatsAndFinishesAtNormalSize() {
        assertEquals(0f, shiroRevealPhase(0f, 0), 0f)
        assertTrue(shiroRevealPhase(0.2f, 0) > 0f)
        assertEquals(0f, shiroRevealPhase(0.2f, 4), 0f)
        for (cat in 0..4) {
            assertEquals(1f, shiroRevealPhase(1f, cat), 0f)
            assertEquals(1f, shiroRevealScale(shiroRevealPhase(1f, cat)), 0.0001f)
        }
        val scales = (0..100).map { shiroRevealScale(it / 100f) }
        assertTrue(scales.max() > 1f)
        assertTrue(scales.all { it in 0.199f..1.08f })
    }
}
