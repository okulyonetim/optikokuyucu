package com.okulyonetim.optikokuyucu.ui

import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument

/** UI-only one-shot navigation handoff. It never changes persisted form or recognition geometry. */
internal enum class DesignerLibraryOpenMode { EDIT, PREVIEW }

internal data class DesignerLibraryOpenRequest(
    val document: DesignerDocument,
    val mode: DesignerLibraryOpenMode
)

internal object DesignerLibraryOpenHandoff {
    @Volatile
    private var pending: DesignerLibraryOpenRequest? = null

    fun offer(document: DesignerDocument, mode: DesignerLibraryOpenMode) {
        pending = DesignerLibraryOpenRequest(document = document, mode = mode)
    }

    @Synchronized
    fun consume(): DesignerLibraryOpenRequest? {
        val request = pending
        pending = null
        return request
    }
}
