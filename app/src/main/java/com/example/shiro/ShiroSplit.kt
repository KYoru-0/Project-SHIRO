package com.example.shiro

internal const val SHIRO_FAMILY_SIZE = 3 // One original and its two children.
internal const val SHIRO_ORIGINAL_COUNT = 5
internal const val SHIRO_MAX_GENERATION = 1
internal const val SHIRO_MAX_CATS = 10

/** Reserve enough stable slots for each size; split and merge can reuse any free matching slot. */
internal fun createShiroFamilies(width: Float, radius: Float): List<WaveCatBody> =
    List(SHIRO_ORIGINAL_COUNT * SHIRO_FAMILY_SIZE) { index ->
        val slot = index % SHIRO_FAMILY_SIZE
        val generation = if (slot == 0) 0 else 1
        WaveCatBody(width * (0.12f + index / SHIRO_FAMILY_SIZE * 0.19f), generation, slot == 0)
            .apply { height = radius * sizeScale }
    }

/** Replaces one live parent with two smaller children, preserving total body area. */
internal fun splitShiro(bodies: List<WaveCatBody>, index: Int, originalWidth: Float): List<Int>? {
    val parent = bodies.getOrNull(index) ?: return null
    if (!parent.active || parent.generation >= SHIRO_MAX_GENERATION || bodies.count { it.active } >= SHIRO_MAX_CATS) return null
    val children = bodies.indices.filter { !bodies[it].active && bodies[it].generation == parent.generation + 1 }.take(2)
    if (children.size != 2) return null
    parent.release()
    parent.active = false
    children.forEachIndexed { side, childIndex ->
        val direction = if (side == 0) -1f else 1f
        bodies[childIndex].apply {
            resetForReuse()
            x = parent.x + direction * originalWidth * parent.sizeScale * 0.16f
            height = parent.height + 3f
            rotation = parent.rotation + direction * 12f
            velocityX = (parent.velocityX + direction * 42f).coerceIn(-220f, 220f)
            velocityY = maxOf(parent.velocityY, 100f)
            spin = parent.spin + direction * 75f
            wobble(0.3f)
        }
    }
    return children
}

/** Only a deliberately dragged cat can merge, and only through actual silhouette contact. */
internal fun mergeDraggedShiro(bodies: List<WaveCatBody>, index: Int, outlines: List<CatOutline>): Int? {
    val dragged = bodies.getOrNull(index) ?: return null
    if (!dragged.active || !dragged.isHeld || dragged.generation == 0) return null
    val targetIndex = bodies.indices.filter { other ->
        val target = bodies[other]
        other != index && target.active && !target.isHeld && target.generation == dragged.generation &&
            catOverlap(outlines[index], dragged.x, dragged.height, outlines[other], target.x, target.height) != null
    }.minByOrNull { other ->
        val dx = bodies[other].x - dragged.x
        val dy = bodies[other].height - dragged.height
        dx * dx + dy * dy
    } ?: return null
    val mergedIndex = bodies.indices.firstOrNull {
        !bodies[it].active && bodies[it].generation == dragged.generation - 1
    } ?: return null
    val target = bodies[targetIndex]
    val merged = bodies[mergedIndex]
    merged.resetForReuse()
    merged.x = (dragged.x + target.x) * 0.5f
    merged.height = (dragged.height + target.height) * 0.5f
    merged.rotation = dragged.rotation
    merged.velocityX = (dragged.velocityX + target.velocityX) * 0.5f
    merged.velocityY = (dragged.velocityY + target.velocityY) * 0.5f
    merged.spin = (dragged.spin + target.spin) * 0.5f
    merged.wobble(0.4f)
    dragged.release()
    dragged.active = false
    target.active = false
    return mergedIndex
}
