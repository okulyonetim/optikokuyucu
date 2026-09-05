package com.okulyonetim.optikokuyucu.omr.tracking

import com.okulyonetim.optikokuyucu.omr.geometry.ImagePoint
import com.okulyonetim.optikokuyucu.omr.geometry.ImageQuadrilateral
import com.okulyonetim.optikokuyucu.omr.geometry.QuadrilateralQuality
import kotlin.math.hypot
import kotlin.math.sqrt

enum class PageTrackingPhase {
    SEARCHING,
    TRACKING,
    LOCKED
}

data class PageTrackingState(
    val phase: PageTrackingPhase,
    val smoothedQuad: ImageQuadrilateral?,
    val confidence: Double,
    val stableFrames: Int,
    val missedFrames: Int,
    val motionRatio: Double
) {
    companion object {
        val Empty = PageTrackingState(
            phase = PageTrackingPhase.SEARCHING,
            smoothedQuad = null,
            confidence = 0.0,
            stableFrames = 0,
            missedFrames = 0,
            motionRatio = 1.0
        )
    }
}

/**
 * Converts noisy per-frame page candidates into a deliberate lock state.
 *
 * No OMR answer should be accepted from SEARCHING/TRACKING. A later recognition stage
 * must only consume normalized frames while this tracker is LOCKED.
 */
class PageLockTracker(
    private val acquireScore: Double = 0.76,
    private val lockScore: Double = 0.82,
    private val framesToLock: Int = 5,
    private val maxStableMotionRatio: Double = 0.025,
    private val maxMissedFrames: Int = 3,
    private val smoothingAlpha: Double = 0.35
) {
    private var state = PageTrackingState.Empty

    init {
        require(acquireScore in 0.0..1.0)
        require(lockScore in acquireScore..1.0)
        require(framesToLock >= 2)
        require(maxStableMotionRatio > 0.0)
        require(maxMissedFrames >= 0)
        require(smoothingAlpha in 0.0..1.0)
    }

    fun currentState(): PageTrackingState = state

    fun reset(): PageTrackingState {
        state = PageTrackingState.Empty
        return state
    }

    fun onCandidate(
        quad: ImageQuadrilateral?,
        quality: QuadrilateralQuality?,
        frameWidth: Int,
        frameHeight: Int
    ): PageTrackingState {
        if (quad == null || quality == null || quality.totalScore < acquireScore) {
            return onMiss()
        }

        val previous = state.smoothedQuad
        val motionRatio = if (previous == null) {
            0.0
        } else {
            normalizedCornerMotion(previous, quad, frameWidth, frameHeight)
        }

        val smoothed = if (previous == null) quad else smooth(previous, quad, smoothingAlpha)
        val stable = previous == null || motionRatio <= maxStableMotionRatio

        val nextStableFrames = if (stable) state.stableFrames + 1 else 1
        val canLock = quality.totalScore >= lockScore &&
            stable &&
            nextStableFrames >= framesToLock

        val nextPhase = when {
            canLock -> PageTrackingPhase.LOCKED
            state.phase == PageTrackingPhase.LOCKED && stable && quality.totalScore >= acquireScore ->
                PageTrackingPhase.LOCKED
            else -> PageTrackingPhase.TRACKING
        }

        state = PageTrackingState(
            phase = nextPhase,
            smoothedQuad = smoothed,
            confidence = quality.totalScore,
            stableFrames = nextStableFrames,
            missedFrames = 0,
            motionRatio = motionRatio
        )
        return state
    }

    private fun onMiss(): PageTrackingState {
        if (state.smoothedQuad == null) return state

        val misses = state.missedFrames + 1
        if (misses > maxMissedFrames) {
            return reset()
        }

        state = state.copy(
            phase = if (state.phase == PageTrackingPhase.LOCKED) {
                PageTrackingPhase.TRACKING
            } else {
                state.phase
            },
            stableFrames = 0,
            missedFrames = misses,
            motionRatio = 1.0
        )
        return state
    }

    private fun normalizedCornerMotion(
        previous: ImageQuadrilateral,
        current: ImageQuadrilateral,
        frameWidth: Int,
        frameHeight: Int
    ): Double {
        if (frameWidth <= 0 || frameHeight <= 0) return 1.0
        val diagonal = hypot(frameWidth.toDouble(), frameHeight.toDouble())
        if (diagonal <= 0.0) return 1.0

        val previousPoints = previous.points
        val currentPoints = current.points
        var squared = 0.0
        for (i in previousPoints.indices) {
            val dx = currentPoints[i].x - previousPoints[i].x
            val dy = currentPoints[i].y - previousPoints[i].y
            squared += dx * dx + dy * dy
        }
        val rmsPixels = sqrt(squared / previousPoints.size)
        return rmsPixels / diagonal
    }

    private fun smooth(
        previous: ImageQuadrilateral,
        current: ImageQuadrilateral,
        alpha: Double
    ): ImageQuadrilateral = ImageQuadrilateral(
        topLeft = lerp(previous.topLeft, current.topLeft, alpha),
        topRight = lerp(previous.topRight, current.topRight, alpha),
        bottomRight = lerp(previous.bottomRight, current.bottomRight, alpha),
        bottomLeft = lerp(previous.bottomLeft, current.bottomLeft, alpha)
    )

    private fun lerp(a: ImagePoint, b: ImagePoint, alpha: Double): ImagePoint = ImagePoint(
        x = a.x + (b.x - a.x) * alpha,
        y = a.y + (b.y - a.y) * alpha
    )
}
