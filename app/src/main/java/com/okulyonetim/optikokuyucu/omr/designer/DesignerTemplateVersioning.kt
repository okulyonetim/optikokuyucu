package com.okulyonetim.optikokuyucu.omr.designer

object DesignerTemplateVersioning {
    /**
     * Resolves a safe document version for persistence without mutating an existing saved version.
     * Identical content is idempotent even when the caller still holds an older version number;
     * conflicting content receives the next available version.
     */
    fun resolveForSave(
        document: DesignerDocument,
        existing: List<DesignerDocument>
    ): DesignerDocument {
        val sameTemplate = existing.filter { it.id == document.id }

        val sameContent = sameTemplate.firstOrNull { candidate ->
            candidate.copy(version = document.version) == document
        }
        if (sameContent != null) return sameContent

        val exactVersion = sameTemplate.firstOrNull { it.version == document.version }
        if (exactVersion == null) return document

        val nextVersion = maxOf(
            document.version + 1,
            (sameTemplate.maxOfOrNull { it.version } ?: document.version) + 1
        )
        return document.copy(version = nextVersion)
    }
}
