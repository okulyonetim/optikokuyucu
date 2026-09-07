package com.okulyonetim.optikokuyucu.school

import android.content.Context

/**
 * Students hidden here are removed only from the Optik Okuyucu device roster. Their Okul Yönetim
 * / oy_veliler records are never deleted or updated by this store.
 */
class SchoolStudentVisibilityStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hiddenStudentNumbers(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()

    fun hide(studentNumber: String) {
        val normalized = SchoolStudentMatch.normalizeStudentNumber(studentNumber)
        if (normalized.isBlank()) return
        val next = hiddenStudentNumbers() + normalized
        check(prefs.edit().putStringSet(KEY_HIDDEN, next).commit()) {
            "Öğrenci yerel gizleme bilgisi kaydedilemedi."
        }
    }

    fun restore(studentNumber: String) {
        val normalized = SchoolStudentMatch.normalizeStudentNumber(studentNumber)
        if (normalized.isBlank()) return
        val next = hiddenStudentNumbers() - normalized
        check(prefs.edit().putStringSet(KEY_HIDDEN, next).commit()) {
            "Öğrenci yerel gizleme bilgisi güncellenemedi."
        }
    }

    fun isHidden(studentNumber: String): Boolean =
        SchoolStudentMatch.normalizeStudentNumber(studentNumber) in hiddenStudentNumbers()

    private companion object {
        const val PREFS_NAME = "school-student-visibility"
        const val KEY_HIDDEN = "hidden-student-numbers"
    }
}
