package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

class DesignerEditSafetyTest {
    private val document = DesignerDocument("edit-safety", 1, "Edit Safety")

    @Test
    fun `placement inside safe area and away from fiducials is accepted`() {
        val safe = DesignerPageGeometry.safeArea(document.space)
        val bounds = TemplateRect(
            left = safe.left + safe.width * 0.25,
            top = safe.top + safe.height * 0.25,
            width = safe.width * 0.15,
            height = safe.height * 0.10
        )

        assertNull(DesignerEditSafety.placementIssue(document, bounds))
        assertTrue(DesignerEditSafety.isPlacementSafe(document, bounds))
    }

    @Test
    fun `placement outside safe area is rejected with warning`() {
        val safe = DesignerPageGeometry.safeArea(document.space)
        val bounds = TemplateRect(
            left = safe.left - 1.0,
            top = safe.top + 10.0,
            width = 40.0,
            height = 40.0
        )

        val issue = DesignerEditSafety.placementIssue(document, bounds)

        assertNotNull(issue)
        assertTrue(requireNotNull(issue).contains("güvenli alanın dışına"))
        assertFalse(DesignerEditSafety.isPlacementSafe(document, bounds))
    }

    @Test
    fun `placement overlapping fiducial exclusion is rejected with warning`() {
        val safe = DesignerPageGeometry.safeArea(document.space)
        val exclusion = DesignerEditSafety.fiducialExclusionAreas(document).first()
        val bounds = TemplateRect(
            left = max(safe.left, exclusion.left) + 1.0,
            top = max(safe.top, exclusion.top) + 1.0,
            width = 4.0,
            height = 4.0
        )

        val issue = DesignerEditSafety.placementIssue(document, bounds)

        assertNotNull(issue)
        assertTrue(requireNotNull(issue).contains("köşe işaretleyicisinin"))
        assertFalse(DesignerEditSafety.isPlacementSafe(document, bounds))
    }

    @Test
    fun `new answer area starts with ten questions and compiles safely`() {
        val page = DesignerPageGeometry.apply(document)
        val answer = DesignerAreaCatalog.createAnswerArea(page)

        assertEquals(10, answer.questionCount)

        val compiled = DesignerTemplateCompiler.compile(page.copy(components = listOf(answer)))
        assertEquals(10, compiled.bubbleRows.size)
    }
}
