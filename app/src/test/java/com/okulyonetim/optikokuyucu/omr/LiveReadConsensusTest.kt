package com.okulyonetim.optikokuyucu.omr

import com.okulyonetim.optikokuyucu.omr.live.LiveReadConsensus
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
}
