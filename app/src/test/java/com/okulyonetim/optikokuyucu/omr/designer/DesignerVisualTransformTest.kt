package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerVisualTransformTest {
    @Test
    fun `resize snaps dimensions and stays inside page`() {
        val base = DesignerStarterTemplates.questions20Abcd()
        val document = base.copy(
            visualElements = listOf(
                DesignerBoxElement(
                    id = "box",
                    bounds = TemplateRect(700.0, 1100.0, 250.0, 250.0),
                    strokeWidth = 2.0
                )
            )
        )

        val resized = DesignerVisualTransform.resize(
            document = document,
            elementId = "box",
            deltaWidth = 123.0,
            deltaHeight = 180.0
        )
        val box = resized.visualElements.single() as DesignerBoxElement

        assertEquals(300.0, box.bounds.width, 0.001)
        assertEquals(document.space.height - 1100.0, box.bounds.height, 0.001)
        assertTrue(box.bounds.right <= document.space.width + 0.001)
        assertTrue(box.bounds.bottom <= document.space.height + 0.001)
    }

    @Test
    fun `resize respects minimum size`() {
        val base = DesignerStarterTemplates.questions20Abcd()
        val document = base.copy(
            visualElements = listOf(
                DesignerTextElement(
                    id = "title",
                    bounds = TemplateRect(200.0, 120.0, 400.0, 60.0),
                    text = "Başlık",
                    fontSize = 24.0
                )
            )
        )

        val resized = DesignerVisualTransform.resize(
            document = document,
            elementId = "title",
            deltaWidth = -999.0,
            deltaHeight = -999.0,
            minSize = 20.0
        )
        val title = resized.visualElements.single() as DesignerTextElement

        assertEquals(20.0, title.bounds.width, 0.001)
        assertEquals(20.0, title.bounds.height, 0.001)
    }

    @Test
    fun `horizontal center alignment uses canonical page center`() {
        val base = DesignerStarterTemplates.questions20Abcd()
        val document = base.copy(
            visualElements = listOf(
                DesignerTextElement(
                    id = "title",
                    bounds = TemplateRect(100.0, 120.0, 400.0, 60.0),
                    text = "Başlık",
                    fontSize = 24.0
                )
            )
        )

        val aligned = DesignerVisualTransform.alignHorizontal(
            document,
            "title",
            VisualHorizontalAlignment.CENTER
        )
        val bounds = DesignerVisualGeometry.bounds(aligned.visualElements.single())

        assertEquals((document.space.width - bounds.width) / 2.0, bounds.left, 0.001)
    }

    @Test
    fun `right and bottom alignment keep element inside canonical page`() {
        val base = DesignerStarterTemplates.questions20Abcd()
        val document = base.copy(
            visualElements = listOf(
                DesignerBoxElement(
                    id = "box",
                    bounds = TemplateRect(150.0, 180.0, 260.0, 120.0),
                    strokeWidth = 2.0
                )
            )
        )

        val right = DesignerVisualTransform.alignHorizontal(
            document,
            "box",
            VisualHorizontalAlignment.RIGHT
        )
        val bottom = DesignerVisualTransform.alignVertical(
            right,
            "box",
            VisualVerticalAlignment.BOTTOM
        )
        val bounds = DesignerVisualGeometry.bounds(bottom.visualElements.single())

        assertEquals(document.space.width, bounds.right, 0.001)
        assertTrue(bounds.bottom <= document.space.height + 2.6)
        assertTrue(bounds.bottom >= document.space.height - 2.6)
    }

    @Test
    fun `locked visual ignores resize and alignment`() {
        val base = DesignerStarterTemplates.questions20Abcd()
        val original = DesignerTextElement(
            id = "title",
            bounds = TemplateRect(150.0, 120.0, 400.0, 60.0),
            text = "Kilitli",
            fontSize = 24.0,
            locked = true
        )
        val document = base.copy(visualElements = listOf(original))

        val resized = DesignerVisualTransform.resize(document, "title", 100.0, 100.0)
        val aligned = DesignerVisualTransform.alignHorizontal(
            resized,
            "title",
            VisualHorizontalAlignment.RIGHT
        )

        assertEquals(original, aligned.visualElements.single())
    }
}
