package com.example.shiro

import kotlin.math.exp

/** One scratch accumulator per cat; the returned sample remains an immutable snapshot. */
internal class CatWaveSampler {
    private var floor = 0f
    private var weightSum = 0f
    private var contactX = 0f
    private var contactY = 0f
    private var contactHeight = 0f
    private var hitX = 0f
    private var hitY = 0f

    private fun accumulate(px: Float, py: Float, surface: Float) {
        val required = surface - py
        if (required > floor) {
            val scale = exp((floor - required) / 2f)
            weightSum *= scale; contactX *= scale; contactY *= scale; contactHeight *= scale
            floor = required
            hitX = px; hitY = surface
        }
        val weight = exp((required - floor) / 2f)
        weightSum += weight
        contactX += px * weight; contactY += py * weight; contactHeight += surface * weight
    }

    fun sample(shape: CatOutline, bands: FloatArray, x: Float, width: Float, amplitude: Float): CatWaveSample {
        floor = Float.NEGATIVE_INFINITY
        weightSum = 0f; contactX = 0f; contactY = 0f; contactHeight = 0f; hitX = 0f; hitY = 0f
        val slot = if (bands.isEmpty()) width else width / bands.size
        val lastBand = bands.lastIndex
        val points = shape.points
        for (index in points.indices) {
            val p = points[index]
            val q = points[if (index == points.lastIndex) 0 else index + 1]
            accumulate(p.x, p.y, 0f)
            if (bands.isEmpty()) continue
            val left = minOf(p.x, q.x)
            val right = maxOf(p.x, q.x)
            // Primitive bounds avoid ClosedRange<Integer> work on every outline edge.
            val first = ((x + left) / slot).toInt().coerceIn(0, lastBand)
            val last = ((x + right) / slot).toInt().coerceIn(0, lastBand)
            for (bar in first..last) {
                val from = maxOf(left, (bar + SPECTRUM_BAR_INSET) * slot - x)
                val to = minOf(right, (bar + 1f - SPECTRUM_BAR_INSET) * slot - x)
                if (from > to) continue
                val surface = bands[bar] * amplitude
                if (right - left < 0.0001f) {
                    accumulate(p.x, minOf(p.y, q.y), surface)
                } else {
                    val fromY = p.y + (q.y - p.y) * (from - p.x) / (q.x - p.x)
                    val toY = p.y + (q.y - p.y) * (to - p.x) / (q.x - p.x)
                    accumulate(from, fromY, surface)
                    accumulate(to, toY, surface)
                }
            }
        }
        return CatWaveSample(floor, CatPoint(contactX / weightSum, contactY / weightSum),
            contactHeight / weightSum, CatPoint(x + hitX, hitY))
    }
}
