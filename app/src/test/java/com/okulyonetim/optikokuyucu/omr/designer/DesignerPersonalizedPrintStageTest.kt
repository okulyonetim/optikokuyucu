package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerPersonalizedPrintStageTest {
    @Test
    fun `bound text and component label typography survive codec`() {
        val base = DesignerPhysicalTestPack.document()
        val bound = DesignerAreaCatalog.createBoundTextArea(base, DesignerTextBinding.STUDENT_NAME).copy(
            fontSize = 26.0,
            bold = true,
            alignment = DesignerTextAlignment.END
        )
        val answer = (base.components.last() as QuestionGroupComponent).copy(
            labelFontSize = 17.0,
            labelBold = false,
            labelAlignment = DesignerTextAlignment.END
        )
        val source = base.copy(
            components = base.components.dropLast(1) + answer,
            visualElements = base.visualElements + bound
        )

        val decoded = DesignerDocumentCodec.decode(DesignerDocumentCodec.encode(source))
        val decodedBound = decoded.visualElements.filterIsInstance<DesignerTextElement>().first { it.id == bound.id }
        val decodedAnswer = decoded.components.filterIsInstance<QuestionGroupComponent>().single()

        assertEquals(DesignerTextBinding.STUDENT_NAME, decodedBound.binding)
        assertEquals(26.0, decodedBound.fontSize, 0.0)
        assertTrue(decodedBound.bold)
        assertEquals(DesignerTextAlignment.END, decodedBound.alignment)
        assertEquals(17.0, decodedAnswer.labelFontSize, 0.0)
        assertTrue(!decodedAnswer.labelBold)
        assertEquals(DesignerTextAlignment.END, decodedAnswer.labelAlignment)
    }

    @Test
    fun `personalized render fills exact student number marks and header digits`() {
        val base = DesignerPhysicalTestPack.document()
        val studentName = DesignerAreaCatalog.createBoundTextArea(base, DesignerTextBinding.STUDENT_NAME)
        val document = base.copy(visualElements = base.visualElements + studentName)
        val context = DesignerPrintContext(
            studentName = "Ali İmran Karagöz",
            className = "7/A",
            studentNumber = "123456",
            examName = "LGS Deneme 1",
            schoolName = "Koruk Ortaokulu"
        )

        assertNull(DesignerPrintPersonalization.studentNumberIssue(document, context))
        val resolved = DesignerPrintPersonalization.resolveDocument(document, context)
        assertEquals("Ali İmran Karagöz", resolved.visualElements.filterIsInstance<DesignerTextElement>().first { it.id == studentName.id }.text)

        val plan = DesignerPrintRenderer.render(resolved, context)
        assertEquals(6, plan.bubbles.count { it.filled })
        val headerDigits = plan.texts.filter { it.bold }.map { it.text }
        assertEquals(listOf("1", "2", "3", "4", "5", "6"), headerDigits)
    }

    @Test
    fun `invalid personalized number is rejected instead of partially encoded`() {
        val document = DesignerPhysicalTestPack.document()
        val issue = DesignerPrintPersonalization.studentNumberIssue(
            document,
            DesignerPrintContext(studentNumber = "123")
        )
        assertTrue(issue?.contains("6 haneli") == true)
        assertEquals(null, DesignerPrintPersonalization.studentNumberValues(document, DesignerPrintContext(studentNumber = "123")))
    }

    @Test
    fun `page wide safe margin is two percent while fiducials keep local exclusion`() {
        val document = DesignerPageGeometry.apply(DesignerDocument("margin-test", 1, "Margin"))
        val safe = DesignerPageGeometry.safeArea(document.space)
        assertEquals(20.0, safe.left, 0.0001)
        assertEquals(20.0, safe.top, 0.0001)
        assertEquals(document.space.width - 40.0, safe.width, 0.0001)

        val topLeftExclusion = DesignerEditSafety.fiducialExclusionAreas(document).first()
        assertTrue(topLeftExclusion.right > document.fiducials.first().bounds.right)
        assertTrue(!DesignerEditSafety.isPlacementSafe(document, TemplateRect(35.0, 35.0, 30.0, 30.0)))
        assertTrue(DesignerEditSafety.isPlacementSafe(document, TemplateRect(120.0, 35.0, 80.0, 30.0)))
    }

    @Test
    fun `pdf profiles retain small nonzero printer margins`() {
        assertEquals(12.0, PdfPageProfile.A4.marginPoints, 0.0)
        assertEquals(9.0, PdfPageProfile.A5.marginPoints, 0.0)
        assertTrue(PdfPageProfile.A4.marginPoints > 0.0)
    }
}
