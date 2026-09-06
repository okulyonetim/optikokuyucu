package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswer
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswerState
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkColumn
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkGrid
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkState
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.results.ScanSource
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKey
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeySource
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExamPaperResolutionTest {
    @Test
    fun `corrected booklet overrides raw read and selects corrected key`() {
        val record = record(booklet = "A", studentNumber = "000016")
        val link = ExamPaperLink(
            scanRecordId = record.id,
            studentNumber = "16",
            bookletCode = "B",
            linkedAtEpochMs = 2L
        )
        val keyA = variantKey("A", expected = "A")
        val keyB = variantKey("B", expected = "B")

        val resolved = ExamPaperResolution.answerKey(link, record, listOf(keyA, keyB))

        assertEquals("B", resolved?.variantValue)
        assertEquals("B", resolved?.answerKey?.answers?.get("1"))
    }

    @Test
    fun `corrected booklet never falls back to another variant`() {
        val record = record(booklet = "A", studentNumber = "000016")
        val link = ExamPaperLink(
            scanRecordId = record.id,
            bookletCode = "B",
            linkedAtEpochMs = 2L
        )

        val resolved = ExamPaperResolution.answerKey(link, record, listOf(variantKey("A", expected = "A")))

        assertNull(resolved)
    }

    @Test
    fun `general key remains valid when corrected variant key is absent`() {
        val record = record(booklet = "A", studentNumber = "000016")
        val link = ExamPaperLink(
            scanRecordId = record.id,
            bookletCode = "B",
            linkedAtEpochMs = 2L
        )
        val general = StoredAnswerKey(
            answerKey = AnswerKey(record.templateId, record.templateVersion, mapOf("1" to "C")),
            createdAtEpochMs = 3L,
            source = AnswerKeySource.MANUAL
        )

        assertEquals(general, ExamPaperResolution.answerKey(link, record, listOf(variantKey("A", "A"), general)))
    }

    @Test
    fun `designer semantic ids provide metadata fallbacks without legacy grid names`() {
        val record = record(booklet = "A", studentNumber = "000016")
        val link = ExamPaperLink(scanRecordId = record.id, linkedAtEpochMs = 2L)

        val metadata = ExamPaperResolution.metadata(link, record)

        assertEquals("000016", metadata.studentNumber)
        assertEquals("A", metadata.bookletCode)
    }

    private fun variantKey(variant: String, expected: String): StoredAnswerKey = StoredAnswerKey(
        answerKey = AnswerKey(TEMPLATE_ID, TEMPLATE_VERSION, mapOf("1" to expected)),
        variantGridId = "booklet-1",
        variantValue = variant,
        createdAtEpochMs = 3L,
        source = AnswerKeySource.MANUAL
    )

    private fun record(booklet: String, studentNumber: String): ScanRecord = ScanRecord(
        id = "scan-1",
        templateId = TEMPLATE_ID,
        templateVersion = TEMPLATE_VERSION,
        capturedAtEpochMs = 1L,
        source = ScanSource.LIVE_CAMERA,
        sourceWidth = 1000,
        sourceHeight = 1414,
        pageConfidence = 0.95,
        decisionConfidence = 0.90,
        elapsedMs = 10.0,
        answers = listOf(
            RecordedAnswer(
                questionId = "1",
                state = RecordedAnswerState.MARKED,
                selectedChoice = "A",
                confidence = 0.9,
                choiceScores = mapOf("A" to 0.8)
            )
        ),
        markGrids = listOf(
            grid("number-1", studentNumber.map(Char::toString)),
            grid("booklet-1", listOf(booklet))
        )
    )

    private fun grid(id: String, values: List<String>): RecordedMarkGrid = RecordedMarkGrid(
        gridId = id,
        columns = values.mapIndexed { index, value ->
            RecordedMarkColumn(
                columnId = (index + 1).toString(),
                state = RecordedMarkState.MARKED,
                selectedValue = value,
                confidence = 0.9,
                scores = mapOf(value to 0.8)
            )
        }
    )

    private companion object {
        const val TEMPLATE_ID = "designer-form"
        const val TEMPLATE_VERSION = 1
    }
}
