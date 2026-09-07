package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplateRect

/** Keeps editable OMR components inside the printable/safe page area after their size changes. */
object DesignerComponentPlacement {
    fun fitInsideSafeArea(document: DesignerDocument, component: DesignerOmrComponent): DesignerOmrComponent {
        val safe = DesignerPageGeometry.safeArea(document.space)
        val bounds = DesignerComponentGeometry.bounds(component)
        if (bounds.width > safe.width || bounds.height > safe.height) return component

        val dx = when {
            bounds.left < safe.left -> safe.left - bounds.left
            bounds.right > safe.right -> safe.right - bounds.right
            else -> 0.0
        }
        val dy = when {
            bounds.top < safe.top -> safe.top - bounds.top
            bounds.bottom > safe.bottom -> safe.bottom - bounds.bottom
            else -> 0.0
        }
        if (dx == 0.0 && dy == 0.0) return component
        return translate(component, dx, dy)
    }

    fun translate(component: DesignerOmrComponent, dx: Double, dy: Double): DesignerOmrComponent = when (component) {
        is QuestionGroupComponent -> component.copy(
            firstChoiceX = component.firstChoiceX + dx,
            topY = component.topY + dy
        )
        is NumericGridComponent -> component.copy(
            startX = component.startX + dx,
            topY = component.topY + dy
        )
        is SingleChoiceComponent -> component.copy(
            start = component.start.copy(
                x = component.start.x + dx,
                y = component.start.y + dy
            )
        )
    }

    fun fitsInsideSafeArea(document: DesignerDocument, component: DesignerOmrComponent): Boolean {
        val safe = DesignerPageGeometry.safeArea(document.space)
        return contains(safe, DesignerComponentGeometry.bounds(component))
    }

    private fun contains(container: TemplateRect, child: TemplateRect): Boolean =
        child.left >= container.left && child.top >= container.top &&
            child.right <= container.right && child.bottom <= container.bottom
}
