package com.okulyonetim.optikokuyucu.omr

import com.okulyonetim.optikokuyucu.omr.bubble.BubbleInkSamplingGeometry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmallBubbleSamplingTest {
    @Test
    fun `small bubbles use a wider glyph-safe ink annulus`() {
        assertTrue(BubbleInkSamplingGeometry.isMarkSample(distanceRatio = 0.48, radius = 7.0))
        assertTrue(BubbleInkSamplingGeometry.isMarkSample(distanceRatio = 0.80, radius = 7.0))
    }

    @Test
    fun `small bubble sampling still excludes center glyph and printed ring`() {
        assertFalse(BubbleInkSamplingGeometry.isMarkSample(distanceRatio = 0.40, radius = 7.0))
        assertFalse(BubbleInkSamplingGeometry.isMarkSample(distanceRatio = 0.90, radius = 7.0))
    }

    @Test
    fun `normal bubbles retain established annulus`() {
        assertFalse(BubbleInkSamplingGeometry.isMarkSample(distanceRatio = 0.48, radius = 12.0))
        assertTrue(BubbleInkSamplingGeometry.isMarkSample(distanceRatio = 0.60, radius = 12.0))
        assertFalse(BubbleInkSamplingGeometry.isMarkSample(distanceRatio = 0.80, radius = 12.0))
    }

    @Test
    fun `boundary designer bubble remains small profile while warning sized bubble is normal`() {
        val small = BubbleInkSamplingGeometry.markSampleRange(radius = 9.0)
        val normal = BubbleInkSamplingGeometry.markSampleRange(radius = 9.01)

        assertTrue(small.start < normal.start)
        assertTrue(small.endInclusive > normal.endInclusive)
    }
}
