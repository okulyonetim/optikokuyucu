package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import kotlin.math.ceil

object DesignerComponentGeometry {
    fun bounds(component: DesignerOmrComponent): TemplateRect = when (component) {
        is QuestionGroupComponent -> questionBounds(component)
        is NumericGridComponent -> numericBounds(component)
        is SingleChoiceComponent -> choiceBounds(component)
    }

    /** Last component wins, matching ordinary editor z-order expectations. */
    fun hitTest(document: DesignerDocument, point: TemplatePoint): String? =
        document.components.asReversed().firstOrNull { contains(bounds(it), point) }?.id

    private fun questionBounds(component: QuestionGroupComponent): TemplateRect {
        val rowsPerColumn = ceil(component.questionCount.toDouble() / component.columns.toDouble())
            .toInt()
            .coerceAtLeast(1)
        val usedColumns = ((component.questionCount - 1) / rowsPerColumn) + 1
        val rowsInFirstColumn = minOf(rowsPerColumn, component.questionCount)
        val lastChoiceOffset = (component.choices.size - 1) * component.choiceGap

        val left = component.firstChoiceX - component.bubbleRadius
        val top = component.topY - component.bubbleRadius
        val right = component.firstChoiceX +
            (usedColumns - 1) * component.columnGap +
            lastChoiceOffset +
            component.bubbleRadius
        val bottom = component.topY +
            (rowsInFirstColumn - 1) * component.rowGap +
            component.bubbleRadius
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

    private fun contains(rect: TemplateRect, point: TemplatePoint): Boolean =
        point.x in rect.left..rect.right && point.y in rect.top..rect.bottom
}
