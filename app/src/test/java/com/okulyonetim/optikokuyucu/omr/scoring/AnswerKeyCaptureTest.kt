package com.okulyonetim.optikokuyucu.omr.scoring

import com.okulyonetim.optikokuyucu.omr.bubble.BubbleReadResult
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionRead
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerKeyCaptureTest {
    @Test
    fun `fully marked read becomes answer key`() {
        val result = AnswerKeyCapture.fromRead(
            templateId = "exam",
            templateVersion = 3,
            read = BubbleReadResult(
                listOf(
                    q("1", QuestionState.MARKED, "A"),
                    q("2", QuestionState.MARKED, "D")
                )
            )
        )

        assertTrue(result.successful)
        assertNotNull(result.answerKey)
        assertEquals(mapOf("1" to "A", "2" to "D"), result.answerKey?.answers)
        assertTrue(result.invalidQuestionIds.isEmpty())
    }

    @Test
    fun `double mark in answer key becomes two accepted choices`() {
        val result = AnswerKeyCapture.fromRead(
            templateId = "exam",
            templateVersion = 1,
            read = BubbleReadResult(
                listOf(
                    q(
                        id = "1",
                        state = QuestionState.DOUBLE_MARK,
                        choice = null,
                        scores = mapOf("A" to 0.31, "B" to 0.04, "C" to 0.27, "D" to 0.02)
                    )
                )
            )
        )

        assertTrue(result.successful)
        assertEquals("A|C", result.answerKey?.answers?.get("1"))
        assertTrue(result.invalidQuestionIds.isEmpty())
    }

    @Test
    fun `blank suspicious and ambiguous multi marks block answer key capture`() {
        val result = AnswerKeyCapture.fromRead(
            templateId = "exam",
            templateVersion = 1,
            read = BubbleReadResult(
                listOf(
                    q("1", QuestionState.MARKED, "A"),
                    q("2", QuestionState.BLANK, null),
                    q("3", QuestionState.SUSPICIOUS, "B"),
                    q(
                        id = "4",
                        state = QuestionState.DOUBLE_MARK,
                        choice = null,
                        scores = mapOf("A" to 0.30, "B" to 0.26, "C" to 0.22, "D" to 0.01)
                    )
                )
            )
        )

        assertFalse(result.successful)
        assertEquals(null, result.answerKey)
        assertEquals(listOf("2", "3", "4"), result.invalidQuestionIds)
    }

    private fun q(
        id: String,
        state: QuestionState,
        choice: String?,
        scores: Map<String, Double> = emptyMap()
    ): QuestionRead = QuestionRead(
        questionId = id,
        state = state,
        selectedChoice = choice,
        confidence = 0.9,
        choiceScores = scores
    )
}
