package com.okulyonetim.optikokuyucu.omr.geometry

import org.junit.Assert.assertTrue
import org.junit.Test

class QuadrilateralQualityTest {
    @Test
    fun centeredPortraitPage_scoresHigh() {
        val quad = ImageQuadrilateral(
            topLeft = ImagePoint(220.0, 90.0),
            topRight = ImagePoint(740.0, 100.0),
            bottomRight = ImagePoint(800.0, 630.0),
            bottomLeft = ImagePoint(170.0, 620.0)
        )

        val quality = QuadrilateralQualityEvaluator.evaluate(quad, 960, 720)

        assertTrue("Expected useful area", quality.areaRatio > 0.35)
        assertTrue("Expected convex page", quality.convexityScore == 1.0)
        assertTrue("Expected strong geometry score: ${quality.totalScore}", quality.totalScore > 0.85)
    }

    @Test
    fun tinyCandidate_isRejectedByArea() {
        val quad = ImageQuadrilateral(
            topLeft = ImagePoint(450.0, 320.0),
            topRight = ImagePoint(510.0, 320.0),
            bottomRight = ImagePoint(510.0, 380.0),
            bottomLeft = ImagePoint(450.0, 380.0)
        )

        val quality = QuadrilateralQualityEvaluator.evaluate(quad, 960, 720)

        assertTrue(quality.areaRatio < 0.01)
        assertTrue("Tiny false positives must not score as valid sheets", quality.totalScore < 0.75)
    }

    @Test
    fun selfCrossingCandidate_scoresPoorly() {
        val quad = ImageQuadrilateral(
            topLeft = ImagePoint(180.0, 120.0),
            topRight = ImagePoint(780.0, 620.0),
            bottomRight = ImagePoint(760.0, 120.0),
            bottomLeft = ImagePoint(200.0, 620.0)
        )

        val quality = QuadrilateralQualityEvaluator.evaluate(quad, 960, 720)

        assertTrue(quality.convexityScore == 0.0)
        assertTrue("Crossed corners must score poorly", quality.totalScore < 0.70)
    }

    @Test
    fun moderatePerspectiveStillPassesGeometryGate() {
        val quad = ImageQuadrilateral(
            topLeft = ImagePoint(300.0, 80.0),
            topRight = ImagePoint(700.0, 135.0),
            bottomRight = ImagePoint(835.0, 650.0),
            bottomLeft = ImagePoint(135.0, 610.0)
        )

        val quality = QuadrilateralQualityEvaluator.evaluate(quad, 960, 720)

        assertTrue("Perspective should be tolerated: ${quality.totalScore}", quality.totalScore > 0.75)
    }
}
