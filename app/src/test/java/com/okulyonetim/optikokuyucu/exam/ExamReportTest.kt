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
    fun `builder uses configured score while preserving net metadata and booklet resolution`() {
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
        assertEquals(ExamScoringType.NORMAL, report.scoringType)

        val first = report.rows[0]
        assertEquals("Ali; İmran", first.studentName)
        assertEquals("8-A", first.className)
        assertEquals("123", first.studentNumber)
        assertEquals("A", first.bookletCode)
        assertEquals(1, first.correct)
        assertEquals(1, first.wrong)
        assertEquals(1, first.blank)
        assertEquals(0.75, requireNotNull(first.net), 0.0001)
        assertEquals(25.0, requireNotNull(first.points), 0.0001)
        assertEquals(100.0, requireNotNull(first.maximumPoints), 0.0001)
        assertEquals(1, first.overallRank)
        assertEquals(1, first.classRank)
        assertEquals(ExamCalculatedScoreScope.SCALED, first.scoreScope)
        assertEquals(1, first.lessons.size)
        assertEquals("genel", first.lessons.single().lessonId)
        assertEquals(0.75, first.lessons.single().net, 0.0001)
        assertEquals(ExamReportRowStatus.SCORED, first.status)

        assertEquals(ExamReportRowStatus.NO_ANSWER_KEY, report.rows[1].status)
        assertEquals("456", report.rows[1].studentNumber)
        assertEquals(ExamReportRowStatus.SCAN_MISSING, report.rows[2].status)
    }

    @Test
    fun `report carries separate lesson score breakdown`() {
        val exam = basicExam("scan-a")
        val scan = record(
            id = "scan-a",
            booklet = "A",
            studentNumber = "16",
            answers = listOf(
                answer("turkce:1", RecordedAnswerState.MARKED, "A"),
                answer("matematik:1", RecordedAnswerState.MARKED, "B")
            )
        )
        val key = generalKey(
            linkedMapOf(
                "turkce:1" to "A",
                "matematik:1" to "C"
            )
        )

        val row = ExamReportBuilder.build(exam, listOf(scan), listOf(key)).rows.single()

        assertEquals(listOf("matematik", "turkce"), row.lessons.map { it.lessonId })
        assertEquals(0, row.lessons[0].correct)
        assertEquals(1, row.lessons[0].wrong)
        assertEquals(0.0, row.lessons[0].net, 0.0001)
        assertEquals(1, row.lessons[1].correct)
        assertEquals(1.0, row.lessons[1].net, 0.0001)
        assertTrue(examLessonDetailsText(row.lessons).contains("Matematik: D 0 Y 1 B 0 N 0,00"))
        assertTrue(examLessonDetailsText(row.lessons).contains("Türkçe: D 1 Y 0 B 0 N 1,00"))
    }

    @Test
    fun `report uses corrected booklet instead of raw booklet`() {
        val exam = basicExam("scan-a").copy(
            papers = listOf(
                ExamPaperLink(
                    scanRecordId = "scan-a",
                    bookletCode = "B",
                    linkedAtEpochMs = 2L
                )
            )
        )
        val scan = record(
            id = "scan-a",
            booklet = "A",
            studentNumber = "16",
            answers = listOf(answer("1", RecordedAnswerState.MARKED, "B"))
        )
        val keyA = StoredAnswerKey(
            answerKey = AnswerKey(selection.templateId, selection.templateVersion, mapOf("1" to "A")),
            variantGridId = "booklet",
            variantValue = "A",
            createdAtEpochMs = 5L,
            source = AnswerKeySource.MANUAL
        )
        val keyB = StoredAnswerKey(
            answerKey = AnswerKey(selection.templateId, selection.templateVersion, mapOf("1" to "B")),
            variantGridId = "booklet",
            variantValue = "B",
            createdAtEpochMs = 6L,
            source = AnswerKeySource.MANUAL
        )

        val row = ExamReportBuilder.build(exam, listOf(scan), listOf(keyA, keyB)).rows.single()

        assertEquals("B", row.bookletCode)
        assertEquals(1, row.correct)
        assertEquals(0, row.wrong)
        assertEquals(100.0, requireNotNull(row.points), 0.0001)
        assertEquals(ExamReportRowStatus.SCORED, row.status)
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
        assertEquals(0.0, requireNotNull(row.net), 0.0001)
        assertEquals(0.0, requireNotNull(row.points), 0.0001)
        assertEquals(null, row.overallRank)
    }

    @Test
    fun `scored rows receive overall and class ranking`() {
        val exam = basicExam("scan-a").copy(
            papers = listOf(
                ExamPaperLink("scan-a", studentName = "A", className = "8-A", linkedAtEpochMs = 2L),
                ExamPaperLink("scan-b", studentName = "B", className = "8-A", linkedAtEpochMs = 3L)
            )
        )
        val records = listOf(
            record("scan-a", "A", "1", listOf(answer("1", RecordedAnswerState.MARKED, "A"))),
            record("scan-b", "A", "2", listOf(answer("1", RecordedAnswerState.MARKED, "B")))
        )
        val rows = ExamReportBuilder.build(
            exam,
            records,
            listOf(generalKey(mapOf("1" to "A")))
        ).rows

        assertEquals(100.0, requireNotNull(rows[0].points), 0.0001)
        assertEquals(0.0, requireNotNull(rows[1].points), 0.0001)
        assertEquals(1, rows[0].overallRank)
        assertEquals(2, rows[1].overallRank)
        assertEquals(1, rows[0].classRank)
        assertEquals(2, rows[1].classRank)
    }

    @Test
    fun `csv is bom prefixed excel friendly and includes net score ranking and lessons`() {
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
        assertTrue(csv.contains("Ders Detayları"))
        assertTrue(csv.contains("\"Ali; İmran\""))
        assertTrue(csv.contains("123;A;"))
        assertTrue(csv.contains(";1;0;0;0;0;0;1,00;100,00;100,00;1;;PUANLANDI;Genel: D 1 Y 0 B 0 N 1,00;;scan-a"))
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
