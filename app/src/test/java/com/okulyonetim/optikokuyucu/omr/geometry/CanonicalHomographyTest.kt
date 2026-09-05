package com.okulyonetim.optikokuyucu.omr.geometry

import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalHomographyTest {
    private val template = StandardOmrTemplate.DEFAULT

    @Test
    fun a4ToA5LikeUniformScaleAndMargins_mapBackToSameCanonicalPoints() {
        val quad = projectAnchors { x, y ->
            ImagePoint(70.0 + x * 0.48, 45.0 + y * 0.48)
        }
        assertCanonicalRecovery(quad)
    }

    @Test
    fun printerWithDifferentHorizontalAndVerticalScale_isStillRecovered() {
        val quad = projectAnchors { x, y ->
            ImagePoint(82.0 + x * 0.57, 31.0 + y * 0.53)
        }
        assertCanonicalRecovery(quad)
    }

    @Test
    fun perspectiveCameraPose_isRecovered() {
        val source = template.fiducials.associateBy { it.corner }
        fun p(corner: com.okulyonetim.optikokuyucu.omr.template.FiducialCorner) =
            requireNotNull(source[corner]).bounds.center

        val canonical = listOf(
            p(com.okulyonetim.optikokuyucu.omr.template.FiducialCorner.TOP_LEFT),
            p(com.okulyonetim.optikokuyucu.omr.template.FiducialCorner.TOP_RIGHT),
            p(com.okulyonetim.optikokuyucu.omr.template.FiducialCorner.BOTTOM_RIGHT),
            p(com.okulyonetim.optikokuyucu.omr.template.FiducialCorner.BOTTOM_LEFT)
        )
        val image = listOf(
            180.0 to 110.0,
            780.0 to 150.0,
            720.0 to 650.0,
            120.0 to 610.0
        )
        val forward = CanonicalHomographySolver.solveFourPointTransform(
            canonical.map { it.x to it.y },
            image
        )
        assertNotNull(forward)

        val projected = canonical.map { point ->
            val mapped = requireNotNull(forward!!.map(point.x, point.y))
            ImagePoint(mapped.first, mapped.second)
        }
        val quad = ImageQuadrilateral(projected[0], projected[1], projected[2], projected[3])
        assertCanonicalRecovery(quad)
    }

    private fun projectAnchors(transform: (Double, Double) -> ImagePoint): ImageQuadrilateral {
        val byCorner = template.fiducials.associateBy { it.corner }
        fun mapped(corner: com.okulyonetim.optikokuyucu.omr.template.FiducialCorner): ImagePoint {
            val point = requireNotNull(byCorner[corner]).bounds.center
            return transform(point.x, point.y)
        }
        return ImageQuadrilateral(
            mapped(com.okulyonetim.optikokuyucu.omr.template.FiducialCorner.TOP_LEFT),
            mapped(com.okulyonetim.optikokuyucu.omr.template.FiducialCorner.TOP_RIGHT),
            mapped(com.okulyonetim.optikokuyucu.omr.template.FiducialCorner.BOTTOM_RIGHT),
            mapped(com.okulyonetim.optikokuyucu.omr.template.FiducialCorner.BOTTOM_LEFT)
        )
    }

    private fun assertCanonicalRecovery(quad: ImageQuadrilateral) {
        val registration = CanonicalHomographySolver.solve(quad, template)
        assertNotNull(registration)
        assertTrue(registration!!.normalizedReprojectionError < 1e-9)

        val samples = listOf(
            TemplatePoint(500.0, 707.106781),
            TemplatePoint(260.0, 340.0),
            TemplatePoint(820.0, 1090.0)
        )
        for (expected in samples) {
            val image = requireNotNull(registration.templateToImage.mapTemplate(expected))
            val recovered = requireNotNull(registration.imageToTemplate.mapImage(image))
            assertEquals(expected.x, recovered.x, 1e-5)
            assertEquals(expected.y, recovered.y, 1e-5)
        }
    }
}
