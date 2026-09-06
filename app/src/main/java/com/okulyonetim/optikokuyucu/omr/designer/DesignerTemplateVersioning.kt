package com.okulyonetim.optikokuyucu.omr.designer

object DesignerTemplateVersioning {
    /**
     * Resolves a safe document version for persistence without mutating an existing saved version.
     * Immutable baselines (for example built-in starter templates) participate in the version
     * history even though they are not stored by FileDesignerDocumentRepository.
     *
     * Identical content is idempotent even when the caller still holds an older version number;
     * conflicting or stale content receives a version newer than every known version of the same
     * template id.
     */
    fun resolveForSave(
        document: DesignerDocument,
        existing: List<DesignerDocument>,
        immutableBaselines: List<DesignerDocument> = emptyList()
    ): DesignerDocument {
        val sameTemplate = (immutableBaselines + existing).filter { it.id == document.id }

        val sameContent = sameTemplate
            .filter { candidate -> candidate.copy(version = document.version) == document }
            .maxByOrNull { it.version }
        if (sameContent != null) return sameContent

        val maxVersion = sameTemplate.maxOfOrNull { it.version }
        val requestedVersionIsFree = sameTemplate.none { it.version == document.version }
        if (requestedVersionIsFree && (maxVersion == null || document.version > maxVersion)) {
            return document
        }

        val nextVersion = maxOf(
            document.version + 1,
            (maxVersion ?: document.version) + 1
        )
        return document.copy(version = nextVersion)
    }

    fun isHistoricalVersion(
        id: String,
        version: Int,
        existing: List<DesignerDocument>
    ): Boolean = existing.any { candidate ->
        candidate.id == id && candidate.version > version
    }
}
