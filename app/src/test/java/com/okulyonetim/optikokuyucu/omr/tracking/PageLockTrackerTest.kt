package com.okulyonetim.optikokuyucu.omr.tracking

import com.okulyonetim.optikokuyucu.omr.geometry.ImagePoint
import com.okulyonetim.optikokuyucu.omr.geometry.ImageQuadrilateral
import com.okulyonetim.optikokuyucu.omr.geometry.QuadrilateralQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLockTrackerTest {
    private val goodQuality = QuadrilateralQuality(
        areaRatio = 0.45,
        convexityScore = 1.0,
        cornerAngleScore = 0.95,
        oppositeEdgeScore = 0.95,
        totalScore = 0.90
    )

    @Test
    fun stableCandidate_requiresMultipleFramesBeforeLock() {
        val tracker = PageLockTracker(framesToLock = 5)
        val quad = baseQuad()

        repeat(4) {
            val state = tracker.onCandidate(quad, goodQuality, 960, 720)
            assertEquals(PageTrackingPhase.TRACKING, state.phase)
        }

        val locked = tracker.onCandidate(quad, goodQuality, 960, 720)
        assertEquals(PageTrackingPhase.LOCKED, locked.phase)
        assertTrue(locked.stableFrames >= 5)
    }

    @Test
    fun largeMovement_breaksStableFrameSequence() {
        val tracker = PageLockTracker(framesToLock = 4)
        val quad = baseQuad()

        tracker.onCandidate(quad, goodQuality, 960, 720)
        tracker.onCandidate(shift(quad, 3.0, 2.0), goodQuality, 960, 720)

        val moved = tracker.onCandidate(shift(quad, 180.0, 90.0), goodQuality, 960, 720)
        assertEquals(PageTrackingPhase.TRACKING, moved.phase)
        assertEquals(1, moved.stableFrames)
        assertTrue(moved.motionRatio > 0.025)
    }

    @Test
    fun shortDetectionDrop_doesNotImmediatelyForgetPage() {
        val tracker = PageLockTracker(framesToLock = 3, maxMissedFrames = 2)
        val quad = baseQuad()

        repeat(3) { tracker.onCandidate(quad, goodQuality, 960, 720) }
        assertEquals(PageTrackingPhase.LOCKED, tracker.currentState().phase)

        val firstMiss = tracker.onCandidate(null, null, 960, 720)
        assertEquals(PageTrackingPhase.TRACKING, firstMiss.phase)
        assertTrue(firstMiss.smoothedQuad != null)

        val secondMiss = tracker.onCandidate(null, null, 960, 720)
        assertTrue(secondMiss.smoothedQuad != null)

        val thirdMiss = tracker.onCandidate(null, null, 960, 720)
        assertEquals(PageTrackingPhase.SEARCHING, thirdMiss.phase)
        assertEquals(null, thirdMiss.smoothedQuad)
    }

    @Test
    fun weakCandidate_neverLocks() {
        val tracker = PageLockTracker(framesToLock = 3)
        val weak = goodQuality.copy(totalScore = 0.60)

        repeat(10) {
            tracker.onCandidate(baseQuad(), weak, 960, 720)
        }

        assertEquals(PageTrackingPhase.SEARCHING, tracker.currentState().phase)
    }

    private fun baseQuad() = ImageQuadrilateral(
        topLeft = ImagePoint(220.0, 80.0),
        topRight = ImagePoint(730.0, 100.0),
        bottomRight = ImagePoint(790.0, 640.0),
        bottomLeft = ImagePoint(170.0, 620.0)
    )

    private fun shift(quad: ImageQuadrilateral, dx: Double, dy: Double) = ImageQuadrilateral(
        topLeft = ImagePoint(quad.topLeft.x + dx, quad.topLeft.y + dy),
        topRight = ImagePoint(quad.topRight.x + dx, quad.topRight.y + dy),
        bottomRight = ImagePoint(quad.bottomRight.x + dx, quad.bottomRight.y + dy),
        bottomLeft = ImagePoint(quad.bottomLeft.x + dx, quad.bottomLeft.y + dy)
    )
}
