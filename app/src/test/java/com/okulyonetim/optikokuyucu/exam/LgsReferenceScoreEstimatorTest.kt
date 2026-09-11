package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.scoring.ExamScore
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluation
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LgsReferenceScoreEstimatorTest {
    @Test
    fun `single student receives calibrated estimated LGS score`() {
        val result = LgsReferenceScoreEstimator.calculate(
            listOf(structuredPaper("single", allCorrect = false))
        ).getValue("single")

        assertEquals(187.8131, requireNotNull(result.calculatedScore), 0.0001)
        assertEquals(500.0, requireNotNull(result.maximumScore), 0.0001)
        assertTrue(result.note.contains("Tahmini 2026 LGS"))
        assertTrue(result.note.contains("Resmî MEB sonucu değildir"))
    }

    @Test
    fun `full score reaches 500 without a cohort`() {
        val result = LgsReferenceScoreEstimator.calculate(
            listOf(structuredPaper("full", allCorrect = true))
        ).getValue("full")

        assertEquals(90.0, result.net, 0.0001)
        assertEquals(500.0, requireNotNull(result.calculatedScore), 0.0001)
        assertEquals(
            listOf("turkce", "matematik", "fen", "inkilap", "din", "yabanci"),
            result.lessons.map { it.lessonId }
        )
    }

    @Test
    fun `three wrong answers reduce one net before reference scoring`() {
        val evaluations = buildList {
            addLesson("answers-1", 20, correct = 0, wrong = 3)
            addLesson("answers-2", 10, correct = 0, wrong = 0)
            addLesson("answers-3", 10, correct = 0, wrong = 0)
            addLesson("answers-4", 10, correct = 0, wrong = 0)
            addLesson("answers-5", 20, correct = 0, wrong = 0)
            addLesson("answers-6", 20, correct = 0, wrong = 0)
        }
        val result = LgsReferenceScoreEstimator.calculate(
            listOf(ExamPaperScoreInput("paper", ExamScore(evaluations)))
        ).getValue("paper")

        assertEquals(-1.0, result.lessons.first { it.lessonId == "turkce" }.net, 0.0001)
        assertEquals(183.6311, requireNotNull(result.calculatedScore), 0.0001)
    }

    @Test
    fun `ayse miran sample matches observed 2026 calculator result`() {
        val evaluations = buildList {
            addLesson("answers-1", 20, correct = 2, wrong = 3)
            addLesson("answers-2", 10, correct = 0, wrong = 3)
            addLesson("answers-3", 10, correct = 0, wrong = 1)
            addLesson("answers-4", 10, correct = 0, wrong = 2)
            addLesson("answers-5", 20, correct = 1, wrong = 1)
            addLesson("answers-6", 20, correct = 2, wrong = 1)
        }
        val result = LgsReferenceScoreEstimator.calculate(
            listOf(ExamPaperScoreInput("ayse", ExamScore(evaluations)))
        ).getValue("ayse")

        assertEquals(1.3333333333, result.net, 0.0001)
        assertEquals(198.3064, requireNotNull(result.calculatedScore), 0.0005)
    }

    private fun structuredPaper(id: String, allCorrect: Boolean): ExamPaperScoreInput {
        val evaluations = buildList {
            addLesson("answers-1", 20, correct = if (allCorrect) 20 else 0, wrong = 0)
            addLesson("answers-2", 10, correct = if (allCorrect) 10 else 0, wrong = 0)
            addLesson("answers-3", 10, correct = if (allCorrect) 10 else 0, wrong = 0)
            addLesson("answers-4", 10, correct = if (allCorrect) 10 else 0, wrong = 0)
            addLesson("answers-5", 20, correct = if (allCorrect) 20 else 0, wrong = 0)
            addLesson("answers-6", 20, correct = if (allCorrect) 20 else 0, wrong = 0)
        }
        return ExamPaperScoreInput(id, ExamScore(evaluations))
    }

    private fun MutableList<QuestionEvaluation>.addLesson(
        prefix: String,
        count: Int,
        correct: Int,
        wrong: Int
    ) {
        repeat(count) { index ->
            val state = when {
                index < correct -> QuestionEvaluationState.CORRECT
                index < correct + wrong -> QuestionEvaluationState.WRONG
                else -> QuestionEvaluationState.BLANK
            }
            add(
                QuestionEvaluation(
                    questionId = "$prefix:${index + 1}",
                    state = state,
                    expectedChoice = "A",
                    selectedChoice = when (state) {
                        QuestionEvaluationState.BLANK -> null
                        QuestionEvaluationState.WRONG -> "B"
                        else -> "A"
                    },
                    recognitionConfidence = 1.0,
                    points = 0.0
                )
            )
        }
    }
}
