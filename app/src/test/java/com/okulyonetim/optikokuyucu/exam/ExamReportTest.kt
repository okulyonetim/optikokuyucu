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
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamReportTest {
    private val selection = ActiveTemplateSelection(
        source = ActiveTemplateSource.DESIGNER_DOCUMENT,
        templateId = "exam-template",
        templateVersion = 1
    )

    @Test
    fun `builder uses exam metadata fallbacks answer key variant and wrong penalty`() {
        val exam = Exam(
            id = "exam-1",
            name = "Deneme; 1",
            schoolName = "Koruk Ortaokulu",
            templateSelection = selection,
            wrongAnswerPolicy = WrongAnswerPolicy.FOUR_WRONG_ONE_CORRECT,
            examDateEpochDay = 1L,
            createdAtEpochMs = 1L,
            papers = listOf(
                ExamPaperLink(
                    scanRecordId = "scan-a",
                    studentName = "Ali; İmran",
                    className = "8-A",
                    linkedAtEpochMs = 10L
                ),
                ExamPaperLink(scanRecordId = "scan-b", linkedAtEpochMs = 11L),
                ExamPaperLink(scanRecordId = "missing", studentName = "Eksik", linkedAtEpochMs = 12L)
            )
        )
        val scanA = record(
            id = "scan-a",
            booklet = "A",
            studentNumber = "123",
            answers = listOf(
                answer("1", RecordedAnswerState.MARKED, "A"),
                answer("2", RecordedAnswerState.MARKED, "B"),
                answer("3", RecordedAnswerState.BLANK, null)
            )
        )
        val scanB = record(
            id = "scan-b",
            booklet = "B",
            studentNumber = "456",
            answers = listOf(answer("1", RecordedAnswerState.MARKED, "A"))
        )
        val keyA = StoredAnswerKey(
            answerKey = AnswerKey(
                templateId = selection.templateId,
                templateVersion = selection.templateVersion,
                answers = linkedMapOf("1" to "A", "2" to "C", "3" to "D")
            ),
            variantGridId = "booklet",
            variantValue = "A",
            createdAtEpochMs = 5L,
            source = AnswerKeySource.SCAN_RECORD,
            sourceRecordId = "key-a"
        )

        val report = ExamReportBuilder.build(
            exam = exam,
            records = listOf(scanA, scanB),
            answerKeys = listOf(keyA),
            generatedAtEpochMs = 99L
        )

        assertEquals(3, report.paperCount)
        assertEquals(1, report.scoredCount)
        assertEquals(1, report.noAnswerKeyCount)
        assertEquals(1, report.missingScanCount)
        assertEquals(0, report.reviewRequiredCount)

        val first = report.rows[0]
        assertEquals("Ali; İmran", first.studentName)
        assertEquals("8-A", first.className)
        assertEquals("123", first.studentNumber)
        assertEquals("A", first.bookletCode)
        assertEquals(1, first.correct)
        assertEquals(1, first.wrong)
        assertEquals(1, first.blank)
        assertEquals(0.75, requireNotNull(first.points), 0.0001)
        assertEquals(3.0, requireNotNull(first.maximumPoints), 0.0001)
        assertEquals(ExamReportRowStatus.SCORED, first.status)

        assertEquals(ExamReportRowStatus.NO_ANSWER_KEY, report.rows[1].status)
        assertEquals("456", report.rows[1].studentNumber)
        assertEquals(ExamReportRowStatus.SCAN_MISSING, report.rows[2].status)
    }

    @Test
    fun `review required is preserved instead of silently scoring uncertain read`() {
        val exam = basicExam("scan-a")
        val record = record(
            id = "scan-a",
            booklet = "A",
            studentNumber = "1",
            answers = listOf(answer("1", RecordedAnswerState.SUSPICIOUS, "A"))
        )
        val key = generalKey(mapOf("1" to "A"))

        val row = ExamReportBuilder.build(exam, listOf(record), listOf(key)).rows.single()

        assertEquals(ExamReportRowStatus.REVIEW_REQUIRED, row.status)
        assertEquals(1, row.suspicious)
        assertEquals(0.0, requireNotNull(row.points), 0.0001)
    }

    @Test
    fun `csv is bom prefixed excel friendly and quotes delimiters`() {
        val report = ExamReportBuilder.build(
            exam = basicExam("scan-a", studentName = "Ali; İmran"),
            records = listOf(
                record(
                    id = "scan-a",
                    booklet = "A",
                    studentNumber = "123",
                    answers = listOf(answer("1", RecordedAnswerState.MARKED, "A"))
                )
            ),
            answerKeys = listOf(generalKey(mapOf("1" to "A"))),
            generatedAtEpochMs = 99L
        )

        val csv = ExamReportCsvExporter.export(report)

        assertTrue(csv.startsWith("\uFEFFSıra;Öğrenci;"))
        assertTrue(csv.contains("\"Ali; İmran\""))
        assertTrue(csv.contains("123;A;"))
        assertTrue(csv.contains(";1;0;0;0;0;0;1,00;1,00;PUANLANDI;scan-a"))
    }

    private fun basicExam(scanId: String, studentName: String = ""): Exam = Exam(
        id = "exam-1",
        name = "Deneme",
        schoolName = "Okul",
        templateSelection = selection,
        examDateEpochDay = 1L,
        createdAtEpochMs = 1L,
        papers = listOf(
            ExamPaperLink(
                scanRecordId = scanId,
                studentName = studentName,
                linkedAtEpochMs = 2L
            )
        )
    )

    private fun generalKey(answers: Map<String, String>): StoredAnswerKey = StoredAnswerKey(
        answerKey = AnswerKey(
            templateId = selection.templateId,
            templateVersion = selection.templateVersion,
            answers = answers
        ),
        createdAtEpochMs = 5L,
        source = AnswerKeySource.SCAN_RECORD,
        sourceRecordId = "key"
    )

    private fun record(
        id: String,
        booklet: String,
        studentNumber: String,
        answers: List<RecordedAnswer>
    ): ScanRecord = ScanRecord(
        id = id,
        templateId = selection.templateId,
        templateVersion = selection.templateVersion,
        capturedAtEpochMs = 1_700_000_000_000L,
        source = ScanSource.LIVE_CAMERA,
        sourceWidth = 1000,
        sourceHeight = 1414,
        pageConfidence = 0.95,
        decisionConfidence = 0.90,
        elapsedMs = 12.0,
        answers = answers,
        markGrids = listOf(
            grid("studentNumber", studentNumber.map(Char::toString)),
            grid("booklet", listOf(booklet))
        )
    )

    private fun answer(
        id: String,
        state: RecordedAnswerState,
        choice: String?
    ): RecordedAnswer = RecordedAnswer(
        questionId = id,
        state = state,
        selectedChoice = choice,
        confidence = 0.9,
        choiceScores = choice?.let { mapOf(it to 0.5) }.orEmpty()
    )

    private fun grid(id: String, values: List<String>): RecordedMarkGrid = RecordedMarkGrid(
        gridId = id,
        columns = values.mapIndexed { index, value ->
            RecordedMarkColumn(
                columnId = (index + 1).toString(),
                state = RecordedMarkState.MARKED,
                selectedValue = value,
                confidence = 0.9,
                scores = mapOf(value to 0.5)
            )
        }
    )
}
