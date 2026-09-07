package com.okulyonetim.optikokuyucu.omr

import com.okulyonetim.optikokuyucu.omr.bubble.BubbleInkSamplingGeometry
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleInkPolicyTest {
    @Test
    fun `patchy pencil fill gains useful coverage evidence`() {
        val contrastOnly = BubbleInkSamplingGeometry.contrastScore(
            markMean = 220.0,
            localBackground = 240.0
        )
        val combined = BubbleInkSamplingGeometry.combinedScore(
            markMean = 220.0,
            localBackground = 240.0,
            darkFraction = 0.18
        )

        assertTrue(combined > contrastOnly)
        assertTrue(combined >= 0.085)
    }

    @Test
    fun `isolated dirt does not become a mark`() {
        val combined = BubbleInkSamplingGeometry.combinedScore(
            markMean = 236.0,
            localBackground = 240.0,
            darkFraction = 0.05
        )

        assertTrue(combined < 0.07)
    }

    @Test
    fun `dark-pixel cutoff follows local illumination`() {
        val brightPaperCutoff = BubbleInkSamplingGeometry.darkPixelCutoff(245.0)
        val shadowedPaperCutoff = BubbleInkSamplingGeometry.darkPixelCutoff(170.0)

        assertTrue(brightPaperCutoff > shadowedPaperCutoff)
        assertTrue(brightPaperCutoff < 245.0)
        assertTrue(shadowedPaperCutoff < 170.0)
    }
}
