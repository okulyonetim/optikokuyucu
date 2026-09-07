package com.okulyonetim.optikokuyucu.school

import android.content.Context
import com.okulyonetim.optikokuyucu.student.FileStudentRosterRepository
import com.okulyonetim.optikokuyucu.student.StudentGender
import com.okulyonetim.optikokuyucu.student.StudentImportSummary
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

enum class SchoolStudentSkipReason {
    WITHOUT_NUMBER,
    WITHOUT_NAME,
    WITHOUT_CLASS
}

data class SchoolStudentMapping(
    val entry: StudentRosterEntry?,
    val skipReason: SchoolStudentSkipReason? = null
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

/** Pure field mapping from Okul Yönetim documents to the existing offline roster model. */
object SchoolDirectoryMapper {
    fun schoolClass(doc: FirestoreDocument): SchoolClassIdentity {
        val name = doc.fields["ad"]?.toString().orEmpty().trim()
        val (grade, branch) = SchoolClassParser.parse(name, doc.fields["seviye"])
        return SchoolClassIdentity(doc.id, name, grade, branch)
    }

    fun student(
        doc: FirestoreDocument,
        classes: Map<String, SchoolClassIdentity>,
        updatedAtEpochMs: Long = System.currentTimeMillis()
    ): SchoolStudentMapping {
        val number = SchoolStudentMatch.normalizeStudentNumber(doc.fields["ogrenciNo"]?.toString().orEmpty())
        if (number.isBlank()) return SchoolStudentMapping(null, SchoolStudentSkipReason.WITHOUT_NUMBER)

        val fullName = doc.fields["ogrenciAdi"]?.toString().orEmpty().trim()
        if (fullName.isBlank()) return SchoolStudentMapping(null, SchoolStudentSkipReason.WITHOUT_NAME)

        val classId = doc.fields["sinifId"]?.toString().orEmpty()
        val schoolClass = classes[classId]
        val gradeLevel = schoolClass?.gradeLevel
        if (schoolClass == null || gradeLevel !in 1..12) {
            return SchoolStudentMapping(null, SchoolStudentSkipReason.WITHOUT_CLASS)
        }

        val guardianPhone = listOf("telefon1", "telefon", "telefon2", "telefon3")
            .asSequence()
            .map { key -> doc.fields[key]?.toString().orEmpty().trim() }
            .firstOrNull(String::isNotBlank)
            .orEmpty()

        return SchoolStudentMapping(
            entry = StudentRosterEntry(
                studentNumber = number,
                fullName = fullName,
                gender = StudentGender.fromEschool(doc.fields["cinsiyet"]?.toString().orEmpty()),
                gradeLevel = requireNotNull(gradeLevel),
                branch = schoolClass.branch,
                guardianName = doc.fields["veliAdi"]?.toString().orEmpty().trim(),
                guardianPhone = guardianPhone,
                updatedAtEpochMs = updatedAtEpochMs
            )
        )
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
            val schoolClass = SchoolDirectoryMapper.schoolClass(doc)
            doc.id to schoolClass
        }
        val studentDocs = client.listDocuments(SchoolPortalConfig.STUDENTS)

        val numberToDocumentId = studentDocs.mapNotNull { doc ->
            val number = SchoolStudentMatch.normalizeStudentNumber(doc.fields["ogrenciNo"]?.toString().orEmpty())
            number.takeIf(String::isNotBlank)?.let { it to doc.id }
        }.toMap()
        SchoolStudentIdentityStore(context.applicationContext).replace(numberToDocumentId)

        val mapped = studentDocs.map { doc -> SchoolDirectoryMapper.student(doc, classes) }
        val withoutNumber = mapped.count { it.skipReason == SchoolStudentSkipReason.WITHOUT_NUMBER }
        val withoutClass = mapped.count { it.skipReason == SchoolStudentSkipReason.WITHOUT_CLASS }
        val entries = mapped.mapNotNull { it.entry }

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
    private val cloudCoordinator = SchoolCloudSyncCoordinator(appContext, client)

    fun cachedSession(): SchoolPortalSession? = client.cachedSession()

    fun signIn(username: String, password: String): SchoolPortalSession =
        client.signIn(username, password)

    fun refreshProfile(): SchoolPortalSession = client.refreshProfile()

    fun syncDirectory(): SchoolDirectorySyncResult =
        SchoolDirectorySyncService(appContext, client).sync()

    fun syncExamsAndResults(force: Boolean = false): SchoolExamCloudSyncResult? =
        cloudCoordinator.syncIfChanged(force)

    fun invalidateCloudSync() = cloudCoordinator.invalidate()

    fun signOut() {
        cloudCoordinator.invalidate()
        client.signOut()
    }

    companion object {
        @Volatile private var instance: SchoolPortalManager? = null

        fun get(context: Context): SchoolPortalManager = instance ?: synchronized(this) {
            instance ?: SchoolPortalManager(context).also { instance = it }
        }
    }
}
