package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import org.junit.Assert.assertEquals
import org.junit.Test

class DesignerAdjustableSpacingTest {
    @Test
    fun `answer group compiler honors custom choice row and column gaps`() {
        val component = QuestionGroupComponent(
            id = "answers-spacing",
            startQuestion = 1,
            questionCount = 4,
            choices = listOf("A", "B", "C"),
            columns = 2,
            firstChoiceX = 200.0,
            topY = 300.0,
            bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS,
            choiceGap = 26.0,
            rowGap = 34.0,
            columnGap = 210.0,
            questionIdPrefix = "answers-spacing"
        )
        val document = DesignerPageGeometry.apply(
            DesignerDocument(id = "spacing", version = 1, name = "Spacing")
        ).copy(components = listOf(component))
        val rows = DesignerTemplateCompiler.compile(document).bubbleRows.associateBy { it.id }

        val q1 = requireNotNull(rows["answers-spacing:1"])
        val q2 = requireNotNull(rows["answers-spacing:2"])
        val q3 = requireNotNull(rows["answers-spacing:3"])
        assertEquals(26.0, q1.bubbles[1].center.x - q1.bubbles[0].center.x, 0.000001)
        assertEquals(34.0, q2.bubbles[0].center.y - q1.bubbles[0].center.y, 0.000001)
        assertEquals(210.0, q3.bubbles[0].center.x - q1.bubbles[0].center.x, 0.000001)
    }

    @Test
    fun `number and booklet compiler honor custom bubble gaps`() {
        val number = NumericGridComponent(
            id = "number-spacing",
            digits = 2,
            startX = 100.0,
            topY = 100.0,
            bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS,
            columnGap = 31.0,
            rowGap = 27.0
        )
        val booklet = SingleChoiceComponent(
            id = "booklet-spacing",
            choices = listOf("A", "B", "C"),
            start = TemplatePoint(400.0, 200.0),
            bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS,
            gap = 35.0
        )
        val document = DesignerPageGeometry.apply(
            DesignerDocument(id = "grid-spacing", version = 1, name = "Grid Spacing")
        ).copy(components = listOf(number, booklet))
        val grids = DesignerTemplateCompiler.compile(document).markGrids.associateBy { it.id }

        val numberGrid = requireNotNull(grids[number.id])
        assertEquals(31.0, numberGrid.columns[1].marks[0].center.x - numberGrid.columns[0].marks[0].center.x, 0.000001)
        assertEquals(27.0, numberGrid.columns[0].marks[1].center.y - numberGrid.columns[0].marks[0].center.y, 0.000001)

        val bookletGrid = requireNotNull(grids[booklet.id])
        assertEquals(35.0, bookletGrid.columns.single().marks[1].center.x - bookletGrid.columns.single().marks[0].center.x, 0.000001)
    }
}
