package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.scoring.ExamScore
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluation
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluationState
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import org.junit.Assert.assertEquals
import org.junit.Test

class ExamPaperPresentationTest {
    private val selection = ActiveTemplateSelection(
        source = ActiveTemplateSource.DESIGNER_DOCUMENT,
        templateId = "lgs",
        templateVersion = 1
    )

    @Test
    fun metadataEditorPreservesRawLinkIdentity() {
        val exam = Exam(
            id = "exam",
            name = "LGS",
            schoolName = "Okul",
            templateSelection = selection,
            examDateEpochDay = 1L,
            createdAtEpochMs = 1L,
            papers = listOf(
                ExamPaperLink(
                    scanRecordId = "scan-1",
                    studentNumber = "16",
                    bookletCode = "A",
                    linkedAtEpochMs = 77L
                )
            )
        )

        val updated = ExamPaperMetadataEditor.update(
            exam = exam,
            scanRecordId = "scan-1",
            studentName = " FATMA ZEHRA GÜRBÜZ ",
            className = " 8A ",
            studentNumber = " 16 ",
            bookletCode = " B "
        )
        val link = updated.papers.single()

        assertEquals("scan-1", link.scanRecordId)
        assertEquals(77L, link.linkedAtEpochMs)
        assertEquals("FATMA ZEHRA GÜRBÜZ", link.studentName)
        assertEquals("8A", link.className)
        assertEquals("16", link.studentNumber)
        assertEquals("B", link.bookletCode)
    }

    @Test
    fun fourWrongPolicyProducesReferenceStyleNet() {
        val evaluations = buildList {
            repeat(12) { index -> add(eval("q$index", QuestionEvaluationState.CORRECT, 1.0)) }
            repeat(8) { index -> add(eval("w$index", QuestionEvaluationState.WRONG, -0.25)) }
            repeat(3) { index -> add(eval("b$index", QuestionEvaluationState.BLANK, 0.0)) }
        }
        val metrics = ExamPaperMetrics.from(ExamScore(evaluations))

        assertEquals(12, metrics.correct)
        assertEquals(8, metrics.wrong)
        assertEquals(3, metrics.blank)
        assertEquals(10.0, metrics.net, 0.0001)
    }

    @Test
    fun lessonPrefixAndQuestionNumberAreStable() {
        assertEquals("turkce", questionLessonPrefix("turkce:20"))
        assertEquals("20", questionDisplayNumber("turkce:20"))
        assertEquals(null, questionLessonPrefix("7"))
        assertEquals("7", questionDisplayNumber("7"))
    }

    private fun eval(id: String, state: QuestionEvaluationState, points: Double) =
        QuestionEvaluation(
            questionId = id,
            state = state,
            expectedChoice = "A",
            selectedChoice = if (state == QuestionEvaluationState.BLANK) null else "A",
            recognitionConfidence = 1.0,
            points = points
        )
}
