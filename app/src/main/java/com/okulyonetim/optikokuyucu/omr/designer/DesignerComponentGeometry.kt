package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object DesignerComponentGeometry {
    fun bounds(component: DesignerOmrComponent): TemplateRect = when (component) {
        is QuestionGroupComponent -> questionBounds(component)
        is NumericGridComponent -> numericBounds(component)
        is SingleChoiceComponent -> choiceBounds(component)
    }

    /**
     * Last component wins, matching ordinary editor z-order expectations.
     *
     * Interaction geometry deliberately includes the visible label/header decoration while
     * recognition geometry returned by [bounds] stays unchanged. This lets users grab
     * "Kitapçık Türü", "Öğrenci Numarası" and course titles directly without changing OMR math.
     */
    fun hitTest(document: DesignerDocument, point: TemplatePoint): String? =
        document.components.asReversed().firstOrNull { contains(interactionBounds(it), point) }?.id

    fun interactionBounds(component: DesignerOmrComponent): TemplateRect {
        var result = bounds(component)

        if (component is NumericGridComponent) {
            DesignerEditorLayout.numericHeaderBoxes(component).forEach { box ->
                result = union(result, box)
            }
        }

        if (DesignerEditorLayout.componentShowsLabel(component)) {
            val text = DesignerEditorLayout.componentLabel(component)
            if (text.isNotBlank()) {
                val anchor = DesignerEditorLayout.labelAnchor(component)
                val radius = DesignerEditorLayout.componentBubbleRadius(component)
                val fontSize = radius * 1.15
                val width = max(radius * 7.0, text.length * fontSize * 0.68)
                val height = max(radius * 3.4, fontSize * 2.2)
                val left = when (DesignerEditorLayout.componentLabelAlignment(component)) {
                    DesignerTextAlignment.START -> anchor.x
                    DesignerTextAlignment.CENTER -> anchor.x - width / 2.0
                    DesignerTextAlignment.END -> anchor.x - width
                }
                val labelRect = TemplateRect(
                    left = left - radius,
                    top = anchor.y - height * 0.72,
                    width = width + radius * 2.0,
                    height = height
                )
                result = union(result, labelRect)
            }
        }

        return result
    }

    private fun questionBounds(component: QuestionGroupComponent): TemplateRect {
        val questionsPerBlock = ceil(component.questionCount.toDouble() / component.columns.toDouble())
            .toInt()
            .coerceAtLeast(1)
        val usedBlocks = ((component.questionCount - 1) / questionsPerBlock) + 1
        val questionsInFirstBlock = minOf(questionsPerBlock, component.questionCount)
        val choiceSpan = (component.choices.size - 1) * component.choiceGap

        val left = component.firstChoiceX - component.bubbleRadius
        val top = component.topY - component.bubbleRadius
        val right: Double
        val bottom: Double

        when (component.orientation) {
            QuestionGroupOrientation.VERTICAL -> {
                right = component.firstChoiceX +
                    (usedBlocks - 1) * component.columnGap +
                    choiceSpan +
                    component.bubbleRadius
                bottom = component.topY +
                    (questionsInFirstBlock - 1) * component.rowGap +
                    component.bubbleRadius
            }
            QuestionGroupOrientation.HORIZONTAL -> {
                right = component.firstChoiceX +
                    (questionsInFirstBlock - 1) * component.rowGap +
                    component.bubbleRadius
                bottom = component.topY +
                    (usedBlocks - 1) * component.columnGap +
                    choiceSpan +
                    component.bubbleRadius
            }
        }
        return TemplateRect(left, top, right - left, bottom - top)
    }

    private fun numericBounds(component: NumericGridComponent): TemplateRect {
        val left = component.startX - component.bubbleRadius
        val top = component.topY - component.bubbleRadius
        val right: Double
        val bottom: Double
        when (component.orientation) {
            NumericGridOrientation.DIGITS_HORIZONTAL -> {
                right = component.startX +
                    (component.digits - 1) * component.columnGap +
                    component.bubbleRadius
                bottom = component.topY +
                    (component.values.size - 1) * component.rowGap +
                    component.bubbleRadius
            }
            NumericGridOrientation.DIGITS_VERTICAL -> {
                right = component.startX +
                    (component.values.size - 1) * component.rowGap +
                    component.bubbleRadius
                bottom = component.topY +
                    (component.digits - 1) * component.columnGap +
                    component.bubbleRadius
            }
        }
        return TemplateRect(left, top, right - left, bottom - top)
    }

    private fun choiceBounds(component: SingleChoiceComponent): TemplateRect {
        val lastOffset = (component.choices.size - 1) * component.gap
        val left = component.start.x - component.bubbleRadius
        val top = component.start.y - component.bubbleRadius
        val right = component.start.x +
            (if (component.axis == ChoiceAxis.HORIZONTAL) lastOffset else 0.0) +
            component.bubbleRadius
        val bottom = component.start.y +
            (if (component.axis == ChoiceAxis.VERTICAL) lastOffset else 0.0) +
            component.bubbleRadius
        return TemplateRect(left, top, right - left, bottom - top)
    }

    private fun union(a: TemplateRect, b: TemplateRect): TemplateRect {
        val left = min(a.left, b.left)
        val top = min(a.top, b.top)
        val right = max(a.right, b.right)
        val bottom = max(a.bottom, b.bottom)
        return TemplateRect(left, top, right - left, bottom - top)
    }

    private fun contains(rect: TemplateRect, point: TemplatePoint): Boolean =
        point.x in rect.left..rect.right && point.y in rect.top..rect.bottom
}
