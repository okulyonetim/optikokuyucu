package com.okulyonetim.optikokuyucu.exam

import android.content.Context
import java.io.File
import java.security.MessageDigest

internal object ExamTemplateBindingPolicy {
    fun validateUpdate(stored: Exam, incoming: Exam) {
        require(stored.id == incoming.id) { "Sınav kimliği değiştirilemez." }
        require(stored.templateSelection == incoming.templateSelection) {
            "Sınavın optik form sürümü oluşturulduktan sonra değiştirilemez."
        }
    }
}

interface ExamRepository {
    fun save(exam: Exam)
    fun load(id: String): Exam?
    fun list(): List<Exam>
    fun delete(id: String): Boolean
}

/** App-private exam storage; atomic-ish writes, no network and no storage permission. */
class FileExamRepository(context: Context) : ExamRepository {
    private val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    override fun save(exam: Exam) {
        val destination = fileFor(exam.id)
        if (destination.isFile) {
            val stored = runCatching { ExamCodec.decode(destination.readBytes()) }
                .getOrElse { error ->
                    throw IllegalStateException("Mevcut sınav kaydı okunamadı.", error)
                }
            ExamTemplateBindingPolicy.validateUpdate(stored, exam)
        }

        val temporary = File(directory, destination.name + ".tmp")
        temporary.writeBytes(ExamCodec.encode(exam))
        if (destination.exists() && !destination.delete()) {
            temporary.delete()
            error("Eski sınav kaydı güncellenemedi.")
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            error("Sınav kaydı kalıcı depoya taşınamadı.")
        }
    }

    override fun load(id: String): Exam? {
        val file = fileFor(id)
        if (!file.isFile) return null
        return runCatching { ExamCodec.decode(file.readBytes()) }
            .getOrNull()
            ?.takeIf { it.id == id }
    }

    override fun list(): List<Exam> = directory
        .listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }
        .orEmpty()
        .mapNotNull { file -> runCatching { ExamCodec.decode(file.readBytes()) }.getOrNull() }
        .sortedWith(
            compareByDescending<Exam> { it.examDateEpochDay }
                .thenByDescending { it.createdAtEpochMs }
                .thenBy { it.name }
        )

    override fun delete(id: String): Boolean {
        val file = fileFor(id)
        return !file.exists() || file.delete()
    }

    private fun fileFor(id: String): File = File(directory, keyFor(id) + FILE_SUFFIX)

    private fun keyFor(id: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    private companion object {
        const val DIRECTORY_NAME = "omr-exams"
        const val FILE_SUFFIX = ".omrexam"
    }
}
