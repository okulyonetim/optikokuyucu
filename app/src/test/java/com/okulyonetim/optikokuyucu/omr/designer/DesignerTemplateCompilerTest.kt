package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import org.junit.Assert.assertEquals
import org.junit.Test

class DesignerTemplateCompilerTest {
    @Test
    fun `parametric components compile into flat recognition geometry`() {
        val document = DesignerDocument(
            id = "designer-test",
            version = 1,
            name = "Designer Test",
            components = listOf(
                QuestionGroupComponent(
                    id = "questions",
                    startQuestion = 1,
                    questionCount = 8,
                    choices = listOf("A", "B", "C", "D"),
                    columns = 2,
                    firstChoiceX = 120.0,
                    topY = 220.0,
                    bubbleRadius = 10.0,
                    choiceGap = 38.0,
                    rowGap = 44.0,
                    columnGap = 360.0
                ),
                NumericGridComponent(
                    id = "studentNumber",
                    digits = 6,
                    startX = 120.0,
                    topY = 650.0,
                    bubbleRadius = 10.0,
                    columnGap = 45.0,
                    rowGap = 36.0,
                    label = "Öğrenci No",
                    showLabel = true
                ),
                SingleChoiceComponent(
                    id = "booklet",
                    choices = listOf("A", "B"),
                    start = TemplatePoint(150.0, 1080.0),
                    bubbleRadius = 11.0,
                    gap = 60.0
                )
            )
        )

        val template = DesignerTemplateCompiler.compile(document)

        assertEquals(8, template.bubbleRows.size)
        assertEquals(32, template.bubbleRows.sumOf { it.bubbles.size })
        assertEquals(listOf("studentNumber", "booklet"), template.markGrids.map { it.id })
        assertEquals(6, template.markGrids.first().columns.size)
        assertEquals(10, template.markGrids.first().columns.first().marks.size)
        assertEquals(listOf("A", "B"), template.markGrids.last().columns.single().marks.map { it.id })

        assertEquals(120.0, template.bubbleRows.first().bubbles.first().center.x, 0.001)
        assertEquals(480.0, template.bubbleRows[4].bubbles.first().center.x, 0.001)
    }

    @Test
    fun `vertical number area custom pattern compiles from same canonical component`() {
        val component = NumericGridComponent(
            id = "number-1",
            digits = 3,
            startX = 200.0,
            topY = 300.0,
            bubbleRadius = 10.0,
            columnGap = 50.0,
            rowGap = 40.0,
            values = listOf("A", "B", "C"),
            orientation = NumericGridOrientation.DIGITS_VERTICAL,
            label = "Kod",
            showLabel = false
        )
        val template = DesignerTemplateCompiler.compile(
            DesignerDocument(
                id = "number-test",
                version = 1,
                name = "Number Test",
                components = listOf(component)
            )
        )

        val grid = template.markGrids.single()

        assertEquals(listOf("1", "2", "3"), grid.columns.map { it.id })
        assertEquals(listOf("A", "B", "C"), grid.columns.first().marks.map { it.id })
        assertEquals(200.0, grid.columns.first().marks.first().center.x, 0.001)
        assertEquals(240.0, grid.columns.first().marks[1].center.x, 0.001)
        assertEquals(350.0, grid.columns[1].marks.first().center.y, 0.001)
    }
}
