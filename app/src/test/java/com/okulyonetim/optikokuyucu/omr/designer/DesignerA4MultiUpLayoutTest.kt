package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerA4MultiUpLayoutTest {
    @Test
    fun a5A6A7UseExpectedA4SlotCounts() {
        assertEquals(2, DesignerA4MultiUpLayout.planFor(PdfPageProfile.A5)!!.itemsPerSheet)
        assertEquals(2, DesignerA4MultiUpLayout.planFor(PdfPageProfile.A5_LANDSCAPE)!!.itemsPerSheet)
        assertEquals(4, DesignerA4MultiUpLayout.planFor(PdfPageProfile.A6)!!.itemsPerSheet)
        assertEquals(4, DesignerA4MultiUpLayout.planFor(PdfPageProfile.A6_LANDSCAPE)!!.itemsPerSheet)
        assertEquals(8, DesignerA4MultiUpLayout.planFor(PdfPageProfile.A7)!!.itemsPerSheet)
        assertEquals(8, DesignerA4MultiUpLayout.planFor(PdfPageProfile.A7_LANDSCAPE)!!.itemsPerSheet)
    }

    @Test
    fun multiUpSelectsA4OrientationThatPreservesSourceOrientation() {
        assertEquals(PdfPageProfile.A4_LANDSCAPE, DesignerA4MultiUpLayout.planFor(PdfPageProfile.A5)!!.outputProfile)
        assertEquals(PdfPageProfile.A4, DesignerA4MultiUpLayout.planFor(PdfPageProfile.A5_LANDSCAPE)!!.outputProfile)
        assertEquals(PdfPageProfile.A4, DesignerA4MultiUpLayout.planFor(PdfPageProfile.A6)!!.outputProfile)
        assertEquals(PdfPageProfile.A4_LANDSCAPE, DesignerA4MultiUpLayout.planFor(PdfPageProfile.A6_LANDSCAPE)!!.outputProfile)
        assertEquals(PdfPageProfile.A4_LANDSCAPE, DesignerA4MultiUpLayout.planFor(PdfPageProfile.A7)!!.outputProfile)
        assertEquals(PdfPageProfile.A4, DesignerA4MultiUpLayout.planFor(PdfPageProfile.A7_LANDSCAPE)!!.outputProfile)
    }

    @Test
    fun onlySmallAProfilesOfferMultiUp() {
        assertNull(DesignerA4MultiUpLayout.planFor(PdfPageProfile.A3))
        assertNull(DesignerA4MultiUpLayout.planFor(PdfPageProfile.A4))
        assertNull(DesignerA4MultiUpLayout.planFor(PdfPageProfile.LETTER))
    }

    @Test
    fun slotsStayInsideA4AndSourceIsNeverEnlarged() {
        val profiles = listOf(
            PdfPageProfile.A5,
            PdfPageProfile.A5_LANDSCAPE,
            PdfPageProfile.A6,
            PdfPageProfile.A6_LANDSCAPE,
            PdfPageProfile.A7,
            PdfPageProfile.A7_LANDSCAPE
        )
        profiles.forEach { source ->
            val plan = DesignerA4MultiUpLayout.planFor(source)!!
            assertTrue(plan.pageScale <= 1.0)
            assertTrue(plan.pageScale > 0.995)
            repeat(plan.itemsPerSheet) { index ->
                val slot = plan.slot(index)
                assertTrue(slot.left >= 0.0)
                assertTrue(slot.top >= 0.0)
                assertTrue(slot.left + slot.width <= plan.outputProfile.widthPoints + 0.000001)
                assertTrue(slot.top + slot.height <= plan.outputProfile.heightPoints + 0.000001)
            }
        }
    }
}