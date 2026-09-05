package com.okulyonetim.optikokuyucu.omr.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OmrTemplateTest {
    @Test
    fun baselineTemplate_isA4AndHasFourUniqueMarkers() {
        val template = StandardOmrTemplate.A4

        assertEquals(210.0, template.pageMm.width, 0.0)
        assertEquals(297.0, template.pageMm.height, 0.0)
        assertEquals(4, template.fiducials.size)
        assertEquals(4, template.fiducials.map { it.markerId }.toSet().size)
        assertEquals(4, template.fiducials.map { it.corner }.toSet().size)
    }

    @Test
    fun everyMarker_isInsidePhysicalPage() {
        val template = StandardOmrTemplate.A4
        assertTrue(template.fiducials.all { it.boundsMm.isInside(template.pageMm) })
    }

    @Test
    fun markerCenters_formExpectedPortraitLayout() {
        val byCorner = StandardOmrTemplate.A4.fiducials.associateBy { it.corner }

        val tl = requireNotNull(byCorner[FiducialCorner.TOP_LEFT]).boundsMm.center
        val tr = requireNotNull(byCorner[FiducialCorner.TOP_RIGHT]).boundsMm.center
        val br = requireNotNull(byCorner[FiducialCorner.BOTTOM_RIGHT]).boundsMm.center
        val bl = requireNotNull(byCorner[FiducialCorner.BOTTOM_LEFT]).boundsMm.center

        assertTrue(tl.x < tr.x)
        assertTrue(bl.x < br.x)
        assertTrue(tl.y < bl.y)
        assertTrue(tr.y < br.y)
    }
}
