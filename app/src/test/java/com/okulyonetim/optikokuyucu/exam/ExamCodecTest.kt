package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExamCodecTest {
    private val selection = ActiveTemplateSelection(
        source = ActiveTemplateSource.DESIGNER_DOCUMENT,
        templateId = "lgs-mini",
        templateVersion = 3
    )

    @Test
    fun roundTripPreservesExamAndPaperMetadata() {
        val original = Exam(
            id = "exam-1",
            name = "LGS MEBİ DENEME",
            schoolName = "Koruk Ortaokulu",
            templateSelection = selection,
            wrongAnswerPolicy = WrongAnswerPolicy.FOUR_WRONG_ONE_CORRECT,
            folderName = "8A",
            examDateEpochDay = 21000L,
            createdAtEpochMs = 123456L,
            papers = listOf(
                ExamPaperLink(
                    scanRecordId = "scan-16",
                    studentName = "FATMA ZEHRA GÜRBÜZ",
                    className = "8A",
                    studentNumber = "16",
                    bookletCode = "A",
                    linkedAtEpochMs = 123999L
                )
            )
        )

        assertEquals(original, ExamCodec.decode(ExamCodec.encode(original)))
        assertEquals(ExamStatus.READ, original.status)
    }

    @Test
    fun waitingExamHasNoPapers() {
        val exam = ExamFactory.create(
            name = "LGS",
            schoolName = "Koruk Ortaokulu",
            templateSelection = selection,
            examDateEpochDay = 21000L,
            id = "exam-empty",
            createdAtEpochMs = 1L
        )

        assertEquals(ExamStatus.WAITING, exam.status)
        assertEquals(emptyList<ExamPaperLink>(), exam.papers)
    }

    @Test
    fun duplicateScanCannotBeStoredTwice() {
        assertThrows(IllegalArgumentException::class.java) {
            Exam(
                id = "exam-duplicate",
                name = "Deneme",
                schoolName = "Okul",
                templateSelection = selection,
                examDateEpochDay = 21000L,
                createdAtEpochMs = 1L,
                papers = listOf(
                    ExamPaperLink("same-scan", linkedAtEpochMs = 1L),
                    ExamPaperLink("same-scan", linkedAtEpochMs = 2L)
                )
            )
        }
    }
}
