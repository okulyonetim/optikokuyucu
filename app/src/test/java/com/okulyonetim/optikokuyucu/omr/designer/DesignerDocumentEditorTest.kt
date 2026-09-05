package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerDocumentEditorTest {
    @Test
    fun `component move snaps to canonical grid`() {
        val document = DesignerStarterTemplates.questions20Abcd()
        val moved = DesignerDocumentEditor.moveComponent(
            document = document,
            componentId = "questions",
            deltaX = 12.1,
            deltaY = -7.9,
            snapStep = 5.0
        )
        val group = moved.components.single() as QuestionGroupComponent

        assertEquals(440.0, group.firstChoiceX, 0.001)
        assertEquals(290.0, group.topY, 0.001)
    }

    @Test
    fun `duplicate gets new id and offset while source remains`() {
        val document = DesignerStarterTemplates.questions20Abcd()
        val duplicated = DesignerDocumentEditor.duplicateComponent(
            document = document,
            componentId = "questions",
            newId = "questions-copy",
            offsetX = 25.0,
            offsetY = 30.0
        )

        assertEquals(2, duplicated.components.size)
        assertTrue(duplicated.components.any { it.id == "questions" })
        assertTrue(duplicated.components.any { it.id == "questions-copy" })
    }

    @Test
    fun `delete removes only requested component`() {
        val document = DesignerStarterTemplates.questions20Abcd().copy(
            components = DesignerStarterTemplates.questions20Abcd().components + NumericGridComponent(
                id = "studentNumber",
                digits = 6,
                startX = 120.0,
                topY = 800.0,
                bubbleRadius = 10.0,
                columnGap = 45.0,
                rowGap = 34.0
            )
        )

        val deleted = DesignerDocumentEditor.deleteComponent(document, "studentNumber")

        assertEquals(1, deleted.components.size)
        assertFalse(deleted.components.any { it.id == "studentNumber" })
    }
}
