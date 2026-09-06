package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPdfLayout
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPhysicalTestPack
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPrintRenderer
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.NumericGridComponent
import com.okulyonetim.optikokuyucu.omr.designer.PdfPageProfile
import com.okulyonetim.optikokuyucu.omr.designer.QuestionGroupComponent
import com.okulyonetim.optikokuyucu.omr.designer.SingleChoiceComponent
import com.okulyonetim.optikokuyucu.omr.designer.TemplateReadabilityAnalyzer
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
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateDefaults
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import com.okulyonetim.optikokuyucu.omr.template.AnswerKeyTemplateTargetResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage15DeviceWorkflowReadinessTest {
    @Test
    fun `answer key target resolves selected historical template exactly`() {
        val document = printableDocument()
        val resolved = AnswerKeyTemplateTargetResolver.resolve(
            selection = selection(document),
            savedDocuments = listOf(document),
            starterDocuments = emptyList()
        )

        assertNotNull(resolved)
        assertEquals(document.id, resolved?.template?.id)
        assertEquals(document.version, resolved?.template?.version)
    }

    @Test
    fun `missing selected historical template never falls back to default`() {
        val missing = ActiveTemplateSelection(
            source = ActiveTemplateSource.DESIGNER_DOCUMENT,
            templateId = "missing-history",
            templateVersion = 9
        )

        val resolved = AnswerKeyTemplateTargetResolver.resolve(
            selection = missing,
            savedDocuments = emptyList(),
            starterDocuments = emptyList()
        )

        assertNull(resolved)
        assertEquals(
            ActiveOmrTemplateDefaults.template.id,
            ActiveOmrTemplateDefaults.selection.templateId
        )
    }

    @Test
    fun `physical Turkish designer source reaches linked student booklet and scored report`() {
        val document = printableDocument()
        val renderPlan = DesignerPrintRenderer.render(document)
        val template = DesignerTemplateCompiler.compile(document)
        val readability = TemplateReadabilityAnalyzer.analyze(document, template)
        val pdfTransform = DesignerPdfLayout.fit(template.space, PdfPageProfile.A4)

        assertTrue(readability.issues.joinToString { it.type.name }, readability.canSave)
        assertEquals(template, renderPlan.template)
        assertTrue(renderPlan.bubbles.isNotEmpty())
        assertTrue(pdfTransform.scale > 0.0)

        val number = document.components.filterIsInstance<NumericGridComponent>().single()
        val booklet = document.components.filterIsInstance<SingleChoiceComponent>().single()
        val answers = document.components.filterIsInstance<QuestionGroupComponent>().single()
        val questionId = DesignerTemplateCompiler.questionReadId(answers, answers.startQuestion)
        val expectedChoice = DesignerPhysicalTestPack.answerFor(answers.startQuestion)

        val record = ScanRecord(
            id = "stage15-scan",
            templateId = template.id,
            templateVersion = template.version,
            capturedAtEpochMs = 100L,
            source = ScanSource.LIVE_CAMERA,
            sourceWidth = 960,
            sourceHeight = 540,
            pageConfidence = 0.96,
            decisionConfidence = 0.94,
            elapsedMs = 11.0,
            answers = listOf(
                RecordedAnswer(
                    questionId = questionId,
                    state = RecordedAnswerState.MARKED,
                    selectedChoice = expectedChoice,
                    confidence = 0.96,
                    choiceScores = mapOf(expectedChoice to 0.42)
                )
            ),
            markGrids = listOf(
                grid(number.id, DesignerPhysicalTestPack.STUDENT_NUMBER),
                grid(booklet.id, DesignerPhysicalTestPack.BOOKLET_CODE)
            )
        )

        val repository = MemoryExamRepository(
            Exam(
                id = "stage15-exam",
                name = "Türkçe Uçtan Uca",
                schoolName = "Örnek Okul",
                templateSelection = selection(document),
                examDateEpochDay = 1L,
                createdAtEpochMs = 1L
            )
        )
        val linked = ExamPaperRegistrar(repository).register(
            examId = "stage15-exam",
            record = record,
            linkedAtEpochMs = 200L
        )
        val key = StoredAnswerKey(
            answerKey = AnswerKey(
                templateId = document.id,
                templateVersion = document.version,
                answers = mapOf(questionId to expectedChoice)
            ),
            variantGridId = booklet.id,
            variantValue = DesignerPhysicalTestPack.BOOKLET_CODE,
            createdAtEpochMs = 300L,
            source = AnswerKeySource.GALLERY
        )
        val row = ExamReportBuilder.build(linked, listOf(record), listOf(key)).rows.single()

        assertEquals(DesignerPhysicalTestPack.STUDENT_NUMBER, linked.papers.single().studentNumber)
        assertEquals(DesignerPhysicalTestPack.BOOKLET_CODE, linked.papers.single().bookletCode)
        assertEquals(ExamReportRowStatus.SCORED, row.status)
        assertEquals(1, row.correct)
        assertEquals(0, row.wrong)
    }

    private fun printableDocument(): DesignerDocument = DesignerPhysicalTestPack.document()

    private fun selection(document: DesignerDocument) = ActiveTemplateSelection(
        source = ActiveTemplateSource.DESIGNER_DOCUMENT,
        templateId = document.id,
        templateVersion = document.version
    )

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
