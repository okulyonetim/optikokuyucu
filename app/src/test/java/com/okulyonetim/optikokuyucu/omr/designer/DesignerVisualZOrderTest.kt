package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import org.junit.Assert.assertEquals
import org.junit.Test

class DesignerVisualZOrderTest {
    @Test
    fun `forward and backward move one render layer`() {
        val document = documentWithThreeVisuals()

        val forward = DesignerVisualZOrder.apply(
            document,
            "middle",
            VisualZOrderAction.BRING_FORWARD
        )
        assertEquals(listOf("back", "front", "middle"), forward.visualElements.map { it.id })

        val backward = DesignerVisualZOrder.apply(
            document,
            "middle",
            VisualZOrderAction.SEND_BACKWARD
        )
        assertEquals(listOf("middle", "back", "front"), backward.visualElements.map { it.id })
    }

    @Test
    fun `front and back jump to render extremes`() {
        val document = documentWithThreeVisuals()

        val front = DesignerVisualZOrder.apply(
            document,
            "back",
            VisualZOrderAction.BRING_TO_FRONT
        )
        assertEquals(listOf("middle", "front", "back"), front.visualElements.map { it.id })

        val back = DesignerVisualZOrder.apply(
            document,
            "front",
            VisualZOrderAction.SEND_TO_BACK
        )
        assertEquals(listOf("front", "back", "middle"), back.visualElements.map { it.id })
    }

    @Test
    fun `locked element keeps z order`() {
        val document = documentWithThreeVisuals().copy(
            visualElements = documentWithThreeVisuals().visualElements.map {
                if (it.id == "middle") (it as DesignerBoxElement).copy(locked = true) else it
            }
        )

        val reordered = DesignerVisualZOrder.apply(
            document,
            "middle",
            VisualZOrderAction.BRING_TO_FRONT
        )

        assertEquals(document, reordered)
    }

    private fun documentWithThreeVisuals(): DesignerDocument =
        DesignerStarterTemplates.questions20Abcd().copy(
            visualElements = listOf(
                box("back", 100.0),
                box("middle", 140.0),
                box("front", 180.0)
            )
        )

    private fun box(id: String, y: Double): DesignerBoxElement = DesignerBoxElement(
        id = id,
        bounds = TemplateRect(100.0, y, 200.0, 80.0),
        strokeWidth = 2.0
    )
}
