package com.okulyonetim.optikokuyucu.school

import android.content.Context
import com.okulyonetim.optikokuyucu.settings.AppSettingsRepository
import com.okulyonetim.optikokuyucu.student.StudentSchoolIdentity

data class SchoolInstitutionInfo(
    val schoolName: String,
    val primarySchoolName: String,
    val middleSchoolName: String
) {
    fun preferredSchoolName(): String = schoolName
        .ifBlank { middleSchoolName }
        .ifBlank { primarySchoolName }
        .ifBlank { StudentSchoolIdentity.MIDDLE_SCHOOL_NAME }
}

object SchoolInstitutionMapper {
    fun from(document: FirestoreDocument?): SchoolInstitutionInfo {
        val fields = document?.fields.orEmpty()
        return SchoolInstitutionInfo(
            schoolName = fields["okulAdi"]?.toString().orEmpty().trim(),
            primarySchoolName = fields["ilkokulAdi"]?.toString().orEmpty().trim(),
            middleSchoolName = fields["ortaokulAdi"]?.toString().orEmpty().trim()
        )
    }
}

/** Reads institution data from Okul Yönetim; this service never writes institution documents. */
class SchoolInstitutionSyncService(
    context: Context,
    private val client: SchoolPortalClient
) {
    private val settings = AppSettingsRepository(context.applicationContext)

    fun refresh(): SchoolInstitutionInfo {
        requireNotNull(client.cachedSession()) { "Okul Yönetim oturumu yok." }
        val document = client.getDocument(SCHOOL_INFO_COLLECTION, SCHOOL_INFO_DOCUMENT_ID)
        val info = SchoolInstitutionMapper.from(document)
        settings.syncTrustedSchoolName(info.preferredSchoolName())
        return info
    }

    private companion object {
        // Okul Yönetim web uygulamasındaki COL.okulBilgileri gerçek koleksiyonudur.
        const val SCHOOL_INFO_COLLECTION = "oy_okulBilgileri"
        const val SCHOOL_INFO_DOCUMENT_ID = "ayarlar"
    }
}
