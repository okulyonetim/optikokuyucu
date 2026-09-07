package com.okulyonetim.optikokuyucu.omr.live

import com.okulyonetim.optikokuyucu.omr.bubble.BubbleReadResult
import com.okulyonetim.optikokuyucu.omr.bubble.MarkScoreDecisionEngine
import com.okulyonetim.optikokuyucu.omr.bubble.MarkScoreState
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionRead
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionState
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkColumnRead
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkColumnState
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkGridRead
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkGridReadResult

/**
 * Robust temporal score fusion for the live-camera path.
 *
 * The previous live gate compared already-classified signatures only. A faint pencil mark near a
 * threshold can legitimately wobble between BLANK and MARKED even while the page itself is locked.
 * This class keeps a tiny rolling window of raw per-choice ink scores, takes the median score for
 * every bubble and only then runs the shared production decision engine. A one-frame glare, blur or
 * shadow therefore cannot dominate the final classification, while persistent light marks gain a
 * stable signal without lowering any OMR threshold.
 */
class LiveOmrTemporalFusion(
    private val windowSize: Int = 3
) {
    init {
        require(windowSize >= 3)
    }

    private val frames = ArrayDeque<ScoreFrame>()

    fun offer(
        bubbles: BubbleReadResult,
        markGrids: MarkGridReadResult
    ): FusedOmrRead? {
        val incoming = ScoreFrame.from(bubbles, markGrids)
        if (frames.isNotEmpty() && !frames.last().isCompatibleWith(incoming)) {
            reset()
        }

        frames.addLast(incoming)
        while (frames.size > windowSize) frames.removeFirst()
        if (frames.size < windowSize) return null

        val snapshot = frames.toList()
        val fusedQuestions = incoming.questions.mapIndexed { questionIndex, question ->
            val scores = question.scores.keys.associateWith { choiceId ->
                median(snapshot.map { it.questions[questionIndex].scores.getValue(choiceId) })
            }
            val decision = MarkScoreDecisionEngine.classify(scores)
            QuestionRead(
                questionId = question.id,
                state = when (decision.state) {
                    MarkScoreState.MARKED -> QuestionState.MARKED
                    MarkScoreState.BLANK -> QuestionState.BLANK
                    MarkScoreState.DOUBLE_MARK -> QuestionState.DOUBLE_MARK
                    MarkScoreState.SUSPICIOUS -> QuestionState.SUSPICIOUS
                },
                selectedChoice = decision.selectedKey,
                confidence = decision.confidence,
                choiceScores = decision.scores
            )
        }

        val fusedGrids = incoming.grids.mapIndexed { gridIndex, grid ->
            MarkGridRead(
                gridId = grid.id,
                columns = grid.columns.mapIndexed { columnIndex, column ->
                    val scores = column.scores.keys.associateWith { markId ->
                        median(
                            snapshot.map {
                                it.grids[gridIndex].columns[columnIndex].scores.getValue(markId)
                            }
                        )
                    }
                    val decision = MarkScoreDecisionEngine.classify(scores)
                    MarkColumnRead(
                        columnId = column.id,
                        state = when (decision.state) {
                            MarkScoreState.MARKED -> MarkColumnState.MARKED
                            MarkScoreState.BLANK -> MarkColumnState.BLANK
                            MarkScoreState.DOUBLE_MARK -> MarkColumnState.DOUBLE_MARK
                            MarkScoreState.SUSPICIOUS -> MarkColumnState.SUSPICIOUS
                        },
                        selectedValue = decision.selectedKey,
                        confidence = decision.confidence,
                        scores = decision.scores
                    )
                }
            )
        }

        val confidences = buildList {
            addAll(fusedQuestions.map { it.confidence })
            addAll(fusedGrids.flatMap { grid -> grid.columns.map { it.confidence } })
        }
        return FusedOmrRead(
            bubbleResult = BubbleReadResult(fusedQuestions),
            markGridResult = MarkGridReadResult(fusedGrids),
            decisionConfidence = if (confidences.isEmpty()) {
                0.0
            } else {
                confidences.average().coerceIn(0.0, 1.0)
            }
        )
    }

    fun reset() {
        frames.clear()
    }

    fun currentFrames(): Int = frames.size

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.map { it.coerceIn(0.0, 1.0) }.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    private data class ScoreFrame(
        val questions: List<QuestionScores>,
        val grids: List<GridScores>
    ) {
        fun isCompatibleWith(other: ScoreFrame): Boolean =
            questions.size == other.questions.size &&
                grids.size == other.grids.size &&
                questions.indices.all { index -> questions[index].isCompatibleWith(other.questions[index]) } &&
                grids.indices.all { index -> grids[index].isCompatibleWith(other.grids[index]) }

        companion object {
            fun from(bubbles: BubbleReadResult, markGrids: MarkGridReadResult): ScoreFrame =
                ScoreFrame(
                    questions = bubbles.questions.map {
                        QuestionScores(it.questionId, it.choiceScores)
                    },
                    grids = markGrids.grids.map { grid ->
                        GridScores(
                            id = grid.gridId,
                            columns = grid.columns.map { column ->
                                ColumnScores(column.columnId, column.scores)
                            }
                        )
                    }
                )
        }
    }

    private data class QuestionScores(
        val id: String,
        val scores: Map<String, Double>
    ) {
        fun isCompatibleWith(other: QuestionScores): Boolean =
            id == other.id && scores.keys == other.scores.keys
    }

    private data class GridScores(
        val id: String,
        val columns: List<ColumnScores>
    ) {
        fun isCompatibleWith(other: GridScores): Boolean =
            id == other.id &&
                columns.size == other.columns.size &&
                columns.indices.all { index -> columns[index].isCompatibleWith(other.columns[index]) }
    }

    private data class ColumnScores(
        val id: String,
        val scores: Map<String, Double>
    ) {
        fun isCompatibleWith(other: ColumnScores): Boolean =
            id == other.id && scores.keys == other.scores.keys
    }
}

data class FusedOmrRead(
    val bubbleResult: BubbleReadResult,
    val markGridResult: MarkGridReadResult,
    val decisionConfidence: Double
)
