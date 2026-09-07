package com.okulyonetim.optikokuyucu.school

import android.content.Context
import com.okulyonetim.optikokuyucu.exam.Exam
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import org.json.JSONArray
import org.json.JSONObject

data class SchoolFormOwnership(
    val ownerUid: String,
    val ownerName: String,
    val isPublic: Boolean,
    val cloudDocumentId: String = ""
)

data class SchoolExamSummary(
    val id: String,
    val name: String,
    val schoolName: String,
    val examDateEpochDay: Long,
    val ownerUid: String,
    val ownerName: String,
    val isPublic: Boolean
)

data class SchoolExamListItem(
    val summary: SchoolExamSummary,
    val localExam: Exam?
)

object SchoolContentAccess {
    fun canViewExam(exam: Exam, profile: SchoolUserProfile): Boolean =
        profile.admin || exam.ownerUid == profile.uid || exam.isPublic

    fun canModifyExam(exam: Exam, profile: SchoolUserProfile): Boolean =
        profile.admin || (exam.ownerUid.isNotBlank() && exam.ownerUid == profile.uid)

    fun canPublishExam(profile: SchoolUserProfile): Boolean = profile.admin

    fun canViewExam(summary: SchoolExamSummary, profile: SchoolUserProfile): Boolean =
        profile.admin || summary.ownerUid == profile.uid || summary.isPublic

    fun canModifyForm(ownership: SchoolFormOwnership?, profile: SchoolUserProfile): Boolean =
        profile.admin || (ownership?.ownerUid?.isNotBlank() == true && ownership.ownerUid == profile.uid)

    fun canDeleteForm(ownership: SchoolFormOwnership?, profile: SchoolUserProfile): Boolean =
        canModifyForm(ownership, profile)

    fun canViewForm(ownership: SchoolFormOwnership?, profile: SchoolUserProfile): Boolean =
        profile.admin || ownership == null || ownership.ownerUid == profile.uid || ownership.isPublic

    fun canPublishForm(profile: SchoolUserProfile): Boolean = profile.admin

    fun visibleLocalExams(exams: List<Exam>, profile: SchoolUserProfile): List<Exam> =
        exams.filter { canViewExam(it, profile) }

    fun mergeExamItems(
        localExams: List<Exam>,
        cloud: List<SchoolExamSummary>,
        profile: SchoolUserProfile
    ): List<SchoolExamListItem> {
        val localById = visibleLocalExams(localExams, profile).associateBy { it.id }
        val ids = linkedSetOf<String>()
        ids += localById.keys
        ids += cloud.filter { canViewExam(it, profile) }.map { it.id }
        return ids.mapNotNull { id ->
            val local = localById[id]
            val remote = cloud.firstOrNull { it.id == id && canViewExam(it, profile) }
            val summary = local?.toSummary() ?: remote ?: return@mapNotNull null
            SchoolExamListItem(summary = summary, localExam = local)
        }.sortedWith(
            compareByDescending<SchoolExamListItem> { it.summary.examDateEpochDay }
                .thenBy { it.summary.name }
        )
    }

    fun Exam.toSummary(): SchoolExamSummary = SchoolExamSummary(
        id = id,
        name = name,
        schoolName = schoolName,
        examDateEpochDay = examDateEpochDay,
        ownerUid = ownerUid,
        ownerName = ownerDisplayName,
        isPublic = isPublic
    )
}

class SchoolFormOwnershipStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun ownership(document: DesignerDocument): SchoolFormOwnership? = all()[key(document)]

    fun all(): Map<String, SchoolFormOwnership> {
        val raw = prefs.getString(KEY_RECORDS, "").orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            root.keys().asSequence().associateWith { key ->
                val item = root.getJSONObject(key)
                SchoolFormOwnership(
                    ownerUid = item.optString("ownerUid"),
                    ownerName = item.optString("ownerName"),
                    isPublic = item.optBoolean("public", false),
                    cloudDocumentId = item.optString("cloudDocumentId")
                )
            }
        }.getOrDefault(emptyMap())
    }

    fun claimOwned(document: DesignerDocument, profile: SchoolUserProfile): SchoolFormOwnership {
        val current = ownership(document)
        if (current != null) return current
        val next = SchoolFormOwnership(
            ownerUid = profile.uid,
            ownerName = profile.displayName,
            isPublic = false
        )
        put(document, next)
        return next
    }

    fun put(document: DesignerDocument, ownership: SchoolFormOwnership) {
        val records = all().toMutableMap()
        records[key(document)] = ownership
        save(records)
    }

    fun setPublic(document: DesignerDocument, isPublic: Boolean) {
        val current = ownership(document) ?: return
        put(document, current.copy(isPublic = isPublic))
    }

    fun remove(document: DesignerDocument) {
        val records = all().toMutableMap()
        records.remove(key(document))
        save(records)
    }

    private fun save(records: Map<String, SchoolFormOwnership>) {
        val root = JSONObject()
        records.forEach { (key, value) ->
            root.put(
                key,
                JSONObject()
                    .put("ownerUid", value.ownerUid)
                    .put("ownerName", value.ownerName)
                    .put("public", value.isPublic)
                    .put("cloudDocumentId", value.cloudDocumentId)
            )
        }
        check(prefs.edit().putString(KEY_RECORDS, root.toString()).commit()) {
            "Form sahiplik bilgisi kaydedilemedi."
        }
    }

    private fun key(document: DesignerDocument): String = "${document.id}@${document.version}"

    private companion object {
        const val PREFS_NAME = "school-form-ownership"
        const val KEY_RECORDS = "records"
    }
}

class SchoolExamCatalogStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun replace(items: List<SchoolExamSummary>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("schoolName", item.schoolName)
                    .put("examDateEpochDay", item.examDateEpochDay)
                    .put("ownerUid", item.ownerUid)
                    .put("ownerName", item.ownerName)
                    .put("public", item.isPublic)
            )
        }
        check(prefs.edit().putString(KEY_ITEMS, array.toString()).commit()) {
            "Sınav kataloğu kaydedilemedi."
        }
    }

    fun list(): List<SchoolExamSummary> {
        val raw = prefs.getString(KEY_ITEMS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                SchoolExamSummary(
                    id = item.getString("id"),
                    name = item.optString("name"),
                    schoolName = item.optString("schoolName"),
                    examDateEpochDay = item.optLong("examDateEpochDay"),
                    ownerUid = item.optString("ownerUid"),
                    ownerName = item.optString("ownerName"),
                    isPublic = item.optBoolean("public", false)
                )
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFS_NAME = "school-exam-catalog"
        const val KEY_ITEMS = "items"
    }
}

object SchoolAccountMigration {
    fun claimLegacyAdminExams(context: Context, profile: SchoolUserProfile): Int {
        if (!profile.admin) return 0
        val repository = FileExamRepository(context.applicationContext)
        var migrated = 0
        repository.list().filter { it.ownerUid.isBlank() }.forEach { exam ->
            repository.save(
                exam.copy(
                    ownerUid = profile.uid,
                    ownerDisplayName = profile.displayName
                )
            )
            migrated += 1
        }
        return migrated
    }
}
