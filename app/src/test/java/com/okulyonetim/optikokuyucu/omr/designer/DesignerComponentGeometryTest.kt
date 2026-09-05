package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesignerComponentGeometryTest {
    @Test
    fun `question group bounds cover all configured columns`() {
        val group = DesignerStarterTemplates.questions40Abcd().components.single() as QuestionGroupComponent
        val bounds = DesignerComponentGeometry.bounds(group)

        assertEquals(139.0, bounds.left, 0.001)
        assertEquals(259.0, bounds.top, 0.001)
        assertEquals(817.0, bounds.right, 0.001)
    }

    @Test
    fun `hit test selects component inside canonical bounds`() {
        val document = DesignerStarterTemplates.questions20Abcd()

        assertEquals(
            "questions",
            DesignerComponentGeometry.hitTest(document, TemplatePoint(500.0, 500.0))
        )
        assertNull(DesignerComponentGeometry.hitTest(document, TemplatePoint(50.0, 700.0)))
    }
}
