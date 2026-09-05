package com.okulyonetim.optikokuyucu.omr.scoring

import com.okulyonetim.optikokuyucu.omr.bubble.BubbleReadResult
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionState

data class AnswerKeyCaptureResult(
    val answerKey: AnswerKey?,
    val invalidQuestionIds: List<String>
) {
    val successful: Boolean get() = answerKey != null && invalidQuestionIds.isEmpty()
}

/** Converts a recognized answer-key sheet into a key without accepting uncertain OMR decisions. */
object AnswerKeyCapture {
    fun fromRead(
        templateId: String,
        templateVersion: Int,
        read: BubbleReadResult
    ): AnswerKeyCaptureResult {
        require(templateId.isNotBlank())
        require(templateVersion > 0)

        val invalid = read.questions
            .filter { it.state != QuestionState.MARKED || it.selectedChoice.isNullOrBlank() }
            .map { it.questionId }

        if (read.questions.isEmpty() || invalid.isNotEmpty()) {
            return AnswerKeyCaptureResult(
                answerKey = null,
                invalidQuestionIds = if (read.questions.isEmpty()) listOf("<empty>") else invalid
            )
        }

        val answers = read.questions.associate { question ->
            question.questionId to requireNotNull(question.selectedChoice)
        }
        return AnswerKeyCaptureResult(
            answerKey = AnswerKey(
                templateId = templateId,
                templateVersion = templateVersion,
                answers = answers
            ),
            invalidQuestionIds = emptyList()
        )
    }
}
