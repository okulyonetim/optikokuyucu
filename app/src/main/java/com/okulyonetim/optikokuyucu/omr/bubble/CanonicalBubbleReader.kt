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
        val decision = MarkScoreDecisionEngine.classify(scores)
        return QuestionRead(
            questionId = questionId,
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
