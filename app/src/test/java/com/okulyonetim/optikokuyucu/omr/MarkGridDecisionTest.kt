package com.okulyonetim.optikokuyucu.omr

import com.okulyonetim.optikokuyucu.omr.markgrid.MarkColumnState
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkGridDecisionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkGridDecisionTest {
    @Test
    fun `clear single mark is accepted`() {
        val result = MarkGridDecisionEngine.classify(
            mapOf("0" to 0.02, "1" to 0.03, "2" to 0.31, "3" to 0.04)
        )

        assertEquals(MarkColumnState.MARKED, result.state)
        assertEquals("2", result.selectedValue)
    }

    @Test
    fun `weak column stays blank`() {
        val result = MarkGridDecisionEngine.classify(
            mapOf("0" to 0.03, "1" to 0.04, "2" to 0.05, "3" to 0.02)
        )

        assertEquals(MarkColumnState.BLANK, result.state)
        assertNull(result.selectedValue)
    }

    @Test
    fun `two strong marks are rejected as double`() {
        val result = MarkGridDecisionEngine.classify(
            mapOf("0" to 0.28, "1" to 0.03, "2" to 0.25, "3" to 0.02)
        )

        assertEquals(MarkColumnState.DOUBLE_MARK, result.state)
        assertNull(result.selectedValue)
    }

    @Test
    fun `close weak marks are treated as double`() {
        val result = MarkGridDecisionEngine.classify(
            mapOf("0" to 0.15, "1" to 0.13, "2" to 0.03, "3" to 0.02)
        )

        assertEquals(MarkColumnState.DOUBLE_MARK, result.state)
        assertNull(result.selectedValue)
    }
}
