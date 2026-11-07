package com.example.shiro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkPaletteTest {
    @Test fun solidCoverGetsLighterAndDarkerShades() {
        val palette = artworkPalette(IntArray(100) { 0xFF408080.toInt() })
        assertEquals(0xFF408080.toInt(), palette[1])
        assertTrue((palette[0] and 255) > (palette[1] and 255))
        assertTrue((palette[2] and 255) < (palette[1] and 255))
    }

    @Test fun dominantDistinctColorsSurviveWithoutBecomingAnAverage() {
        val pixels = IntArray(100) { if (it < 70) 0xFFFF0000.toInt() else 0xFF0000FF.toInt() }
        val palette = artworkPalette(pixels)
        assertEquals(0xFFFF0000.toInt(), palette.first())
        assertEquals(0xFF0000FF.toInt(), palette.last())
        assertEquals(0xFF7F007F.toInt(), palette[1])
    }

    @Test fun transparentPixelsDoNotPolluteColorsAndEmptyArtFallsBackToBlue() {
        assertEquals(artworkPalette(intArrayOf()), artworkPalette(IntArray(10) { 0x00FF0000 }))
        assertEquals(artworkPalette(intArrayOf(0xFF00FF00.toInt())),
            artworkPalette(intArrayOf(0x00FF0000, 0xFF00FF00.toInt())))
    }

    @Test fun grayscaleCoverRemainsGrayscaleAndOpaque() {
        val palette = artworkPalette(intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()))
        assertTrue(palette.all { color ->
            color ushr 24 == 255 && (color ushr 16 and 255) == (color ushr 8 and 255) &&
                (color ushr 8 and 255) == (color and 255)
        })
    }
}
