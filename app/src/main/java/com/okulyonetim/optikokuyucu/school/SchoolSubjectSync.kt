package com.okulyonetim.optikokuyucu.school

import android.content.Context
import com.okulyonetim.optikokuyucu.settings.AppSettingsRepository
import java.util.Locale

data class SchoolSubjectSyncResult(
    val cloudDocuments: Int,
    val importedSubjects: Int,
    val subjects: List<String>
)

object SchoolSubjectMapper {
    private val TurkishLocale = Locale.forLanguageTag("tr-TR")

    fun names(documents: List<FirestoreDocument>): List<String> = documents
        .mapNotNull { document ->
            document.fields["ad"]
                ?.toString()
                ?.trim()
                ?.replace(Regex("\\s+"), " ")
                ?.takeIf(String::isNotBlank)
        }
        .distinctBy { it.lowercase(TurkishLocale) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.lowercase(TurkishLocale) })
}

class SchoolSubjectSyncService(
    context: Context,
    private val client: SchoolPortalClient
) {
    private val appContext = context.applicationContext

    fun sync(): SchoolSubjectSyncResult {
        val documents = client.listDocuments(SCHOOL_SUBJECT_COLLECTION)
        val subjects = SchoolSubjectMapper.names(documents)
        require(subjects.isNotEmpty()) {
            "Okul Yönetim ders listesi boş. Mevcut cihaz dersleri değiştirilmedi."
        }
        AppSettingsRepository(appContext).saveSubjects(subjects)
        return SchoolSubjectSyncResult(
            cloudDocuments = documents.size,
            importedSubjects = subjects.size,
            subjects = subjects
        )
    }

    private companion object {
        // Okul Yönetim web uygulamasındaki COL.dersListesi gerçek koleksiyonudur.
        const val SCHOOL_SUBJECT_COLLECTION = "oy_dersListesi"
    }
}
