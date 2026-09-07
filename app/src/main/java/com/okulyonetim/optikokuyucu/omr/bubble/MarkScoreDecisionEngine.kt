package com.okulyonetim.optikokuyucu.omr.bubble

/** Shared score decision used by both answer bubbles and generic mark grids. */
enum class MarkScoreState {
    MARKED,
    BLANK,
    DOUBLE_MARK,
    SUSPICIOUS
}

data class MarkScoreDecision(
    val state: MarkScoreState,
    val selectedKey: String?,
    val confidence: Double,
    val scores: Map<String, Double>
)

/**
 * Converts normalized ink scores into one OMR decision.
 *
 * There are two single-mark paths:
 * 1. a normal mark above the established absolute level;
 * 2. a light mark that is below that level but is clearly isolated from every other option.
 *
 * The second path is important for pencil-filled student numbers and lightly shaded answers. It is
 * deliberately gap-gated, so uniformly gray paper, shadows and print dirt remain blank instead of
 * turning into false marks.
 */
object MarkScoreDecisionEngine {
    fun classify(scores: Map<String, Double>): MarkScoreDecision {
        val sorted = scores.entries.sortedByDescending { it.value }
        val best = sorted.getOrNull(0)
        val second = sorted.getOrNull(1)
        if (best == null) {
            return MarkScoreDecision(MarkScoreState.BLANK, null, 0.0, scores)
        }

        val bestScore = best.value.coerceIn(0.0, 1.0)
        val secondScore = (second?.value ?: 0.0).coerceIn(0.0, 1.0)
        val gap = (bestScore - secondScore).coerceAtLeast(0.0)
        val strongMarkCount = sorted.count { it.value >= STRONG_MARK_SCORE }

        return when {
            bestScore < ABSOLUTE_MIN_MARK_SCORE -> blank(bestScore, scores)

            strongMarkCount >= 2 ->
                MarkScoreDecision(
                    state = MarkScoreState.DOUBLE_MARK,
                    selectedKey = null,
                    confidence = (secondScore / STRONG_MARK_SCORE).coerceIn(0.0, 1.0),
                    scores = scores
                )

            secondScore >= DOUBLE_MARK_SCORE && gap < DOUBLE_GAP ->
                MarkScoreDecision(
                    state = MarkScoreState.DOUBLE_MARK,
                    selectedKey = null,
                    confidence = (1.0 - gap / DOUBLE_GAP).coerceIn(0.0, 1.0),
                    scores = scores
                )

            bestScore < CLEAR_MARK_SCORE && gap < LIGHT_MARK_REQUIRED_GAP ->
                blank(bestScore, scores)

            bestScore >= CLEAR_MARK_SCORE && gap >= CONFIDENT_GAP ->
                marked(best.key, bestScore, gap, scores, light = false)

            bestScore >= LIGHT_MARK_MIN_SCORE && gap >= LIGHT_MARK_REQUIRED_GAP ->
                marked(best.key, bestScore, gap, scores, light = true)

            else ->
                MarkScoreDecision(
                    state = MarkScoreState.SUSPICIOUS,
                    selectedKey = best.key,
                    confidence = (bestScore * 0.55).coerceIn(0.0, 1.0),
                    scores = scores
                )
        }
    }

    private fun blank(bestScore: Double, scores: Map<String, Double>) =
        MarkScoreDecision(
            state = MarkScoreState.BLANK,
            selectedKey = null,
            confidence = (1.0 - bestScore / CLEAR_MARK_SCORE).coerceIn(0.0, 1.0),
            scores = scores
        )

    private fun marked(
        key: String,
        bestScore: Double,
        gap: Double,
        scores: Map<String, Double>,
        light: Boolean
    ): MarkScoreDecision {
        val confidence = if (light) {
            ((bestScore / CLEAR_MARK_SCORE) * 0.55 +
                (gap / LIGHT_MARK_REQUIRED_GAP).coerceIn(0.0, 1.0) * 0.45)
                .coerceIn(0.0, 1.0)
        } else {
            ((bestScore * 0.65) + (gap * 1.8 * 0.35)).coerceIn(0.0, 1.0)
        }
        return MarkScoreDecision(
            state = MarkScoreState.MARKED,
            selectedKey = key,
            confidence = confidence,
            scores = scores
        )
    }

    private const val ABSOLUTE_MIN_MARK_SCORE = 0.07
    private const val LIGHT_MARK_MIN_SCORE = 0.085
    private const val CLEAR_MARK_SCORE = 0.12
    private const val STRONG_MARK_SCORE = 0.20
    private const val DOUBLE_MARK_SCORE = 0.105
    private const val DOUBLE_GAP = 0.050
    private const val CONFIDENT_GAP = 0.040
    private const val LIGHT_MARK_REQUIRED_GAP = 0.055
}
