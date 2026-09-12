package com.okulyonetim.optikokuyucu.omr.results

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualAnswerOverrideApplierTest {
    private fun record(): ScanRecord = ScanRecord(
        id = "scan-1",
        templateId = "template",
        templateVersion = 1,
        capturedAtEpochMs = 1L,
        source = ScanSource.GALLERY,
        sourceWidth = 100,
        sourceHeight = 100,
        pageConfidence = null,
        decisionConfidence = null,
        elapsedMs = 1.0,
        answers = listOf(
            RecordedAnswer("1", RecordedAnswerState.MARKED, "A", 0.8, mapOf("A" to 0.8, "B" to 0.2)),
            RecordedAnswer("2", RecordedAnswerState.MARKED, "B", 0.8, mapOf("A" to 0.2, "B" to 0.8))
        ),
        markGrids = emptyList()
    )

    @Test
    fun `manual choice changes effective answer without mutating raw record`() {
        val raw = record()
        val effective = ManualAnswerOverrideApplier.apply(raw, mapOf("1" to "B"))
        assertEquals("A", raw.answers[0].selectedChoice)
        assertEquals("B", effective.answers[0].selectedChoice)
        assertEquals(RecordedAnswerState.MARKED, effective.answers[0].state)
        assertEquals(1.0, effective.answers[0].confidence, 0.0)
    }

    @Test
    fun `manual blank converts only selected question to blank`() {
        val raw = record()
        val effective = ManualAnswerOverrideApplier.apply(raw, mapOf("2" to ManualAnswerOverrideApplier.BLANK_TOKEN))
        assertEquals(RecordedAnswerState.BLANK, effective.answers[1].state)
        assertNull(effective.answers[1].selectedChoice)
        assertEquals("A", effective.answers[0].selectedChoice)
    }
}
