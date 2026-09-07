package com.okulyonetim.optikokuyucu.omr.scoring

import com.okulyonetim.optikokuyucu.omr.bubble.BubbleReadResult
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionRead
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionState
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswer
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswerState
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord

data class AnswerKeyCaptureResult(
    val answerKey: AnswerKey?,
    val invalidQuestionIds: List<String>
) {
    val successful: Boolean get() = answerKey != null && invalidQuestionIds.isEmpty()
}

/**
 * Converts recognized answer-key data into a key.
 *
 * A normal single mark becomes one accepted choice. A genuine DOUBLE_MARK is valid for an answer
 * key and becomes two accepted choices (for example A|C). Blank, suspicious and ambiguous
 * three-or-more-mark rows are still rejected.
 */
object AnswerKeyCapture {
    fun fromRead(
        templateId: String,
        templateVersion: Int,
        read: BubbleReadResult
    ): AnswerKeyCaptureResult {
        require(templateId.isNotBlank())
        require(templateVersion > 0)

        val captured = read.questions.map { question ->
            question.questionId to captureChoices(question)
        }
        val invalid = captured.filter { it.second == null }.map { it.first }

        if (read.questions.isEmpty() || invalid.isNotEmpty()) {
            return AnswerKeyCaptureResult(
                answerKey = null,
                invalidQuestionIds = if (read.questions.isEmpty()) listOf("<empty>") else invalid
            )
        }

        val answers = captured.associate { (questionId, choices) ->
            questionId to AnswerKeyChoiceCodec.encode(requireNotNull(choices))
        }
        return success(templateId, templateVersion, answers)
    }

    fun fromRecord(record: ScanRecord): AnswerKeyCaptureResult {
        val captured = record.answers.map { answer ->
            answer.questionId to captureChoices(answer)
        }
        val invalid = captured.filter { it.second == null }.map { it.first }

        if (record.answers.isEmpty() || invalid.isNotEmpty()) {
            return AnswerKeyCaptureResult(
                answerKey = null,
                invalidQuestionIds = if (record.answers.isEmpty()) listOf("<empty>") else invalid
            )
        }

        val answers = captured.associate { (questionId, choices) ->
            questionId to AnswerKeyChoiceCodec.encode(requireNotNull(choices))
        }
        return success(record.templateId, record.templateVersion, answers)
    }

    private fun captureChoices(question: QuestionRead): List<String>? = when (question.state) {
        QuestionState.MARKED -> question.selectedChoice?.takeIf { it.isNotBlank() }?.let(::listOf)
        QuestionState.DOUBLE_MARK -> doubleChoices(question.choiceScores)
        QuestionState.BLANK,
        QuestionState.SUSPICIOUS -> null
    }

    private fun captureChoices(answer: RecordedAnswer): List<String>? = when (answer.state) {
        RecordedAnswerState.MARKED -> answer.selectedChoice?.takeIf { it.isNotBlank() }?.let(::listOf)
        RecordedAnswerState.DOUBLE_MARK -> doubleChoices(answer.choiceScores)
        RecordedAnswerState.BLANK,
        RecordedAnswerState.SUSPICIOUS -> null
    }

    private fun doubleChoices(scores: Map<String, Double>): List<String>? {
        val candidates = scores.entries
            .filter { it.key.isNotBlank() && it.value >= DOUBLE_KEY_MIN_SCORE }
            .sortedByDescending { it.value }
            .map { it.key }
            .distinct()
        return candidates.takeIf { it.size == 2 }
    }

    private fun success(
        templateId: String,
        templateVersion: Int,
        answers: Map<String, String>
    ): AnswerKeyCaptureResult = AnswerKeyCaptureResult(
        answerKey = AnswerKey(
            templateId = templateId,
            templateVersion = templateVersion,
            answers = answers
        ),
        invalidQuestionIds = emptyList()
    )

    private const val DOUBLE_KEY_MIN_SCORE = 0.11
}
