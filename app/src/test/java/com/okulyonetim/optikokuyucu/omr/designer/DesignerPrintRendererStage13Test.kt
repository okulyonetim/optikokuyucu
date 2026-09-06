package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerPrintRendererStage13Test {
    @Test
    fun `answer render plan uses compiled geometry and document appearance`() {
        val appearance = DesignerAnswerAppearance(
            bubbleOutlineWidth = 2.2,
            choiceLabelScale = 0.67,
            questionNumberScale = 1.18,
            questionNumberDistanceInRadii = 2.75
        )
        val source = DesignerStarterTemplates.questions20Abcd()
        val document = source.copy(
            formSpec = source.formSpec.copy(answerAppearance = appearance)
        )

        val plan = DesignerPrintRenderer.render(document)
        val firstCompiled = plan.template.bubbleRows.first().bubbles.first()
        val firstBubble = plan.bubbles.first()
        val choiceLabel = plan.texts.first {
            it.text == firstCompiled.id &&
                it.alignment == DesignerTextAlignment.CENTER &&
                it.anchor == firstCompiled.center
        }
        val questionNumber = plan.texts.first {
            it.text == "1" && it.alignment == DesignerTextAlignment.END
        }

        assertEquals(firstCompiled.center, firstBubble.center)
        assertEquals(firstCompiled.radius, firstBubble.radius, 0.000001)
        assertEquals(appearance.bubbleOutlineWidth, firstBubble.outlineWidth, 0.000001)
        assertEquals(firstCompiled.center, choiceLabel.anchor)
        assertEquals(firstCompiled.radius * appearance.choiceLabelScale, choiceLabel.textSize, 0.000001)
        assertEquals(firstCompiled.center.y, questionNumber.anchor.y, 0.000001)
        assertEquals(
            firstCompiled.center.x - firstCompiled.radius * appearance.questionNumberDistanceInRadii,
            questionNumber.anchor.x,
            0.000001
        )
        assertEquals(firstCompiled.radius * appearance.questionNumberScale, questionNumber.textSize, 0.000001)
    }

    @Test
    fun `render plan includes every compiled omr mark without parallel geometry`() {
        val base = DesignerStarterTemplates.questions20Abcd()
        val document = base.copy(
            components = base.components + listOf(
                NumericGridComponent(
                    id = "studentNumber",
                    digits = 2,
                    startX = 150.0,
                    topY = 1180.0,
                    bubbleRadius = 7.5,
                    columnGap = 24.0,
                    rowGap = 24.0
                ),
                SingleChoiceComponent(
                    id = "booklet",
                    choices = listOf("A", "B"),
                    start = TemplatePoint(720.0, 1180.0),
                    bubbleRadius = 7.5,
                    gap = 26.0
                )
            )
        )

        val plan = DesignerPrintRenderer.render(document)
        val expectedMarkCount = plan.template.bubbleRows.sumOf { it.bubbles.size } +
            plan.template.markGrids.sumOf { grid -> grid.columns.sumOf { it.marks.size } }

        assertEquals(expectedMarkCount, plan.bubbles.size)
        assertTrue(plan.texts.size >= expectedMarkCount)
    }

    @Test
    fun `pdf profiles map the same renderer canonical bubble center`() {
        val plan = DesignerPrintRenderer.render(DesignerStarterTemplates.questions20Abcd())
        val bubble = plan.bubbles.first()
        val compiledCenter = plan.template.bubbleRows.first().bubbles.first().center

        listOf(PdfPageProfile.A4, PdfPageProfile.A5).forEach { profile ->
            val transform = DesignerPdfLayout.fit(plan.template.space, profile)
            val fromRenderer = transform.map(bubble.center)
            val fromRecognition = transform.map(compiledCenter)

            assertEquals(fromRecognition.x, fromRenderer.x, 0.000001)
            assertEquals(fromRecognition.y, fromRenderer.y, 0.000001)
        }
    }
}
