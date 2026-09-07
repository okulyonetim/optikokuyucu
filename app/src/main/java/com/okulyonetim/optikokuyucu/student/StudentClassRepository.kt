package com.okulyonetim.optikokuyucu.student

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.Base64

data class StudentClassEntry(
    val gradeLevel: Int,
    val branch: String
) {
    init {
        require(gradeLevel in 1..12) { "Sınıf seviyesi 1–12 arasında olmalıdır." }
    }

    val normalizedBranch: String
        get() = StudentClassName.normalizeBranch(branch)

    val className: String
        get() = StudentClassName.format(gradeLevel, normalizedBranch)

    fun normalized(): StudentClassEntry = copy(branch = normalizedBranch)
}

interface StudentClassRepository {
    fun save(entry: StudentClassEntry)
    fun list(): List<StudentClassEntry>
    fun delete(entry: StudentClassEntry): Boolean
}

/** App-private class catalog. Empty classes can exist independently from the student roster. */
class FileStudentClassRepository(context: Context) : StudentClassRepository {
    private val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    override fun save(entry: StudentClassEntry) {
        val normalized = entry.normalized()
        val target = fileFor(normalized)
        val temporary = File(directory, target.name + ".tmp")
        val branchEncoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(normalized.branch.toByteArray(Charsets.UTF_8))
        temporary.writeText("${normalized.gradeLevel}\n$branchEncoded", Charsets.UTF_8)
        if (target.exists() && !target.delete()) {
            temporary.delete()
            error("Sınıf kaydı güncellenemedi.")
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            error("Sınıf kaydı kalıcı depoya taşınamadı.")
        }
    }

    override fun list(): List<StudentClassEntry> = directory
        .listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }
        .orEmpty()
        .mapNotNull(::decode)
        .distinctBy { it.className }
        .sortedWith(compareBy<StudentClassEntry> { it.gradeLevel }.thenBy { it.branch })

    override fun delete(entry: StudentClassEntry): Boolean {
        val file = fileFor(entry.normalized())
        return !file.exists() || file.delete()
    }

    private fun decode(file: File): StudentClassEntry? = runCatching {
        val lines = file.readLines(Charsets.UTF_8)
        if (lines.size < 2) return@runCatching null
        val grade = lines[0].trim().toIntOrNull() ?: return@runCatching null
        val branch = String(Base64.getUrlDecoder().decode(lines[1].trim()), Charsets.UTF_8)
        StudentClassEntry(grade, branch).normalized()
    }.getOrNull()

    private fun fileFor(entry: StudentClassEntry): File {
        val key = entry.className.lowercase()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
        return File(directory, digest + FILE_SUFFIX)
    }

    private companion object {
        const val DIRECTORY_NAME = "omr-student-classes"
        const val FILE_SUFFIX = ".omrclass"
    }
}
