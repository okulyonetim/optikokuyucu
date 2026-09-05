package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamGalleryBatchProgressTest {
    @Test
    fun tracksImportedFailedAndRemainingItems() {
        var progress = ExamGalleryBatchProgress.start(4)

        progress = progress.onImported()
        progress = progress.onFailed()
        progress = progress.onImported()

        assertEquals(4, progress.total)
        assertEquals(3, progress.processed)
        assertEquals(2, progress.imported)
        assertEquals(1, progress.failed)
        assertEquals(1, progress.remaining)

        progress = progress.onImported()
        assertTrue(progress.completed)
        assertEquals(0, progress.remaining)
    }

    @Test
    fun duplicateGuardMatchesOnlyNonBlankStudentNumbers() {
        val exam = Exam(
            id = "exam-1",
            name = "Deneme",
            schoolName = "Okul",
            templateSelection = ActiveTemplateSelection(
                source = ActiveTemplateSource.STANDARD,
                templateId = "template",
                templateVersion = 1
            ),
            examDateEpochDay = 1L,
            createdAtEpochMs = 1L,
            papers = listOf(
                ExamPaperLink(scanRecordId = "scan-1", studentNumber = "123456", linkedAtEpochMs = 1L),
                ExamPaperLink(scanRecordId = "scan-2", studentNumber = "", linkedAtEpochMs = 2L)
            )
        )

        assertTrue(exam.containsStudentNumber("123456"))
        assertTrue(exam.containsStudentNumber(" 123456 "))
        assertFalse(exam.containsStudentNumber("654321"))
        assertFalse(exam.containsStudentNumber(""))
        assertFalse(exam.containsStudentNumber("   "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyBatch() {
        ExamGalleryBatchProgress.start(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsProgressWhoseCountersDoNotMatchProcessedCount() {
        ExamGalleryBatchProgress(total = 3, processed = 2, imported = 2, failed = 1)
    }
}
