package com.okulyonetim.optikokuyucu.omr.bubble

import com.okulyonetim.optikokuyucu.omr.geometry.ImagePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class BubbleInkScorerTest {
    private val width = 200
    private val height = 200

    private fun paper(value: Int = 240): ByteArray = ByteArray(width * height) { value.toByte() }

    private fun fillDisc(pixels: ByteArray, cx: Double, cy: Double, r: Double, value: Int) {
        for (y in 0 until height) for (x in 0 until width) {
            if (hypot(x + 0.5 - cx, y + 0.5 - cy) <= r) pixels[y * width + x] = value.toByte()
        }
    }

    @Test
    fun `blank paper scores zero`() {
        val image = GrayImage(width, height, paper())
        assertEquals(0.0, BubbleInkScorer.score(image, ImagePoint(100.0, 100.0), 10.0), 1e-9)
    }

    @Test
    fun `dark filled bubble scores high`() {
        val pixels = paper()
        fillDisc(pixels, 100.0, 100.0, 10.0, 40)
        val score = BubbleInkScorer.score(GrayImage(width, height, pixels), ImagePoint(100.0, 100.0), 10.0)
        assertTrue("score=$score", score > 0.6)
    }

    @Test
    fun `printed glyph in the bubble center is ignored`() {
        val pixels = paper()
        fillDisc(pixels, 100.0, 100.0, 10.0 * 0.35, 20) // very dark letter core, inside the 0.50R inner edge
        val score = BubbleInkScorer.score(GrayImage(width, height, pixels), ImagePoint(100.0, 100.0), 10.0)
        assertEquals(0.0, score, 1e-9)
    }

    @Test
    fun `darker fill scores higher than lighter fill`() {
        val dark = paper().also { fillDisc(it, 100.0, 100.0, 9.0, 50) }
        val light = paper().also { fillDisc(it, 100.0, 100.0, 9.0, 170) }
        val darkScore = BubbleInkScorer.score(GrayImage(width, height, dark), ImagePoint(100.0, 100.0), 10.0)
        val lightScore = BubbleInkScorer.score(GrayImage(width, height, light), ImagePoint(100.0, 100.0), 10.0)
        assertTrue(darkScore > lightScore)
    }

    @Test
    fun `bubbles clipped by the image border score zero instead of crashing`() {
        val image = GrayImage(width, height, paper())
        assertEquals(0.0, BubbleInkScorer.score(image, ImagePoint(-50.0, 100.0), 10.0), 1e-9)
        assertEquals(0.0, BubbleInkScorer.score(image, ImagePoint(100.0, 500.0), 10.0), 1e-9)
        // Center on the very edge: the sample rings are mostly outside, so there is too little data.
        assertEquals(0.0, BubbleInkScorer.score(image, ImagePoint(0.0, 0.0), 10.0), 1e-9)
    }

    @Test
    fun `scores do not depend on where the bubble sits in the image`() {
        val a = paper().also { fillDisc(it, 60.0, 70.0, 9.0, 60) }
        val b = paper().also { fillDisc(it, 140.0, 130.0, 9.0, 60) }
        val scoreA = BubbleInkScorer.score(GrayImage(width, height, a), ImagePoint(60.0, 70.0), 10.0)
        val scoreB = BubbleInkScorer.score(GrayImage(width, height, b), ImagePoint(140.0, 130.0), 10.0)
        assertEquals(scoreA, scoreB, 0.02)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pixel buffer must match the declared size`() {
        GrayImage(10, 10, ByteArray(99))
    }
}
