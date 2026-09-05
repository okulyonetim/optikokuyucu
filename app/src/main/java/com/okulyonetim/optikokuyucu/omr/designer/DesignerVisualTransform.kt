package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import kotlin.math.max

enum class VisualHorizontalAlignment {
    LEFT,
    CENTER,
    RIGHT
}

enum class VisualVerticalAlignment {
    TOP,
    CENTER,
    BOTTOM
}

/**
 * UI-agnostic transforms for visual-layer elements.
 *
 * Resize operations are anchored at the element's top-left corner. All transforms stay in the
 * canonical page bounds; recognition safety is still evaluated separately by the readability gate.
 */
object DesignerVisualTransform {
    fun resize(
        document: DesignerDocument,
        elementId: String,
        deltaWidth: Double,
        deltaHeight: Double,
        snapStep: Double = 5.0,
        minSize: Double = 10.0
    ): DesignerDocument {
        require(snapStep > 0.0)
        require(minSize > 0.0)

        return document.copy(
            visualElements = document.visualElements.map { element ->
                if (element.id != elementId || element.locked) return@map element
                when (element) {
                    is DesignerTextElement -> element.copy(
                        bounds = resizedBounds(
                            element.bounds,
                            document,
                            deltaWidth,
                            deltaHeight,
                            snapStep,
                            minSize
                        )
                    )
                    is DesignerBoxElement -> element.copy(
                        bounds = resizedBounds(
                            element.bounds,
                            document,
                            deltaWidth,
                            deltaHeight,
                            snapStep,
                            minSize
                        )
                    )
                    is DesignerLineElement -> element
                }
            }
        )
    }

    fun alignHorizontal(
        document: DesignerDocument,
        elementId: String,
        alignment: VisualHorizontalAlignment,
        snapStep: Double = 5.0
    ): DesignerDocument {
        require(snapStep > 0.0)
        val element = document.visualElements.firstOrNull { it.id == elementId } ?: return document
        if (element.locked) return document
        val bounds = DesignerVisualGeometry.bounds(element)
        val maxLeft = max(0.0, document.space.width - bounds.width)
        val targetLeft = when (alignment) {
            VisualHorizontalAlignment.LEFT -> 0.0
            VisualHorizontalAlignment.CENTER -> snapped(maxLeft / 2.0, snapStep).coerceIn(0.0, maxLeft)
            VisualHorizontalAlignment.RIGHT -> maxLeft
        }
        return translateExact(document, elementId, targetLeft - bounds.left, 0.0)
    }

    fun alignVertical(
        document: DesignerDocument,
        elementId: String,
        alignment: VisualVerticalAlignment,
        snapStep: Double = 5.0
    ): DesignerDocument {
        require(snapStep > 0.0)
        val element = document.visualElements.firstOrNull { it.id == elementId } ?: return document
        if (element.locked) return document
        val bounds = DesignerVisualGeometry.bounds(element)
        val maxTop = max(0.0, document.space.height - bounds.height)
        val targetTop = when (alignment) {
            VisualVerticalAlignment.TOP -> 0.0
            VisualVerticalAlignment.CENTER -> snapped(maxTop / 2.0, snapStep).coerceIn(0.0, maxTop)
            VisualVerticalAlignment.BOTTOM -> maxTop
        }
        return translateExact(document, elementId, 0.0, targetTop - bounds.top)
    }

    private fun translateExact(
        document: DesignerDocument,
        elementId: String,
        deltaX: Double,
        deltaY: Double
    ): DesignerDocument = document.copy(
        visualElements = document.visualElements.map { element ->
            if (element.id != elementId || element.locked) {
                element
            } else {
                when (element) {
                    is DesignerTextElement -> element.copy(
                        bounds = element.bounds.copy(
                            left = element.bounds.left + deltaX,
                            top = element.bounds.top + deltaY
                        )
                    )
                    is DesignerBoxElement -> element.copy(
                        bounds = element.bounds.copy(
                            left = element.bounds.left + deltaX,
                            top = element.bounds.top + deltaY
                        )
                    )
                    is DesignerLineElement -> element.copy(
                        start = TemplatePoint(
                            element.start.x + deltaX,
                            element.start.y + deltaY
                        ),
                        end = TemplatePoint(
                            element.end.x + deltaX,
                            element.end.y + deltaY
                        )
                    )
                }
            }
        }
    )

    private fun resizedBounds(
        bounds: TemplateRect,
        document: DesignerDocument,
        deltaWidth: Double,
        deltaHeight: Double,
        snapStep: Double,
        minSize: Double
    ): TemplateRect {
        val left = bounds.left.coerceIn(0.0, max(0.0, document.space.width - minSize))
        val top = bounds.top.coerceIn(0.0, max(0.0, document.space.height - minSize))
        val maxWidth = max(minSize, document.space.width - left)
        val maxHeight = max(minSize, document.space.height - top)
        val width = snapped(bounds.width + deltaWidth, snapStep)
            .coerceIn(minSize, maxWidth)
        val height = snapped(bounds.height + deltaHeight, snapStep)
            .coerceIn(minSize, maxHeight)
        return bounds.copy(left = left, top = top, width = width, height = height)
    }

    private fun snapped(value: Double, step: Double): Double =
        DesignerDocumentEditor.snap(value, step)
}
