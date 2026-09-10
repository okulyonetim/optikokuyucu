package com.okulyonetim.optikokuyucu.school

import java.util.Locale

/** Central action-layer policy for Okul Yönetim synchronization. */
object SchoolSyncAccessPolicy {
    private val TurkishLocale = Locale.forLanguageTag("tr-TR")

    fun isTeacher(profile: SchoolUserProfile): Boolean {
        if (profile.admin) return false
        if (profile.linkedTeacherId.isNotBlank()) return true
        val roleId = profile.roleId
            .trim()
            .lowercase(TurkishLocale)
            .replace('ı', 'i')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')
        return "ogretmen" in roleId || "teacher" in roleId
    }

    fun canSyncDirectory(profile: SchoolUserProfile): Boolean = !isTeacher(profile)

    fun canSyncSubjects(profile: SchoolUserProfile): Boolean = !isTeacher(profile)

    fun canSyncTemplates(profile: SchoolUserProfile): Boolean = !isTeacher(profile)

    fun canSyncExamDefinition(profile: SchoolUserProfile): Boolean =
        !isTeacher(profile) && profile.canEdit("sinavIslemleri")

    fun canSyncResults(profile: SchoolUserProfile): Boolean =
        profile.canView("denemeSonuclari")

    fun canChangeCloudContent(profile: SchoolUserProfile): Boolean = !isTeacher(profile)

    fun requireDirectorySync(profile: SchoolUserProfile) {
        require(canSyncDirectory(profile)) {
            "Öğretmen hesapları öğrenci listesini Okul Yönetim ile eşitleyemez."
        }
    }

    fun requireSubjectSync(profile: SchoolUserProfile) {
        require(canSyncSubjects(profile)) {
            "Öğretmen hesapları ders listesini Okul Yönetim ile eşitleyemez."
        }
    }

    fun requireTemplateSync(profile: SchoolUserProfile) {
        require(canSyncTemplates(profile)) {
            "Öğretmen hesapları optik form verilerini Okul Yönetim ile eşitleyemez."
        }
    }

    fun requireCloudContentChange(profile: SchoolUserProfile) {
        require(canChangeCloudContent(profile)) {
            "Öğretmen hesapları Okul Yönetim kurum/sınav/form verilerini değiştiremez."
        }
    }
}
