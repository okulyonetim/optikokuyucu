package com.okulyonetim.optikokuyucu.omr

import com.okulyonetim.optikokuyucu.omr.bubble.BubbleMicroAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleMicroAlignmentTest {
    @Test
    fun `several nearby dark samples preserve a real mark`() {
        val score = BubbleMicroAlignment.combine(
            listOf(0.16, 0.15, 0.14, 0.04, 0.03)
        )

        assertEquals(0.15, score, 0.0001)
        assertTrue(score >= 0.12)
    }

    @Test
    fun `one isolated spike is not promoted into a light mark`() {
        val score = BubbleMicroAlignment.combine(
            listOf(0.18, 0.03, 0.03, 0.03, 0.02)
        )

        assertTrue(score < 0.085)
    }

    @Test
    fun `offsets stay tiny relative to a normal bubble`() {
        val offsets = BubbleMicroAlignment.offsets(radius = 10.0)

        assertEquals(5, offsets.size)
        assertEquals(1.0, offsets.maxOf { kotlin.math.abs(it.dx) }, 0.0001)
        assertEquals(1.0, offsets.maxOf { kotlin.math.abs(it.dy) }, 0.0001)
    }

    @Test
    fun `very large bubbles cap the correction distance`() {
        val offsets = BubbleMicroAlignment.offsets(radius = 30.0)

        assertEquals(1.35, offsets.maxOf { kotlin.math.abs(it.dx) }, 0.0001)
        assertEquals(1.35, offsets.maxOf { kotlin.math.abs(it.dy) }, 0.0001)
    }
}
