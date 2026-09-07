package com.okulyonetim.optikokuyucu.omr.markgrid

import com.okulyonetim.optikokuyucu.omr.bubble.BubbleInkScorer
import com.okulyonetim.optikokuyucu.omr.bubble.MarkScoreDecisionEngine
import com.okulyonetim.optikokuyucu.omr.bubble.MarkScoreState
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
        val decision = MarkScoreDecisionEngine.classify(scores)
        return MarkDecision(
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
