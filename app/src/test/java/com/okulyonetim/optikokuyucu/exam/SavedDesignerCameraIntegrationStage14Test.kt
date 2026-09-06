package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.designer.DesignerAreaCatalog
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswer
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswerState
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkColumn
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkGrid
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkState
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.results.ScanSource
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKey
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeyResolver
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeySource
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import com.okulyonetim.optikokuyucu.omr.template.OmrRecognitionBindingsResolver
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SavedDesignerCameraIntegrationStage14Test {
    @Test
    fun `saved designer grid semantics flow from compiled template into exam and scoring`() {
        var document = DesignerDocument(
            id = "saved-camera-form",
            version = 4,
            name = "Kamera Formu"
        )
        val number = DesignerAreaCatalog.createNumberArea(document)
        document = document.copy(components = listOf(number))
        val booklet = DesignerAreaCatalog.createBookletArea(document)
        document = document.copy(components = document.components + booklet)
        val answers = DesignerAreaCatalog.createAnswerArea(document)
        document = document.copy(components = document.components + answers)

        val template = DesignerTemplateCompiler.compile(document)
        val bindings = OmrRecognitionBindingsResolver.fromTemplate(template)

        assertEquals(number.id, bindings.studentNumberGridId)
        assertEquals(booklet.id, bindings.bookletGridId)
        assertNotNull(template.markGrids.firstOrNull { it.id == number.id })
        assertNotNull(template.markGrids.firstOrNull { it.id == booklet.id })

        val questionId = DesignerTemplateCompiler.questionReadId(answers, answers.startQuestion)
        val record = ScanRecord(
            id = "scan-custom",
            templateId = template.id,
            templateVersion = template.version,
            capturedAtEpochMs = 10L,
            source = ScanSource.LIVE_CAMERA,
            sourceWidth = 960,
            sourceHeight = 540,
            pageConfidence = 0.95,
            decisionConfidence = 0.92,
            elapsedMs = 12.0,
            answers = listOf(
                RecordedAnswer(
                    questionId = questionId,
                    state = RecordedAnswerState.MARKED,
                    selectedChoice = "A",
                    confidence = 0.95,
                    choiceScores = mapOf("A" to 0.4)
                )
            ),
            markGrids = listOf(
                grid(number.id, "16"),
                grid(booklet.id, "B")
            )
        )

        val selection = ActiveTemplateSelection(
            source = ActiveTemplateSource.DESIGNER_DOCUMENT,
            templateId = document.id,
            templateVersion = document.version
        )
        val repository = MemoryExamRepository(
            Exam(
                id = "exam",
                name = "Deneme",
                schoolName = "Okul",
                templateSelection = selection,
                examDateEpochDay = 1L,
                createdAtEpochMs = 1L
            )
        )

        val linkedExam = ExamPaperRegistrar(repository).register("exam", record, linkedAtEpochMs = 20L)
        assertEquals("16", linkedExam.papers.single().studentNumber)
        assertEquals("B", linkedExam.papers.single().bookletCode)

        val key = StoredAnswerKey(
            answerKey = AnswerKey(
                templateId = document.id,
                templateVersion = document.version,
                answers = mapOf(questionId to "A")
            ),
            variantGridId = booklet.id,
            variantValue = "B",
            createdAtEpochMs = 30L,
            source = AnswerKeySource.GALLERY
        )
        assertEquals(key, AnswerKeyResolver.resolve(record, listOf(key)))

        val row = ExamReportBuilder.build(linkedExam, listOf(record), listOf(key)).rows.single()
        assertEquals("16", row.studentNumber)
        assertEquals("B", row.bookletCode)
        assertEquals(ExamReportRowStatus.SCORED, row.status)
        assertEquals(1, row.correct)
    }

    @Test
    fun `standard production template keeps legacy semantic grid ids`() {
        val bindings = OmrRecognitionBindingsResolver.fromTemplate(
            StandardOmrTemplate.SAMPLE_20_ABCD_STUDENT_6_BOOKLET_AB
        )

        assertEquals("studentNumber", bindings.studentNumberGridId)
        assertEquals("booklet", bindings.bookletGridId)
    }

    private fun grid(id: String, value: String): RecordedMarkGrid = RecordedMarkGrid(
        gridId = id,
        columns = value.mapIndexed { index, character ->
            RecordedMarkColumn(
                columnId = (index + 1).toString(),
                state = RecordedMarkState.MARKED,
                selectedValue = character.toString(),
                confidence = 0.95,
                scores = mapOf(character.toString() to 0.4)
            )
        }
    )

    private class MemoryExamRepository(initial: Exam) : ExamRepository {
        private var exam: Exam? = initial
        override fun save(exam: Exam) { this.exam = exam }
        override fun load(id: String): Exam? = exam?.takeIf { it.id == id }
        override fun list(): List<Exam> = listOfNotNull(exam)
        override fun delete(id: String): Boolean {
            if (exam?.id == id) exam = null
            return true
        }
    }
}
