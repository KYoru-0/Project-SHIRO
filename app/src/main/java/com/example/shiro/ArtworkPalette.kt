package com.example.shiro

import kotlin.math.abs

/** Small RGB histogram: choose actual dominant colors, then arrange them light to dark. */
internal fun artworkPalette(pixels: IntArray): List<Int> {
    val counts = IntArray(512)
    val red = LongArray(512)
    val green = LongArray(512)
    val blue = LongArray(512)
    for (pixel in pixels) {
        if (pixel ushr 24 < 128) continue
        val r = pixel ushr 16 and 255
        val g = pixel ushr 8 and 255
        val b = pixel and 255
        val bin = (r shr 5) * 64 + (g shr 5) * 8 + (b shr 5)
        counts[bin]++
        red[bin] += r.toLong()
        green[bin] += g.toLong()
        blue[bin] += b.toLong()
    }
    val selected = mutableListOf<Int>()
    for (bin in counts.indices.sortedByDescending { counts[it] }) {
        if (counts[bin] == 0) break
        val color = (0xFF shl 24) or ((red[bin] / counts[bin]).toInt() shl 16) or
            ((green[bin] / counts[bin]).toInt() shl 8) or (blue[bin] / counts[bin]).toInt()
        if (selected.all { other ->
            listOf(16, 8, 0).sumOf { shift -> abs((color ushr shift and 255) - (other ushr shift and 255)) } > 100
        }) selected.add(color)
        if (selected.size == 3) break
    }
    if (selected.isEmpty()) return listOf(0xFF7DD3FC.toInt(), 0xFF0284C7.toInt(), 0xFF075985.toInt())
    if (selected.size == 1) {
        val color = selected.single()
        fun shade(target: Int, amount: Float): Int {
            var result = 0xFF shl 24
            for (shift in listOf(16, 8, 0)) {
                val channel = color ushr shift and 255
                result = result or ((channel + (target - channel) * amount).toInt() shl shift)
            }
            return result
        }
        return listOf(shade(255, 0.3f), color, shade(0, 0.3f))
    }
    val sorted = selected.sortedByDescending { color ->
        (color ushr 16 and 255) * 0.2126f + (color ushr 8 and 255) * 0.7152f + (color and 255) * 0.0722f
    }
    if (sorted.size == 3) return sorted
    var midpoint = 0xFF shl 24
    for (shift in listOf(16, 8, 0)) {
        midpoint = midpoint or (((sorted[0] ushr shift and 255) + (sorted[1] ushr shift and 255)) / 2 shl shift)
    }
    return listOf(sorted[0], midpoint, sorted[1])
}
