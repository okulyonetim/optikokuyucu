package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import kotlin.math.round

/** UI-agnostic editing operations shared by designer surfaces. */
object DesignerDocumentEditor {
    fun replaceComponent(document: DesignerDocument, component: DesignerOmrComponent): DesignerDocument {
        require(document.components.any { it.id == component.id }) { "Designer component does not exist." }
        return document.copy(
            components = document.components.map { existing ->
                if (existing.id == component.id) component else existing
            }
        )
    }

    fun moveComponent(
        document: DesignerDocument,
        componentId: String,
        deltaX: Double,
        deltaY: Double,
        snapStep: Double = 5.0
    ): DesignerDocument = document.copy(
        components = document.components.map { component ->
            if (component.id != componentId) component else move(component, deltaX, deltaY, snapStep)
        }
    )

    fun deleteComponent(document: DesignerDocument, componentId: String): DesignerDocument =
        document.copy(components = document.components.filterNot { it.id == componentId })

    fun duplicateComponent(
        document: DesignerDocument,
        componentId: String,
        newId: String,
        offsetX: Double = 20.0,
        offsetY: Double = 20.0,
        snapStep: Double = 5.0
    ): DesignerDocument {
        require(newId.isNotBlank())
        require(document.components.none { it.id == newId }) { "Designer component id already exists." }
        val source = document.components.firstOrNull { it.id == componentId } ?: return document
        val duplicate = withId(move(source, offsetX, offsetY, snapStep), newId)
        return document.copy(components = document.components + duplicate)
    }

    fun moveVisualElement(
        document: DesignerDocument,
        elementId: String,
        deltaX: Double,
        deltaY: Double,
        snapStep: Double = 5.0
    ): DesignerDocument = document.copy(
        visualElements = document.visualElements.map { element ->
            if (element.id != elementId || element.locked) element
            else move(element, deltaX, deltaY, snapStep)
        }
    )

    fun deleteVisualElement(document: DesignerDocument, elementId: String): DesignerDocument =
        document.copy(
            visualElements = document.visualElements.filterNot { it.id == elementId && !it.locked }
        )

    fun duplicateVisualElement(
        document: DesignerDocument,
        elementId: String,
        newId: String,
        offsetX: Double = 20.0,
        offsetY: Double = 20.0,
        snapStep: Double = 5.0
    ): DesignerDocument {
        require(newId.isNotBlank())
        require(document.visualElements.none { it.id == newId }) {
            "Designer visual element id already exists."
        }
        val source = document.visualElements.firstOrNull { it.id == elementId } ?: return document
        val duplicate = withId(move(source, offsetX, offsetY, snapStep), newId)
        return document.copy(visualElements = document.visualElements + duplicate)
    }

    fun setVisualElementLocked(
        document: DesignerDocument,
        elementId: String,
        locked: Boolean
    ): DesignerDocument = document.copy(
        visualElements = document.visualElements.map { element ->
            if (element.id != elementId) element else withLocked(element, locked)
        }
    )

    fun setVisualText(document: DesignerDocument, elementId: String, text: String): DesignerDocument {
        require(text.isNotEmpty())
        return document.copy(
            visualElements = document.visualElements.map { element ->
                if (element.id != elementId || element.locked || element !is DesignerTextElement) element
                else element.copy(text = text)
            }
        )
    }

    fun setVisualFontSize(document: DesignerDocument, elementId: String, fontSize: Double): DesignerDocument {
        require(fontSize > 0.0)
        return document.copy(
            visualElements = document.visualElements.map { element ->
                if (element.id != elementId || element.locked || element !is DesignerTextElement) element
                else element.copy(fontSize = fontSize)
            }
        )
    }

    fun setVisualTextAlignment(
        document: DesignerDocument,
        elementId: String,
        alignment: DesignerTextAlignment
    ): DesignerDocument = document.copy(
        visualElements = document.visualElements.map { element ->
            if (element.id != elementId || element.locked || element !is DesignerTextElement) element
            else element.copy(alignment = alignment)
        }
    )

    fun setVisualBold(document: DesignerDocument, elementId: String, bold: Boolean): DesignerDocument =
        document.copy(
            visualElements = document.visualElements.map { element ->
                if (element.id != elementId || element.locked || element !is DesignerTextElement) element
                else element.copy(bold = bold)
            }
        )

    fun setVisualStrokeWidth(
        document: DesignerDocument,
        elementId: String,
        strokeWidth: Double
    ): DesignerDocument {
        require(strokeWidth > 0.0)
        return document.copy(
            visualElements = document.visualElements.map { element ->
                if (element.id != elementId || element.locked) {
                    element
                } else {
                    when (element) {
                        is DesignerBoxElement -> element.copy(strokeWidth = strokeWidth)
                        is DesignerLineElement -> element.copy(strokeWidth = strokeWidth)
                        is DesignerTextElement,
                        is DesignerImageElement -> element
                    }
                }
            }
        )
    }

    fun snap(value: Double, step: Double): Double {
        require(step > 0.0)
        return round(value / step) * step
    }

    private fun move(
        component: DesignerOmrComponent,
        dx: Double,
        dy: Double,
        step: Double
    ): DesignerOmrComponent = when (component) {
        is QuestionGroupComponent -> component.copy(
            firstChoiceX = snap(component.firstChoiceX + dx, step),
            topY = snap(component.topY + dy, step)
        )
        is NumericGridComponent -> component.copy(
            startX = snap(component.startX + dx, step),
            topY = snap(component.topY + dy, step)
        )
        is SingleChoiceComponent -> component.copy(
            start = TemplatePoint(snap(component.start.x + dx, step), snap(component.start.y + dy, step))
        )
    }

    private fun withId(component: DesignerOmrComponent, id: String): DesignerOmrComponent = when (component) {
        is QuestionGroupComponent -> component.copy(id = id)
        is NumericGridComponent -> component.copy(id = id)
        is SingleChoiceComponent -> component.copy(id = id)
    }

    private fun move(element: DesignerVisualElement, dx: Double, dy: Double, step: Double): DesignerVisualElement =
        when (element) {
            is DesignerTextElement -> element.copy(bounds = move(element.bounds, dx, dy, step))
            is DesignerImageElement -> element.copy(bounds = move(element.bounds, dx, dy, step))
            is DesignerBoxElement -> element.copy(bounds = move(element.bounds, dx, dy, step))
            is DesignerLineElement -> element.copy(
                start = TemplatePoint(snap(element.start.x + dx, step), snap(element.start.y + dy, step)),
                end = TemplatePoint(snap(element.end.x + dx, step), snap(element.end.y + dy, step))
            )
        }

    private fun withId(element: DesignerVisualElement, id: String): DesignerVisualElement = when (element) {
        is DesignerTextElement -> element.copy(id = id)
        is DesignerImageElement -> element.copy(id = id)
        is DesignerBoxElement -> element.copy(id = id)
        is DesignerLineElement -> element.copy(id = id)
    }

    private fun withLocked(element: DesignerVisualElement, locked: Boolean): DesignerVisualElement = when (element) {
        is DesignerTextElement -> element.copy(locked = locked)
        is DesignerImageElement -> element.copy(locked = locked)
        is DesignerBoxElement -> element.copy(locked = locked)
        is DesignerLineElement -> element.copy(locked = locked)
    }

    private fun move(rect: TemplateRect, dx: Double, dy: Double, step: Double): TemplateRect =
        rect.copy(left = snap(rect.left + dx, step), top = snap(rect.top + dy, step))
}
