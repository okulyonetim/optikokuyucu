package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ExamCodecTest {
    private val selection = ActiveTemplateSelection(
        source = ActiveTemplateSource.DESIGNER_DOCUMENT,
        templateId = "lgs-mini",
        templateVersion = 3
    )

    @Test
    fun roundTripPreservesExamPaperAndSetupMetadata() {
        val original = Exam(
            id = "exam-1",
            name = "DENEME SINAVI",
            schoolName = "TEST ORTAOKULU",
            templateSelection = selection,
            wrongAnswerPolicy = WrongAnswerPolicy.FOUR_WRONG_ONE_CORRECT,
            folderName = "8A",
            examDateEpochDay = 21000L,
            createdAtEpochMs = 123456L,
            papers = listOf(
                ExamPaperLink(
                    scanRecordId = "scan-16",
                    studentName = "TEST ÖĞRENCİ",
                    className = "8-A",
                    studentNumber = "16",
                    bookletCode = "A",
                    linkedAtEpochMs = 123999L
                )
            ),
            participants = listOf(
                ExamParticipant("16", "TEST ÖĞRENCİ", "8-A"),
                ExamParticipant("44", "ÖRNEK ÖĞRENCİ", "8-B")
            ),
            bookletCount = 4,
            personalizedFormsEnabled = true
        )

        assertEquals(original, ExamCodec.decode(ExamCodec.encode(original)))
        assertEquals(ExamStatus.READ, original.status)
    }

    @Test
    fun legacySchemaOneExamStillDecodesWithSafeDefaults() {
        val bytes = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(0x4F4D4558)
                out.writeInt(1)
                out.writeUTF("legacy-exam")
                out.writeUTF("Eski Sınav")
                out.writeUTF("Test Okulu")
                out.writeUTF(ActiveTemplateSource.DESIGNER_DOCUMENT.name)
                out.writeUTF("legacy-form")
                out.writeInt(1)
                out.writeUTF(WrongAnswerPolicy.KEEP_AS_IS.name)
                out.writeUTF("")
                out.writeLong(21000L)
                out.writeLong(10L)
                out.writeInt(0)
            }
        }.toByteArray()

        val decoded = ExamCodec.decode(bytes)

        assertEquals(1, decoded.bookletCount)
        assertFalse(decoded.personalizedFormsEnabled)
        assertEquals(emptyList<ExamParticipant>(), decoded.participants)
    }

    @Test
    fun waitingExamHasNoPapers() {
        val exam = ExamFactory.create(
            name = "LGS",
            schoolName = "Test Ortaokulu",
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

    @Test
    fun personalizedFormsRequireParticipants() {
        assertThrows(IllegalArgumentException::class.java) {
            ExamFactory.create(
                name = "Deneme",
                schoolName = "Okul",
                templateSelection = selection,
                examDateEpochDay = 21000L,
                personalizedFormsEnabled = true,
                id = "exam-personalized-empty",
                createdAtEpochMs = 1L
            )
        }
    }
}
