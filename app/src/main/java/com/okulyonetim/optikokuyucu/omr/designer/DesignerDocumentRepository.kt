package com.okulyonetim.optikokuyucu.omr.designer

import android.content.Context
import java.io.File
import java.util.Base64

interface DesignerDocumentRepository {
    /**
     * Saves a form and returns its persisted identity. Once an id/version exists in device storage,
     * later saves update that same form in place instead of creating another visible version.
     * Immutable built-in starter baselines can still receive a one-time safe derived version.
     */
    fun save(document: DesignerDocument): DesignerDocument
    fun load(id: String, version: Int): DesignerDocument?
    fun list(): List<DesignerDocument>
    fun delete(id: String, version: Int): Boolean
}

internal object DesignerDocumentSavePolicy {
    fun resolveForSave(
        document: DesignerDocument,
        existing: List<DesignerDocument>,
        immutableBaselines: List<DesignerDocument> = emptyList()
    ): DesignerDocument {
        val exactStoredVersionExists = existing.any { candidate ->
            candidate.id == document.id && candidate.version == document.version
        }
        return if (exactStoredVersionExists) {
            document
        } else {
            DesignerTemplateVersioning.resolveForSave(
                document = document,
                existing = existing,
                immutableBaselines = immutableBaselines
            )
        }
    }
}

/**
 * Fully offline repository backed by app-private files. A Room implementation can replace this
 * later without changing designer UI or compiler contracts.
 */
class FileDesignerDocumentRepository(context: Context) : DesignerDocumentRepository {
    private val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    override fun save(document: DesignerDocument): DesignerDocument {
        val existing = list()
        val resolved = DesignerDocumentSavePolicy.resolveForSave(
            document = document,
            existing = existing,
            immutableBaselines = DesignerStarterTemplates.all()
        )
        val safety = TemplateReadabilityAnalyzer.analyze(resolved)
        require(safety.canSave) {
            val firstError = safety.issues.firstOrNull { it.severity == ReadabilitySeverity.ERROR }
            buildString {
                append("OMR güvenlik kontrolü başarısız: ${safety.errorCount} hata")
                if (firstError != null) append(". ${firstError.message}")
            }
        }
        val target = fileFor(resolved.id, resolved.version)

        if (target.isFile) {
            val stored = runCatching { DesignerDocumentCodec.decode(target.readBytes()) }.getOrNull()
            if (stored == resolved) return resolved
        }

        val temp = File(directory, target.name + ".tmp")
        val bytes = DesignerDocumentCodec.encode(resolved)
        temp.outputStream().buffered().use { it.write(bytes) }
        if (target.exists() && !target.delete()) {
            temp.delete()
            error("Mevcut şablon güncellenemedi.")
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            error("Şablon dosyası kaydedilemedi.")
        }
        return resolved
    }

    override fun load(id: String, version: Int): DesignerDocument? {
        val file = fileFor(id, version)
        if (!file.isFile) return null
        return DesignerDocumentCodec.decode(file.readBytes())
    }

    override fun list(): List<DesignerDocument> = directory.listFiles()
        .orEmpty()
        .asSequence()
        .filter { it.isFile && it.extension == EXTENSION }
        .mapNotNull { file -> runCatching { DesignerDocumentCodec.decode(file.readBytes()) }.getOrNull() }
        .sortedWith(compareBy<DesignerDocument> { it.name }.thenByDescending { it.version })
        .toList()

    override fun delete(id: String, version: Int): Boolean {
        val file = fileFor(id, version)
        return !file.exists() || file.delete()
    }

    private fun fileFor(id: String, version: Int): File {
        require(id.isNotBlank())
        require(version > 0)
        val encodedId = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(id.toByteArray(Charsets.UTF_8))
        return File(directory, "$encodedId-v$version.$EXTENSION")
    }

    private companion object {
        const val DIRECTORY_NAME = "omr-designer-templates"
        const val EXTENSION = "omrd"
    }
}
