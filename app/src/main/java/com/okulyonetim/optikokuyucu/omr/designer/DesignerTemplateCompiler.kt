package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.BubbleRowSpec
import com.okulyonetim.optikokuyucu.omr.template.BubbleSpec
import com.okulyonetim.optikokuyucu.omr.template.MarkGridColumnSpec
import com.okulyonetim.optikokuyucu.omr.template.MarkGridSpec
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import kotlin.math.ceil

/** Converts the compact editable document into the flat geometry consumed by recognition. */
object DesignerTemplateCompiler {
    fun compile(document: DesignerDocument): OmrTemplate {
        val questionRows = mutableListOf<BubbleRowSpec>()
        val markGrids = mutableListOf<MarkGridSpec>()

        document.components.forEach { component ->
            when (component) {
                is QuestionGroupComponent -> questionRows += compileQuestionGroup(component)
                is NumericGridComponent -> markGrids += compileNumericGrid(component)
                is SingleChoiceComponent -> markGrids += compileSingleChoice(component)
            }
        }

        require(questionRows.map { it.id }.toSet().size == questionRows.size) {
            "Compiled question ids must be unique."
        }
        require(markGrids.map { it.id }.toSet().size == markGrids.size) {
            "Compiled mark-grid ids must be unique."
        }

        return OmrTemplate(
            id = document.id,
            version = document.version,
            space = document.space,
            fiducials = document.fiducials,
            bubbleRows = questionRows,
            markGrids = markGrids
        )
    }

    fun questionReadId(component: QuestionGroupComponent, questionNumber: Int): String {
        require(questionNumber >= component.startQuestion)
        return if (component.questionIdPrefix.isBlank()) {
            questionNumber.toString()
        } else {
            "${component.questionIdPrefix}:$questionNumber"
        }
    }

    fun questionsPerBlock(component: QuestionGroupComponent): Int =
        ceil(component.questionCount.toDouble() / component.columns.toDouble())
            .toInt()
            .coerceAtLeast(1)

    private fun compileQuestionGroup(component: QuestionGroupComponent): List<BubbleRowSpec> {
        val questionsPerBlock = questionsPerBlock(component)

        return (0 until component.questionCount).map { index ->
            val block = index / questionsPerBlock
            val position = index % questionsPerBlock
            val questionNumber = component.startQuestion + index
            val firstChoiceX: Double
            val firstChoiceY: Double

            when (component.orientation) {
                QuestionGroupOrientation.VERTICAL -> {
                    firstChoiceX = component.firstChoiceX + block * component.columnGap
                    firstChoiceY = component.topY + position * component.rowGap
                }
                QuestionGroupOrientation.HORIZONTAL -> {
                    firstChoiceX = component.firstChoiceX + position * component.rowGap
                    firstChoiceY = component.topY + block * component.columnGap
                }
            }

            BubbleRowSpec(
                id = questionReadId(component, questionNumber),
                bubbles = component.choices.mapIndexed { choiceIndex, choice ->
                    val x = firstChoiceX +
                        if (component.orientation == QuestionGroupOrientation.VERTICAL) {
                            choiceIndex * component.choiceGap
                        } else {
                            0.0
                        }
                    val y = firstChoiceY +
                        if (component.orientation == QuestionGroupOrientation.HORIZONTAL) {
                            choiceIndex * component.choiceGap
                        } else {
                            0.0
                        }
                    BubbleSpec(
                        id = choice,
                        center = TemplatePoint(x = x, y = y),
                        radius = component.bubbleRadius
                    )
                }
            )
        }
    }

    private fun compileNumericGrid(component: NumericGridComponent): MarkGridSpec =
        MarkGridSpec(
            id = component.id,
            columns = (0 until component.digits).map { position ->
                MarkGridColumnSpec(
                    id = (position + 1).toString(),
                    marks = component.values.mapIndexed { valueIndex, value ->
                        val x = when (component.orientation) {
                            NumericGridOrientation.DIGITS_HORIZONTAL ->
                                component.startX + position * component.columnGap
                            NumericGridOrientation.DIGITS_VERTICAL ->
                                component.startX + valueIndex * component.rowGap
                        }
                        val y = when (component.orientation) {
                            NumericGridOrientation.DIGITS_HORIZONTAL ->
                                component.topY + valueIndex * component.rowGap
                            NumericGridOrientation.DIGITS_VERTICAL ->
                                component.topY + position * component.columnGap
                        }
                        BubbleSpec(
                            id = value,
                            center = TemplatePoint(x = x, y = y),
                            radius = component.bubbleRadius
                        )
                    }
                )
            }
        )

    private fun compileSingleChoice(component: SingleChoiceComponent): MarkGridSpec =
        MarkGridSpec(
            id = component.id,
            columns = listOf(
                MarkGridColumnSpec(
                    id = "value",
                    marks = component.choices.mapIndexed { index, value ->
                        val x = component.start.x +
                            if (component.axis == ChoiceAxis.HORIZONTAL) index * component.gap else 0.0
                        val y = component.start.y +
                            if (component.axis == ChoiceAxis.VERTICAL) index * component.gap else 0.0
                        BubbleSpec(
                            id = value,
                            center = TemplatePoint(x, y),
                            radius = component.bubbleRadius
                        )
                    }
                )
            )
        )
}
