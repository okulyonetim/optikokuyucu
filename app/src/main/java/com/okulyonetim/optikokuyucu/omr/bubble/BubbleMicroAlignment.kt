package com.okulyonetim.optikokuyucu.omr.bubble

/**
 * Small canonical-space center offsets used to tolerate residual marker/warp error without
 * weakening the mark thresholds. A real filled bubble remains dark for several neighboring
 * samples, while an isolated printed edge or dirt spike normally affects only one sample.
 */
object BubbleMicroAlignment {
    data class Offset(val dx: Double, val dy: Double)

    fun offsets(radius: Double): List<Offset> {
        val shift = (radius.coerceAtLeast(2.0) * SHIFT_RATIO).coerceIn(MIN_SHIFT, MAX_SHIFT)
        return listOf(
            Offset(0.0, 0.0),
            Offset(-shift, 0.0),
            Offset(shift, 0.0),
            Offset(0.0, -shift),
            Offset(0.0, shift)
        )
    }

    /**
     * Require support from several nearby samples instead of trusting one maximum. The three
     * strongest of five samples tolerate a small center error while suppressing one-off spikes.
     */
    fun combine(scores: List<Double>): Double {
        if (scores.isEmpty()) return 0.0
        val bounded = scores.map { it.coerceIn(0.0, 1.0) }.sortedDescending()
        val supportCount = minOf(REQUIRED_SUPPORT, bounded.size)
        return bounded.take(supportCount).average().coerceIn(0.0, 1.0)
    }

    private const val SHIFT_RATIO = 0.10
    private const val MIN_SHIFT = 0.65
    private const val MAX_SHIFT = 1.35
    private const val REQUIRED_SUPPORT = 3
}
