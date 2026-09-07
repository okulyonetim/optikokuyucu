package com.okulyonetim.optikokuyucu.school

import android.content.Context
import com.okulyonetim.optikokuyucu.student.StudentNumber
import com.okulyonetim.optikokuyucu.student.StudentSchoolIdentity
import org.json.JSONObject

/** Pure matching rules shared by cloud upload and tests. */
object SchoolStudentMatch {
    fun normalizeStudentNumber(raw: String): String = StudentNumber.normalize(raw)

    fun documentIdFor(studentNumber: String, numberToDocumentId: Map<String, String>): String? {
        val normalized = normalizeStudentNumber(studentNumber)
        if (normalized.isBlank()) return null
        return numberToDocumentId[normalized]?.takeIf(String::isNotBlank)
    }

    fun schoolIdentityKey(studentNumber: String, gradeLevel: Int): String =
        StudentSchoolIdentity.identityKey(studentNumber, gradeLevel)

    fun <T> upsertByStudentNumber(
        target: MutableMap<String, T>,
        studentNumber: String,
        value: T
    ): Boolean {
        val normalized = normalizeStudentNumber(studentNumber)
        if (normalized.isBlank()) return false
        target[normalized] = value
        return true
    }

    fun <T> upsertBySchoolAndStudentNumber(
        target: MutableMap<String, T>,
        studentNumber: String,
        gradeLevel: Int,
        value: T
    ): Boolean {
        val key = schoolIdentityKey(studentNumber, gradeLevel)
        if (key.isBlank()) return false
        target[key] = value
        return true
    }
}

data class SchoolStudentDocumentIdentity(
    val studentNumber: String,
    val gradeLevel: Int,
    val documentId: String
)

/**
 * Firestore student document ids are cached with a compound school+number key. This is required
 * because Koruk İlkokulu (1–4) and Koruk Ortaokulu (5–8) may legally use the same student number.
 */
class SchoolStudentIdentityStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun replace(identities: Collection<SchoolStudentDocumentIdentity>) {
        val json = JSONObject()
        identities.forEach { identity ->
            val key = SchoolStudentMatch.schoolIdentityKey(identity.studentNumber, identity.gradeLevel)
            if (key.isNotBlank() && identity.documentId.isNotBlank()) json.put(key, identity.documentId)
        }
        prefs.edit().putString(KEY_IDENTITIES, json.toString()).apply()
    }

    /** Compatibility helper retained for older tests/callers that only have globally unique ids. */
    fun replace(numberToDocumentId: Map<String, String>) {
        val json = JSONObject()
        numberToDocumentId.forEach { (number, documentId) ->
            val normalized = SchoolStudentMatch.normalizeStudentNumber(number)
            if (normalized.isNotBlank() && documentId.isNotBlank()) json.put(normalized, documentId)
        }
        prefs.edit().putString(KEY_IDENTITIES, json.toString()).apply()
    }

    fun documentIdFor(studentNumber: String, gradeLevel: Int): String? {
        val normalized = SchoolStudentMatch.normalizeStudentNumber(studentNumber)
        if (normalized.isBlank()) return null
        val root = readRoot() ?: return null
        val compound = SchoolStudentMatch.schoolIdentityKey(normalized, gradeLevel)
        return root.optString(compound).takeIf(String::isNotBlank)
            ?: root.optString(normalized).takeIf(String::isNotBlank) // 0.17.1 legacy cache
    }

    /**
     * Number-only lookup succeeds only when the cache contains one unambiguous student. New code
     * should provide grade level whenever possible.
     */
    fun documentIdFor(studentNumber: String): String? {
        val normalized = SchoolStudentMatch.normalizeStudentNumber(studentNumber)
        if (normalized.isBlank()) return null
        val root = readRoot() ?: return null
        root.optString(normalized).takeIf(String::isNotBlank)?.let { return it }

        val suffix = ":$normalized"
        val matches = root.keys().asSequence()
            .filter { it.endsWith(suffix) }
            .mapNotNull { key -> root.optString(key).takeIf(String::isNotBlank) }
            .distinct()
            .toList()
        return matches.singleOrNull()
    }

    private fun readRoot(): JSONObject? {
        val raw = prefs.getString(KEY_IDENTITIES, "").orEmpty()
        if (raw.isBlank()) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private companion object {
        const val PREFS_NAME = "school-student-identities"
        const val KEY_IDENTITIES = "number-to-document-id"
    }
}
