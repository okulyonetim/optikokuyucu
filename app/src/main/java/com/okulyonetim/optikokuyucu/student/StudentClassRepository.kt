package com.okulyonetim.optikokuyucu.student

import android.content.Context
import com.okulyonetim.optikokuyucu.storage.AppLog
import com.okulyonetim.optikokuyucu.storage.AtomicFiles
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

    init {
        AtomicFiles.cleanStaleTemporaries(directory)
    }

    override fun save(entry: StudentClassEntry) {
        val normalized = entry.normalized()
        val branchEncoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(normalized.branch.toByteArray(Charsets.UTF_8))
        synchronized(AtomicFiles.lockFor(directory)) {
            AtomicFiles.writeText(fileFor(normalized), "${normalized.gradeLevel}\n$branchEncoded")
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

    private fun decode(file: File): StudentClassEntry? = try {
        val lines = file.readLines(Charsets.UTF_8)
        val grade = lines.getOrNull(0)?.trim()?.toIntOrNull()
        val encodedBranch = lines.getOrNull(1)?.trim()
        if (grade == null || encodedBranch == null) {
            AppLog.warn("Storage", "Sınıf kaydı biçimi geçersiz: ${file.name}")
            null
        } else {
            val branch = String(Base64.getUrlDecoder().decode(encodedBranch), Charsets.UTF_8)
            StudentClassEntry(grade, branch).normalized()
        }
    } catch (error: Throwable) {
        AppLog.warn("Storage", "Sınıf kaydı okunamadı: ${file.name}", error)
        null
    }

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
