package com.okulyonetim.optikokuyucu.omr.designer

object DesignerTemplateVersioning {
    /**
     * Resolves a safe document version for persistence without mutating an existing saved version.
     * Identical content is idempotent; conflicting content receives the next available version.
     */
    fun resolveForSave(
        document: DesignerDocument,
        existing: List<DesignerDocument>
    ): DesignerDocument {
        val sameTemplate = existing.filter { it.id == document.id }
        val exactVersion = sameTemplate.firstOrNull { it.version == document.version }

        if (exactVersion == null || exactVersion == document) return document

        val nextVersion = maxOf(
            document.version + 1,
            (sameTemplate.maxOfOrNull { it.version } ?: document.version) + 1
        )
        return document.copy(version = nextVersion)
    }
}
