package com.example.shiro

import kotlin.math.abs

/** Prefer a cover's colored accent over black/white backgrounds, then keep one coherent hue. */
internal fun artworkPalette(pixels: IntArray): List<Int> {
    val counts = IntArray(512)
    val red = LongArray(512)
    val green = LongArray(512)
    val blue = LongArray(512)
    var opaque = 0
    for (pixel in pixels) {
        if (pixel ushr 24 < 128) continue
        opaque++
        val r = pixel ushr 16 and 255
        val g = pixel ushr 8 and 255
        val b = pixel and 255
        if (maxOf(r, g, b) < 32 || minOf(r, g, b) > 240 || maxOf(r, g, b) - minOf(r, g, b) < 24) continue
        val bin = (r shr 5) * 64 + (g shr 5) * 8 + (b shr 5)
        counts[bin]++
        red[bin] += r.toLong()
        green[bin] += g.toLong()
        blue[bin] += b.toLong()
    }
    if (opaque == 0) return listOf(0xFF7DD3FC.toInt(), 0xFF0284C7.toInt(), 0xFF075985.toInt())
    val bin = counts.indices.maxBy { counts[it] }
    // Monochrome artwork gets soft silver shades, never a black floor.
    if (counts[bin] == 0) return listOf(0xFFCCCCCC.toInt(), 0xFFAAAAAA.toInt(), 0xFF888888.toInt())
    val r = red[bin].toFloat() / counts[bin] / 255f
    val g = green[bin].toFloat() / counts[bin] / 255f
    val b = blue[bin].toFloat() / counts[bin] / 255f
    val maximum = maxOf(r, g, b)
    val minimum = minOf(r, g, b)
    val delta = maximum - minimum
    val lightness = (maximum + minimum) / 2f
    val saturation = (delta / (1f - abs(2f * lightness - 1f))).coerceIn(0.3f, 0.65f)
    val hue = when (maximum) {
        r -> ((g - b) / delta + 6f) % 6f
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    }
    val middle = lightness.coerceIn(0.46f, 0.64f)
    return listOf(hslColor(hue, saturation * 0.8f, middle + 0.18f),
        hslColor(hue, saturation, middle), hslColor(hue, saturation, middle - 0.12f))
}

/** Hue is expressed in six sectors; lightness is deliberately bounded by the palette above. */
private fun hslColor(hue: Float, saturation: Float, lightness: Float): Int {
    val chroma = (1f - abs(2f * lightness - 1f)) * saturation
    val x = chroma * (1f - abs(hue % 2f - 1f))
    val m = lightness - chroma / 2f
    val channels = when (hue.toInt()) {
        0 -> floatArrayOf(chroma, x, 0f)
        1 -> floatArrayOf(x, chroma, 0f)
        2 -> floatArrayOf(0f, chroma, x)
        3 -> floatArrayOf(0f, x, chroma)
        4 -> floatArrayOf(x, 0f, chroma)
        else -> floatArrayOf(chroma, 0f, x)
    }
    var result = 0xFF shl 24
    channels.forEachIndexed { index, value ->
        result = result or (((value + m) * 255f).toInt().coerceIn(0, 255) shl (16 - index * 8))
    }
    return result
}
