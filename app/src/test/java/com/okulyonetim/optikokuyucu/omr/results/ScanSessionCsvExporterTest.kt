package com.okulyonetim.optikokuyucu.omr.results

import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKey
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeySource
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSessionCsvExporterTest {
    @Test
    fun `export includes score student and booklet`() {
        val record = ScanRecord(
            id = "record-1",
            templateId = "exam",
            templateVersion = 1,
            capturedAtEpochMs = 1_700_000_000_000L,
            source = ScanSource.LIVE_CAMERA,
            sourceWidth = 100,
            sourceHeight = 200,
            pageConfidence = 0.9,
            decisionConfidence = 0.9,
            elapsedMs = 10.0,
            answers = listOf(
                answer("1", "A"),
                answer("2", "B")
            ),
            markGrids = listOf(
                grid("studentNumber", listOf("1", "2", "3")),
                grid("booklet", listOf("A"))
            )
        )
        val key = StoredAnswerKey(
            answerKey = AnswerKey(
                templateId = "exam",
                templateVersion = 1,
                answers = linkedMapOf("1" to "A", "2" to "C")
            ),
            variantGridId = "booklet",
            variantValue = "A",
            createdAtEpochMs = 100L,
            source = AnswerKeySource.SCAN_RECORD,
            sourceRecordId = "key-a"
        )

        val csv = ScanSessionCsvExporter.export(listOf(record), listOf(key))

        assertTrue(csv.startsWith("\uFEFFSıra;"))
        assertTrue(csv.contains("123;A;exam;1;Canlı kamera;1;1;0;0;0;0;1,00;2,00;PUANLANDI"))
    }

    private fun answer(id: String, choice: String): RecordedAnswer = RecordedAnswer(
        questionId = id,
        state = RecordedAnswerState.MARKED,
        selectedChoice = choice,
        confidence = 0.9,
        choiceScores = emptyMap()
    )

    private fun grid(id: String, values: List<String>): RecordedMarkGrid = RecordedMarkGrid(
        gridId = id,
        columns = values.mapIndexed { index, value ->
            RecordedMarkColumn(
                columnId = (index + 1).toString(),
                state = RecordedMarkState.MARKED,
                selectedValue = value,
                confidence = 0.9,
                scores = mapOf(value to 0.4)
            )
        }
    )
}
