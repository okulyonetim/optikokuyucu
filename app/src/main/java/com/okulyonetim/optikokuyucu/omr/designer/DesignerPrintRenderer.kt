package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint

/**
 * Pure, platform-independent OMR print scene derived from the canonical DesignerDocument.
 * Recognition geometry still comes only from DesignerTemplateCompiler; this scene only describes
 * how those compiled marks and their visible labels are painted by preview/PDF surfaces.
 */
data class DesignerPrintBubble(
    val center: TemplatePoint,
    val radius: Double,
    val outlineWidth: Double
) {
    init {
        require(radius > 0.0)
        require(outlineWidth > 0.0)
    }
}

data class DesignerPrintText(
    val text: String,
    val anchor: TemplatePoint,
    val textSize: Double,
    val alignment: DesignerTextAlignment
) {
    init {
        require(text.isNotBlank())
        require(textSize > 0.0)
    }
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

    fun render(document: DesignerDocument): DesignerPrintRenderPlan {
        val template = DesignerTemplateCompiler.compile(document)
        val rowsById = template.bubbleRows.associateBy { it.id }
        val gridsById = template.markGrids.associateBy { it.id }
        val bubbles = mutableListOf<DesignerPrintBubble>()
        val texts = mutableListOf<DesignerPrintText>()

        document.components.forEach { component ->
            when (component) {
                is QuestionGroupComponent -> {
                    val appearance = document.formSpec.answerAppearance
                    repeat(component.questionCount) { index ->
                        val number = component.startQuestion + index
                        val row = rowsById[DesignerTemplateCompiler.questionReadId(component, number)]
                            ?: return@repeat
                        val first = row.bubbles.firstOrNull() ?: return@repeat
                        texts += DesignerPrintText(
                            text = number.toString(),
                            anchor = TemplatePoint(
                                x = first.center.x - first.radius * appearance.questionNumberDistanceInRadii,
                                y = first.center.y
                            ),
                            textSize = first.radius * appearance.questionNumberScale,
                            alignment = DesignerTextAlignment.END
                        )
                        row.bubbles.forEach { bubble ->
                            bubbles += DesignerPrintBubble(
                                center = bubble.center,
                                radius = bubble.radius,
                                outlineWidth = appearance.bubbleOutlineWidth
                            )
                            texts += DesignerPrintText(
                                text = bubble.id,
                                anchor = bubble.center,
                                textSize = bubble.radius * appearance.choiceLabelScale,
                                alignment = DesignerTextAlignment.CENTER
                            )
                        }
                    }
                }

                is NumericGridComponent -> {
                    gridsById[component.id]?.columns.orEmpty().forEach { column ->
                        column.marks.forEach { mark ->
                            bubbles += DesignerPrintBubble(
                                center = mark.center,
                                radius = mark.radius,
                                outlineWidth = NUMBER_OUTLINE_WIDTH
                            )
                            texts += DesignerPrintText(
                                text = mark.id,
                                anchor = mark.center,
                                textSize = mark.radius * NUMBER_LABEL_SCALE,
                                alignment = DesignerTextAlignment.CENTER
                            )
                        }
                    }
                }

                is SingleChoiceComponent -> {
                    gridsById[component.id]?.columns?.firstOrNull()?.marks.orEmpty().forEach { mark ->
                        bubbles += DesignerPrintBubble(
                            center = mark.center,
                            radius = mark.radius,
                            outlineWidth = SINGLE_CHOICE_OUTLINE_WIDTH
                        )
                        texts += DesignerPrintText(
                            text = mark.id,
                            anchor = mark.center,
                            textSize = mark.radius * SINGLE_CHOICE_LABEL_SCALE,
                            alignment = DesignerTextAlignment.CENTER
                        )
                    }
                }
            }
        }

        return DesignerPrintRenderPlan(
            template = template,
            bubbles = bubbles,
            texts = texts
        )
    }
}
