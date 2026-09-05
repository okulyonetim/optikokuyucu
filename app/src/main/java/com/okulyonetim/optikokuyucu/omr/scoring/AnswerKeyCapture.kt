package com.okulyonetim.optikokuyucu.omr.scoring

import com.okulyonetim.optikokuyucu.omr.bubble.BubbleReadResult
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionState
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswerState
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord

data class AnswerKeyCaptureResult(
    val answerKey: AnswerKey?,
    val invalidQuestionIds: List<String>
) {
    val successful: Boolean get() = answerKey != null && invalidQuestionIds.isEmpty()
}

/** Converts recognized answer-key data into a key without accepting uncertain OMR decisions. */
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
        return success(templateId, templateVersion, answers)
    }

    fun fromRecord(record: ScanRecord): AnswerKeyCaptureResult {
        val invalid = record.answers
            .filter { it.state != RecordedAnswerState.MARKED || it.selectedChoice.isNullOrBlank() }
            .map { it.questionId }

        if (record.answers.isEmpty() || invalid.isNotEmpty()) {
            return AnswerKeyCaptureResult(
                answerKey = null,
                invalidQuestionIds = if (record.answers.isEmpty()) listOf("<empty>") else invalid
            )
        }

        val answers = record.answers.associate { answer ->
            answer.questionId to requireNotNull(answer.selectedChoice)
        }
        return success(record.templateId, record.templateVersion, answers)
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
}
