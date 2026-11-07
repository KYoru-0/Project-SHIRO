package com.example.shiro

import kotlin.math.exp
import kotlin.math.hypot

/** Cache used before the profiled hot-loop changes, for a fair before/after comparison. */
internal class ReferenceCatOutlineCache {
    private val mesh = FloatArray((JELLY_COLUMNS + 1) * (JELLY_ROWS + 1) * 2)
    private var width = 0f
    private var height = 0f
    private var jelly = 0f
    private var bend = 0f
    private var rotation = 0f
    private var contact: WaveContact? = null
    private var outline: CatOutline? = null

    fun get(hull: List<CatPoint>, width: Float, height: Float, jelly: Float, bend: Float,
        rotation: Float, contact: WaveContact?): CatOutline {
        val previous = outline
        if (previous != null && this.width == width && this.height == height && this.jelly == jelly &&
            this.bend == bend && this.rotation == rotation && this.contact == contact) return previous
        this.width = width; this.height = height; this.jelly = jelly
        this.bend = bend; this.rotation = rotation; this.contact = contact
        referenceUpdateJellyMesh(mesh, width, height, jelly, bend, contact)
        return outlineFromMesh(hull, width, height, rotation, mesh).also { outline = it }
    }
}

// Previous algorithms retained only as a fidelity/performance reference for the hot-loop changes.
internal fun referenceSampleCatWave(shape: CatOutline, bands: FloatArray, x: Float, width: Float, amplitude: Float): CatWaveSample {
    var floor = Float.NEGATIVE_INFINITY
    var weightSum = 0f
    var contactX = 0f
    var contactY = 0f
    var contactHeight = 0f
    var hit = CatPoint(x, 0f)
    fun sample(px: Float, py: Float, surface: Float) {
        val required = surface - py
        if (required > floor) {
            val scale = kotlin.math.exp((floor - required) / 2f)
            weightSum *= scale; contactX *= scale; contactY *= scale; contactHeight *= scale
            floor = required
            // Keep a real support point for effects; the weighted point may sit between two bars.
            hit = CatPoint(x + px, surface)
        }
        val weight = kotlin.math.exp((required - floor) / 2f)
        weightSum += weight
        contactX += px * weight
        contactY += py * weight
        contactHeight += surface * weight
    }
    val slot = if (bands.isEmpty()) width else width / bands.size
    shape.points.forEachIndexed { index, p ->
        val q = shape.points[(index + 1) % shape.points.size]
        sample(p.x, p.y, 0f)
        if (bands.isEmpty()) return@forEachIndexed
        val left = minOf(p.x, q.x)
        val right = maxOf(p.x, q.x)
        val first = ((x + left) / slot).toInt().coerceIn(bands.indices)
        val last = ((x + right) / slot).toInt().coerceIn(bands.indices)
        // Clip each outline edge to each bar top. Tiny overlaps must not slip between samples.
        for (bar in first..last) {
            val from = maxOf(left, (bar + SPECTRUM_BAR_INSET) * slot - x)
            val to = minOf(right, (bar + 1f - SPECTRUM_BAR_INSET) * slot - x)
            if (from > to) continue
            val surface = bands[bar] * amplitude
            if (right - left < 0.0001f) {
                sample(p.x, minOf(p.y, q.y), surface)
            } else {
                fun edgeY(px: Float) = p.y + (q.y - p.y) * (px - p.x) / (q.x - p.x)
                sample(from, edgeY(from), surface)
                sample(to, edgeY(to), surface)
            }
        }
    }
    return CatWaveSample(floor, CatPoint(contactX / weightSum, contactY / weightSum), contactHeight / weightSum, hit)
}


internal fun referenceUpdateJellyMesh(vertices: FloatArray, width: Float, height: Float, jelly: Float, bend: Float,
    contact: WaveContact? = null) {
    val squash = jelly.coerceIn(-0.75f, 1.20f)
    val lean = bend.coerceIn(-1f, 1f)
    val centerX = width * JELLY_CORE_U
    val centerY = height * JELLY_CORE_V
    val core = height * JELLY_CORE_RADIUS
    val contactX = if (contact == null) 0f else contact.u * width - centerX
    val contactY = if (contact == null) 1f else contact.v * height - centerY
    val contactRadius = hypot(contactX, contactY).coerceAtLeast(0.001f)
    val directionX = contactX / contactRadius
    val directionY = contactY / contactRadius
    val spread = if (contact == null) 0.5f else
        (contact.spread * width / contactRadius).coerceIn(0.38f, 0.85f)
    var index = 0
    for (row in 0..JELLY_ROWS) {
        val v = row.toFloat() / JELLY_ROWS
        for (column in 0..JELLY_COLUMNS) {
            val u = column.toFloat() / JELLY_COLUMNS
            val dx = u * width - centerX
            val dy = v * height - centerY
            val radius = hypot(dx, dy)
            var mappedRadius = radius
            if (radius > core) {
                val nx = dx / radius
                val ny = dy / radius
                val globalStrength = if (contact == null) 0.32f else 0.08f
                var shellScale = 1f + squash * globalStrength * (nx * nx - ny * ny) + 0.10f * lean * nx * ny
                if (contact != null) {
                    val distanceSquared = ((nx - directionX) * (nx - directionX) +
                        (ny - directionY) * (ny - directionY)) / (spread * spread)
                    val influence = exp(-0.5f * distanceSquared)
                    val press = (squash.coerceAtLeast(0f) * 0.85f).coerceAtMost(0.94f)
                    // The center of the dent approaches the core; its shoulders bulge outward.
                    shellScale *= 1f - press * influence + press * 0.5f * distanceSquared * influence +
                        (-squash).coerceAtLeast(0f) * 0.22f * influence
                }
                mappedRadius = core + (radius - core) * shellScale.coerceIn(0.06f, 1.65f)
            }
            val scale = if (radius == 0f) 1f else mappedRadius / radius
            vertices[index++] = centerX + dx * scale
            vertices[index++] = centerY + dy * scale
        }
    }
}
