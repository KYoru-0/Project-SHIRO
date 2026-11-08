package com.example.shiro

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class StageGradientTest {
    @Test fun strongestBeatsKeepTheOverlaySubtle() {
        assertEquals(0.24f, stageGradientOpacity(MusicBackdropFrame(energy = 1f, pulse = 1f)), 0.001f)
        assertTrue(stageGradientOpacity(MusicBackdropFrame(energy = 2f, pulse = 2f)) <= 0.24f)
    }
    @Test fun floorCoverageAndCircleSeamsStayIntactWithoutAllocatingVertexBuffers() {
        val sheet = GradientSheet()
        val buffer = sheet.vertices
        for (liquid in listOf(0f, 0.5f, 1f)) for (circle in listOf(false, true)) {
            for (layer in 0..2) repeat(100) { step ->
                sheet.update(MusicBackdropFrame(step * 0.1, 1f, 1f, 1f,
                    FloatArray(24) { if (it % 3 == 0) 1f else 0f }, step * 0.03f), layer, liquid, circle)
                assertSame(buffer, sheet.vertices)
                assertTrue(buffer.all { it.isFinite() })
                for (column in 0..GradientSheet.COLUMNS) {
                    val top = column * 2
                    val bottom = (GradientSheet.COLUMNS + 1 + column) * 2
                    if (circle) {
                        val inner = hypot(buffer[top] - 0.5f, buffer[top + 1] - 0.5f)
                        val outer = hypot(buffer[bottom] - 0.5f, buffer[bottom + 1] - 0.5f)
                        assertTrue("Circle layers must not invert", inner > 0.02f && outer - inner > 0.22f)
                    } else {
                        assertEquals(buffer[top], buffer[bottom], 0f)
                        assertTrue(buffer[bottom + 1] - buffer[top + 1] > 0.22f)
                        if (column > 0) assertTrue(buffer[top] > buffer[top - 2])
                    }
                }
                if (circle) {
                    for (row in 0..1) for (axis in 0..1) {
                        val start = row * (GradientSheet.COLUMNS + 1) * 2 + axis
                        assertEquals("Closed ring has no seam", buffer[start], buffer[start + GradientSheet.COLUMNS * 2], 0f)
                    }
                } else assertTrue(buffer[0] < 0f && buffer[GradientSheet.COLUMNS * 2] > 1f)
            }
        }
    }

    @Test fun beatsMoveLayersSmoothlyAndPauseFadesThemOut() {
        for (circle in listOf(false, true)) {
            val motion = MusicBackdropMotion()
            val sheets = Array(3) { GradientSheet() }
            val previous = Array(3) { FloatArray(sheets[it].vertices.size) }
            var opacityBeforePause = 0f
            var frame = MusicBackdropFrame()
            assertEquals(0f, stageGradientOpacity(frame), 0f)
            repeat(900) { step ->
                val bands = FloatArray(24) { if ((it + step / 20) % 3 == 0) 0.9f else 0.25f }
                frame = motion.advance(bands,
                    MusicDynamics(-6f, if (step % 20 < 2) 1f else 0f, 190f, 1f),
                    playing = step < 300, seconds = 1f / 60)
                if (step == 299) opacityBeforePause = stageGradientOpacity(frame)
                if (step == 300) assertTrue(stageGradientOpacity(frame) in 0f..opacityBeforePause)
                if (step == 420) assertTrue("Gradient should disappear after the pause fade", stageGradientOpacity(frame) < 0.005f)
                for (layer in 0..2) {
                    sheets[layer].update(frame, layer, 1f, circle)
                    if (step > 0) for (i in previous[layer].indices) {
                        assertTrue("Beats must not teleport a layer", abs(previous[layer][i] - sheets[layer].vertices[i]) < 0.04f)
                    }
                    sheets[layer].vertices.copyInto(previous[layer])
                }
            }
            assertTrue(opacityBeforePause in 0.1f..0.24f)
            assertTrue(motion.isSettled)
            assertEquals(0f, stageGradientOpacity(frame), 0f)
            assertEquals(0f, stageGradientOpacity(motion.advance(FloatArray(24), MusicDynamics(), true, 1f / 60)), 0f)
            assertEquals(0f, stageGradientOpacity(motion.advance(FloatArray(24) { 1f }, MusicDynamics(), true,
                1f / 60, motionEnabled = false)), 0f)
        }
    }

    @Test fun audioShapeAndRotationDriveTheSelectedLayout() {
        val sheet = GradientSheet()
        val silent = MusicBackdropFrame(0.3, 0.7f, 0.3f, 0.2f, FloatArray(24))
        val loud = silent.copy(spectrum = FloatArray(24) { if (it < 12) 1f else 0f })
        sheet.update(silent, 0, 0f, false)
        val baseline = sheet.vertices.copyOf()
        sheet.update(loud, 0, 0f, false)
        val bassColumn = GradientSheet.COLUMNS / 4 * 2 + 1
        val trebleColumn = GradientSheet.COLUMNS * 3 / 4 * 2 + 1
        assertTrue("Bass movement stays visible but restrained", baseline[bassColumn] - sheet.vertices[bassColumn] in 0.1f..0.2f)
        assertEquals(baseline[trebleColumn], sheet.vertices[trebleColumn], 0.001f)
        val spectrum = sheet.vertices.copyOf()
        sheet.update(loud.copy(phase = 1.3), 0, 1f, false)
        assertTrue("Liquid keeps a gentle flowing shape", spectrum.indices.any { abs(spectrum[it] - sheet.vertices[it]) > 0.03f })
        sheet.update(loud, 0, 0f, true)
        val circle = sheet.vertices.copyOf()
        sheet.update(loud.copy(rotation = (Math.PI / 2).toFloat()), 0, 0f, true)
        for (i in circle.indices step 2) {
            assertEquals(circle[i + 1] - 0.5f, sheet.vertices[i] - 0.5f, 0.0001f)
            assertEquals(-(circle[i] - 0.5f), sheet.vertices[i + 1] - 0.5f, 0.0001f)
        }
    }

    @Test fun overlaysStayInTheActualArtworkFamilyAndMonochromeStaysNeutral() {
        val seed = Color(0xFFE9D966)
        val colors = stageGradientColors(playerColors(seed))
        assertEquals(seed, colors.first())
        assertEquals(3, colors.distinct().size)
        for (color in colors) {
            assertTrue("Yellow cover must stay gold, not pink or cyan", color.red > color.blue && color.green > color.blue)
        }
        for (color in stageGradientColors(playerColors(Color.Gray))) {
            assertEquals(color.red, color.green, 0.001f)
            assertEquals(color.red, color.blue, 0.001f)
        }
    }
}
