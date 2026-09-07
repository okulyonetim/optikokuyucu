package com.okulyonetim.optikokuyucu.omr

import com.okulyonetim.optikokuyucu.omr.bubble.BubbleInkSamplingGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmallBubbleSamplingTest {
    @Test
    fun `legacy small bubbles keep conservative glyph-safe annulus`() {
        assertFalse(BubbleInkSamplingGeometry.isMarkSample(distanceRatio = 0.48, radius = 7.0))
        assertTrue(BubbleInkSamplingGeometry.isMarkSample(distanceRatio = 0.60, radius = 7.0))
        assertFalse(BubbleInkSamplingGeometry.isMarkSample(distanceRatio = 0.80, radius = 7.0))
    }

    @Test
    fun `current and legacy bubble radii use identical sampling geometry`() {
        val legacy = BubbleInkSamplingGeometry.markSampleRange(radius = 7.0)
        val current = BubbleInkSamplingGeometry.markSampleRange(radius = 10.35)

        assertEquals(current.start, legacy.start, 0.0001)
        assertEquals(current.endInclusive, legacy.endInclusive, 0.0001)
    }

    @Test
    fun `printed outline zone remains excluded for every supported bubble size`() {
        listOf(6.0, 7.0, 8.0, 9.0, 10.35, 12.0).forEach { radius ->
            assertFalse(BubbleInkSamplingGeometry.isMarkSample(distanceRatio = 0.80, radius = radius))
            assertFalse(BubbleInkSamplingGeometry.isMarkSample(distanceRatio = 0.84, radius = radius))
        }
    }
}
