package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerPhysicalPaperScaleTest {
    @Test
    fun `canonical density is identical on A4 A5 A6 and A7`() {
        val papers = listOf(
            DesignerPaperSize.A4,
            DesignerPaperSize.A5,
            DesignerPaperSize.A6,
            DesignerPaperSize.A7
        )
        val densities = papers.map { paper ->
            val document = DesignerPageGeometry.apply(
                DesignerDocument(id = "density-${paper.name}", version = 1, name = paper.displayName),
                paperSize = paper
            )
            DesignerEditorLayout.canonicalUnitsPerMillimeter(document)
        }
        densities.forEach { density ->
            assertEquals(DesignerPageGeometry.CANONICAL_UNITS_PER_MM, density, 0.000001)
        }
    }

    @Test
    fun `standard bubble and spacing remain same physical size from A4 to A7`() {
        fun document(paper: DesignerPaperSize) = DesignerPageGeometry.apply(
            DesignerDocument(id = "physical-${paper.name}", version = 1, name = paper.displayName),
            paperSize = paper
        )

        val a4 = document(DesignerPaperSize.A4)
        val a7 = document(DesignerPaperSize.A7)
        val a4Answer = DesignerAreaCatalog.createAnswerArea(a4)
        val a7Answer = DesignerAreaCatalog.createAnswerArea(a7)
        val a4Number = DesignerAreaCatalog.createNumberArea(a4)
        val a7Number = DesignerAreaCatalog.createNumberArea(a7)

        val a4Density = DesignerEditorLayout.canonicalUnitsPerMillimeter(a4)
        val a7Density = DesignerEditorLayout.canonicalUnitsPerMillimeter(a7)

        assertEquals(a4Answer.bubbleRadius / a4Density, a7Answer.bubbleRadius / a7Density, 0.000001)
        assertEquals(a4Answer.choiceGap / a4Density, a7Answer.choiceGap / a7Density, 0.000001)
        assertEquals(a4Answer.rowGap / a4Density, a7Answer.rowGap / a7Density, 0.000001)
        assertEquals(a4Number.columnGap / a4Density, a7Number.columnGap / a7Density, 0.000001)
        assertEquals(a4Number.rowGap / a4Density, a7Number.rowGap / a7Density, 0.000001)

        val a4PrintedDiameter = printedDiameterMm(a4, a4Answer.bubbleRadius)
        val a7PrintedDiameter = printedDiameterMm(a7, a7Answer.bubbleRadius)
        assertEquals(a4PrintedDiameter, a7PrintedDiameter, 0.03)
        assertTrue(a4PrintedDiameter in 2.7..3.1)
    }

    private fun printedDiameterMm(document: DesignerDocument, radius: Double): Double {
        val profile = requireNotNull(document.formSpec.pdfProfile())
        val transform = DesignerPdfLayout.fit(document.space, profile)
        return transform.length(radius * 2.0) * 25.4 / 72.0
    }
}
