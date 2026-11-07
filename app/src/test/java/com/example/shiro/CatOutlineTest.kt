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

    private fun artworkHull(): List<CatPoint> {
        val image = artworkImage()
        return opaqueCatHull(image.width, image.height) { x, y -> image.getRGB(x, y) ushr 24 }
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

    @Test fun deformedRotatedOutlinesTouchFlatWaveAndFitScreen() {
        val hull = artworkHull()
        for (angle in listOf(0f, 45f, 90f, 180f, 270f)) for (jelly in listOf(-0.75f, 1.20f)) {
            val shape = deformCatOutline(hull, 60f, 40f, jelly, 1f, angle)
            val floor = catWaveCenterHeight(shape, FloatArray(24) { 0.5f }, 100f, 360f, 100f)
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

    @Test fun emptyBoundingBoxCornersDoNotCollide() {
        val triangle = CatOutline(listOf(CatPoint(0f, 0f), CatPoint(20f, 0f), CatPoint(0f, 20f)))
        assertNull(catOverlap(triangle, 0f, 0f, triangle, 15f, 15f))
        assertNotNull(catOverlap(triangle, 0f, 0f, triangle, 5f, 5f))
    }
}
