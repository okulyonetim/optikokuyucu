package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import org.junit.Assert.assertEquals
import org.junit.Test

class DesignerVisualEditingTest {
    @Test
    fun `text content and font size can be edited when unlocked`() {
        val document = DesignerStarterTemplates.questions20Abcd().copy(
            visualElements = listOf(
                DesignerTextElement(
                    id = "title",
                    bounds = TemplateRect(250.0, 120.0, 500.0, 60.0),
                    text = "Eski",
                    fontSize = 24.0
                )
            )
        )

        val updated = DesignerDocumentEditor.setVisualFontSize(
            DesignerDocumentEditor.setVisualText(document, "title", "Yeni Başlık"),
            "title",
            32.0
        )
        val title = updated.visualElements.single() as DesignerTextElement

        assertEquals("Yeni Başlık", title.text)
        assertEquals(32.0, title.fontSize, 0.001)
    }

    @Test
    fun `locked visual ignores content and movement edits`() {
        val document = DesignerStarterTemplates.questions20Abcd().copy(
            visualElements = listOf(
                DesignerTextElement(
                    id = "title",
                    bounds = TemplateRect(250.0, 120.0, 500.0, 60.0),
                    text = "Kilitli",
                    fontSize = 24.0,
                    locked = true
                )
            )
        )

        val edited = DesignerDocumentEditor.setVisualText(document, "title", "Değişmesin")
        val moved = DesignerDocumentEditor.moveVisualElement(edited, "title", 50.0, 50.0)
        val title = moved.visualElements.single() as DesignerTextElement

        assertEquals("Kilitli", title.text)
        assertEquals(250.0, title.bounds.left, 0.001)
        assertEquals(120.0, title.bounds.top, 0.001)
    }

    @Test
    fun `stroke width changes for box and line`() {
        val document = DesignerStarterTemplates.questions20Abcd().copy(
            visualElements = listOf(
                DesignerBoxElement(
                    id = "box",
                    bounds = TemplateRect(200.0, 100.0, 200.0, 80.0),
                    strokeWidth = 2.0
                ),
                DesignerLineElement(
                    id = "line",
                    start = TemplatePoint(200.0, 210.0),
                    end = TemplatePoint(500.0, 210.0),
                    strokeWidth = 2.0
                )
            )
        )

        val boxUpdated = DesignerDocumentEditor.setVisualStrokeWidth(document, "box", 4.0)
        val allUpdated = DesignerDocumentEditor.setVisualStrokeWidth(boxUpdated, "line", 5.0)

        assertEquals(4.0, (allUpdated.visualElements[0] as DesignerBoxElement).strokeWidth, 0.001)
        assertEquals(5.0, (allUpdated.visualElements[1] as DesignerLineElement).strokeWidth, 0.001)
    }
}
