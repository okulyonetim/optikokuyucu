package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import kotlin.math.hypot

object DesignerResizeHandleGeometry {
    fun handlePoint(element: DesignerVisualElement): TemplatePoint? {
        if (element.locked) return null
        return when (element) {
            is DesignerTextElement,
            is DesignerBoxElement -> {
                val bounds = DesignerVisualGeometry.bounds(element)
                TemplatePoint(bounds.right, bounds.bottom)
            }
            is DesignerLineElement -> element.end
        }
    }

    fun hitTest(
        element: DesignerVisualElement,
        point: TemplatePoint,
        touchRadius: Double = 60.0
    ): Boolean {
        require(touchRadius > 0.0)
        val handle = handlePoint(element) ?: return false
        return hypot(point.x - handle.x, point.y - handle.y) <= touchRadius
    }
}
