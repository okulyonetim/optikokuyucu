package com.okulyonetim.optikokuyucu.omr.scoring

import com.okulyonetim.optikokuyucu.omr.bubble.BubbleReadResult
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionState

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
 */
object OmrScorer {
    fun score(
        read: BubbleReadResult,
        answerKey: AnswerKey,
        policy: ScoringPolicy = ScoringPolicy()
    ): ExamScore {
        val evaluations = read.questions.map { question ->
            val expected = answerKey.answers[question.questionId]
            if (expected == null) {
                return@map QuestionEvaluation(
                    questionId = question.questionId,
                    state = QuestionEvaluationState.NO_KEY,
                    expectedChoice = null,
                    selectedChoice = question.selectedChoice,
                    recognitionConfidence = question.confidence,
                    points = 0.0
                )
            }

            when (question.state) {
                QuestionState.MARKED -> {
                    val correct = question.selectedChoice == expected
                    QuestionEvaluation(
                        questionId = question.questionId,
                        state = if (correct) QuestionEvaluationState.CORRECT else QuestionEvaluationState.WRONG,
                        expectedChoice = expected,
                        selectedChoice = question.selectedChoice,
                        recognitionConfidence = question.confidence,
                        points = if (correct) policy.correctPoints else policy.wrongPoints
                    )
                }

                QuestionState.BLANK -> QuestionEvaluation(
                    questionId = question.questionId,
                    state = QuestionEvaluationState.BLANK,
                    expectedChoice = expected,
                    selectedChoice = null,
                    recognitionConfidence = question.confidence,
                    points = policy.blankPoints
                )

                QuestionState.DOUBLE_MARK -> QuestionEvaluation(
                    questionId = question.questionId,
                    state = QuestionEvaluationState.DOUBLE_MARK,
                    expectedChoice = expected,
                    selectedChoice = null,
                    recognitionConfidence = question.confidence,
                    points = policy.doubleMarkPoints
                )

                QuestionState.SUSPICIOUS -> QuestionEvaluation(
                    questionId = question.questionId,
                    state = QuestionEvaluationState.SUSPICIOUS,
                    expectedChoice = expected,
                    selectedChoice = question.selectedChoice,
                    recognitionConfidence = question.confidence,
                    points = policy.suspiciousPoints
                )
            }
        }
        return ExamScore(evaluations)
    }
}
