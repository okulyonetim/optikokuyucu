package com.okulyonetim.optikokuyucu.omr.markgrid

import com.okulyonetim.optikokuyucu.omr.bubble.BubbleInkScorer
import com.okulyonetim.optikokuyucu.omr.geometry.ImagePoint
import com.okulyonetim.optikokuyucu.omr.template.MarkGridSpec
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import org.opencv.core.Mat

enum class MarkColumnState {
    MARKED,
    BLANK,
    DOUBLE_MARK,
    SUSPICIOUS
}

data class MarkDecision(
    val state: MarkColumnState,
    val selectedValue: String?,
    val confidence: Double,
    val scores: Map<String, Double>
)

/** Pure score decision layer so thresholds are unit-testable without camera/OpenCV input. */
object MarkGridDecisionEngine {
    fun classify(scores: Map<String, Double>): MarkDecision {
        val sorted = scores.entries.sortedByDescending { it.value }
        val best = sorted.getOrNull(0)
        val second = sorted.getOrNull(1)
        if (best == null) {
            return MarkDecision(MarkColumnState.BLANK, null, 0.0, scores)
        }

        val bestScore = best.value
        val secondScore = second?.value ?: 0.0
        val gap = bestScore - secondScore
        val strongMarkCount = sorted.count { it.value >= STRONG_MARK_SCORE }

        return when {
            bestScore < MIN_MARK_SCORE ->
                MarkDecision(
                    state = MarkColumnState.BLANK,
                    selectedValue = null,
                    confidence = (1.0 - bestScore).coerceIn(0.0, 1.0),
                    scores = scores
                )

            strongMarkCount >= 2 ->
                MarkDecision(
                    state = MarkColumnState.DOUBLE_MARK,
                    selectedValue = null,
                    confidence = (secondScore / STRONG_MARK_SCORE).coerceIn(0.0, 1.0),
                    scores = scores
                )

            secondScore >= DOUBLE_MARK_SCORE && gap < DOUBLE_GAP ->
                MarkDecision(
                    state = MarkColumnState.DOUBLE_MARK,
                    selectedValue = null,
                    confidence = (1.0 - gap / DOUBLE_GAP).coerceIn(0.0, 1.0),
                    scores = scores
                )

            gap >= CONFIDENT_GAP ->
                MarkDecision(
                    state = MarkColumnState.MARKED,
                    selectedValue = best.key,
                    confidence = ((bestScore * 0.65) + (gap * 1.8 * 0.35)).coerceIn(0.0, 1.0),
                    scores = scores
                )

            else ->
                MarkDecision(
                    state = MarkColumnState.SUSPICIOUS,
                    selectedValue = best.key,
                    confidence = (bestScore * 0.55).coerceIn(0.0, 1.0),
                    scores = scores
                )
        }
    }

    private const val MIN_MARK_SCORE = 0.12
    private const val STRONG_MARK_SCORE = 0.20
    private const val DOUBLE_MARK_SCORE = 0.11
    private const val DOUBLE_GAP = 0.055
    private const val CONFIDENT_GAP = 0.045
}

data class MarkColumnRead(
    val columnId: String,
    val state: MarkColumnState,
    val selectedValue: String?,
    val confidence: Double,
    val scores: Map<String, Double>
)

data class MarkGridRead(
    val gridId: String,
    val columns: List<MarkColumnRead>
) {
    /** Complete value is intentionally withheld if any column is uncertain. */
    val value: String?
        get() = if (columns.isNotEmpty() && columns.all { it.state == MarkColumnState.MARKED }) {
            columns.joinToString(separator = "") { requireNotNull(it.selectedValue) }
        } else {
            null
        }

    val suspiciousCount: Int
        get() = columns.count {
            it.state == MarkColumnState.SUSPICIOUS || it.state == MarkColumnState.DOUBLE_MARK
        }

    val blankCount: Int get() = columns.count { it.state == MarkColumnState.BLANK }
}

data class MarkGridReadResult(
    val grids: List<MarkGridRead>
) {
    fun grid(id: String): MarkGridRead? = grids.firstOrNull { it.gridId == id }

    companion object {
        val Empty = MarkGridReadResult(emptyList())
    }
}

/**
 * Reads generic mark grids (student number, booklet code, school number, etc.) after the form has
 * already been rectified into canonical template coordinates.
 */
class CanonicalMarkGridReader(
    private val template: OmrTemplate
) {
    fun readCanonical(gray: Mat): MarkGridReadResult {
        if (gray.empty() || gray.channels() != 1) return MarkGridReadResult.Empty

        return MarkGridReadResult(
            template.markGrids.map { grid -> readGrid(gray, grid) }
        )
    }

    private fun readGrid(gray: Mat, grid: MarkGridSpec): MarkGridRead {
        val columns = grid.columns.map { column ->
            val scores = column.marks.associate { mark ->
                mark.id to BubbleInkScorer.score(
                    gray = gray,
                    center = ImagePoint(mark.center.x, mark.center.y),
                    radius = mark.radius
                )
            }
            val decision = MarkGridDecisionEngine.classify(scores)
            MarkColumnRead(
                columnId = column.id,
                state = decision.state,
                selectedValue = decision.selectedValue,
                confidence = decision.confidence,
                scores = decision.scores
            )
        }
        return MarkGridRead(grid.id, columns)
    }
}
