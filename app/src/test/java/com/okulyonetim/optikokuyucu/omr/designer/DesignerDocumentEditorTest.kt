package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerDocumentEditorTest {
    @Test
    fun `component property replacement preserves id and list position`() {
        val source = DesignerStarterTemplates.questions40Abcd()
        val original = source.components.single() as QuestionGroupComponent
        val changed = original.copy(
            questionCount = 35,
            columns = 1,
            bubbleRadius = 12.0,
            rowGap = 52.0
        )

        val result = DesignerDocumentEditor.replaceComponent(source, changed)
        val stored = result.components.single() as QuestionGroupComponent

        assertEquals("questions", stored.id)
        assertEquals(35, stored.questionCount)
        assertEquals(1, stored.columns)
        assertEquals(12.0, stored.bubbleRadius, 0.001)
        assertEquals(52.0, stored.rowGap, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `component replacement cannot create unknown id`() {
        val source = DesignerStarterTemplates.questions20Abcd()
        val original = source.components.single() as QuestionGroupComponent

        DesignerDocumentEditor.replaceComponent(
            source,
            original.copy(id = "unknown")
        )
    }

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

    @Test
    fun `visual duplicate receives new id and snapped offset`() {
        val document = DesignerStarterTemplates.questions20Abcd().copy(
            visualElements = listOf(
                DesignerTextElement(
                    id = "title",
                    bounds = TemplateRect(100.0, 80.0, 300.0, 60.0),
                    text = "SINAV",
                    fontSize = 28.0
                )
            )
        )

        val duplicated = DesignerDocumentEditor.duplicateVisualElement(
            document = document,
            elementId = "title",
            newId = "title-copy",
            offsetX = 22.0,
            offsetY = 18.0
        )
        val copy = duplicated.visualElements.last() as DesignerTextElement

        assertEquals(2, duplicated.visualElements.size)
        assertEquals("title-copy", copy.id)
        assertEquals(120.0, copy.bounds.left, 0.001)
        assertEquals(100.0, copy.bounds.top, 0.001)
    }

    @Test
    fun `locked visual element cannot move or delete until unlocked`() {
        val source = DesignerTextElement(
            id = "school",
            bounds = TemplateRect(80.0, 70.0, 250.0, 50.0),
            text = "OKUL",
            fontSize = 24.0,
            locked = true
        )
        val document = DesignerStarterTemplates.questions20Abcd().copy(
            visualElements = listOf(source)
        )

        val movedWhileLocked = DesignerDocumentEditor.moveVisualElement(
            document,
            "school",
            50.0,
            50.0
        )
        val deletedWhileLocked = DesignerDocumentEditor.deleteVisualElement(
            movedWhileLocked,
            "school"
        )
        assertEquals(source, deletedWhileLocked.visualElements.single())

        val unlocked = DesignerDocumentEditor.setVisualElementLocked(
            deletedWhileLocked,
            "school",
            false
        )
        val moved = DesignerDocumentEditor.moveVisualElement(unlocked, "school", 50.0, 50.0)
        val movedElement = moved.visualElements.single() as DesignerTextElement
        assertEquals(130.0, movedElement.bounds.left, 0.001)
        assertEquals(120.0, movedElement.bounds.top, 0.001)

        val deleted = DesignerDocumentEditor.deleteVisualElement(moved, "school")
        assertTrue(deleted.visualElements.isEmpty())
    }
}
