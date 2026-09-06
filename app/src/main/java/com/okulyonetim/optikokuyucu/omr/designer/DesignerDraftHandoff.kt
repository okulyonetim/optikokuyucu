package com.okulyonetim.optikokuyucu.omr.designer

/**
 * One-shot in-process handoff between the structured form builder and the free-form editor.
 * The document remains immutable; the advanced editor consumes it as its initial draft.
 */
object DesignerDraftHandoff {
    @Volatile
    private var pending: DesignerDocument? = null

    fun offer(document: DesignerDocument) {
        pending = document
    }

    @Synchronized
    fun consume(): DesignerDocument? {
        val document = pending
        pending = null
        return document
    }
}
