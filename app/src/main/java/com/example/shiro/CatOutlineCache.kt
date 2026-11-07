package com.example.shiro

/** Position does not change a local outline. Reuse it until its shape or rotation changes. */
internal class CatOutlineCache {
    private val mesh = JellyMeshBuffer()
    private var hull: List<CatPoint>? = null
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
        if (previous != null && this.hull === hull && this.width == width && this.height == height &&
            this.jelly == jelly && this.bend == bend && this.rotation == rotation && this.contact == contact) {
            return previous
        }
        this.hull = hull; this.width = width; this.height = height
        this.jelly = jelly; this.bend = bend; this.rotation = rotation; this.contact = contact
        return outlineFromMesh(hull, width, height, rotation, mesh.update(width, height, jelly, bend, contact))
            .also { outline = it }
    }
}
