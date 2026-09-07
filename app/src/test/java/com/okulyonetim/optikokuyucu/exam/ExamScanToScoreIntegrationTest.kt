package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswer
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswerState
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkColumn
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkGrid
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkState
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.results.ScanSource
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKey
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeySource
import com.okulyonetim.optikokuyucu.omr.scoring.OmrScorer
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import com.okulyonetim.optikokuyucu.student.StudentGender
import com.okulyonetim.optikokuyucu.student.StudentImportSummary
import com.okulyonetim.optikokuyucu.student.StudentRosterEntry
import com.okulyonetim.optikokuyucu.student.StudentRosterRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class ExamScanToScoreIntegrationTest {
    @Test
    fun `designer camera scan resolves roster booklet answer key and score end to end`() {
        val selection = ActiveTemplateSelection(
            source = ActiveTemplateSource.DESIGNER_DOCUMENT,
            templateId = TEMPLATE_ID,
            templateVersion = TEMPLATE_VERSION
        )
        val exam = ExamFactory.create(
            name = "Deneme Sınavı",
            schoolName = "Test Okulu",
            templateSelection = selection,
            examDateEpochDay = 1L,
            id = "exam-1",
            createdAtEpochMs = 2L
        )
        val examRepository = InMemoryExamRepository(exam)
        val studentRepository = InMemoryStudentRepository(
            StudentRosterEntry(
                studentNumber = "16",
                fullName = "ALİ TEST",
                gender = StudentGender.BOY,
                gradeLevel = 5,
                branch = "A",
                guardianName = "VELİ TEST",
                guardianPhone = "05320000000",
                updatedAtEpochMs = 3L
            )
        )
        val record = cameraRecord(
            studentNumber = "000016",
            booklet = "B",
            answers = mapOf("1" to "B", "2" to "C")
        )

        val registered = ExamPaperRegistrar(examRepository, studentRepository).register(
            examId = exam.id,
            record = record,
            linkedAtEpochMs = 5L
        )
        val link = requireNotNull(registered.paperForScan(record.id))

        assertEquals("16", link.studentNumber)
        assertEquals("ALİ TEST", link.studentName)
        assertEquals("5-A", link.className)
        assertEquals("B", link.bookletCode)

        val keyA = variantKey("A", mapOf("1" to "A", "2" to "A"))
        val keyB = variantKey("B", mapOf("1" to "B", "2" to "C"))
        val resolvedKey = requireNotNull(
            ExamPaperResolution.answerKey(link, record, listOf(keyA, keyB))
        )
        val score = OmrScorer.score(record, resolvedKey.answerKey)

        assertEquals("B", resolvedKey.variantValue)
        assertEquals(2, score.correctCount)
        assertEquals(0, score.wrongCount)
        assertEquals(2.0, score.totalPoints, 0.0)

        val metadata = ExamPaperResolution.metadata(link, record)
        assertEquals("16", metadata.studentNumber)
        assertEquals("5-A", metadata.className)
        assertEquals("B", metadata.bookletCode)

        // Registration and scoring must not rewrite the immutable raw recognition result.
        assertEquals("000016", record.grid("number-1")?.value)
        assertEquals("B", record.grid("booklet-1")?.value)
    }

    private fun variantKey(variant: String, answers: Map<String, String>): StoredAnswerKey =
        StoredAnswerKey(
            answerKey = AnswerKey(TEMPLATE_ID, TEMPLATE_VERSION, answers),
            variantGridId = "booklet-1",
            variantValue = variant,
            createdAtEpochMs = 6L,
            source = AnswerKeySource.MANUAL
        )

    private fun cameraRecord(
        studentNumber: String,
        booklet: String,
        answers: Map<String, String>
    ): ScanRecord = ScanRecord(
        id = "scan-1",
        templateId = TEMPLATE_ID,
        templateVersion = TEMPLATE_VERSION,
        capturedAtEpochMs = 4L,
        source = ScanSource.LIVE_CAMERA,
        sourceWidth = 1000,
        sourceHeight = 1414,
        pageConfidence = 0.96,
        decisionConfidence = 0.92,
        elapsedMs = 12.0,
        answers = answers.map { (questionId, selectedChoice) ->
            RecordedAnswer(
                questionId = questionId,
                state = RecordedAnswerState.MARKED,
                selectedChoice = selectedChoice,
                confidence = 0.95,
                choiceScores = mapOf(selectedChoice to 0.9)
            )
        },
        markGrids = listOf(
            grid("number-1", studentNumber.map(Char::toString)),
            grid("booklet-1", listOf(booklet))
        )
    )

    private fun grid(id: String, values: List<String>): RecordedMarkGrid = RecordedMarkGrid(
        gridId = id,
        columns = values.mapIndexed { index, value ->
            RecordedMarkColumn(
                columnId = (index + 1).toString(),
                state = RecordedMarkState.MARKED,
                selectedValue = value,
                confidence = 0.95,
                scores = mapOf(value to 0.9)
            )
        }
    )

    private class InMemoryExamRepository(initial: Exam) : ExamRepository {
        private var value: Exam? = initial

        override fun save(exam: Exam) {
            value = exam
        }

        override fun load(id: String): Exam? = value?.takeIf { it.id == id }

        override fun list(): List<Exam> = listOfNotNull(value)

        override fun delete(id: String): Boolean {
            if (value?.id == id) value = null
            return true
        }
    }

    private class InMemoryStudentRepository(
        private var value: StudentRosterEntry
    ) : StudentRosterRepository {
        override fun save(entry: StudentRosterEntry) {
            value = entry.normalized()
        }

        override fun findByNumber(studentNumber: String): StudentRosterEntry? =
            value.takeIf {
                it.studentNumber == com.okulyonetim.optikokuyucu.student.StudentNumber.normalize(studentNumber)
            }

        override fun list(): List<StudentRosterEntry> = listOf(value)

        override fun upsertImported(entries: List<StudentRosterEntry>): StudentImportSummary =
            StudentImportSummary(0, 0, entries.size, entries.size)

        override fun delete(studentNumber: String): Boolean = true
    }

    private companion object {
        const val TEMPLATE_ID = "designer-form"
        const val TEMPLATE_VERSION = 1
    }
}
