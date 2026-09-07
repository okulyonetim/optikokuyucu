package com.okulyonetim.optikokuyucu.school

import android.content.Context
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocumentCodec
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import java.time.Instant
import java.time.LocalDate
import java.util.Base64

data class SchoolTemplateSyncResult(
    val uploaded: Int,
    val downloaded: Int,
    val skipped: Int,
    val failures: List<String>
)

class SchoolTemplateCloudSyncService(
    context: Context,
    private val client: SchoolPortalClient
) {
    private val appContext = context.applicationContext
    private val repository = FileDesignerDocumentRepository(appContext)
    private val ownershipStore = SchoolFormOwnershipStore(appContext)

    fun sync(): SchoolTemplateSyncResult {
        val profile = requireNotNull(client.cachedSession()).profile
        val failures = mutableListOf<String>()
        var uploaded = 0
        var downloaded = 0
        var skipped = 0

        repository.list().forEach { document ->
            var ownership = ownershipStore.ownership(document)
            if (ownership == null && profile.admin) {
                ownership = ownershipStore.claimOwned(document, profile)
            }
            if (ownership?.ownerUid != profile.uid) {
                skipped += 1
                return@forEach
            }
            runCatching {
                val cloudId = ownership.cloudDocumentId.ifBlank {
                    SchoolSharedDocumentId.forForm(profile.uid, document.id, document.version)
                }
                client.upsertDocument(
                    SchoolPortalConfig.OPTIK_TEMPLATES,
                    cloudId,
                    templatePayload(document, ownership.copy(cloudDocumentId = cloudId))
                )
                ownershipStore.put(document, ownership.copy(cloudDocumentId = cloudId))
            }.onSuccess {
                uploaded += 1
            }.onFailure { error ->
                failures += "${document.name}: ${error.message ?: "şablon gönderilemedi"}"
            }
        }

        val remote = runCatching { client.listDocuments(SchoolPortalConfig.OPTIK_TEMPLATES) }
            .onFailure { failures += "Paylaşılan şablonlar: ${it.message ?: "alınamadı"}" }
            .getOrDefault(emptyList())

        remote.forEach { cloud ->
            val ownerUid = cloud.string("sahipUid")
            val isPublic = cloud.fields["herkeseAcik"] as? Boolean ?: false
            if (!profile.admin && ownerUid != profile.uid && !isPublic) return@forEach
            val encoded = cloud.string("icerikBase64")
            val document = runCatching {
                DesignerDocumentCodec.decode(Base64.getDecoder().decode(encoded))
            }.getOrElse {
                failures += "${cloud.id}: paylaşılan form içeriği okunamadı"
                return@forEach
            }
            val currentOwnership = ownershipStore.ownership(document)
            if (currentOwnership != null && currentOwnership.ownerUid.isNotBlank() && currentOwnership.ownerUid != ownerUid) {
                skipped += 1
                return@forEach
            }
            runCatching { repository.save(document) }
                .onSuccess { stored ->
                    ownershipStore.put(
                        stored,
                        SchoolFormOwnership(
                            ownerUid = ownerUid,
                            ownerName = cloud.string("sahipAdi"),
                            isPublic = isPublic,
                            cloudDocumentId = cloud.id
                        )
                    )
                    downloaded += 1
                }
                .onFailure { error -> failures += "${document.name}: ${error.message ?: "cihaza kaydedilemedi"}" }
        }

        return SchoolTemplateSyncResult(uploaded, downloaded, skipped, failures)
    }

    fun setPublic(document: DesignerDocument, isPublic: Boolean) {
        val profile = requireNotNull(client.cachedSession()).profile
        require(profile.admin) { "Yalnız admin şablonu herkese açabilir." }
        val ownership = ownershipStore.ownership(document)
            ?: ownershipStore.claimOwned(document, profile)
        val cloudId = ownership.cloudDocumentId.ifBlank {
            SchoolSharedDocumentId.forForm(ownership.ownerUid.ifBlank { profile.uid }, document.id, document.version)
        }
        val next = ownership.copy(isPublic = isPublic, cloudDocumentId = cloudId)
        client.upsertDocument(
            SchoolPortalConfig.OPTIK_TEMPLATES,
            cloudId,
            templatePayload(document, next)
        )
        ownershipStore.put(document, next)
    }

    fun deleteCloudCopy(document: DesignerDocument) {
        val profile = requireNotNull(client.cachedSession()).profile
        val ownership = ownershipStore.ownership(document) ?: return
        require(SchoolContentAccess.canDeleteForm(ownership, profile)) {
            "Bu şablonu silme yetkiniz yok."
        }
        val cloudId = ownership.cloudDocumentId
        if (cloudId.isNotBlank()) client.deleteDocument(SchoolPortalConfig.OPTIK_TEMPLATES, cloudId)
    }

    private fun templatePayload(
        document: DesignerDocument,
        ownership: SchoolFormOwnership
    ): Map<String, Any?> {
        val bytes = DesignerDocumentCodec.encode(document)
        require(bytes.size <= MAX_TEMPLATE_BYTES) {
            "Şablon bulut paylaşımı için çok büyük (${bytes.size} bayt)."
        }
        return linkedMapOf(
            "formId" to document.id,
            "formSurumu" to document.version,
            "ad" to document.name,
            "sahipUid" to ownership.ownerUid,
            "sahipAdi" to ownership.ownerName,
            "herkeseAcik" to ownership.isPublic,
            "icerikBase64" to Base64.getEncoder().encodeToString(bytes),
            "kaynak" to "optik-okuyucu",
            "guncellenmeTarihi" to Instant.now().toString()
        )
    }

    private companion object {
        const val MAX_TEMPLATE_BYTES = 700_000
    }
}

class SchoolExamCatalogSyncService(
    context: Context,
    private val client: SchoolPortalClient
) {
    private val appContext = context.applicationContext
    private val store = SchoolExamCatalogStore(appContext)

    fun refresh(): List<SchoolExamSummary> {
        val profile = requireNotNull(client.cachedSession()).profile
        val summaries = client.listDocuments(SchoolPortalConfig.TRIAL_EXAMS)
            .asSequence()
            .filter { it.string("kaynak") == "optik-okuyucu" }
            .mapNotNull { doc ->
                val date = runCatching { LocalDate.parse(doc.string("tarih")) }.getOrNull() ?: return@mapNotNull null
                SchoolExamSummary(
                    id = doc.string("optikSinavId").ifBlank { doc.id },
                    name = doc.string("ad").ifBlank { "Adsız sınav" },
                    schoolName = doc.string("okulAdi"),
                    examDateEpochDay = date.toEpochDay(),
                    ownerUid = doc.string("sahipUid"),
                    ownerName = doc.string("sahipAdi"),
                    isPublic = doc.fields["herkeseAcik"] as? Boolean ?: false
                )
            }
            .filter { SchoolContentAccess.canViewExam(it, profile) }
            .toList()
        store.replace(summaries)
        return summaries
    }

    fun setPublic(examId: String, isPublic: Boolean) {
        val profile = requireNotNull(client.cachedSession()).profile
        require(profile.admin) { "Yalnız admin sınavı herkese açabilir." }
        val current = client.getDocument(SchoolPortalConfig.TRIAL_EXAMS, examId)
            ?: error("Bulut sınav kaydı bulunamadı.")
        client.upsertDocument(
            SchoolPortalConfig.TRIAL_EXAMS,
            current.id,
            current.fields + mapOf(
                "herkeseAcik" to isPublic,
                "guncellenmeTarihi" to Instant.now().toString()
            )
        )
        refresh()
    }
}

object SchoolSharedDocumentId {
    fun forForm(ownerUid: String, formId: String, version: Int): String {
        val raw = "$ownerUid|$formId|$version"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "${ownerUid.take(24)}-$digest"
    }
}
