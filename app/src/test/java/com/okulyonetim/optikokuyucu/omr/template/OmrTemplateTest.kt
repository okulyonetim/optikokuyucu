package com.okulyonetim.optikokuyucu.omr.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmrTemplateTest {
    @Test
    fun baselineTemplate_usesLogicalSpaceAndFourUniqueMarkers() {
        val template = StandardOmrTemplate.DEFAULT

        assertEquals(1000.0, template.space.width, 0.0)
        assertEquals(1414.213562373095, template.space.height, 1e-9)
        assertEquals(4, template.fiducials.size)
        assertEquals(4, template.fiducials.map { it.markerId }.toSet().size)
        assertEquals(4, template.fiducials.map { it.corner }.toSet().size)
        assertFalse(template.id.contains("a4", ignoreCase = true))
    }

    @Test
    fun everyMarker_isInsideCanonicalTemplateSpace() {
        val template = StandardOmrTemplate.DEFAULT
        assertTrue(template.fiducials.all { it.bounds.isInside(template.space) })
    }

    @Test
    fun markerCenters_formExpectedPortraitLayout() {
        val byCorner = StandardOmrTemplate.DEFAULT.fiducials.associateBy { it.corner }

        val tl = requireNotNull(byCorner[FiducialCorner.TOP_LEFT]).bounds.center
        val tr = requireNotNull(byCorner[FiducialCorner.TOP_RIGHT]).bounds.center
        val br = requireNotNull(byCorner[FiducialCorner.BOTTOM_RIGHT]).bounds.center
        val bl = requireNotNull(byCorner[FiducialCorner.BOTTOM_LEFT]).bounds.center

        assertTrue(tl.x < tr.x)
        assertTrue(bl.x < br.x)
        assertTrue(tl.y < bl.y)
        assertTrue(tr.y < br.y)
    }

    @Test
    fun logicalPoint_survivesDifferentPrintScalesAndMargins() {
        val logical = TemplatePoint(x = 625.0, y = 930.0)

        val a4Like = PrintSimulation(scaleX = 0.210, scaleY = 0.210, offsetX = 8.0, offsetY = 12.0)
        val a5Like = PrintSimulation(scaleX = 0.1485, scaleY = 0.1485, offsetX = 5.0, offsetY = 7.0)
        val printerFit = PrintSimulation(scaleX = 0.134, scaleY = 0.136, offsetX = 11.0, offsetY = 9.0)

        listOf(a4Like, a5Like, printerFit).forEach { print ->
            val printed = print.toPrinted(logical)
            val recovered = print.toLogical(printed)
            assertEquals(logical.x, recovered.x, 1e-9)
            assertEquals(logical.y, recovered.y, 1e-9)
        }
    }

    private data class PrintPoint(val x: Double, val y: Double)

    /**
     * Test-only affine printer model. It represents paper scaling, driver margins and small
     * independent X/Y scaling differences. Production recognition will recover the equivalent
     * mapping from fiducials with homography instead of knowing these values in advance.
     */
    private data class PrintSimulation(
        val scaleX: Double,
        val scaleY: Double,
        val offsetX: Double,
        val offsetY: Double
    ) {
        fun toPrinted(point: TemplatePoint): PrintPoint = PrintPoint(
            x = point.x * scaleX + offsetX,
            y = point.y * scaleY + offsetY
        )

        fun toLogical(point: PrintPoint): TemplatePoint = TemplatePoint(
            x = (point.x - offsetX) / scaleX,
            y = (point.y - offsetY) / scaleY
        )
    }
}
