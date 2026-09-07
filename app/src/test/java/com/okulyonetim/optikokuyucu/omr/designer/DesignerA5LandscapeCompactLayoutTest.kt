package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerA5LandscapeCompactLayoutTest {
    @Test
    fun `a5 landscape single column twenty questions fit safe area`() {
        val document = DesignerPageGeometry.apply(
            DesignerDocument(id = "a5-20", version = 1, name = "A5 20"),
            paperSize = DesignerPaperSize.A5,
            orientation = DesignerPageOrientation.LANDSCAPE
        )
        val answers = DesignerAreaCatalog.createAnswerArea(document).copy(
            questionCount = 20,
            columns = 1
        )

        assertNull(DesignerAreaCatalog.answerAreaIssue(document, answers))
        assertTrue(DesignerComponentPlacement.fitsInsideSafeArea(document, answers))

        val report = TemplateReadabilityAnalyzer.analyze(
            document.copy(components = listOf(answers))
        )
        assertTrue(
            "A5 yatay 20 soruluk tek sütun marker ve OMR güvenlik kontrollerini geçmelidir: ${report.issues}",
            report.canSave
        )
    }

    @Test
    fun `a5 landscape has tighter safe area while a5 portrait keeps default`() {
        val landscapeSpace = DesignerPageGeometry.canonicalSpace(
            DesignerPaperSize.A5,
            DesignerPageOrientation.LANDSCAPE
        )
        val portraitSpace = DesignerPageGeometry.canonicalSpace(
            DesignerPaperSize.A5,
            DesignerPageOrientation.PORTRAIT
        )
        val landscape = DesignerPageGeometry.safeArea(landscapeSpace)
        val portrait = DesignerPageGeometry.safeArea(portraitSpace)

        assertEquals(landscapeSpace.height * 0.065, landscape.top, 0.000001)
        assertEquals(portraitSpace.width * 0.085, portrait.left, 0.000001)
    }

    @Test
    fun `a5 landscape print margin is compact but bubble stays about four millimeters`() {
        assertEquals(14.0, PdfPageProfile.A5_LANDSCAPE.marginPoints, 0.0)
        assertEquals(18.0, PdfPageProfile.A5.marginPoints, 0.0)

        val document = DesignerPageGeometry.apply(
            DesignerDocument(id = "a5-print", version = 1, name = "A5 print"),
            paperSize = DesignerPaperSize.A5,
            orientation = DesignerPageOrientation.LANDSCAPE
        )
        val transform = DesignerPdfLayout.fit(document.space, PdfPageProfile.A5_LANDSCAPE)
        val diameterMm = transform.length(DesignerEditorLayout.STANDARD_BUBBLE_RADIUS * 2.0) * 25.4 / 72.0

        assertTrue(diameterMm in 3.9..4.1)
    }
}
