package com.example.shiro

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerColorsTest {
    @Test fun menusAndDialogsDoNotShowUnderlyingContent() {
        val colors = playerColors(Color(0xFFE9D966))
        for (stop in listOf(colors.glassTint(dense = true), glassHighlight(dense = true))) {
            assertEquals(stop.compositeOver(Color.Black), stop.compositeOver(Color.White))
        }
        assertTrue("Player cards still retain their light glass styling", colors.glassTint().alpha < 0.3f)
    }

    @Test fun artworkShadesPreserveContrastAcrossTheirBlends() {
        for (cover in listOf(0xFFE9D966, 0xFFFF0000, 0xFF0000FF, 0xFF00FF00, 0xFFAAAAAA, 0xFFFFFFFF, 0xFF000000)) {
            val colors = playerColors(Color(artworkPalette(intArrayOf(cover.toInt()))[1]))
            val lights = backdropLights(colors)
            val stops = listOf(colors.selected, colors.backdrop, lights.light, lights.shade, lights.pearl)
            for (from in stops) for (to in stops) for (step in 0..10) {
                val surface = lerp(from, to, step / 10f)
                for (ink in listOf(colors.text, colors.secondaryText, colors.accent)) {
                    assertTrue("Lighting blend contrast for $cover", (surface.luminance() + 0.05f) / (ink.luminance() + 0.05f) >= 4.5f)
                }
            }
            if (cover == 0xFFE9D966) for (stop in stops) {
                assertTrue("Yellow artwork must not acquire red/teal background light", stop.red >= stop.blue && stop.green >= stop.blue)
            }
            if (cover == 0xFFAAAAAA) assertTrue(kotlin.math.abs(lights.light.red - lights.light.blue) < 0.001f)
        }
    }

    @Test fun glassPanelsKeepLabelsReadableOverArtworkAndTintedPages() {
        val covers = listOf(0xFFE9D966, 0xFFFF0000, 0xFF0000FF, 0xFF00FF00, 0xFFAAAAAA, 0xFFFFFFFF, 0xFF000000)
        fun contrast(a: Color, b: Color): Float {
            val light = maxOf(a.luminance(), b.luminance())
            val dark = minOf(a.luminance(), b.luminance())
            return (light + 0.05f) / (dark + 0.05f)
        }
        for (cover in covers) {
            val colors = playerColors(Color(artworkPalette(intArrayOf(cover.toInt()))[1]))
            // Drawers/dialogs sit over arbitrary artwork; regular cards sit on the tinted page.
            val surfaces = listOf(
                colors.glassTint(dense = true).compositeOver(Color.Black),
                colors.glassTint(dense = true).compositeOver(Color.White),
                colors.glassTint().compositeOver(colors.background),
                colors.glassTint().compositeOver(colors.selected),
                colors.glassTint(selected = true).compositeOver(colors.background),
                colors.glassTint().compositeOver(colors.backdrop),
                colors.glassTint(selected = true).compositeOver(colors.backdrop),
                glassHighlight().compositeOver(colors.backdrop),
                glassHighlight(dense = true).compositeOver(Color.Black)
            )
            for (surface in surfaces) {
                assertTrue("Primary label contrast for $cover", contrast(surface, colors.text) >= 4.5f)
                assertTrue("Secondary label contrast for $cover", contrast(surface, colors.secondaryText) >= 4.5f)
                assertTrue("Control contrast for $cover", contrast(surface, colors.accent) >= 3f)
            }
            assertTrue(contrast(Color.White, colors.accent) >= 4.5f)
            // Time labels and secondary metadata can sit directly on the richer backdrop.
            assertTrue("Backdrop text contrast for $cover", contrast(colors.backdrop, colors.secondaryText) >= 4.5f)
            assertTrue("Backdrop control label contrast for $cover", contrast(colors.backdrop, colors.accent) >= 4.5f)
        }
    }

    @Test fun artworkThemesKeepTextAndPlayIconReadable() {
        val covers = listOf(0xFFE9D966, 0xFFFF0000, 0xFF0000FF, 0xFF00FF00, 0xFFAAAAAA, 0xFFFFFFFF, 0xFF000000)
        for (cover in covers) {
            val seed = Color(artworkPalette(intArrayOf(cover.toInt()))[1])
            val colors = playerColors(seed)
            fun contrast(light: Color, dark: Color) = (light.luminance() + 0.05f) / (dark.luminance() + 0.05f)
            assertTrue(contrast(Color.White, colors.accent) >= 4.5f)
            assertTrue(contrast(colors.surface, colors.text) >= 4.5f)
            assertTrue(contrast(colors.surface, colors.secondaryText) >= 4.5f)
            assertTrue(contrast(colors.background, colors.accent) >= 4.5f)
        }
    }
}
