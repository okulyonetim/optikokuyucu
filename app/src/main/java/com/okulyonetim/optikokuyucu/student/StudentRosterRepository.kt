package com.okulyonetim.optikokuyucu.student

import android.content.Context
import java.io.File
import java.security.MessageDigest

interface StudentRosterRepository {
    fun save(entry: StudentRosterEntry)
    fun findByNumber(studentNumber: String): StudentRosterEntry?
    fun list(): List<StudentRosterEntry>
    fun upsertImported(entries: List<StudentRosterEntry>): StudentImportSummary
    fun delete(studentNumber: String): Boolean
}

/** App-private student roster. No student/guardian data leaves the device through this repository. */
class FileStudentRosterRepository(context: Context) : StudentRosterRepository {
    private val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    override fun save(entry: StudentRosterEntry) {
        val normalized = entry.normalized()
        val destination = fileFor(normalized.studentNumber)
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
    }

    override fun findByNumber(studentNumber: String): StudentRosterEntry? {
        val normalizedNumber = StudentNumber.normalize(studentNumber)
        if (normalizedNumber.isBlank()) return null
        val file = fileFor(normalizedNumber)
        if (!file.isFile) return null
        return runCatching { StudentRosterCodec.decode(file.readBytes()) }
            .getOrNull()
            ?.takeIf { it.studentNumber == normalizedNumber }
    }

    override fun list(): List<StudentRosterEntry> = directory
        .listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }
        .orEmpty()
        .mapNotNull { file -> runCatching { StudentRosterCodec.decode(file.readBytes()) }.getOrNull() }
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
        normalized.groupBy { it.studentNumber }.forEach { (number, duplicates) ->
            val identities = duplicates.map { it.fullName.lowercase() to it.className.lowercase() }.distinct()
            require(identities.size == 1) {
                "Öğrenci no $number içe aktarma dosyasında birden fazla öğrenciye ait görünüyor."
            }
        }

        var inserted = 0
        var updated = 0
        var unchanged = 0
        normalized.distinctBy { it.studentNumber }.forEach { incoming ->
            val existing = findByNumber(incoming.studentNumber)
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

    override fun delete(studentNumber: String): Boolean {
        val normalized = StudentNumber.normalize(studentNumber)
        if (normalized.isBlank()) return false
        val file = fileFor(normalized)
        return !file.exists() || file.delete()
    }

    private fun sameData(a: StudentRosterEntry, b: StudentRosterEntry): Boolean =
        a.studentNumber == b.studentNumber &&
            a.fullName == b.fullName &&
            a.gender == b.gender &&
            a.gradeLevel == b.gradeLevel &&
            a.branch == b.branch &&
            a.guardianName == b.guardianName &&
            a.guardianPhone == b.guardianPhone

    private fun fileFor(studentNumber: String): File = File(directory, keyFor(studentNumber) + FILE_SUFFIX)

    private fun keyFor(studentNumber: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(studentNumber.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    private companion object {
        const val DIRECTORY_NAME = "omr-students"
        const val FILE_SUFFIX = ".omrstudent"
    }
}
