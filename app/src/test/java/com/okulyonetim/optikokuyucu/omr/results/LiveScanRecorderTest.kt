package com.okulyonetim.optikokuyucu.omr.results

import com.okulyonetim.optikokuyucu.camera.LiveOmrReadResult
import com.okulyonetim.optikokuyucu.omr.bubble.BubbleReadResult
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionRead
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionState
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkGridReadResult
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveScanRecorderTest {
    @Test
    fun `accepted live read is persisted with template version and confidence`() {
        val repository = FakeRepository()
        val recorder = LiveScanRecorder(repository)
        val template = StandardOmrTemplate.SAMPLE_20_ABCD_STUDENT_6_BOOKLET_AB
        val live = LiveOmrReadResult(
            sequence = 7,
            bubbleResult = BubbleReadResult(
                listOf(
                    QuestionRead(
                        questionId = "1",
                        state = QuestionState.MARKED,
                        selectedChoice = "B",
                        confidence = 0.93,
                        choiceScores = mapOf("B" to 0.27)
                    )
                )
            ),
            markGridResult = MarkGridReadResult(emptyList()),
            pageConfidence = 0.91,
            decisionConfidence = 0.89,
            elapsedMs = 31.5,
            sourceWidth = 960,
            sourceHeight = 540
        )

        val record = recorder.record(
            template = template,
            result = live,
            id = "fixed-id",
            capturedAtEpochMs = 1234L
        )

        assertEquals(1, repository.saved.size)
        assertEquals(record, repository.saved.single())
        assertEquals("fixed-id", record.id)
        assertEquals(template.id, record.templateId)
        assertEquals(template.version, record.templateVersion)
        assertEquals(0.91, record.pageConfidence ?: 0.0, 0.001)
        assertEquals("B", record.answers.single().selectedChoice)
    }

    private class FakeRepository : ScanRecordRepository {
        val saved = mutableListOf<ScanRecord>()

        override fun save(record: ScanRecord) {
            saved += record
        }

        override fun load(id: String): ScanRecord? = saved.firstOrNull { it.id == id }

        override fun list(): List<ScanRecord> = saved.toList()

        override fun delete(id: String): Boolean = saved.removeAll { it.id == id }
    }
}
