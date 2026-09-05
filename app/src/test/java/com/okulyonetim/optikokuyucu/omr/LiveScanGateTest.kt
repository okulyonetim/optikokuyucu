package com.okulyonetim.optikokuyucu.omr

import com.okulyonetim.optikokuyucu.omr.live.LiveScanGate
import com.okulyonetim.optikokuyucu.omr.tracking.PageTrackingPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveScanGateTest {
    @Test
    fun acceptsOnlyLockedHighConfidencePage() {
        val gate = LiveScanGate()

        assertFalse(gate.canRead(PageTrackingPhase.SEARCHING, 4, true, 0.95))
        assertFalse(gate.canRead(PageTrackingPhase.LOCKED, 3, true, 0.95))
        assertFalse(gate.canRead(PageTrackingPhase.LOCKED, 4, false, 0.95))
        assertFalse(gate.canRead(PageTrackingPhase.LOCKED, 4, true, 0.80))
        assertTrue(gate.canRead(PageTrackingPhase.LOCKED, 4, true, 0.90))
    }

    @Test
    fun samePageCannotBeReadTwiceUntilItLeavesView() {
        val gate = LiveScanGate(framesToRearm = 4)
        assertTrue(gate.canRead(PageTrackingPhase.LOCKED, 4, true, 0.92))
        gate.onAcceptedRead()

        repeat(8) {
            assertFalse(gate.canRead(PageTrackingPhase.LOCKED, 4, true, 0.92))
            assertFalse(gate.onFrame(PageTrackingPhase.LOCKED, 4))
        }

        repeat(3) {
            assertFalse(gate.onFrame(PageTrackingPhase.SEARCHING, 0))
        }
        assertTrue(gate.onFrame(PageTrackingPhase.SEARCHING, 0))
        assertTrue(gate.canRead(PageTrackingPhase.LOCKED, 4, true, 0.92))
    }

    @Test
    fun briefMarkerLossDoesNotRearm() {
        val gate = LiveScanGate(framesToRearm = 4)
        gate.onAcceptedRead()

        assertFalse(gate.onFrame(PageTrackingPhase.TRACKING, 1))
        assertFalse(gate.onFrame(PageTrackingPhase.TRACKING, 1))
        assertFalse(gate.onFrame(PageTrackingPhase.LOCKED, 4))
        assertFalse(gate.isArmed())
    }
}
