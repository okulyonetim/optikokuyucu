package com.okulyonetim.optikokuyucu.omr.bubble

import com.okulyonetim.optikokuyucu.omr.geometry.ImagePoint
import org.opencv.core.Mat
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Measures hand-filled ink while deliberately ignoring the printed letter/digit in a bubble's
 * center. The mark sample is an inner annulus that is covered by a normal filled bubble but lies
 * outside the glyph core. Background is sampled only in diagonal sectors so dense neighboring
 * bubbles, question numbers and row labels do not pollute the local paper reference.
 *
 * A single mean-darkness measurement used to miss lightly or unevenly filled pencil marks. The
 * production score now combines local mean contrast with the fraction of genuinely dark pixels in
 * the glyph-safe annulus. A robust background median keeps shadows and isolated print dirt from
 * moving the decision threshold too far.
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
        val markHistogram = IntArray(256)
        val backgroundHistogram = IntArray(256)

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
                val bucket = value.roundToInt().coerceIn(0, 255)

                when {
                    BubbleInkSamplingGeometry.isMarkSample(distanceRatio) -> {
                        markSum += value
                        markCount += 1
                        markHistogram[bucket] += 1
                    }
                    BubbleInkSamplingGeometry.isBackgroundSample(
                        distanceRatio = distanceRatio,
                        normalizedAbsDx = abs(dx) / safeRadius,
                        normalizedAbsDy = abs(dy) / safeRadius
                    ) -> {
                        backgroundSum += value
                        backgroundCount += 1
                        backgroundHistogram[bucket] += 1
                    }
                }
            }
        }

        if (markCount < MIN_SAMPLE_COUNT || backgroundCount < MIN_SAMPLE_COUNT) return 0.0
        val markMean = markSum / markCount
        val backgroundMean = backgroundSum / backgroundCount
        val backgroundMedian = histogramMedian(backgroundHistogram, backgroundCount)
        val localBackground = backgroundMedian * 0.78 + backgroundMean * 0.22
        val cutoff = BubbleInkSamplingGeometry.darkPixelCutoff(localBackground)
            .roundToInt()
            .coerceIn(0, 255)
        var darkPixels = 0
        for (bucket in 0..cutoff) darkPixels += markHistogram[bucket]
        val darkFraction = darkPixels.toDouble() / markCount.toDouble()

        return BubbleInkSamplingGeometry.combinedScore(
            markMean = markMean,
            localBackground = localBackground,
            darkFraction = darkFraction
        )
    }

    private fun histogramMedian(histogram: IntArray, count: Int): Double {
        if (count <= 0) return 255.0
        val target = (count - 1) / 2
        var seen = 0
        histogram.forEachIndexed { value, amount ->
            seen += amount
            if (seen > target) return value.toDouble()
        }
        return 255.0
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

    /** Pixel is considered real hand ink only when it is meaningfully darker than nearby paper. */
    fun darkPixelCutoff(localBackground: Double): Double {
        val margin = maxOf(MIN_DARK_PIXEL_MARGIN, localBackground * DARK_PIXEL_MARGIN_RATIO)
        return (localBackground - margin).coerceIn(0.0, 255.0)
    }

    /**
     * Mean contrast remains the main signal. Dark-pixel coverage adds evidence for light, patchy or
     * pencil-filled bubbles without allowing a few isolated dirty pixels to become a mark.
     */
    fun combinedScore(
        markMean: Double,
        localBackground: Double,
        darkFraction: Double
    ): Double {
        val contrast = contrastScore(markMean, localBackground)
        val coverage = ((darkFraction.coerceIn(0.0, 1.0) - NOISE_COVERAGE) /
            (FULL_MARK_COVERAGE - NOISE_COVERAGE)).coerceIn(0.0, 1.0)
        return (contrast * CONTRAST_WEIGHT + coverage * COVERAGE_WEIGHT).coerceIn(0.0, 1.0)
    }

    private const val MARK_INNER_RATIO = 0.50
    private const val MARK_OUTER_RATIO = 0.76
    private const val BACKGROUND_INNER_RATIO = 1.18
    private const val BACKGROUND_OUTER_RATIO = 1.55
    private const val BACKGROUND_DIAGONAL_MIN_AXIS = 0.34

    private const val MIN_DARK_PIXEL_MARGIN = 12.0
    private const val DARK_PIXEL_MARGIN_RATIO = 0.06
    private const val NOISE_COVERAGE = 0.04
    private const val FULL_MARK_COVERAGE = 0.60
    private const val CONTRAST_WEIGHT = 0.76
    private const val COVERAGE_WEIGHT = 0.24
}
