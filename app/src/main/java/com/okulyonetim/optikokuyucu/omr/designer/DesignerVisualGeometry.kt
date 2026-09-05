package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import kotlin.math.max
import kotlin.math.min

object DesignerVisualGeometry {
    fun bounds(element: DesignerVisualElement): TemplateRect = when (element) {
        is DesignerTextElement -> element.bounds
        is DesignerBoxElement -> element.bounds
        is DesignerLineElement -> lineBounds(element)
    }

    /** Last visual element wins, matching ordinary z-order selection semantics. */
    fun hitTest(
        document: DesignerDocument,
        point: TemplatePoint,
        touchPadding: Double = 8.0
    ): String? {
        require(touchPadding >= 0.0)
        return document.visualElements.asReversed().firstOrNull { element ->
            contains(expand(bounds(element), touchPadding), point)
        }?.id
    }

    private fun lineBounds(element: DesignerLineElement): TemplateRect {
        val halfStroke = max(1.0, element.strokeWidth / 2.0)
        val left = min(element.start.x, element.end.x) - halfStroke
        val top = min(element.start.y, element.end.y) - halfStroke
        val right = max(element.start.x, element.end.x) + halfStroke
        val bottom = max(element.start.y, element.end.y) + halfStroke
        return TemplateRect(left, top, right - left, bottom - top)
    }

    private fun expand(rect: TemplateRect, padding: Double): TemplateRect = TemplateRect(
        left = rect.left - padding,
        top = rect.top - padding,
        width = rect.width + padding * 2.0,
        height = rect.height + padding * 2.0
    )

    private fun contains(rect: TemplateRect, point: TemplatePoint): Boolean =
        point.x in rect.left..rect.right && point.y in rect.top..rect.bottom
}
