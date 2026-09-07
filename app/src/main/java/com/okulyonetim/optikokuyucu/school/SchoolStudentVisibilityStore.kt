package com.okulyonetim.optikokuyucu.school

import android.content.Context
import com.okulyonetim.optikokuyucu.student.StudentRosterEntry
import com.okulyonetim.optikokuyucu.student.StudentSchoolIdentity

/**
 * Students hidden here are removed only from the Optik Okuyucu device roster. Their Okul Yönetim
 * / oy_veliler records are never deleted or updated by this store.
 *
 * New records are stored as institution-aware keys because Koruk İlkokulu and Koruk Ortaokulu may
 * contain the same student number. Legacy plain-number markers are still honored as a wildcard so
 * users who hid a student on an older app version keep the previous behavior after upgrading.
 */
class SchoolStudentVisibilityStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hiddenStudentKeys(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()

    fun hide(studentNumber: String, gradeLevel: Int) {
        val key = StudentSchoolIdentity.identityKey(studentNumber, gradeLevel)
        if (key.isBlank()) return
        val next = hiddenStudentKeys() + key
        check(prefs.edit().putStringSet(KEY_HIDDEN, next).commit()) {
            "Öğrenci yerel gizleme bilgisi kaydedilemedi."
        }
    }

    fun restore(studentNumber: String, gradeLevel: Int) {
        val key = StudentSchoolIdentity.identityKey(studentNumber, gradeLevel)
        if (key.isBlank()) return
        val next = hiddenStudentKeys() - key
        check(prefs.edit().putStringSet(KEY_HIDDEN, next).commit()) {
            "Öğrenci yerel gizleme bilgisi güncellenemedi."
        }
    }

    fun isHidden(studentNumber: String, gradeLevel: Int): Boolean {
        val normalized = SchoolStudentMatch.normalizeStudentNumber(studentNumber)
        if (normalized.isBlank()) return false
        val keys = hiddenStudentKeys()
        return normalized in keys || StudentSchoolIdentity.identityKey(normalized, gradeLevel) in keys
    }

    fun isHidden(entry: StudentRosterEntry): Boolean =
        isHidden(entry.studentNumber, entry.gradeLevel)

    private companion object {
        const val PREFS_NAME = "school-student-visibility"
        const val KEY_HIDDEN = "hidden-student-numbers"
    }
}
