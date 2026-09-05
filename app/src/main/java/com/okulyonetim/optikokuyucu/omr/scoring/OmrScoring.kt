package com.okulyonetim.optikokuyucu.omr.scoring

import com.okulyonetim.optikokuyucu.omr.bubble.BubbleReadResult
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionState
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswerState
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord

data class AnswerKey(
    val templateId: String,
    val templateVersion: Int,
    val answers: Map<String, String>
) {
    init {
        require(templateId.isNotBlank())
        require(templateVersion > 0)
        require(answers.isNotEmpty())
        require(answers.keys.all { it.isNotBlank() })
        require(answers.values.all { it.isNotBlank() })
    }
}

data class ScoringPolicy(
    val correctPoints: Double = 1.0,
    val wrongPoints: Double = 0.0,
    val blankPoints: Double = 0.0,
    val doubleMarkPoints: Double = 0.0,
    val suspiciousPoints: Double = 0.0
)

enum class QuestionEvaluationState {
    CORRECT,
    WRONG,
    BLANK,
    DOUBLE_MARK,
    SUSPICIOUS,
    NO_KEY
}

data class QuestionEvaluation(
    val questionId: String,
    val state: QuestionEvaluationState,
    val expectedChoice: String?,
    val selectedChoice: String?,
    val recognitionConfidence: Double,
    val points: Double
)

data class ExamScore(
    val evaluations: List<QuestionEvaluation>
) {
    val correctCount: Int get() = evaluations.count { it.state == QuestionEvaluationState.CORRECT }
    val wrongCount: Int get() = evaluations.count { it.state == QuestionEvaluationState.WRONG }
    val blankCount: Int get() = evaluations.count { it.state == QuestionEvaluationState.BLANK }
    val doubleMarkCount: Int get() = evaluations.count { it.state == QuestionEvaluationState.DOUBLE_MARK }
    val suspiciousCount: Int get() = evaluations.count { it.state == QuestionEvaluationState.SUSPICIOUS }
    val noKeyCount: Int get() = evaluations.count { it.state == QuestionEvaluationState.NO_KEY }
    val totalPoints: Double get() = evaluations.sumOf { it.points }
    val confidentlyEvaluated: Boolean
        get() = suspiciousCount == 0 && doubleMarkCount == 0 && noKeyCount == 0
}

/**
 * Pure scoring layer. Recognition uncertainty is preserved instead of being silently converted to
 * a wrong answer. The caller explicitly supplies point values, including any wrong-answer penalty.
 * Persisted raw records can be re-scored later without re-reading the physical form.
 */
object OmrScorer {
    fun score(
        read: BubbleReadResult,
        answerKey: AnswerKey,
        policy: ScoringPolicy = ScoringPolicy()
    ): ExamScore = ExamScore(
        read.questions.map { question ->
            evaluate(
                questionId = question.questionId,
                state = when (question.state) {
                    QuestionState.MARKED -> ScorableState.MARKED
                    QuestionState.BLANK -> ScorableState.BLANK
                    QuestionState.DOUBLE_MARK -> ScorableState.DOUBLE_MARK
                    QuestionState.SUSPICIOUS -> ScorableState.SUSPICIOUS
                },
                selectedChoice = question.selectedChoice,
                confidence = question.confidence,
                answerKey = answerKey,
                policy = policy
            )
        }
    )

    fun score(
        record: ScanRecord,
        answerKey: AnswerKey,
        policy: ScoringPolicy = ScoringPolicy()
    ): ExamScore {
        require(record.templateId == answerKey.templateId) {
            "Answer key belongs to a different template id."
        }
        require(record.templateVersion == answerKey.templateVersion) {
            "Answer key belongs to a different template version."
        }
        return ExamScore(
            record.answers.map { answer ->
                evaluate(
                    questionId = answer.questionId,
                    state = when (answer.state) {
                        RecordedAnswerState.MARKED -> ScorableState.MARKED
                        RecordedAnswerState.BLANK -> ScorableState.BLANK
                        RecordedAnswerState.DOUBLE_MARK -> ScorableState.DOUBLE_MARK
                        RecordedAnswerState.SUSPICIOUS -> ScorableState.SUSPICIOUS
                    },
                    selectedChoice = answer.selectedChoice,
                    confidence = answer.confidence,
                    answerKey = answerKey,
                    policy = policy
                )
            }
        )
    }

    private fun evaluate(
        questionId: String,
        state: ScorableState,
        selectedChoice: String?,
        confidence: Double,
        answerKey: AnswerKey,
        policy: ScoringPolicy
    ): QuestionEvaluation {
        val expected = answerKey.answers[questionId]
        if (expected == null) {
            return QuestionEvaluation(
                questionId = questionId,
                state = QuestionEvaluationState.NO_KEY,
                expectedChoice = null,
                selectedChoice = selectedChoice,
                recognitionConfidence = confidence,
                points = 0.0
            )
        }

        return when (state) {
            ScorableState.MARKED -> {
                val correct = selectedChoice == expected
                QuestionEvaluation(
                    questionId = questionId,
                    state = if (correct) QuestionEvaluationState.CORRECT else QuestionEvaluationState.WRONG,
                    expectedChoice = expected,
                    selectedChoice = selectedChoice,
                    recognitionConfidence = confidence,
                    points = if (correct) policy.correctPoints else policy.wrongPoints
                )
            }

            ScorableState.BLANK -> QuestionEvaluation(
                questionId = questionId,
                state = QuestionEvaluationState.BLANK,
                expectedChoice = expected,
                selectedChoice = null,
                recognitionConfidence = confidence,
                points = policy.blankPoints
            )

            ScorableState.DOUBLE_MARK -> QuestionEvaluation(
                questionId = questionId,
                state = QuestionEvaluationState.DOUBLE_MARK,
                expectedChoice = expected,
                selectedChoice = null,
                recognitionConfidence = confidence,
                points = policy.doubleMarkPoints
            )

            ScorableState.SUSPICIOUS -> QuestionEvaluation(
                questionId = questionId,
                state = QuestionEvaluationState.SUSPICIOUS,
                expectedChoice = expected,
                selectedChoice = selectedChoice,
                recognitionConfidence = confidence,
                points = policy.suspiciousPoints
            )
        }
    }

    private enum class ScorableState {
        MARKED,
        BLANK,
        DOUBLE_MARK,
        SUSPICIOUS
    }
}
