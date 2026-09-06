package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerResizeHandleGeometryTest {
    @Test
    fun `text and box expose bottom-right resize handle`() {
        val text = DesignerTextElement(
            id = "title",
            bounds = TemplateRect(100.0, 150.0, 300.0, 60.0),
            text = "Başlık",
            fontSize = 24.0
        )
        val box = DesignerBoxElement(
            id = "box",
            bounds = TemplateRect(250.0, 300.0, 200.0, 100.0),
            strokeWidth = 2.0
        )

        assertNotNull(DesignerResizeHandleGeometry.handlePoint(text))
        assertNotNull(DesignerResizeHandleGeometry.handlePoint(box))
        assertTrue(
            DesignerResizeHandleGeometry.hitTest(
                text,
                TemplatePoint(399.0, 209.0),
                touchRadius = 4.0
            )
        )
    }

    @Test
    fun `locked elements hide handle and line exposes endpoint handle`() {
        val locked = DesignerBoxElement(
            id = "box",
            bounds = TemplateRect(100.0, 100.0, 200.0, 80.0),
            strokeWidth = 2.0,
            locked = true
        )
        val line = DesignerLineElement(
            id = "line",
            start = TemplatePoint(100.0, 200.0),
            end = TemplatePoint(400.0, 200.0),
            strokeWidth = 2.0
        )

        assertNull(DesignerResizeHandleGeometry.handlePoint(locked))
        assertEquals(line.end, DesignerResizeHandleGeometry.handlePoint(line))
        assertTrue(
            DesignerResizeHandleGeometry.hitTest(
                line,
                TemplatePoint(399.0, 201.0),
                touchRadius = 4.0
            )
        )
        assertFalse(DesignerResizeHandleGeometry.hitTest(locked, TemplatePoint(300.0, 180.0)))
    }
}
