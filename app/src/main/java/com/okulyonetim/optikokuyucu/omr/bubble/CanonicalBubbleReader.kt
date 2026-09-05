package com.okulyonetim.optikokuyucu.omr.bubble

import com.okulyonetim.optikokuyucu.omr.geometry.CanonicalRegistration
import com.okulyonetim.optikokuyucu.omr.geometry.ImagePoint
import com.okulyonetim.optikokuyucu.omr.template.BubbleSpec
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import org.opencv.core.Mat
import kotlin.math.hypot

/**
 * Confidence-based classical-CV bubble reader.
 *
 * Preferred production path is readCanonical(): the complete sheet is first rectified into the
 * template's unitless canonical coordinate system, then every bubble is sampled at a stable ROI.
 * This makes bubble geometry independent of A4/A5, printer scale, margins and camera perspective.
 */
class CanonicalBubbleReader(
    private val template: OmrTemplate
) {
    /** Preferred path after CanonicalImageRectifier. */
    fun readCanonical(gray: Mat): BubbleReadResult {
        if (gray.empty() || gray.channels() != 1) return BubbleReadResult(emptyList())

        val rows = template.bubbleRows.map { row ->
            val scores = row.bubbles.associate { bubble ->
                bubble.id to scoreAt(
                    gray = gray,
                    center = ImagePoint(bubble.center.x, bubble.center.y),
                    radius = bubble.radius
                )
            }
            classifyRow(row.id, scores)
        }
        return BubbleReadResult(rows)
    }

    /** Compatibility path for unrectified images; live final-frame flow will migrate to canonical. */
    fun read(
        gray: Mat,
        registration: CanonicalRegistration
    ): BubbleReadResult {
        if (gray.empty() || gray.channels() != 1) return BubbleReadResult(emptyList())

        val rows = template.bubbleRows.map { row ->
            val scores = row.bubbles.associate { bubble ->
                bubble.id to scoreProjected(gray, bubble, registration)
            }
            classifyRow(row.id, scores)
        }
        return BubbleReadResult(rows)
    }

    private fun scoreProjected(
        gray: Mat,
        bubble: BubbleSpec,
        registration: CanonicalRegistration
    ): Double {
        val center = registration.templateToImage.mapTemplate(bubble.center) ?: return 0.0
        val pxX = registration.templateToImage.map(
            bubble.center.x + bubble.radius,
            bubble.center.y
        ) ?: return 0.0
        val pxY = registration.templateToImage.map(
            bubble.center.x,
            bubble.center.y + bubble.radius
        ) ?: return 0.0

        val rx = hypot(pxX.first - center.x, pxX.second - center.y)
        val ry = hypot(pxY.first - center.x, pxY.second - center.y)
        return scoreAt(gray, center, ((rx + ry) / 2.0).coerceAtLeast(2.0))
    }

    private fun scoreAt(
        gray: Mat,
        center: ImagePoint,
        radius: Double
    ): Double = BubbleInkScorer.score(gray, center, radius)

    private fun classifyRow(
        questionId: String,
        scores: Map<String, Double>
    ): QuestionRead {
        val sorted = scores.entries.sortedByDescending { it.value }
        val best = sorted.getOrNull(0)
        val second = sorted.getOrNull(1)
        if (best == null) {
            return QuestionRead(questionId, QuestionState.BLANK, null, 0.0, scores)
        }

        val bestScore = best.value
        val secondScore = second?.value ?: 0.0
        val gap = bestScore - secondScore
        val strongMarkCount = sorted.count { it.value >= STRONG_MARK_SCORE }

        return when {
            bestScore < MIN_MARK_SCORE ->
                QuestionRead(questionId, QuestionState.BLANK, null, 1.0 - bestScore, scores)

            // Two independently strong fills are a double mark even if glare/shadow makes one
            // visibly lighter than the other. Requiring nearly equal darkness caused real double
            // marks to collapse into a single answer when illumination was spatially uneven.
            strongMarkCount >= 2 ->
                QuestionRead(
                    questionId,
                    QuestionState.DOUBLE_MARK,
                    null,
                    (secondScore / STRONG_MARK_SCORE).coerceIn(0.0, 1.0),
                    scores
                )

            // A weaker second candidate is accepted as double only when it is close to the winner.
            // This preserves rejection of erase residue, print dirt and other weak secondary traces.
            secondScore >= DOUBLE_MARK_SCORE && gap < DOUBLE_GAP ->
                QuestionRead(
                    questionId,
                    QuestionState.DOUBLE_MARK,
                    null,
                    (1.0 - gap / DOUBLE_GAP).coerceIn(0.0, 1.0),
                    scores
                )

            gap >= CONFIDENT_GAP ->
                QuestionRead(
                    questionId,
                    QuestionState.MARKED,
                    best.key,
                    ((bestScore * 0.65) + (gap * 1.8 * 0.35)).coerceIn(0.0, 1.0),
                    scores
                )

            else ->
                QuestionRead(
                    questionId,
                    QuestionState.SUSPICIOUS,
                    best.key,
                    (bestScore * 0.55).coerceIn(0.0, 1.0),
                    scores
                )
        }
    }

    companion object {
        private const val MIN_MARK_SCORE = 0.12
        private const val STRONG_MARK_SCORE = 0.20
        private const val DOUBLE_MARK_SCORE = 0.11
        private const val DOUBLE_GAP = 0.055
        private const val CONFIDENT_GAP = 0.045
    }
}

enum class QuestionState {
    MARKED,
    BLANK,
    DOUBLE_MARK,
    SUSPICIOUS
}

data class QuestionRead(
    val questionId: String,
    val state: QuestionState,
    val selectedChoice: String?,
    /** Confidence that the reported state (including BLANK) is correct. */
    val confidence: Double,
    val choiceScores: Map<String, Double>
)

data class BubbleReadResult(
    val questions: List<QuestionRead>
) {
    val markedCount: Int get() = questions.count { it.state == QuestionState.MARKED }
    val blankCount: Int get() = questions.count { it.state == QuestionState.BLANK }
    val doubleMarkCount: Int get() = questions.count { it.state == QuestionState.DOUBLE_MARK }
    val suspiciousCount: Int get() = questions.count {
        it.state == QuestionState.SUSPICIOUS || it.state == QuestionState.DOUBLE_MARK
    }
}
