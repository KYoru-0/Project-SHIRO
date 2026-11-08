package com.example.shiro

import kotlin.random.Random

internal const val SHIRO_REVEAL_MILLIS = 1100
internal const val SHIRO_GATHER_MILLIS = 180

/** A compact group at the top center. The existing physics loop handles the fall and landing. */
internal fun positionShirosForDrop(
    bodies: List<WaveCatBody>, width: Float, height: Float, radius: Float,
    random: Random = Random.Default
) {
    val originals = bodies.filter { it.active && it.generation == 0 }
    val margin = (radius * 2f).coerceAtMost(width / 2f)
    val spacing = (radius * 2.6f).coerceAtMost((width / 2f - margin).coerceAtLeast(0f))
    originals.forEachIndexed { index, body ->
        // Three above, two tucked underneath: close together without intersecting silhouettes.
        val lowerRow = index >= 3
        val column = if (lowerRow) index - 3.5f else index - 1f
        body.x = width / 2f + column * spacing
        body.height = (height - radius * 1.5f - if (lowerRow) spacing * 0.87f else 0f)
            .coerceAtLeast(radius)
        body.velocityX = (random.nextFloat() - 0.5f) * 36f
        body.velocityY = -20f - random.nextFloat() * 50f
        body.rotation = (random.nextFloat() - 0.5f) * 44f
        body.spin = (random.nextFloat() - 0.5f) * 90f
    }
}
