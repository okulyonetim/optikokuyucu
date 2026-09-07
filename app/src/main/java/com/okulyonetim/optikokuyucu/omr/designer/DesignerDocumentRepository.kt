package com.okulyonetim.optikokuyucu.omr.designer

import android.content.Context
import com.okulyonetim.optikokuyucu.school.SchoolContentAccess
import com.okulyonetim.optikokuyucu.school.SchoolFormOwnershipStore
import com.okulyonetim.optikokuyucu.school.SchoolPortalManager
import java.io.File
import java.util.Base64

interface DesignerDocumentRepository {
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

/** Fully offline repository with account ownership enforced for user-initiated mutations. */
class FileDesignerDocumentRepository(context: Context) : DesignerDocumentRepository {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, DIRECTORY_NAME).apply { mkdirs() }
    private val ownershipStore = SchoolFormOwnershipStore(appContext)

    override fun save(document: DesignerDocument): DesignerDocument = persist(document, trustedCloudSync = false)

    /** Cloud sync may materialize another user's public form locally without granting edit ownership. */
    fun saveFromCloud(document: DesignerDocument): DesignerDocument = persist(document, trustedCloudSync = true)

    private fun persist(document: DesignerDocument, trustedCloudSync: Boolean): DesignerDocument {
        val existing = listUnscoped()
        val resolved = DesignerDocumentSavePolicy.resolveForSave(
            document = document,
            existing = existing,
            immutableBaselines = DesignerStarterTemplates.all()
        )
        val target = fileFor(resolved.id, resolved.version)
        val profile = activeProfile()
        if (!trustedCloudSync && profile != null) {
            val ownership = ownershipStore.ownership(resolved)
            if (target.isFile) {
                require(SchoolContentAccess.canModifyForm(ownership, profile)) {
                    "Bu optik formu düzenleme yetkiniz yok."
                }
            } else if (ownership == null) {
                ownershipStore.claimOwned(resolved, profile)
            } else {
                require(SchoolContentAccess.canModifyForm(ownership, profile)) {
                    "Bu optik formu düzenleme yetkiniz yok."
                }
            }
        }

        val safety = TemplateReadabilityAnalyzer.analyze(resolved)
        require(safety.canSave) {
            val firstError = safety.issues.firstOrNull { it.severity == ReadabilitySeverity.ERROR }
            buildString {
                append("OMR güvenlik kontrolü başarısız: ${safety.errorCount} hata")
                if (firstError != null) append(". ${firstError.message}")
            }
        }

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

    override fun list(): List<DesignerDocument> {
        val all = listUnscoped()
        val profile = activeProfile() ?: return all
        return all.filter { document ->
            SchoolContentAccess.canViewForm(ownershipStore.ownership(document), profile)
        }
    }

    private fun listUnscoped(): List<DesignerDocument> = directory.listFiles()
        .orEmpty()
        .asSequence()
        .filter { it.isFile && it.extension == EXTENSION }
        .mapNotNull { file -> runCatching { DesignerDocumentCodec.decode(file.readBytes()) }.getOrNull() }
        .sortedWith(compareBy<DesignerDocument> { it.name }.thenByDescending { it.version })
        .toList()

    override fun delete(id: String, version: Int): Boolean {
        val document = load(id, version)
        val profile = activeProfile()
        if (document != null && profile != null) {
            require(SchoolContentAccess.canDeleteForm(ownershipStore.ownership(document), profile)) {
                "Bu optik formu silme yetkiniz yok."
            }
        }
        val file = fileFor(id, version)
        val deleted = !file.exists() || file.delete()
        if (deleted && document != null) ownershipStore.remove(document)
        return deleted
    }

    private fun activeProfile() =
        runCatching { SchoolPortalManager.get(appContext).cachedSession()?.profile }.getOrNull()

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
