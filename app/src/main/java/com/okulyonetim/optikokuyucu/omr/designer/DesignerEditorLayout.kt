package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import kotlin.math.min

/** Shared compact layout contract used by editor previews, PDF decorations and new OMR fields. */
object DesignerEditorLayout {
    const val STANDARD_BUBBLE_RADIUS = 7.5
    const val ANSWER_CHOICE_GAP = 20.0
    const val ANSWER_ROW_GAP = 28.0
    const val NUMBER_POSITION_GAP = 24.0
    const val NUMBER_VALUE_GAP = 24.0
    const val BOOKLET_GAP = 26.0
    const val GRID_MINOR_MM = 2.5
    const val GRID_MAJOR_MM = 10.0
    const val DRAG_SNAP_MM = 1.0
    const val A4_PORTRAIT_COURSE_SLOTS = 6

    fun canonicalUnitsPerMillimeter(document: DesignerDocument): Double {
        val dimensions = DesignerPageGeometry.dimensions(document.formSpec.paperSize)
            ?: return min(document.space.width, document.space.height) / 210.0
        val physicalWidth = if (document.formSpec.orientation == DesignerPageOrientation.PORTRAIT) dimensions.widthMm else dimensions.heightMm
        return document.space.width / physicalWidth
    }

    fun canonicalForMillimeters(document: DesignerDocument, millimeters: Double): Double = canonicalUnitsPerMillimeter(document) * millimeters

    fun compactAnswerStart(document: DesignerDocument, answerOrdinal: Int): TemplatePoint {
        val safe = DesignerPageGeometry.safeArea(document.space)
        val slots = courseSlotCount(document)
        val slot = answerOrdinal.coerceAtLeast(0) % slots
        val rowBand = answerOrdinal.coerceAtLeast(0) / slots
        val slotWidth = safe.width / slots.toDouble()
        val firstChoiceX = safe.left + slot * slotWidth + STANDARD_BUBBLE_RADIUS * 3.0
        val topY = safe.top + 95.0 + rowBand * 610.0
        return TemplatePoint(firstChoiceX, topY)
    }

    fun compactAnswerColumnGap(document: DesignerDocument): Double =
        DesignerPageGeometry.safeArea(document.space).width / courseSlotCount(document).toDouble()

    fun labelAnchor(component: DesignerOmrComponent): TemplatePoint {
        val bounds = DesignerComponentGeometry.bounds(component)
        val radius = componentBubbleRadius(component)
        val decoratedLeft = if (component is NumericGridComponent && component.orientation == NumericGridOrientation.DIGITS_VERTICAL) {
            numericHeaderBoxes(component).minOfOrNull { it.left } ?: bounds.left
        } else bounds.left
        val decoratedRight = bounds.right
        val x = when (componentLabelAlignment(component)) {
            DesignerTextAlignment.START -> decoratedLeft
            DesignerTextAlignment.CENTER -> (decoratedLeft + decoratedRight) / 2.0
            DesignerTextAlignment.END -> decoratedRight
        }
        val y = if (component is NumericGridComponent && component.orientation == NumericGridOrientation.DIGITS_HORIZONTAL) {
            (numericHeaderBoxes(component).minOfOrNull { it.top } ?: bounds.top) - radius * 1.25
        } else {
            bounds.top - radius * 2.3
        }
        return TemplatePoint(x, y)
    }

    fun componentLabelAlignment(component: DesignerOmrComponent): DesignerTextAlignment = when (component) {
        is QuestionGroupComponent -> component.labelAlignment
        is NumericGridComponent -> component.labelAlignment
        is SingleChoiceComponent -> component.labelAlignment
    }

    fun componentLabel(component: DesignerOmrComponent): String = when (component) {
        is QuestionGroupComponent -> component.label
        is NumericGridComponent -> component.label
        is SingleChoiceComponent -> component.label
    }

    fun componentShowsLabel(component: DesignerOmrComponent): Boolean = when (component) {
        is QuestionGroupComponent -> component.showLabel
        is NumericGridComponent -> component.showLabel
        is SingleChoiceComponent -> component.showLabel
    }

    fun componentBubbleRadius(component: DesignerOmrComponent): Double = when (component) {
        is QuestionGroupComponent -> component.bubbleRadius
        is NumericGridComponent -> component.bubbleRadius
        is SingleChoiceComponent -> component.bubbleRadius
    }

    /** Empty handwritten digit boxes placed before the selectable number bubbles. */
    fun numericHeaderBoxes(component: NumericGridComponent): List<TemplateRect> {
        val side = component.bubbleRadius * 2.25
        val half = side / 2.0
        val offset = component.bubbleRadius * 3.2
        return (0 until component.digits).map { position ->
            when (component.orientation) {
                NumericGridOrientation.DIGITS_HORIZONTAL -> TemplateRect(
                    component.startX + position * component.columnGap - half,
                    component.topY - offset - half,
                    side,
                    side
                )
                NumericGridOrientation.DIGITS_VERTICAL -> TemplateRect(
                    component.startX - offset - half,
                    component.topY + position * component.columnGap - half,
                    side,
                    side
                )
            }
        }
    }

    private fun courseSlotCount(document: DesignerDocument): Int = if (
        document.formSpec.paperSize == DesignerPaperSize.A4 &&
        document.formSpec.orientation == DesignerPageOrientation.PORTRAIT
    ) A4_PORTRAIT_COURSE_SLOTS else 4
}
