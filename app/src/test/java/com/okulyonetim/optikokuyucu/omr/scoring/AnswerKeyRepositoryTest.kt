package com.okulyonetim.optikokuyucu.omr.scoring

import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswer
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswerState
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkColumn
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkGrid
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkState
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.results.ScanSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerKeyRepositoryTest {
    @Test
    fun `stored answer key codec round trips variant scope`() {
        val original = StoredAnswerKey(
            answerKey = AnswerKey(
                templateId = "exam",
                templateVersion = 4,
                answers = linkedMapOf("1" to "A", "2" to "C")
            ),
            variantGridId = "booklet",
            variantValue = "B",
            createdAtEpochMs = 1234L,
            source = AnswerKeySource.SCAN_RECORD,
            sourceRecordId = "record-42"
        )

        val decoded = AnswerKeyCodec.decode(AnswerKeyCodec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `resolver selects matching booklet and never crosses variants`() {
        val keyA = storedKey("A")
        val keyB = storedKey("B")

        assertEquals(keyA, AnswerKeyResolver.resolve(record(booklet = "A"), listOf(keyB, keyA)))
        assertEquals(keyB, AnswerKeyResolver.resolve(record(booklet = "B"), listOf(keyA, keyB)))
        assertNull(AnswerKeyResolver.resolve(record(booklet = "C"), listOf(keyA, keyB)))
    }

    @Test
    fun `record capture rejects uncertain answers`() {
        val valid = AnswerKeyCapture.fromRecord(record(booklet = "A"))
        assertTrue(valid.successful)
        assertEquals(mapOf("1" to "A", "2" to "C"), valid.answerKey?.answers)

        val invalidRecord = record(booklet = "A").copy(
            answers = listOf(
                answer("1", RecordedAnswerState.MARKED, "A"),
                answer("2", RecordedAnswerState.BLANK, null)
            )
        )
        val invalid = AnswerKeyCapture.fromRecord(invalidRecord)
        assertFalse(invalid.successful)
        assertEquals(listOf("2"), invalid.invalidQuestionIds)
    }

    private fun storedKey(booklet: String): StoredAnswerKey = StoredAnswerKey(
        answerKey = AnswerKey(
            templateId = "exam",
            templateVersion = 1,
            answers = linkedMapOf("1" to "A", "2" to "C")
        ),
        variantGridId = "booklet",
        variantValue = booklet,
        createdAtEpochMs = 100L,
        source = AnswerKeySource.SCAN_RECORD,
        sourceRecordId = "key-$booklet"
    )

    private fun record(booklet: String): ScanRecord = ScanRecord(
        id = "record-$booklet",
        templateId = "exam",
        templateVersion = 1,
        capturedAtEpochMs = 200L,
        source = ScanSource.LIVE_CAMERA,
        sourceWidth = 100,
        sourceHeight = 200,
        pageConfidence = 0.9,
        decisionConfidence = 0.9,
        elapsedMs = 12.0,
        answers = listOf(
            answer("1", RecordedAnswerState.MARKED, "A"),
            answer("2", RecordedAnswerState.MARKED, "C")
        ),
        markGrids = listOf(
            RecordedMarkGrid(
                gridId = "booklet",
                columns = listOf(
                    RecordedMarkColumn(
                        columnId = "type",
                        state = RecordedMarkState.MARKED,
                        selectedValue = booklet,
                        confidence = 0.95,
                        scores = mapOf(booklet to 0.4)
                    )
                )
            )
        )
    )

    private fun answer(
        id: String,
        state: RecordedAnswerState,
        choice: String?
    ): RecordedAnswer = RecordedAnswer(
        questionId = id,
        state = state,
        selectedChoice = choice,
        confidence = 0.9,
        choiceScores = emptyMap()
    )
}
