package com.example.shiro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyMeshTest {
    @Test fun restIsUndeformedAndImpactsDoNotFoldTheMesh() {
        val vertices = FloatArray((JELLY_COLUMNS + 1) * (JELLY_ROWS + 1) * 2)
        updateJellyMesh(vertices, 120f, 80f, 0f, 0f)
        assertEquals(0f, vertices.first(), 0f)
        assertEquals(80f, vertices.last(), 0f)
        val contacts = listOf(null,
            WaveContact(0.5f, 0.9f, 0f, -1f, 0.11f),
            WaveContact(0.5f, 0.9f, 0f, -1f, 0.34f),
            WaveContact(0.2f, 0.5f, 1f, 0f, 0.11f),
            WaveContact(0.5f, 0.15f, 0f, 1f, 0.11f),
            WaveContact(0.75f, 0.75f, -0.7071f, -0.7071f, 0.11f),
            WaveContact(0.25f, 0.25f, 0.7071f, 0.7071f, 0.34f))
        for (jelly in listOf(-0.75f, 0f, 1.20f)) for (bend in listOf(-1f, 0f, 1f)) for (contact in contacts) {
            updateJellyMesh(vertices, 120f, 80f, jelly, bend, contact)
            assertTrue(vertices.all { it.isFinite() })
            // Check both triangles of every quad retain their orientation at extreme deformation.
            fun area(a: Int, b: Int, c: Int): Float =
                (vertices[b] - vertices[a]) * (vertices[c + 1] - vertices[a + 1]) -
                    (vertices[b + 1] - vertices[a + 1]) * (vertices[c] - vertices[a])
            for (row in 0 until JELLY_ROWS) for (column in 0 until JELLY_COLUMNS) {
                val a = (row * (JELLY_COLUMNS + 1) + column) * 2
                val b = a + 2
                val c = a + (JELLY_COLUMNS + 1) * 2
                assertTrue(area(a, b, c + 2) > 0f)
                assertTrue(area(a, c + 2, c) > 0f)
            }
        }
    }
}
