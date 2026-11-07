package com.example.shiro

import org.junit.Assert.*
import org.junit.Test

class SpectrumSplashTest {
    @Test fun restingContactsAndEmptyBaselineDoNotSplash() {
        val effects = SpectrumImpacts(2)
        effects.hit(0, CatPoint(80f, 50f), 20f)
        effects.hit(0, CatPoint(80f, 0f), 300f)
        assertTrue(effects.isEmpty)
        effects.hit(0, CatPoint(80f, 50f), 80f)
        effects.hit(1, CatPoint(180f, 90f), 400f)
        assertEquals(2, effects.snapshot().size)
        assertTrue(effects.snapshot()[1].strength > effects.snapshot()[0].strength)
    }

    @Test fun repeatedContactIsDebouncedAndEffectsExpire() {
        val effects = SpectrumImpacts(1)
        effects.hit(0, CatPoint(80f, 50f), 180f)
        repeat(10) { effects.hit(0, CatPoint(80f, 50f), 180f) }
        assertEquals(1, effects.snapshot().size)
        effects.advance(0.2f)
        effects.hit(0, CatPoint(90f, 70f), 240f)
        assertEquals(2, effects.snapshot().size)
        effects.advance(0.6f)
        assertTrue(effects.isEmpty)
    }

    @Test fun burstsStayBoundedAndHiddenSceneCanClearEverything() {
        val effects = SpectrumImpacts(20)
        repeat(20) { effects.hit(it, CatPoint(it * 10f, 60f), 5000f) }
        assertEquals(12, effects.snapshot().size)
        assertTrue(effects.snapshot().all { it.strength <= 1f })
        effects.clear()
        assertTrue(effects.isEmpty)
        effects.hit(0, CatPoint(80f, 50f), 100f)
        assertEquals(1, effects.snapshot().size)
    }
}
