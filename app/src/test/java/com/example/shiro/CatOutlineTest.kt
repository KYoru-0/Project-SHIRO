package com.example.shiro

import java.io.File
import javax.imageio.ImageIO
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class CatOutlineTest {
    private fun vectorPath(): String {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(File("src/main/res/drawable/shiro_body_neutral.xml"))
        return (document.getElementsByTagName("path").item(0) as org.w3c.dom.Element)
            .getAttributeNS("http://schemas.android.com/apk/res/android", "pathData")
    }

    private fun artworkImage(): BufferedImage {
        // This asset uses only M/C/Z. Render those vector commands for the existing hull checks.
        val tokens = Regex("[MCZ]|-?[0-9]+(?:\\.[0-9]+)?").findAll(vectorPath()).map { it.value }.toList()
        val path = Path2D.Float()
        var index = 0
        fun number() = tokens[index++].toDouble()
        while (index < tokens.size) when (tokens[index++]) {
            "M" -> path.moveTo(number(), number())
            "C" -> path.curveTo(number(), number(), number(), number(), number(), number())
            "Z" -> path.closePath()
            else -> error("Unsupported artwork command")
        }
        val image = BufferedImage(576, 384, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.scale(576.0 / 1536, 384.0 / 1024)
        graphics.color = Color(0xFBF9F6)
        graphics.fill(path)
        graphics.color = Color(0x292327)
        graphics.stroke = BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        graphics.draw(path)
        graphics.dispose()
        val preview = File("build/reports/shiro-body-preview.png")
        preview.parentFile?.mkdirs()
        ImageIO.write(image, "png", preview)
        return image
    }

    internal fun artworkHull(): List<CatPoint> {
        val image = artworkImage()
        return opaqueCatHull(image.width, image.height) { x, y -> image.getRGB(x, y) ushr 24 }
    }

    @Test fun deepBorderDentIsPreservedOutsideTheCoreAndCanBePreviewed() {
        val hull = artworkHull()
        val width = 240f
        val height = 160f
        val contact = WaveContact(JELLY_CORE_U, 0.92f, 0f, -1f, 0.11f)
        val rest = deformCatOutline(hull, width, height, 0f, 0f, 0f)
        val dented = deformCatOutline(hull, width, height, 1.2f, 0f, 0f, contact)
        val coreX = (JELLY_CORE_U - 0.5f) * width
        val coreRadius = height * JELLY_CORE_RADIUS
        val bottomIndex = rest.points.indices.filter { rest.points[it].y < 0f }
            .minBy { kotlin.math.abs(rest.points[it].x - coreX) }
        assertTrue(dented.points[bottomIndex].y - rest.points[bottomIndex].y > height * 0.12f)
        assertTrue(dented.points.all { kotlin.math.hypot(it.x - coreX, it.y) >= coreRadius - 0.1f })
        assertTrue(dented.points.indices.any { i ->
            val a = dented.points[i]
            val b = dented.points[(i + 1) % dented.points.size]
            val c = dented.points[(i + 2) % dented.points.size]
            (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x) < -0.01f
        })
        val oldBorder = rest.points[bottomIndex]
        assertFalse(dented.contains(oldBorder.x, oldBorder.y + 2f))

        // Debug-only silhouette comparison. The blue circle is never drawn in the app.
        val image = BufferedImage(900, 320, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0xEEF4F6); g.fillRect(0, 0, 900, 320)
        val shapes = listOf(rest, dented, deformCatOutline(hull, width, height, 1.2f, 0f, 90f, contact))
        shapes.forEachIndexed { index, shape ->
            val center = 150.0 + index * 300
            val path = Path2D.Float()
            shape.points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(center + p.x, 170.0 - p.y) else path.lineTo(center + p.x, 170.0 - p.y)
            }
            path.closePath()
            g.color = Color(0xFBF9F6); g.fill(path)
            g.color = Color(0x292327); g.stroke = BasicStroke(1.5f); g.draw(path)
            g.color = Color(0x2878AA)
            val cx = if (index == 2) 0f else coreX
            val cy = if (index == 2) -coreX else 0f
            g.drawOval((center + cx - coreRadius).toInt(), (170 - cy - coreRadius).toInt(),
                (coreRadius * 2).toInt(), (coreRadius * 2).toInt())
            g.color = Color.DARK_GRAY
            g.drawString(listOf("Rest", "Deep contact dent", "Same dent while rolling")[index], (center - 75).toInt(), 280)
        }
        g.dispose()
        ImageIO.write(image, "png", File("build/reports/shiro-core-preview.png"))
    }

    @Test fun emptyConcaveDentDoesNotCreateAFalseCatCollision() {
        val dent = CatOutline(listOf(CatPoint(-20f, -20f), CatPoint(-8f, -20f),
            CatPoint(-8f, 0f), CatPoint(8f, 0f), CatPoint(8f, -20f), CatPoint(20f, -20f),
            CatPoint(20f, 20f), CatPoint(-20f, 20f)))
        val small = CatOutline(listOf(CatPoint(-3f, -3f), CatPoint(3f, -3f), CatPoint(3f, 3f), CatPoint(-3f, 3f)))
        assertNull(catOverlap(dent, 0f, 0f, small, 0f, -10f))
        assertNotNull(catOverlap(dent, 0f, 0f, small, 9f, -10f))
    }

    @Test fun svgAndAndroidUseTheSameBodyPath() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("../artwork/shiro-body.svg"))
        val svg = (document.getElementsByTagName("path").item(0) as org.w3c.dom.Element).getAttribute("d")
        fun normalize(value: String) = value.trim().replace(Regex("\\s+"), " ")
        assertEquals(normalize(svg), normalize(vectorPath()))
    }

    @Test fun realArtworkExcludesTransparentPaddingAndGlow() {
        val hull = artworkHull()
        assertTrue("The tail must not extend to the left of the body", hull.minOf { it.x } > 0.22f)
        assertTrue(hull.maxOf { it.x } < 0.95f)
        assertTrue(hull.minOf { it.y } > 0.05f)
        val upright = deformCatOutline(hull, 60f, 40f, 0f, 0f, 0f)
        val sideways = deformCatOutline(hull, 60f, 40f, 0f, 0f, 90f)
        assertTrue(upright.support < 18f)
        assertTrue(sideways.support < 30f)
        assertFalse(upright.contains(-29f, 20f))
        assertTrue(upright.contains(0f, 0f))
    }

    @Test fun deformedRotatedOutlinesTouchBarTopsAndFitScreen() {
        val hull = artworkHull()
        for (angle in listOf(0f, 45f, 90f, 180f, 270f)) for (jelly in listOf(-0.75f, 1.20f)) {
            val shape = deformCatOutline(hull, 60f, 40f, jelly, 1f, angle)
            // Align the lowest point with a bar center, away from the intentional gaps.
            val lowest = shape.points.minBy { it.y }
            val floor = catWaveCenterHeight(shape, FloatArray(24) { 0.5f }, 97.5f - lowest.x, 360f, 100f)
            assertEquals(50f, floor + shape.bottom, 0.001f)
            val cat = WaveCatBody(-100f).apply { height = 500f }
            cat.contain(360f, 400f, shape)
            shape.points.forEach {
                assertTrue(cat.x + it.x >= -0.001f)
                assertTrue(cat.x + it.x <= 360.001f)
                assertTrue(cat.height + it.y <= 400.001f)
            }
            cat.height = -100f
            cat.contain(360f, 400f, shape)
            assertEquals(0f, cat.height + shape.bottom, 0.001f)
        }
    }

    @Test fun barGapsHaveNoInvisibleSupportAndTinyEdgeOverlapsStillLand() {
        val tiny = CatOutline(listOf(CatPoint(-0.5f, -1f), CatPoint(0.5f, -1f),
            CatPoint(0.5f, 1f), CatPoint(-0.5f, 1f)))
        val bands = floatArrayOf(0.8f, 0.2f)
        // At x=50 the 8px gap separates the two 42px-wide bars.
        assertEquals(1f, catWaveCenterHeight(tiny, bands, 50f, 100f, 100f), 0.001f)
        assertEquals(81f, catWaveCenterHeight(tiny, bands, 25f, 100f, 100f), 0.001f)
        assertEquals(21f, catWaveCenterHeight(tiny, bands, 75f, 100f, 100f), 0.001f)
        // Only 0.05px of the outline overlaps the first bar's right edge at x=46.
        assertEquals(81f, catWaveCenterHeight(tiny, bands, 46.45f, 100f, 100f), 0.001f)
        assertEquals(1f, catWaveCenterHeight(tiny, floatArrayOf(), 50f, 100f, 100f), 0.001f)
    }

    @Test fun effectAnchorIsOnAnActualBarEvenWhenCatStraddlesAGap() {
        val wide = CatOutline(listOf(CatPoint(-15f, -5f), CatPoint(15f, -5f),
            CatPoint(15f, 5f), CatPoint(-15f, 5f)))
        val sample = sampleCatWave(wide, floatArrayOf(0.8f, 0.8f), 50f, 100f, 100f)
        assertEquals(80f, sample.hit.y, 0f)
        assertTrue(sample.hit.x in 4f..46f || sample.hit.x in 54f..96f)
    }

    @Test fun emptyBoundingBoxCornersDoNotCollide() {
        val triangle = CatOutline(listOf(CatPoint(0f, 0f), CatPoint(20f, 0f), CatPoint(0f, 20f)))
        assertNull(catOverlap(triangle, 0f, 0f, triangle, 15f, 15f))
        assertNotNull(catOverlap(triangle, 0f, 0f, triangle, 5f, 5f))
    }
}
