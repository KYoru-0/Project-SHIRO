package com.example.shiro

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin
import kotlin.system.measureNanoTime

/** Desktop CPU comparison of the actual geometry/physics path; this is not a device FPS test. */
class ShiroPhysicsPerformanceTest {
    private data class Run(val nanos: Long, val builds: Int, val requests: Int, val motion: FloatArray)

    private fun simulate(hull: List<CatPoint>, count: Int, optimized: Boolean, frames: Int, crowded: Boolean = false): Run {
        val bodies = createShiroFamilies(400f, 16f)
        if (count == 10) {
            bodies.indices.filter { bodies[it].active }.forEach { splitShiro(bodies, it, 64f) }
        }
        val active = bodies.indices.filter { bodies[it].active }
        assertEquals(count, active.size)
        active.forEachIndexed { order, index ->
            bodies[index].height = 100f + order % 3 * 20f
            bodies[index].rotation = order * 31f
            if (crowded) {
                bodies[index].x = 265f + order % 5 * 14f
                bodies[index].height = 130f + order / 5 * 9f
            }
        }
        val referenceCaches = bodies.map { ReferenceCatOutlineCache() }
        var builds = 0
        var requests = 0
        val last = arrayOfNulls<CatOutline>(bodies.size)
        fun outline(index: Int): CatOutline {
            val body = bodies[index]
            val width = 64f * body.sizeScale
            val height = width / 1.5f
            val bend = (body.velocityX / 180f + body.spin / 500f).coerceIn(-1f, 1f)
            val result = if (optimized) body.outlineCache.get(hull, width, height, body.compression, bend, body.rotation, body.contact)
                else referenceCaches[index].get(hull, width, height, body.compression, bend, body.rotation, body.contact)
            requests++
            if (last[index] !== result) builds++
            last[index] = result
            return result
        }
        val outlines = bodies.indices.map { outline(it) }.toMutableList()
        val motion = FloatArray(frames * active.size * 6)
        var sample = 0
        fun sampleWave(shape: CatOutline, bands: FloatArray, body: WaveCatBody): CatWaveSample =
            if (optimized) sampleCatWave(shape, bands, body.x, 400f, 208f, body.waveSampler)
            else referenceSampleCatWave(shape, bands, body.x, 400f, 208f)
        val nanos = measureNanoTime {
            repeat(frames) { frame ->
                val bands = FloatArray(24) { 0.3f + 0.25f * sin(it * 0.4f + frame * 0.08f) }
                repeat(2) {
                    active.forEach { index ->
                        val body = bodies[index]
                        val shape = outline(index)
                        val radius = 16f * body.sizeScale
                        val center = body.x / 400f
                        val spread = radius / 400f
                        val slope = (spectrumBandAt(bands, center + spread) - spectrumBandAt(bands, center - spread)) * 208f / (2f * radius)
                        val wave = sampleWave(shape, bands, body)
                        val slot = 400f / bands.size
                        val bar = (wave.hit.x / slot).toInt().coerceIn(0, bands.lastIndex)
                        val contactX = (body.x + wave.point.x).coerceIn((bar + SPECTRUM_BAR_INSET) * slot,
                            (bar + 1f - SPECTRUM_BAR_INSET) * slot)
                        val contact = waveContactAt(CatPoint(contactX - body.x, wave.hit.y - wave.centerHeight),
                            body.rotation, 64f * body.sizeScale, 64f / 1.5f * body.sizeScale, bands.size, 400f)
                        val landing = body.step(wave.centerHeight - shape.support, slope, 1f / 120f,
                            shape.support, wave.surfaceHeight, contact)
                        outlines[index] = outline(index)
                        if (landing > 70f) body.height = maxOf(outlines[index].support,
                            sampleWave(outlines[index], bands, body).centerHeight)
                    }
                    resolveCatContacts(bodies, outlines)
                    repeat(2) {
                        active.forEach { index ->
                            outlines[index] = outline(index)
                            bodies[index].contain(400f, 430f, outlines[index])
                        }
                    }
                }
                active.forEach { index ->
                    val body = bodies[index]
                    motion[sample++] = body.x; motion[sample++] = body.height
                    motion[sample++] = body.rotation; motion[sample++] = body.compression
                    motion[sample++] = body.velocityX; motion[sample++] = body.velocityY
                }
            }
        }
        return Run(nanos, builds, requests, motion)
    }

    @Test fun profiledHotLoopChangesPreserveEveryFrameIncludingCrowdedCats() {
        val hull = CatOutlineTest().artworkHull()
        val report = StringBuilder("Desktop JVM physics comparison; device frame rate still needs Android Studio testing.\n")
        for ((count, crowded) in listOf(5 to false, 10 to false, 10 to true)) {
            simulate(hull, count, false, 60, crowded)
            simulate(hull, count, true, 60, crowded)
            val before = mutableListOf<Run>()
            val after = mutableListOf<Run>()
            repeat(3) {
                before += simulate(hull, count, false, 120, crowded)
                after += simulate(hull, count, true, 120, crowded)
            }
            before.zip(after).forEach { (old, new) ->
                assertArrayEquals(old.motion, new.motion, 0f)
                assertTrue(new.builds < new.requests / 2)
            }
            fun medianMillis(runs: List<Run>) = runs.map { it.nanos / 1_000_000.0 / 120 }.sorted()[1]
            report.append("$count cats (crowded=$crowded): previous %.3f ms/frame; optimized %.3f ms/frame. Outline builds %d -> %d, over %d requests.\n"
                .format(medianMillis(before), medianMillis(after), before[0].builds, after[0].builds, after[0].requests))
        }
        File("build/reports/shiro-physics-performance.txt").apply { parentFile?.mkdirs(); writeText(report.toString()) }
        println(report)
    }
}
