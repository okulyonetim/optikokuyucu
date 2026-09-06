package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerAdvancedVisualEditingTest {
    @Test
    fun `text advanced style edits alignment and bold`() {
        val document = DesignerDocument(
            id = "visual-text",
            version = 1,
            name = "Visual Text",
            visualElements = listOf(
                DesignerTextElement(
                    id = "title",
                    bounds = TemplateRect(100.0, 100.0, 300.0, 60.0),
                    text = "BAŞLIK",
                    fontSize = 24.0
                )
            )
        )

        val aligned = DesignerDocumentEditor.setVisualTextAlignment(
            document,
            "title",
            DesignerTextAlignment.END
        )
        val bold = DesignerDocumentEditor.setVisualBold(aligned, "title", true)
        val result = bold.visualElements.single() as DesignerTextElement

        assertEquals(DesignerTextAlignment.END, result.alignment)
        assertTrue(result.bold)
    }

    @Test
    fun `locked text ignores advanced style edits`() {
        val source = DesignerTextElement(
            id = "locked-title",
            bounds = TemplateRect(100.0, 100.0, 300.0, 60.0),
            text = "BAŞLIK",
            fontSize = 24.0,
            alignment = DesignerTextAlignment.START,
            bold = false,
            locked = true
        )
        val document = DesignerDocument(
            id = "locked-text",
            version = 1,
            name = "Locked Text",
            visualElements = listOf(source)
        )

        val aligned = DesignerDocumentEditor.setVisualTextAlignment(
            document,
            "locked-title",
            DesignerTextAlignment.END
        )
        val bold = DesignerDocumentEditor.setVisualBold(aligned, "locked-title", true)

        assertEquals(source, bold.visualElements.single())
    }

    @Test
    fun `line resize moves snapped end and exposes endpoint handle`() {
        val line = DesignerLineElement(
            id = "line",
            start = TemplatePoint(100.0, 100.0),
            end = TemplatePoint(300.0, 200.0),
            strokeWidth = 2.0
        )
        val document = DesignerDocument(
            id = "line-resize",
            version = 1,
            name = "Line Resize",
            visualElements = listOf(line)
        )

        val resized = DesignerVisualTransform.resize(
            document = document,
            elementId = "line",
            deltaWidth = 17.0,
            deltaHeight = -13.0
        )
        val result = resized.visualElements.single() as DesignerLineElement

        assertEquals(315.0, result.end.x, 0.001)
        assertEquals(185.0, result.end.y, 0.001)
        assertEquals(result.end, DesignerResizeHandleGeometry.handlePoint(result))
    }

    @Test
    fun `line resize remains inside page bounds`() {
        val line = DesignerLineElement(
            id = "line",
            start = TemplatePoint(100.0, 100.0),
            end = TemplatePoint(300.0, 200.0),
            strokeWidth = 2.0
        )
        val document = DesignerDocument(
            id = "line-bounds",
            version = 1,
            name = "Line Bounds",
            visualElements = listOf(line)
        )

        val resized = DesignerVisualTransform.resize(
            document = document,
            elementId = "line",
            deltaWidth = 99999.0,
            deltaHeight = 99999.0
        )
        val result = resized.visualElements.single() as DesignerLineElement

        assertEquals(document.space.width, result.end.x, 0.001)
        assertEquals(document.space.height, result.end.y, 0.001)
    }
}
