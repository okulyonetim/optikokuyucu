package com.okulyonetim.optikokuyucu.omr.designer

enum class VisualZOrderAction {
    SEND_TO_BACK,
    SEND_BACKWARD,
    BRING_FORWARD,
    BRING_TO_FRONT
}

/** List order is render order: first is back, last is front. */
object DesignerVisualZOrder {
    fun apply(
        document: DesignerDocument,
        elementId: String,
        action: VisualZOrderAction
    ): DesignerDocument {
        val index = document.visualElements.indexOfFirst { it.id == elementId }
        if (index < 0) return document
        val element = document.visualElements[index]
        if (element.locked || document.visualElements.size < 2) return document

        val targetIndex = when (action) {
            VisualZOrderAction.SEND_TO_BACK -> 0
            VisualZOrderAction.SEND_BACKWARD -> (index - 1).coerceAtLeast(0)
            VisualZOrderAction.BRING_FORWARD -> (index + 1).coerceAtMost(document.visualElements.lastIndex)
            VisualZOrderAction.BRING_TO_FRONT -> document.visualElements.lastIndex
        }
        if (targetIndex == index) return document

        val reordered = document.visualElements.toMutableList()
        reordered.removeAt(index)
        reordered.add(targetIndex, element)
        return document.copy(visualElements = reordered)
    }
}
