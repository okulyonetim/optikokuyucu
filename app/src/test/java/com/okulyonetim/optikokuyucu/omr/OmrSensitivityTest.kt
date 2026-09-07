package com.okulyonetim.optikokuyucu.omr

import com.okulyonetim.optikokuyucu.omr.bubble.OmrSensitivity
import com.okulyonetim.optikokuyucu.omr.bubble.OmrSensitivityPolicy
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkColumnState
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkGridDecisionEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class OmrSensitivityTest {
    @Test
    fun `normal profile preserves known good morning thresholds exactly`() {
        val normal = OmrSensitivityPolicy.thresholds(OmrSensitivity.NORMAL)

        assertEquals(0.12, normal.minMarkScore, 0.000001)
        assertEquals(0.20, normal.strongMarkScore, 0.000001)
        assertEquals(0.11, normal.doubleMarkScore, 0.000001)
        assertEquals(0.055, normal.doubleGap, 0.000001)
        assertEquals(0.045, normal.confidentGap, 0.000001)
    }

    @Test
    fun `high profile can accept a light isolated mark without changing normal behavior`() {
        val scores = mapOf("0" to 0.02, "1" to 0.10, "2" to 0.02, "3" to 0.02)

        assertEquals(
            MarkColumnState.BLANK,
            MarkGridDecisionEngine.classify(scores, OmrSensitivity.NORMAL).state
        )
        assertEquals(
            MarkColumnState.MARKED,
            MarkGridDecisionEngine.classify(scores, OmrSensitivity.HIGH).state
        )
        assertEquals(
            MarkColumnState.BLANK,
            MarkGridDecisionEngine.classify(scores, OmrSensitivity.LOW).state
        )
    }
}
