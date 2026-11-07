package com.example.shiro

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class StageHotLoopTest {
    @Test fun reusedMeshIsIdenticalToPreviousGeometryIncludingResizesAndChangingContacts() {
        val random = Random(731)
        val mesh = JellyMeshBuffer()
        val expected = FloatArray((JELLY_COLUMNS + 1) * (JELLY_ROWS + 1) * 2)
        repeat(160) { step ->
            val width = listOf(64f, 32f, 95.5f, 48.75f)[step / 40]
            val height = width / 1.5f
            val jelly = random.nextFloat() * 1.95f - 0.75f
            val bend = random.nextFloat() * 2f - 1f
            val contact = if (step % 3 == 0) null else WaveContact(random.nextFloat(), random.nextFloat(),
                0f, -1f, 0.08f + random.nextFloat() * 0.3f)
            referenceUpdateJellyMesh(expected, width, height, jelly, bend, contact)
            val actual = mesh.update(width, height, jelly, bend, contact)
            assertArrayEquals(expected, actual, 0f)
            assertSame(actual, mesh.update(width, height, jelly, bend, contact))
        }
    }

    @Test fun reusedWaveSamplerKeepsAllContactsWeightsAndGapBehaviorExactly() {
        val hull = CatOutlineTest().artworkHull()
        val sampler = CatWaveSampler()
        val random = Random(52)
        repeat(240) { index ->
            val width = if (index % 2 == 0) 64f else 32f
            val shape = deformCatOutline(hull, width, width / 1.5f,
                random.nextFloat() * 1.95f - 0.75f, random.nextFloat() * 2f - 1f, random.nextFloat() * 360f)
            val bands = FloatArray(listOf(0, 1, 2, 24)[index % 4]) {
                if (index % 7 == 0) 0f else random.nextFloat()
            }
            val x = random.nextFloat() * 500f - 50f
            val expected = referenceSampleCatWave(shape, bands, x, 400f, 208f)
            val actual = sampleCatWave(shape, bands, x, 400f, 208f, sampler)
            assertEquals(expected, actual)
        }
    }
}
