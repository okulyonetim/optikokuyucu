package com.okulyonetim.optikokuyucu.school

import android.content.Context
import com.okulyonetim.optikokuyucu.student.FileStudentRosterRepository
import com.okulyonetim.optikokuyucu.student.StudentGender
import com.okulyonetim.optikokuyucu.student.StudentImportSummary
import com.okulyonetim.optikokuyucu.student.StudentNumber
import com.okulyonetim.optikokuyucu.student.StudentRosterEntry

data class SchoolClassIdentity(
    val id: String,
    val name: String,
    val gradeLevel: Int?,
    val branch: String
)

data class SchoolDirectorySyncResult(
    val cloudStudents: Int,
    val importedStudents: Int,
    val skippedWithoutNumber: Int,
    val skippedWithoutClass: Int,
    val localSummary: StudentImportSummary
)

object SchoolClassParser {
    fun parse(className: String, explicitLevel: Any? = null): Pair<Int?, String> {
        val normalized = className.trim().replace(Regex("\\s+"), " ")
        val match = Regex("^(\\d{1,2})\\s*(?:[-/.]|\\s)?\\s*(.*)$").find(normalized)
        val parsedLevel = match?.groupValues?.getOrNull(1)?.toIntOrNull()
        val explicit = when (explicitLevel) {
            is Number -> explicitLevel.toInt()
            else -> explicitLevel?.toString()?.filter(Char::isDigit)?.toIntOrNull()
        }
        val level = parsedLevel ?: explicit
        val branch = match?.groupValues?.getOrNull(2)
            ?.trim()
            ?.removePrefix("-")
            ?.trim()
            .orEmpty()
        return level to branch
    }
}

/** Maps Okul Yönetim oy_veliler records into the existing offline OMR roster. */
class SchoolDirectorySyncService(
    private val context: Context,
    private val client: SchoolPortalClient
) {
    fun sync(): SchoolDirectorySyncResult {
        val classDocs = client.listDocuments(SchoolPortalConfig.CLASSES)
        val classes = classDocs.associate { doc ->
            val name = doc.fields["ad"]?.toString().orEmpty().trim()
            val (grade, branch) = SchoolClassParser.parse(name, doc.fields["seviye"])
            doc.id to SchoolClassIdentity(doc.id, name, grade, branch)
        }
        val studentDocs = client.listDocuments(SchoolPortalConfig.STUDENTS)

        var withoutNumber = 0
        var withoutClass = 0
        val entries = studentDocs.mapNotNull { doc ->
            val number = StudentNumber.normalize(doc.fields["ogrenciNo"]?.toString().orEmpty())
            if (number.isBlank()) {
                withoutNumber += 1
                return@mapNotNull null
            }
            val fullName = doc.fields["ogrenciAdi"]?.toString().orEmpty().trim()
            if (fullName.isBlank()) return@mapNotNull null
            val classId = doc.fields["sinifId"]?.toString().orEmpty()
            val schoolClass = classes[classId]
            val gradeLevel = schoolClass?.gradeLevel
            if (schoolClass == null || gradeLevel !in 1..12) {
                withoutClass += 1
                return@mapNotNull null
            }
            val genderText = doc.fields["cinsiyet"]?.toString().orEmpty()
            val guardianPhone = listOf("telefon1", "telefon", "telefon2", "telefon3")
                .asSequence()
                .map { key -> doc.fields[key]?.toString().orEmpty().trim() }
                .firstOrNull(String::isNotBlank)
                .orEmpty()
            StudentRosterEntry(
                studentNumber = number,
                fullName = fullName,
                gender = StudentGender.fromEschool(genderText),
                gradeLevel = requireNotNull(gradeLevel),
                branch = schoolClass.branch,
                guardianName = doc.fields["veliAdi"]?.toString().orEmpty().trim(),
                guardianPhone = guardianPhone,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        }

        val repository = FileStudentRosterRepository(context.applicationContext)
        val summary = repository.upsertImported(entries)
        return SchoolDirectorySyncResult(
            cloudStudents = studentDocs.size,
            importedStudents = entries.distinctBy { it.studentNumber }.size,
            skippedWithoutNumber = withoutNumber,
            skippedWithoutClass = withoutClass,
            localSummary = summary
        )
    }
}

/** Application-level access point for the shared school session and sync services. */
class SchoolPortalManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val store = SchoolSessionStore(appContext)
    val client = SchoolPortalClient(store)

    fun cachedSession(): SchoolPortalSession? = client.cachedSession()

    fun signIn(username: String, password: String): SchoolPortalSession =
        client.signIn(username, password)

    fun refreshProfile(): SchoolPortalSession = client.refreshProfile()

    fun syncDirectory(): SchoolDirectorySyncResult =
        SchoolDirectorySyncService(appContext, client).sync()

    fun syncExamsAndResults(): SchoolExamCloudSyncResult =
        SchoolExamCloudSyncService(appContext, client).syncAll()

    fun signOut() = client.signOut()

    companion object {
        @Volatile private var instance: SchoolPortalManager? = null

        fun get(context: Context): SchoolPortalManager = instance ?: synchronized(this) {
            instance ?: SchoolPortalManager(context).also { instance = it }
        }
    }
}
