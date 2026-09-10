package com.okulyonetim.optikokuyucu.ui

import com.okulyonetim.optikokuyucu.student.StudentSchoolIdentity
import java.util.Locale

/** Pure rules used by Yeni Sınav to keep school selection consistent with student grades. */
internal object ExamSchoolSelection {
    private val TurkishLocale = Locale.forLanguageTag("tr-TR")

    fun canonicalName(raw: String): String {
        val normalized = raw.trim().replace(Regex("\\s+"), " ")
        return when {
            normalized.equals(StudentSchoolIdentity.PRIMARY_SCHOOL_NAME, ignoreCase = true) ->
                StudentSchoolIdentity.PRIMARY_SCHOOL_NAME
            normalized.equals(StudentSchoolIdentity.MIDDLE_SCHOOL_NAME, ignoreCase = true) ->
                StudentSchoolIdentity.MIDDLE_SCHOOL_NAME
            else -> normalized
        }
    }

    fun options(configuredSchoolName: String, existingSchoolName: String? = null): List<String> =
        buildList {
            configuredSchoolName.takeIf(String::isNotBlank)?.let { add(canonicalName(it)) }
            existingSchoolName?.takeIf(String::isNotBlank)?.let { add(canonicalName(it)) }
            add(StudentSchoolIdentity.PRIMARY_SCHOOL_NAME)
            add(StudentSchoolIdentity.MIDDLE_SCHOOL_NAME)
        }
            .filter(String::isNotBlank)
            .distinctBy { it.lowercase(TurkishLocale) }

    /** Returns a school only when all selected grades belong to the same primary/middle group. */
    fun schoolForGrades(grades: Iterable<Int>): String? {
        val schools = grades
            .map(StudentSchoolIdentity::schoolNameForGrade)
            .filter(String::isNotBlank)
            .distinct()
        return schools.singleOrNull()
    }
}
