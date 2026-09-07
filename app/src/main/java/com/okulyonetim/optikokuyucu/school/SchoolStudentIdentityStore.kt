package com.okulyonetim.optikokuyucu.school

import android.content.Context
import com.okulyonetim.optikokuyucu.student.StudentNumber
import org.json.JSONObject

/**
 * The cross-app lookup key is the student number. The Firestore document id is cached only as a
 * compatibility pointer so the existing Okul Yönetim student profile can recognize uploaded rows
 * without changing its historical result model.
 */
class SchoolStudentIdentityStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun replace(numberToDocumentId: Map<String, String>) {
        val json = JSONObject()
        numberToDocumentId.forEach { (number, documentId) ->
            val normalized = StudentNumber.normalize(number)
            if (normalized.isNotBlank() && documentId.isNotBlank()) json.put(normalized, documentId)
        }
        prefs.edit().putString(KEY_IDENTITIES, json.toString()).apply()
    }

    fun documentIdFor(studentNumber: String): String? {
        val normalized = StudentNumber.normalize(studentNumber)
        if (normalized.isBlank()) return null
        val raw = prefs.getString(KEY_IDENTITIES, "").orEmpty()
        if (raw.isBlank()) return null
        return runCatching { JSONObject(raw).optString(normalized).takeIf(String::isNotBlank) }
            .getOrNull()
    }

    private companion object {
        const val PREFS_NAME = "school-student-identities"
        const val KEY_IDENTITIES = "number-to-document-id"
    }
}
