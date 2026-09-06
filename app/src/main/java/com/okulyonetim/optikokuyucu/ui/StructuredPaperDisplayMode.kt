package com.okulyonetim.optikokuyucu.ui

/**
 * View-only state for the structured paper workspace.
 *
 * This is deliberately not persisted in DesignerDocument: switching between edit and print preview
 * must never alter OMR geometry or template versioning.
 */
internal enum class StructuredPaperDisplayMode {
    EDIT,
    PREVIEW;

    val editorChromeVisible: Boolean get() = this == EDIT
    val directEditingEnabled: Boolean get() = this == EDIT
    val usesPrintInk: Boolean get() = this == PREVIEW
    val viewportNavigationAlwaysEnabled: Boolean get() = this == PREVIEW
}
