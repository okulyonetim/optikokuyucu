package com.okulyonetim.optikokuyucu.omr.results

import com.okulyonetim.optikokuyucu.omr.bubble.BubbleReadResult
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionState
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkColumnState
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkGridReadResult
import java.util.UUID

enum class ScanSource {
    LIVE_CAMERA,
    GALLERY
}

enum class RecordedAnswerState {
    MARKED,
    BLANK,
    DOUBLE_MARK,
    SUSPICIOUS
}

data class RecordedAnswer(
    val questionId: String,
    val state: RecordedAnswerState,
    val selectedChoice: String?,
    val confidence: Double,
    val choiceScores: Map<String, Double>
)

enum class RecordedMarkState {
    MARKED,
    BLANK,
    DOUBLE_MARK,
    SUSPICIOUS
}

data class RecordedMarkColumn(
    val columnId: String,
    val state: RecordedMarkState,
    val selectedValue: String?,
    val confidence: Double,
    val scores: Map<String, Double>
)

data class RecordedMarkGrid(
    val gridId: String,
    val columns: List<RecordedMarkColumn>
) {
    val value: String?
        get() = if (columns.isNotEmpty() && columns.all { it.state == RecordedMarkState.MARKED }) {
            columns.joinToString(separator = "") { requireNotNull(it.selectedValue) }
        } else {
            null
        }
}

/**
 * Immutable raw recognition record. Scoring is intentionally not embedded: the same captured OMR
 * result can be re-evaluated later with a corrected answer key or scoring policy without losing
 * the original recognition states/confidences.
 */
data class ScanRecord(
    val id: String,
    val templateId: String,
    val templateVersion: Int,
    val capturedAtEpochMs: Long,
    val source: ScanSource,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val pageConfidence: Double?,
    val decisionConfidence: Double?,
    val elapsedMs: Double,
    val answers: List<RecordedAnswer>,
    val markGrids: List<RecordedMarkGrid>
) {
    init {
        require(id.isNotBlank())
        require(templateId.isNotBlank())
        require(templateVersion > 0)
        require(capturedAtEpochMs >= 0L)
        require(sourceWidth >= 0 && sourceHeight >= 0)
        require(elapsedMs >= 0.0)
    }

    fun grid(id: String): RecordedMarkGrid? = markGrids.firstOrNull { it.gridId == id }
}

object ScanRecordFactory {
    fun fromRecognition(
        templateId: String,
        templateVersion: Int,
        source: ScanSource,
        sourceWidth: Int,
        sourceHeight: Int,
        elapsedMs: Double,
        bubbleResult: BubbleReadResult,
        markGridResult: MarkGridReadResult,
        pageConfidence: Double? = null,
        decisionConfidence: Double? = null,
        id: String = UUID.randomUUID().toString(),
        capturedAtEpochMs: Long = System.currentTimeMillis()
    ): ScanRecord = ScanRecord(
        id = id,
        templateId = templateId,
        templateVersion = templateVersion,
        capturedAtEpochMs = capturedAtEpochMs,
        source = source,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        pageConfidence = pageConfidence,
        decisionConfidence = decisionConfidence,
        elapsedMs = elapsedMs,
        answers = bubbleResult.questions.map { question ->
            RecordedAnswer(
                questionId = question.questionId,
                state = when (question.state) {
                    QuestionState.MARKED -> RecordedAnswerState.MARKED
                    QuestionState.BLANK -> RecordedAnswerState.BLANK
                    QuestionState.DOUBLE_MARK -> RecordedAnswerState.DOUBLE_MARK
                    QuestionState.SUSPICIOUS -> RecordedAnswerState.SUSPICIOUS
                },
                selectedChoice = question.selectedChoice,
                confidence = question.confidence,
                choiceScores = question.choiceScores.toMap()
            )
        },
        markGrids = markGridResult.grids.map { grid ->
            RecordedMarkGrid(
                gridId = grid.gridId,
                columns = grid.columns.map { column ->
                    RecordedMarkColumn(
                        columnId = column.columnId,
                        state = when (column.state) {
                            MarkColumnState.MARKED -> RecordedMarkState.MARKED
                            MarkColumnState.BLANK -> RecordedMarkState.BLANK
                            MarkColumnState.DOUBLE_MARK -> RecordedMarkState.DOUBLE_MARK
                            MarkColumnState.SUSPICIOUS -> RecordedMarkState.SUSPICIOUS
                        },
                        selectedValue = column.selectedValue,
                        confidence = column.confidence,
                        scores = column.scores.toMap()
                    )
                }
            )
        }
    )
}
