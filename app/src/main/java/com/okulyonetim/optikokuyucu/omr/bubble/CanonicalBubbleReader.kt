package com.okulyonetim.optikokuyucu.omr.bubble

import com.okulyonetim.optikokuyucu.omr.geometry.CanonicalRegistration
import com.okulyonetim.optikokuyucu.omr.template.BubbleSpec
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import org.opencv.core.Mat
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * First deterministic bubble reader used for gallery/synthetic tests.
 *
 * It deliberately avoids a single global gray threshold. Each bubble is scored by comparing the
 * central fill region with a local surrounding ring. This is only the first classical-CV stage;
 * real pencil/pen tuning will be added after the gallery loop is proven.
 */
class CanonicalBubbleReader(
    private val template: OmrTemplate
) {
    fun read(
        gray: Mat,
        registration: CanonicalRegistration
    ): BubbleReadResult {
        if (gray.empty() || gray.channels() != 1) {
            return BubbleReadResult(emptyList())
        }

        val rows = template.bubbleRows.map { row ->
            val scores = row.bubbles.associate { bubble ->
                bubble.id to scoreBubble(gray, bubble, registration)
            }
            classifyRow(row.id, scores)
        }
        return BubbleReadResult(rows)
    }

    private fun scoreBubble(
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
        val radius = ((rx + ry) / 2.0).coerceAtLeast(2.0)

        val innerRadius = radius * 0.58
        val ringInner = radius * 1.22
        val ringOuter = radius * 1.75

        var innerSum = 0.0
        var innerCount = 0
        var ringSum = 0.0
        var ringCount = 0

        val left = max(0, (center.x - ringOuter).toInt())
        val right = min(gray.cols() - 1, (center.x + ringOuter).toInt())
        val top = max(0, (center.y - ringOuter).toInt())
        val bottom = min(gray.rows() - 1, (center.y + ringOuter).toInt())

        for (y in top..bottom) {
            for (x in left..right) {
                val dx = x + 0.5 - center.x
                val dy = y + 0.5 - center.y
                val distance = hypot(dx, dy)
                val value = gray.get(y, x)?.firstOrNull() ?: continue

                when {
                    distance <= innerRadius -> {
                        innerSum += value
                        innerCount++
                    }
                    distance in ringInner..ringOuter -> {
                        ringSum += value
                        ringCount++
                    }
                }
            }
        }

        if (innerCount < 6 || ringCount < 6) return 0.0
        val innerMean = innerSum / innerCount
        val localBackground = ringSum / ringCount

        return ((localBackground - innerMean) / 255.0).coerceIn(0.0, 1.0)
    }

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

        return when {
            bestScore < MIN_MARK_SCORE ->
                QuestionRead(questionId, QuestionState.BLANK, null, 1.0 - bestScore, scores)

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
    val confidence: Double,
    val choiceScores: Map<String, Double>
)

data class BubbleReadResult(
    val questions: List<QuestionRead>
) {
    val markedCount: Int get() = questions.count { it.state == QuestionState.MARKED }
    val suspiciousCount: Int get() = questions.count {
        it.state == QuestionState.SUSPICIOUS || it.state == QuestionState.DOUBLE_MARK
    }
}
