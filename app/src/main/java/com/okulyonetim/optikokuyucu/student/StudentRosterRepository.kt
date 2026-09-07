package com.okulyonetim.optikokuyucu.student

import android.content.Context
import com.okulyonetim.optikokuyucu.school.SchoolStudentVisibilityStore
import java.io.File
import java.security.MessageDigest

interface StudentRosterRepository {
    fun save(entry: StudentRosterEntry)
    fun findByNumber(studentNumber: String): StudentRosterEntry?
    fun findByNumberAndGrade(studentNumber: String, gradeLevel: Int): StudentRosterEntry? =
        listByNumber(studentNumber).firstOrNull {
            StudentSchoolIdentity.sameInstitution(it.gradeLevel, gradeLevel)
        }
    fun listByNumber(studentNumber: String): List<StudentRosterEntry> {
        val normalized = StudentNumber.normalize(studentNumber)
        if (normalized.isBlank()) return emptyList()
        return list().filter { it.studentNumber == normalized }
    }
    fun list(): List<StudentRosterEntry>
    fun upsertImported(entries: List<StudentRosterEntry>): StudentImportSummary
    fun delete(studentNumber: String): Boolean
    fun delete(studentNumber: String, gradeLevel: Int): Boolean = delete(studentNumber)
}

/** App-private student roster. No student/guardian data leaves the device through this repository. */
class FileStudentRosterRepository(context: Context) : StudentRosterRepository {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, DIRECTORY_NAME).apply { mkdirs() }
    private val schoolVisibilityStore = SchoolStudentVisibilityStore(appContext)

    override fun save(entry: StudentRosterEntry) {
        val normalized = entry.normalized()
        val destination = fileFor(normalized)
        val temporary = File(directory, destination.name + ".tmp")
        temporary.writeBytes(StudentRosterCodec.encode(normalized))
        if (destination.exists() && !destination.delete()) {
            temporary.delete()
            error("Eski öğrenci kaydı güncellenemedi.")
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            error("Öğrenci kaydı kalıcı depoya taşınamadı.")
        }

        // 0.17.1 ve öncesindeki yalnız-numara dosyasını, aynı kurum kimliğine aitse temizle.
        // Böylece yükseltme sonrası öğrenci listesinde aynı kayıt iki kez görünmez.
        val legacy = legacyFileFor(normalized.studentNumber)
        if (legacy != destination && legacy.isFile) {
            val legacyEntry = runCatching { StudentRosterCodec.decode(legacy.readBytes()) }.getOrNull()
            if (legacyEntry?.identityKey == normalized.identityKey) legacy.delete()
        }
    }

    /** Rewrites an entry whose grade change may also move it between İlkokul and Ortaokul. */
    fun replace(oldEntry: StudentRosterEntry, newEntry: StudentRosterEntry) {
        val oldNormalized = oldEntry.normalized()
        val newNormalized = newEntry.normalized()
        save(newNormalized)
        if (oldNormalized.identityKey == newNormalized.identityKey) return

        val oldFile = fileForIdentity(oldNormalized.identityKey)
        if (oldFile.isFile && !oldFile.delete()) {
            error("Eski kurum öğrenci kaydı temizlenemedi.")
        }
        val legacy = legacyFileFor(oldNormalized.studentNumber)
        if (legacy.isFile) {
            val legacyEntry = runCatching { StudentRosterCodec.decode(legacy.readBytes()) }.getOrNull()
            if (legacyEntry?.identityKey == oldNormalized.identityKey && !legacy.delete()) {
                error("Eski öğrenci kaydı temizlenemedi.")
            }
        }
    }

    /**
     * Numara iki kurumda birden varsa bilinçli olarak null döner. Çağıran taraf sınıf/kurum bilgisi
     * ile [findByNumberAndGrade] kullanmalıdır; böylece yanlış öğrencinin seçilmesi engellenir.
     */
    override fun findByNumber(studentNumber: String): StudentRosterEntry? =
        listByNumber(studentNumber).singleOrNull()

    override fun findByNumberAndGrade(studentNumber: String, gradeLevel: Int): StudentRosterEntry? {
        val identity = StudentSchoolIdentity.identityKey(studentNumber, gradeLevel)
        if (identity.isBlank()) return null
        return list().firstOrNull { it.identityKey == identity }
    }

    override fun listByNumber(studentNumber: String): List<StudentRosterEntry> {
        val normalized = StudentNumber.normalize(studentNumber)
        if (normalized.isBlank()) return emptyList()
        return list().filter { it.studentNumber == normalized }
    }

    override fun list(): List<StudentRosterEntry> = directory
        .listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }
        .orEmpty()
        .mapNotNull { file -> runCatching { StudentRosterCodec.decode(file.readBytes()) }.getOrNull() }
        .groupBy { it.identityKey }
        .mapNotNull { (_, records) -> records.maxByOrNull { it.updatedAtEpochMs } }
        .sortedWith(
            compareBy<StudentRosterEntry> { it.gradeLevel }
                .thenBy { it.branch }
                .thenBy { it.fullName }
                .thenBy { it.studentNumber.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.studentNumber }
        )

    override fun upsertImported(entries: List<StudentRosterEntry>): StudentImportSummary {
        if (entries.isEmpty()) return StudentImportSummary(0, 0, 0, 0)
        val normalized = entries.map(StudentRosterEntry::normalized)
            .filterNot(schoolVisibilityStore::isHidden)
        if (normalized.isEmpty()) return StudentImportSummary(0, 0, 0, 0)

        // Aynı numara İlkokul ve Ortaokulda bulunabilir. Yalnız aynı kurum içinde aynı numaranın
        // iki farklı öğrenciye ait görünmesi gerçek bir çakışmadır.
        normalized.groupBy { it.identityKey }.forEach { (_, duplicates) ->
            val identities = duplicates.map { it.fullName.lowercase() to it.className.lowercase() }.distinct()
            require(identities.size == 1) {
                val sample = duplicates.first()
                val school = sample.schoolName.ifBlank { "${sample.gradeLevel}. sınıf" }
                "$school öğrenci no ${sample.studentNumber} birden fazla öğrenciye ait görünüyor."
            }
        }

        var inserted = 0
        var updated = 0
        var unchanged = 0
        normalized.distinctBy { it.identityKey }.forEach { incoming ->
            val existing = findByNumberAndGrade(incoming.studentNumber, incoming.gradeLevel)
            val merged = if (existing == null) {
                incoming
            } else {
                incoming.copy(
                    guardianName = incoming.guardianName.ifBlank { existing.guardianName },
                    guardianPhone = incoming.guardianPhone.ifBlank { existing.guardianPhone },
                    updatedAtEpochMs = incoming.updatedAtEpochMs
                ).normalized()
            }
            when {
                existing == null -> {
                    save(merged)
                    inserted += 1
                }
                sameData(existing, merged) -> unchanged += 1
                else -> {
                    save(merged)
                    updated += 1
                }
            }
        }
        return StudentImportSummary(
            inserted = inserted,
            updated = updated,
            unchanged = unchanged,
            total = inserted + updated + unchanged
        )
    }

    /**
     * Legacy convenience path. Only succeeds when the number identifies exactly one local student.
     * If the same number exists in both Koruk İlkokulu and Koruk Ortaokulu, grade must be supplied.
     */
    override fun delete(studentNumber: String): Boolean {
        val only = listByNumber(studentNumber).singleOrNull() ?: return false
        return delete(only.studentNumber, only.gradeLevel)
    }

    /**
     * Deletes only the app-private roster file and records a local institution-aware suppression
     * marker. No Firestore delete/update is performed; Okul Yönetim oy_veliler remains untouched.
     */
    override fun delete(studentNumber: String, gradeLevel: Int): Boolean {
        val normalizedNumber = StudentNumber.normalize(studentNumber)
        if (normalizedNumber.isBlank()) return false
        val identity = StudentSchoolIdentity.identityKey(normalizedNumber, gradeLevel)
        val matching = list().firstOrNull { it.identityKey == identity }

        val destination = matching?.let(::fileFor) ?: fileForIdentity(identity)
        var deleted = !destination.exists() || destination.delete()

        // Yükseltme öncesi numara-temelli dosya hâlâ varsa ve aynı kuruma aitse onu da kaldır.
        val legacy = legacyFileFor(normalizedNumber)
        if (legacy.isFile) {
            val legacyEntry = runCatching { StudentRosterCodec.decode(legacy.readBytes()) }.getOrNull()
            if (legacyEntry?.identityKey == identity) deleted = legacy.delete() && deleted
        }

        if (deleted) schoolVisibilityStore.hide(normalizedNumber, gradeLevel)
        return deleted
    }

    private fun sameData(a: StudentRosterEntry, b: StudentRosterEntry): Boolean =
        a.studentNumber == b.studentNumber &&
            a.fullName == b.fullName &&
            a.gender == b.gender &&
            a.gradeLevel == b.gradeLevel &&
            a.branch == b.branch &&
            a.guardianName == b.guardianName &&
            a.guardianPhone == b.guardianPhone

    private fun fileFor(entry: StudentRosterEntry): File = fileForIdentity(entry.identityKey)

    private fun fileForIdentity(identityKey: String): File =
        File(directory, hashKey(identityKey) + FILE_SUFFIX)

    private fun legacyFileFor(studentNumber: String): File =
        File(directory, hashKey(StudentNumber.normalize(studentNumber)) + FILE_SUFFIX)

    private fun hashKey(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    private companion object {
        const val DIRECTORY_NAME = "omr-students"
        const val FILE_SUFFIX = ".omrstudent"
    }
}
