package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import kotlin.math.max
import kotlin.math.min

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
        val targetLeft = when (alignment) {
            VisualHorizontalAlignment.LEFT -> 0.0
            VisualHorizontalAlignment.CENTER -> (document.space.width - bounds.width) / 2.0
            VisualHorizontalAlignment.RIGHT -> document.space.width - bounds.width
        }
        return moveBy(
            document,
            elementId,
            deltaX = snapped(targetLeft, snapStep) - bounds.left,
            deltaY = 0.0,
            snapStep = snapStep
        )
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
        val targetTop = when (alignment) {
            VisualVerticalAlignment.TOP -> 0.0
            VisualVerticalAlignment.CENTER -> (document.space.height - bounds.height) / 2.0
            VisualVerticalAlignment.BOTTOM -> document.space.height - bounds.height
        }
        return moveBy(
            document,
            elementId,
            deltaX = 0.0,
            deltaY = snapped(targetTop, snapStep) - bounds.top,
            snapStep = snapStep
        )
    }

    private fun moveBy(
        document: DesignerDocument,
        elementId: String,
        deltaX: Double,
        deltaY: Double,
        snapStep: Double
    ): DesignerDocument {
        val moved = DesignerDocumentEditor.moveVisualElement(
            document = document,
            elementId = elementId,
            deltaX = deltaX,
            deltaY = deltaY,
            snapStep = snapStep
        )
        return clampInsidePage(moved, elementId, snapStep)
    }

    private fun clampInsidePage(
        document: DesignerDocument,
        elementId: String,
        snapStep: Double
    ): DesignerDocument {
        val element = document.visualElements.firstOrNull { it.id == elementId } ?: return document
        val bounds = DesignerVisualGeometry.bounds(element)
        val desiredLeft = bounds.left.coerceIn(0.0, max(0.0, document.space.width - bounds.width))
        val desiredTop = bounds.top.coerceIn(0.0, max(0.0, document.space.height - bounds.height))
        val dx = snapped(desiredLeft, snapStep) - bounds.left
        val dy = snapped(desiredTop, snapStep) - bounds.top
        if (dx == 0.0 && dy == 0.0) return document
        return DesignerDocumentEditor.moveVisualElement(document, elementId, dx, dy, snapStep)
    }

    private fun resizedBounds(
        bounds: TemplateRect,
        document: DesignerDocument,
        deltaWidth: Double,
        deltaHeight: Double,
        snapStep: Double,
        minSize: Double
    ): TemplateRect {
        val maxWidth = max(minSize, document.space.width - bounds.left)
        val maxHeight = max(minSize, document.space.height - bounds.top)
        val width = snapped(bounds.width + deltaWidth, snapStep)
            .coerceIn(minSize, maxWidth)
        val height = snapped(bounds.height + deltaHeight, snapStep)
            .coerceIn(minSize, maxHeight)
        return bounds.copy(width = width, height = height)
    }

    private fun snapped(value: Double, step: Double): Double =
        DesignerDocumentEditor.snap(value, step)
}
