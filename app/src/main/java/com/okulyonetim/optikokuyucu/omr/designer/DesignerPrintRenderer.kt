package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint

/**
 * Pure, platform-independent OMR print scene derived from the canonical DesignerDocument.
 * Recognition geometry still comes only from DesignerTemplateCompiler; personalization only adds
 * print ink to already-compiled marks and never defines alternate recognition coordinates.
 */
data class DesignerPrintBubble(
    val center: TemplatePoint,
    val radius: Double,
    val outlineWidth: Double,
    val filled: Boolean = false
) {
    init { require(radius > 0.0); require(outlineWidth > 0.0) }
}

data class DesignerPrintText(
    val text: String,
    val anchor: TemplatePoint,
    val textSize: Double,
    val alignment: DesignerTextAlignment,
    val bold: Boolean = false
) {
    init { require(text.isNotBlank()); require(textSize > 0.0) }
}

data class DesignerPrintRenderPlan(
    val template: OmrTemplate,
    val bubbles: List<DesignerPrintBubble>,
    val texts: List<DesignerPrintText>
)

object DesignerPrintRenderer {
    private const val NUMBER_LABEL_SCALE = 0.82
    private const val NUMBER_OUTLINE_WIDTH = 1.05
    private const val SINGLE_CHOICE_LABEL_SCALE = 0.82
    private const val SINGLE_CHOICE_OUTLINE_WIDTH = 1.10

    fun render(
        document: DesignerDocument,
        context: DesignerPrintContext = DesignerPrintContext()
    ): DesignerPrintRenderPlan {
        val template = DesignerTemplateCompiler.compile(document)
        val rowsById = template.bubbleRows.associateBy { it.id }
        val gridsById = template.markGrids.associateBy { it.id }
        val bubbles = mutableListOf<DesignerPrintBubble>()
        val texts = mutableListOf<DesignerPrintText>()
        val studentNumberComponent = DesignerPrintPersonalization.studentNumberComponent(document)
        val studentNumberValues = DesignerPrintPersonalization.studentNumberValues(document, context)

        document.components.forEach { component ->
            when (component) {
                is QuestionGroupComponent -> {
                    val appearance = document.formSpec.answerAppearance
                    repeat(component.questionCount) { index ->
                        val number = component.startQuestion + index
                        val row = rowsById[DesignerTemplateCompiler.questionReadId(component, number)] ?: return@repeat
                        val first = row.bubbles.firstOrNull() ?: return@repeat
                        texts += DesignerPrintText(
                            number.toString(),
                            TemplatePoint(first.center.x - first.radius * appearance.questionNumberDistanceInRadii, first.center.y),
                            first.radius * appearance.questionNumberScale,
                            DesignerTextAlignment.END
                        )
                        row.bubbles.forEach { bubble ->
                            bubbles += DesignerPrintBubble(bubble.center, bubble.radius, appearance.bubbleOutlineWidth)
                            texts += DesignerPrintText(
                                bubble.id,
                                bubble.center,
                                bubble.radius * appearance.choiceLabelScale,
                                DesignerTextAlignment.CENTER
                            )
                        }
                    }
                }

                is NumericGridComponent -> {
                    val personalizedNumber = component.id == studentNumberComponent?.id && studentNumberValues != null
                    gridsById[component.id]?.columns.orEmpty().forEachIndexed { columnIndex, column ->
                        val selectedValue = studentNumberValues?.getOrNull(columnIndex)
                        column.marks.forEach { mark ->
                            bubbles += DesignerPrintBubble(
                                center = mark.center,
                                radius = mark.radius,
                                outlineWidth = NUMBER_OUTLINE_WIDTH,
                                filled = personalizedNumber && mark.id == selectedValue
                            )
                            texts += DesignerPrintText(
                                mark.id,
                                mark.center,
                                mark.radius * NUMBER_LABEL_SCALE,
                                DesignerTextAlignment.CENTER
                            )
                        }
                    }
                    if (personalizedNumber) {
                        DesignerEditorLayout.numericHeaderBoxes(component).forEachIndexed { index, box ->
                            studentNumberValues?.getOrNull(index)?.let { value ->
                                texts += DesignerPrintText(
                                    text = value,
                                    anchor = TemplatePoint(box.left + box.width / 2.0, box.top + box.height / 2.0),
                                    textSize = component.bubbleRadius * 1.25,
                                    alignment = DesignerTextAlignment.CENTER,
                                    bold = true
                                )
                            }
                        }
                    }
                }

                is SingleChoiceComponent -> {
                    gridsById[component.id]?.columns?.firstOrNull()?.marks.orEmpty().forEach { mark ->
                        bubbles += DesignerPrintBubble(mark.center, mark.radius, SINGLE_CHOICE_OUTLINE_WIDTH)
                        texts += DesignerPrintText(
                            mark.id,
                            mark.center,
                            mark.radius * SINGLE_CHOICE_LABEL_SCALE,
                            DesignerTextAlignment.CENTER
                        )
                    }
                }
            }
        }

        return DesignerPrintRenderPlan(template, bubbles, texts)
    }
}
