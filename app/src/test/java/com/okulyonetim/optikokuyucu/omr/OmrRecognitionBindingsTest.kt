package com.okulyonetim.optikokuyucu.omr

import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkColumn
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkGrid
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkState
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.results.ScanSource
import com.okulyonetim.optikokuyucu.omr.template.OmrRecognitionBindingsResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OmrRecognitionBindingsTest {
    @Test
    fun studentNumberAllowsUnusedEdgeColumns() {
        val trailingBlank = record(
            column(RecordedMarkState.MARKED, "1"),
            column(RecordedMarkState.MARKED, "6"),
            column(RecordedMarkState.BLANK, null)
        )
        val leadingBlank = record(
            column(RecordedMarkState.BLANK, null),
            column(RecordedMarkState.MARKED, "1"),
            column(RecordedMarkState.MARKED, "6")
        )

        assertEquals("16", OmrRecognitionBindingsResolver.fromRecord(trailingBlank).studentNumber(trailingBlank))
        assertEquals("16", OmrRecognitionBindingsResolver.fromRecord(leadingBlank).studentNumber(leadingBlank))
    }

    @Test
    fun studentNumberRejectsInternalBlankOrUncertainColumn() {
        val internalBlank = record(
            column(RecordedMarkState.MARKED, "1"),
            column(RecordedMarkState.BLANK, null),
            column(RecordedMarkState.MARKED, "6")
        )
        val suspicious = record(
            column(RecordedMarkState.MARKED, "1"),
            column(RecordedMarkState.SUSPICIOUS, "6"),
            column(RecordedMarkState.BLANK, null)
        )

        assertNull(OmrRecognitionBindingsResolver.fromRecord(internalBlank).studentNumber(internalBlank))
        assertNull(OmrRecognitionBindingsResolver.fromRecord(suspicious).studentNumber(suspicious))
    }

    private fun record(vararg columns: RecordedMarkColumn): ScanRecord = ScanRecord(
        id = "scan",
        templateId = "template",
        templateVersion = 1,
        capturedAtEpochMs = 1L,
        source = ScanSource.LIVE_CAMERA,
        sourceWidth = 100,
        sourceHeight = 100,
        pageConfidence = 1.0,
        decisionConfidence = 1.0,
        elapsedMs = 1.0,
        answers = emptyList(),
        markGrids = listOf(RecordedMarkGrid("number-1", columns.toList()))
    )

    private fun column(state: RecordedMarkState, value: String?): RecordedMarkColumn = RecordedMarkColumn(
        columnId = "c-${System.nanoTime()}",
        state = state,
        selectedValue = value,
        confidence = 1.0,
        scores = emptyMap()
    )
}
