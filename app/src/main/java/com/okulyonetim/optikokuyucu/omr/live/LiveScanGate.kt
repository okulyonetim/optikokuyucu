package com.okulyonetim.optikokuyucu.omr.live

import com.okulyonetim.optikokuyucu.omr.tracking.PageTrackingPhase

/**
 * Ensures one physical sheet produces at most one accepted live result.
 *
 * The gate is armed initially. Once a result is accepted it stays disarmed until the form has
 * clearly left the camera view for several consecutive frames. This avoids duplicate reads while
 * the user is still holding the same sheet in front of the camera.
 */
class LiveScanGate(
    private val framesToRearm: Int = 4,
    private val maxMarkersForAbsentPage: Int = 1
) {
    init {
        require(framesToRearm >= 2)
        require(maxMarkersForAbsentPage in 0..3)
    }

    private var armed = true
    private var absentFrames = 0

    fun isArmed(): Boolean = armed

    fun canRead(
        phase: PageTrackingPhase,
        markerCount: Int,
        registrationReady: Boolean,
        pageConfidence: Double
    ): Boolean = armed &&
        phase == PageTrackingPhase.LOCKED &&
        markerCount == 4 &&
        registrationReady &&
        pageConfidence >= MIN_ACCEPT_CONFIDENCE

    fun onAcceptedRead() {
        armed = false
        absentFrames = 0
    }

    /**
     * Call once per analyzed frame. Returns true only on the frame that rearms the scanner.
     */
    fun onFrame(phase: PageTrackingPhase, markerCount: Int): Boolean {
        if (armed) {
            absentFrames = 0
            return false
        }

        val pageAbsent = phase == PageTrackingPhase.SEARCHING ||
            markerCount <= maxMarkersForAbsentPage

        absentFrames = if (pageAbsent) absentFrames + 1 else 0
        if (absentFrames >= framesToRearm) {
            armed = true
            absentFrames = 0
            return true
        }
        return false
    }

    fun reset() {
        armed = true
        absentFrames = 0
    }

    private companion object {
        const val MIN_ACCEPT_CONFIDENCE = 0.82
    }
}
