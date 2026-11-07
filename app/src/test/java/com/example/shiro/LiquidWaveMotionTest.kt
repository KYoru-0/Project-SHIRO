package com.example.shiro

import org.junit.Assert.*
import org.junit.Test

class LiquidWaveMotionTest {
    @Test fun frontRibbonMatchesContactGeometryAndHasLocalRecoveringDents() {
        val motion = LiquidWaveMotion(400f)
        val frame = VisualizerFrame(FloatArray(24) { 0.6f }, energy = 0.6f)
        val base = motion.advance(frame, 0f, 208f)
        motion.contact(200f, 300f, 1f)
        val dented = motion.advance(frame, 1f / 60f, 208f)
        assertTrue(base.pieces[0].heightAt(200f) > dented.pieces[0].heightAt(200f) + 3f)
        assertEquals(base.pieces[0].heightAt(50f), dented.pieces[0].heightAt(50f), 0.01f)
        val shape = CatOutline(listOf(CatPoint(-2f, -2f), CatPoint(2f, -2f), CatPoint(2f, 2f), CatPoint(-2f, 2f)))
        val sample = GlassSurfaceSampler().sample(shape, dented, 200f, 200f)
        assertEquals(dented.pieces[0].heightAt(sample.hit.x), sample.hit.y, 0.001f)
        assertEquals(sample.hit.y + 2f, sample.centerHeight, 0.001f)
        var recovered = dented
        repeat(180) { recovered = motion.advance(frame, 1f / 60f, 208f) }
        assertArrayEquals(base.pieces[0].top, recovered.pieces[0].top, 0.01f)
    }

    @Test fun pauseAndSilenceLeaveNoInvisibleRaisedFloor() {
        val motion = LiquidWaveMotion(400f)
        val frame = VisualizerFrame(FloatArray(24) { 1f }, energy = 1f)
        motion.advance(frame, 1f / 60f, 208f)
        motion.contact(200f, 10_000f, 100f)
        val silent = motion.advance(VisualizerFrame(), 1f / 60f, 208f)
        assertTrue(silent.pieces[0].top.all { it == 0f })
        assertTrue(silent.pieces[0].bottom.all { it == 0f })
        assertEquals(0f, silent.energy, 0f)
    }
}
