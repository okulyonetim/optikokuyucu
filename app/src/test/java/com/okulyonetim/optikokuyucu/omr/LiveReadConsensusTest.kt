package com.okulyonetim.optikokuyucu.omr

import com.okulyonetim.optikokuyucu.omr.live.LiveReadConsensus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveReadConsensusTest {
    @Test
    fun requiresTwoConsecutiveEqualSignatures() {
        val consensus = LiveReadConsensus(requiredConsecutiveMatches = 2)
        assertFalse(consensus.offer("1:A|2:-"))
        assertTrue(consensus.offer("1:A|2:-"))
    }

    @Test
    fun changedAnswerRestartsConsensus() {
        val consensus = LiveReadConsensus(requiredConsecutiveMatches = 2)
        assertFalse(consensus.offer("1:A|2:-"))
        assertFalse(consensus.offer("1:B|2:-"))
        assertTrue(consensus.offer("1:B|2:-"))
    }

    @Test
    fun resetPreventsStaleFramePairing() {
        val consensus = LiveReadConsensus(requiredConsecutiveMatches = 2)
        assertFalse(consensus.offer("1:A"))
        consensus.reset()
        assertFalse(consensus.offer("1:A"))
        assertTrue(consensus.offer("1:A"))
    }

    @Test
    fun threeFrameConsensusNeedsThreeIdenticalReads() {
        val consensus = LiveReadConsensus(requiredConsecutiveMatches = 3)
        assertFalse(consensus.offer("1:A"))
        assertFalse(consensus.offer("1:A"))
        assertTrue(consensus.offer("1:A"))
    }

    @Test
    fun reportsWhichItemsFlickerBetweenFrames() {
        val consensus = LiveReadConsensus(requiredConsecutiveMatches = 3)
        consensus.offer("a", mapOf("1" to "MARKED:A", "2" to "MARKED:B", "3" to "BLANK:-"))
        consensus.offer("b", mapOf("1" to "MARKED:A", "2" to "SUSPICIOUS:B", "3" to "MARKED:C"))

        assertEquals(listOf("2", "3"), consensus.unstableKeys())
    }

    @Test
    fun noUnstableItemsBeforeAnyMismatchOrAfterReset() {
        val consensus = LiveReadConsensus(requiredConsecutiveMatches = 2)
        consensus.offer("a", mapOf("1" to "MARKED:A"))
        assertTrue(consensus.unstableKeys().isEmpty())

        consensus.offer("b", mapOf("1" to "MARKED:B"))
        assertEquals(listOf("1"), consensus.unstableKeys())

        consensus.reset()
        assertTrue(consensus.unstableKeys().isEmpty())
    }
}
