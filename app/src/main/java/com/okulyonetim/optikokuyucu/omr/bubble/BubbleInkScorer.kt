package com.okulyonetim.optikokuyucu.omr.bubble

import com.okulyonetim.optikokuyucu.omr.geometry.ImagePoint
import org.opencv.core.Mat
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Measures hand-filled ink while deliberately ignoring the printed letter/digit in a bubble's
 * center. The mark sample is an inner annulus that is covered by a normal filled bubble but lies
 * outside the glyph core. Background is sampled only in diagonal sectors so dense neighboring
 * bubbles, question numbers and row labels do not pollute the local paper reference.
 */
object BubbleInkScorer {
    fun score(
        gray: Mat,
        center: ImagePoint,
        radius: Double
    ): Double {
        val safeRadius = radius.coerceAtLeast(2.0)
        val outerSearch = safeRadius * BACKGROUND_OUTER_RATIO

        var markSum = 0.0
        var markCount = 0
        var backgroundSum = 0.0
        var backgroundCount = 0

        val left = max(0, (center.x - outerSearch).toInt())
        val right = min(gray.cols() - 1, (center.x + outerSearch).toInt())
        val top = max(0, (center.y - outerSearch).toInt())
        val bottom = min(gray.rows() - 1, (center.y + outerSearch).toInt())
        if (left > right || top > bottom) return 0.0

        for (y in top..bottom) {
            for (x in left..right) {
                val dx = x + 0.5 - center.x
                val dy = y + 0.5 - center.y
                val distanceRatio = hypot(dx, dy) / safeRadius
                val value = gray.get(y, x)?.firstOrNull() ?: continue

                when {
                    BubbleInkSamplingGeometry.isMarkSample(distanceRatio) -> {
                        markSum += value
                        markCount += 1
                    }
                    BubbleInkSamplingGeometry.isBackgroundSample(
                        distanceRatio = distanceRatio,
                        normalizedAbsDx = abs(dx) / safeRadius,
                        normalizedAbsDy = abs(dy) / safeRadius
                    ) -> {
                        backgroundSum += value
                        backgroundCount += 1
                    }
                }
            }
        }

        if (markCount < MIN_SAMPLE_COUNT || backgroundCount < MIN_SAMPLE_COUNT) return 0.0
        val markMean = markSum / markCount
        val localBackground = backgroundSum / backgroundCount
        return BubbleInkSamplingGeometry.contrastScore(markMean, localBackground)
    }

    private const val BACKGROUND_OUTER_RATIO = 1.55
    private const val MIN_SAMPLE_COUNT = 6
}

/** Pure geometry/contrast policy kept separately so the glyph-safe behavior is JVM-testable. */
object BubbleInkSamplingGeometry {
    fun isMarkSample(distanceRatio: Double): Boolean =
        distanceRatio in MARK_INNER_RATIO..MARK_OUTER_RATIO

    fun isBackgroundSample(
        distanceRatio: Double,
        normalizedAbsDx: Double,
        normalizedAbsDy: Double
    ): Boolean =
        distanceRatio in BACKGROUND_INNER_RATIO..BACKGROUND_OUTER_RATIO &&
            normalizedAbsDx >= BACKGROUND_DIAGONAL_MIN_AXIS &&
            normalizedAbsDy >= BACKGROUND_DIAGONAL_MIN_AXIS

    fun contrastScore(markMean: Double, localBackground: Double): Double =
        ((localBackground - markMean) / 255.0).coerceIn(0.0, 1.0)

    private const val MARK_INNER_RATIO = 0.50
    private const val MARK_OUTER_RATIO = 0.74
    private const val BACKGROUND_INNER_RATIO = 1.18
    private const val BACKGROUND_OUTER_RATIO = 1.55
    private const val BACKGROUND_DIAGONAL_MIN_AXIS = 0.34
}
