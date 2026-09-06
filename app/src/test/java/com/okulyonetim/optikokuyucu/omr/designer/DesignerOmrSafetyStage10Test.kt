package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerOmrSafetyStage10Test {
    @Test
    fun `existing starter templates remain saveable`() {
        val compact = TemplateReadabilityAnalyzer.analyze(DesignerStarterTemplates.questions20Abcd())
        val dense = TemplateReadabilityAnalyzer.analyze(DesignerStarterTemplates.questions80Abcd())

        assertTrue(compact.canSave)
        assertTrue(dense.canSave)
    }

    @Test
    fun `omr component outside canonical page blocks save`() {
        val base = DesignerPageGeometry.apply(DesignerDocument("outside", 1, "Outside"))
        val component = SingleChoiceComponent(
            id = "booklet",
            choices = listOf("A", "B"),
            start = TemplatePoint(0.0, 300.0),
            bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS,
            gap = DesignerEditorLayout.BOOKLET_GAP
        )
        val report = TemplateReadabilityAnalyzer.analyze(base.copy(components = listOf(component)))

        assertFalse(report.canSave)
        assertTrue(report.issues.any { it.type == ReadabilityIssueType.OMR_AREA_OUTSIDE_PAGE })
    }

    @Test
    fun `overlapping omr component areas block save`() {
        val base = DesignerPageGeometry.apply(DesignerDocument("overlap", 1, "Overlap"))
        val first = SingleChoiceComponent(
            id = "booklet-a",
            choices = listOf("A", "B"),
            start = TemplatePoint(300.0, 400.0),
            bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS,
            gap = DesignerEditorLayout.BOOKLET_GAP
        )
        val second = first.copy(id = "booklet-b", start = TemplatePoint(310.0, 400.0))
        val report = TemplateReadabilityAnalyzer.analyze(base.copy(components = listOf(first, second)))

        assertFalse(report.canSave)
        assertTrue(report.issues.any { it.type == ReadabilityIssueType.OMR_AREA_OVERLAP })
    }

    @Test
    fun `same namespace question overlap reports duplicate read ids without throwing`() {
        val base = DesignerPageGeometry.apply(DesignerDocument("duplicate-questions", 1, "Duplicate Questions"))
        val first = questionGroup(
            id = "math-a",
            prefix = "course",
            startQuestion = 1,
            firstChoiceX = 250.0,
            topY = 300.0
        )
        val second = questionGroup(
            id = "math-b",
            prefix = "course",
            startQuestion = 3,
            firstChoiceX = 650.0,
            topY = 800.0
        )

        val report = TemplateReadabilityAnalyzer.analyze(base.copy(components = listOf(first, second)))

        assertFalse(report.canSave)
        assertTrue(report.issues.any { it.type == ReadabilityIssueType.QUESTION_NUMBER_OVERLAP })
        assertTrue(report.issues.any { it.type == ReadabilityIssueType.DUPLICATE_OMR_ID })
        assertTrue(report.issues.none { it.type == ReadabilityIssueType.TEMPLATE_COMPILE_CONFLICT })
    }

    @Test
    fun `same visible question numbers in different course prefixes are allowed`() {
        val base = DesignerPageGeometry.apply(DesignerDocument("multi-course", 1, "Multi Course"))
        val math = questionGroup(
            id = "math",
            prefix = "math",
            startQuestion = 1,
            firstChoiceX = 250.0,
            topY = 300.0
        )
        val science = questionGroup(
            id = "science",
            prefix = "science",
            startQuestion = 1,
            firstChoiceX = 650.0,
            topY = 800.0
        )

        val report = TemplateReadabilityAnalyzer.analyze(base.copy(components = listOf(math, science)))

        assertTrue(report.canSave)
        assertTrue(report.issues.none { it.type == ReadabilityIssueType.QUESTION_NUMBER_OVERLAP })
        assertTrue(report.issues.none { it.type == ReadabilityIssueType.DUPLICATE_OMR_ID })
    }

    @Test
    fun `full document gate preserves existing minimum bubble readability rule`() {
        val base = DesignerPageGeometry.apply(DesignerDocument("tiny", 1, "Tiny"))
        val tiny = SingleChoiceComponent(
            id = "tiny-grid",
            choices = listOf("A", "B"),
            start = TemplatePoint(400.0, 500.0),
            bubbleRadius = 2.0,
            gap = 30.0
        )

        val report = TemplateReadabilityAnalyzer.analyze(base.copy(components = listOf(tiny)))

        assertFalse(report.canSave)
        assertTrue(report.issues.any { it.type == ReadabilityIssueType.MARK_TOO_SMALL })
    }

    private fun questionGroup(
        id: String,
        prefix: String,
        startQuestion: Int,
        firstChoiceX: Double,
        topY: Double
    ) = QuestionGroupComponent(
        id = id,
        startQuestion = startQuestion,
        questionCount = 3,
        choices = listOf("A", "B"),
        columns = 1,
        firstChoiceX = firstChoiceX,
        topY = topY,
        bubbleRadius = 10.0,
        choiceGap = 40.0,
        rowGap = 40.0,
        columnGap = 180.0,
        questionIdPrefix = prefix
    )
}
