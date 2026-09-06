package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerPageGeometryTest {
    @Test
    fun `iso paper dimensions match supported physical sizes`() {
        val expected = mapOf(
            DesignerPaperSize.A3 to (297.0 to 420.0),
            DesignerPaperSize.A4 to (210.0 to 297.0),
            DesignerPaperSize.A5 to (148.0 to 210.0),
            DesignerPaperSize.A6 to (105.0 to 148.0),
            DesignerPaperSize.A7 to (74.0 to 105.0)
        )

        expected.forEach { (paper, size) ->
            val dimensions = requireNotNull(DesignerPageGeometry.dimensions(paper))
            assertEquals(size.first, dimensions.widthMm, 0.0001)
            assertEquals(size.second, dimensions.heightMm, 0.0001)
        }
    }

    @Test
    fun `canonical page preserves physical aspect in both orientations`() {
        val dimensions = requireNotNull(DesignerPageGeometry.dimensions(DesignerPaperSize.A5))
        val portrait = DesignerPageGeometry.canonicalSpace(
            DesignerPaperSize.A5,
            DesignerPageOrientation.PORTRAIT
        )
        val landscape = DesignerPageGeometry.canonicalSpace(
            DesignerPaperSize.A5,
            DesignerPageOrientation.LANDSCAPE
        )

        assertEquals(DesignerPageGeometry.CANONICAL_SHORT_SIDE, portrait.width, 0.0001)
        assertEquals(dimensions.widthMm / dimensions.heightMm, portrait.aspectRatio, 0.000001)
        assertEquals(portrait.height, landscape.width, 0.000001)
        assertEquals(portrait.width, landscape.height, 0.000001)
        assertTrue(portrait.height > portrait.width)
        assertTrue(landscape.width > landscape.height)
    }

    @Test
    fun `applying paper geometry updates space fiducials and form spec together`() {
        val original = DesignerDocument(
            id = "paper-test",
            version = 1,
            name = "Paper Test",
            formSpec = DesignerFormSpec(
                examMode = DesignerExamMode.MULTI_LESSON,
                examPreset = DesignerExamPreset.LGS
            )
        )

        val updated = DesignerPageGeometry.apply(
            document = original,
            paperSize = DesignerPaperSize.A3,
            orientation = DesignerPageOrientation.LANDSCAPE
        )

        assertEquals(DesignerPaperSize.A3, updated.formSpec.paperSize)
        assertEquals(DesignerPageOrientation.LANDSCAPE, updated.formSpec.orientation)
        assertEquals(DesignerExamMode.MULTI_LESSON, updated.formSpec.examMode)
        assertEquals(DesignerExamPreset.LGS, updated.formSpec.examPreset)
        assertTrue(updated.space.width > updated.space.height)
        assertEquals(listOf(11, 22, 33, 44), updated.fiducials.map { it.markerId })
        assertTrue(updated.fiducials.all { it.bounds.isInside(updated.space) })
        assertTrue(DesignerPageGeometry.safeArea(updated.space).isInside(updated.space))
    }
}
