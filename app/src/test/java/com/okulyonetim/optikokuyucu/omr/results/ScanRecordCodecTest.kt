package com.okulyonetim.optikokuyucu.omr.results

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanRecordCodecTest {
    @Test
    fun `scan record round trips without losing recognition detail`() {
        val source = ScanRecord(
            id = "scan-1",
            templateId = "exam",
            templateVersion = 3,
            capturedAtEpochMs = 123456789L,
            source = ScanSource.LIVE_CAMERA,
            sourceWidth = 1920,
            sourceHeight = 1080,
            pageConfidence = 0.94,
            decisionConfidence = 0.88,
            elapsedMs = 42.5,
            answers = listOf(
                RecordedAnswer(
                    questionId = "1",
                    state = RecordedAnswerState.MARKED,
                    selectedChoice = "B",
                    confidence = 0.91,
                    choiceScores = mapOf("A" to 0.02, "B" to 0.27)
                ),
                RecordedAnswer(
                    questionId = "2",
                    state = RecordedAnswerState.DOUBLE_MARK,
                    selectedChoice = null,
                    confidence = 0.84,
                    choiceScores = mapOf("A" to 0.24, "C" to 0.22)
                )
            ),
            markGrids = listOf(
                RecordedMarkGrid(
                    gridId = "studentNumber",
                    columns = listOf(
                        RecordedMarkColumn(
                            columnId = "digit-1",
                            state = RecordedMarkState.MARKED,
                            selectedValue = "7",
                            confidence = 0.93,
                            scores = mapOf("7" to 0.28)
                        )
                    )
                )
            )
        )

        val decoded = ScanRecordCodec.decode(ScanRecordCodec.encode(source))

        assertEquals(source, decoded)
        assertEquals("7", decoded.grid("studentNumber")?.value)
    }
}
