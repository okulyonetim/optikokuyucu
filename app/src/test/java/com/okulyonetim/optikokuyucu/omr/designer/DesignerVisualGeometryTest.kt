package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesignerVisualGeometryTest {
    @Test
    fun `last overlapping visual element wins hit test`() {
        val document = DesignerStarterTemplates.questions20Abcd().copy(
            visualElements = listOf(
                DesignerBoxElement(
                    id = "box",
                    bounds = TemplateRect(100.0, 100.0, 200.0, 100.0),
                    strokeWidth = 2.0
                ),
                DesignerTextElement(
                    id = "title",
                    bounds = TemplateRect(120.0, 120.0, 160.0, 60.0),
                    text = "Başlık",
                    fontSize = 24.0
                )
            )
        )

        assertEquals(
            "title",
            DesignerVisualGeometry.hitTest(document, TemplatePoint(150.0, 145.0))
        )
    }

    @Test
    fun `line can be selected with touch padding`() {
        val document = DesignerStarterTemplates.questions20Abcd().copy(
            visualElements = listOf(
                DesignerLineElement(
                    id = "line",
                    start = TemplatePoint(100.0, 300.0),
                    end = TemplatePoint(400.0, 300.0),
                    strokeWidth = 2.0
                )
            )
        )

        assertEquals(
            "line",
            DesignerVisualGeometry.hitTest(
                document,
                TemplatePoint(220.0, 306.0),
                touchPadding = 8.0
            )
        )
        assertNull(
            DesignerVisualGeometry.hitTest(
                document,
                TemplatePoint(220.0, 330.0),
                touchPadding = 8.0
            )
        )
    }
}
