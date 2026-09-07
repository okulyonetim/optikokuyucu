package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.student.StudentNumber

/** Immutable progress state for a user-started multi-image exam import. */
data class ExamGalleryBatchProgress(
    val total: Int = 0,
    val processed: Int = 0,
    val imported: Int = 0,
    val failed: Int = 0
) {
    init {
        require(total >= 0)
        require(processed in 0..total)
        require(imported >= 0 && failed >= 0)
        require(imported + failed == processed)
    }

    val remaining: Int get() = total - processed
    val completed: Boolean get() = processed == total

    fun onImported(): ExamGalleryBatchProgress {
        require(processed < total) { "Toplu içe aktarma zaten tamamlandı." }
        return copy(processed = processed + 1, imported = imported + 1)
    }

    fun onFailed(): ExamGalleryBatchProgress {
        require(processed < total) { "Toplu içe aktarma zaten tamamlandı." }
        return copy(processed = processed + 1, failed = failed + 1)
    }

    companion object {
        fun start(total: Int): ExamGalleryBatchProgress {
            require(total > 0) { "En az bir görsel seçilmelidir." }
            return ExamGalleryBatchProgress(total = total)
        }
    }
}

/** Returns the existing exam paper for one readable normalized student number. */
fun Exam.paperForStudentNumber(studentNumber: String): ExamPaperLink? {
    val normalized = StudentNumber.normalize(studentNumber)
    if (normalized.isBlank()) return null
    return papers.firstOrNull {
        StudentNumber.normalize(it.studentNumber) == normalized
    }
}

/**
 * Blank/unreadable numbers are intentionally not treated as duplicates so they can still be reviewed
 * and corrected manually from the student-paper detail screen.
 */
fun Exam.containsStudentNumber(studentNumber: String): Boolean =
    paperForStudentNumber(studentNumber) != null
