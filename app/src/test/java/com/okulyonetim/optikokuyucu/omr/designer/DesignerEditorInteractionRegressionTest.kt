package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerEditorInteractionRegressionTest {
    @Test
    fun `fiducial exclusion rejects placement that ordinary page safe area still allows`() {
        val document = DesignerPhysicalTestPack.document()
        val pageSafe = DesignerPageGeometry.safeArea(document.space)
        val nearTopLeftMarker = TemplateRect(
            left = pageSafe.left + 1.0,
            top = pageSafe.top + 1.0,
            width = 10.0,
            height = 10.0
        )

        assertTrue(nearTopLeftMarker.left >= pageSafe.left)
        assertTrue(nearTopLeftMarker.top >= pageSafe.top)
        assertFalse(DesignerEditSafety.isPlacementSafe(document, nearTopLeftMarker))
        assertTrue(DesignerEditSafety.isPlacementSafe(document, TemplateRect(150.0, 150.0, 20.0, 20.0)))
    }

    @Test
    fun `booklet title is part of component drag hit target without changing recognition bounds`() {
        val document = DesignerPhysicalTestPack.document()
        val booklet = document.components.filterIsInstance<SingleChoiceComponent>().single()
        val recognitionBounds = DesignerComponentGeometry.bounds(booklet)
        val interactionBounds = DesignerComponentGeometry.interactionBounds(booklet)
        val labelAnchor = DesignerEditorLayout.labelAnchor(booklet)

        assertTrue(interactionBounds.top < recognitionBounds.top)
        assertEquals(booklet.id, DesignerComponentGeometry.hitTest(document, labelAnchor))
        assertEquals(recognitionBounds, DesignerComponentGeometry.bounds(booklet))
    }

    @Test
    fun `physical Turkish probe text is editable and movable`() {
        val probe = DesignerPhysicalTestPack.document().visualElements
            .filterIsInstance<DesignerTextElement>()
            .single { it.id == "turkish-font-probe" }

        assertFalse(probe.locked)
    }
}
