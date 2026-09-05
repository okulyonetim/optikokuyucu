package com.okulyonetim.optikokuyucu.omr.scoring

import com.okulyonetim.optikokuyucu.omr.bubble.BubbleReadResult
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionRead
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionState
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswer
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswerState
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.results.ScanSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OmrScoringTest {
    @Test
    fun `scoring preserves uncertain states and explicit penalty policy`() {
        val read = BubbleReadResult(
            listOf(
                q("1", QuestionState.MARKED, "A", 0.95),
                q("2", QuestionState.MARKED, "C", 0.91),
                q("3", QuestionState.BLANK, null, 0.99),
                q("4", QuestionState.DOUBLE_MARK, null, 0.87),
                q("5", QuestionState.SUSPICIOUS, "B", 0.52),
                q("6", QuestionState.MARKED, "D", 0.90)
            )
        )
        val key = AnswerKey(
            templateId = "test",
            templateVersion = 1,
            answers = mapOf(
                "1" to "A",
                "2" to "B",
                "3" to "C",
                "4" to "D",
                "5" to "B"
            )
        )
        val score = OmrScorer.score(
            read = read,
            answerKey = key,
            policy = ScoringPolicy(
                correctPoints = 4.0,
                wrongPoints = -1.0,
                blankPoints = 0.0,
                doubleMarkPoints = 0.0,
                suspiciousPoints = 0.0
            )
        )

        assertEquals(1, score.correctCount)
        assertEquals(1, score.wrongCount)
        assertEquals(1, score.blankCount)
        assertEquals(1, score.doubleMarkCount)
        assertEquals(1, score.suspiciousCount)
        assertEquals(1, score.noKeyCount)
        assertEquals(3.0, score.totalPoints, 0.001)
        assertFalse(score.confidentlyEvaluated)
        assertEquals(0.52, score.evaluations.first { it.questionId == "5" }.recognitionConfidence, 0.001)
    }

    @Test
    fun `default policy reports counts without inventing wrong penalty`() {
        val read = BubbleReadResult(
            listOf(
                q("1", QuestionState.MARKED, "A", 1.0),
                q("2", QuestionState.MARKED, "C", 1.0)
            )
        )
        val key = AnswerKey("test", 1, mapOf("1" to "A", "2" to "B"))

        val score = OmrScorer.score(read, key)

        assertEquals(1, score.correctCount)
        assertEquals(1, score.wrongCount)
        assertEquals(1.0, score.totalPoints, 0.001)
    }

    @Test
    fun `persisted record can be rescored with matching template key`() {
        val record = ScanRecord(
            id = "record",
            templateId = "exam",
            templateVersion = 2,
            capturedAtEpochMs = 1L,
            source = ScanSource.GALLERY,
            sourceWidth = 1000,
            sourceHeight = 1414,
            pageConfidence = null,
            decisionConfidence = null,
            elapsedMs = 20.0,
            answers = listOf(
                RecordedAnswer("1", RecordedAnswerState.MARKED, "C", 0.93, emptyMap()),
                RecordedAnswer("2", RecordedAnswerState.BLANK, null, 0.99, emptyMap())
            ),
            markGrids = emptyList()
        )
        val key = AnswerKey("exam", 2, mapOf("1" to "C", "2" to "A"))

        val score = OmrScorer.score(record, key)

        assertEquals(1, score.correctCount)
        assertEquals(1, score.blankCount)
        assertEquals(1.0, score.totalPoints, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `persisted record rejects answer key from different template version`() {
        val record = ScanRecord(
            id = "record",
            templateId = "exam",
            templateVersion = 2,
            capturedAtEpochMs = 1L,
            source = ScanSource.GALLERY,
            sourceWidth = 1000,
            sourceHeight = 1414,
            pageConfidence = null,
            decisionConfidence = null,
            elapsedMs = 20.0,
            answers = listOf(
                RecordedAnswer("1", RecordedAnswerState.MARKED, "A", 0.9, emptyMap())
            ),
            markGrids = emptyList()
        )

        OmrScorer.score(record, AnswerKey("exam", 3, mapOf("1" to "A")))
    }

    private fun q(
        id: String,
        state: QuestionState,
        choice: String?,
        confidence: Double
    ): QuestionRead = QuestionRead(
        questionId = id,
        state = state,
        selectedChoice = choice,
        confidence = confidence,
        choiceScores = emptyMap()
    )
}
