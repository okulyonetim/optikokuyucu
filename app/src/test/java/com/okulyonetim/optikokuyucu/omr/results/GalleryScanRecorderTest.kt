package com.okulyonetim.optikokuyucu.omr.results

import com.okulyonetim.optikokuyucu.omr.bubble.BubbleReadResult
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionRead
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionState
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkColumnRead
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkColumnState
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkGridRead
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkGridReadResult
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryScanRecorderTest {
    @Test
    fun `explicit gallery recognition becomes immutable gallery scan record`() {
        val repository = MemoryRepository()
        val recorder = GalleryScanRecorder(repository)
        val template = StandardOmrTemplate.SAMPLE_20_ABCD_STUDENT_6_BOOKLET_AB
        val bubbles = BubbleReadResult(
            listOf(
                QuestionRead("1", QuestionState.MARKED, "B", 0.92, mapOf("A" to 0.02, "B" to 0.44)),
                QuestionRead("2", QuestionState.BLANK, null, 0.88, emptyMap())
            )
        )
        val grids = MarkGridReadResult(
            listOf(
                MarkGridRead(
                    gridId = "booklet",
                    columns = listOf(
                        MarkColumnRead(
                            columnId = "type",
                            state = MarkColumnState.MARKED,
                            selectedValue = "A",
                            confidence = 0.95,
                            scores = mapOf("A" to 0.43, "B" to 0.01)
                        )
                    )
                )
            )
        )

        val record = recorder.recordRecognition(
            template = template,
            sourceWidth = 1440,
            sourceHeight = 2048,
            elapsedMs = 37.5,
            bubbleResult = bubbles,
            markGridResult = grids,
            id = "gallery-1",
            capturedAtEpochMs = 1234L
        )

        assertEquals("gallery-1", record.id)
        assertEquals(ScanSource.GALLERY, record.source)
        assertEquals(template.id, record.templateId)
        assertEquals(template.version, record.templateVersion)
        assertEquals(1440, record.sourceWidth)
        assertEquals(2048, record.sourceHeight)
        assertEquals(1234L, record.capturedAtEpochMs)
        assertEquals(RecordedAnswerState.MARKED, record.answers[0].state)
        assertEquals("B", record.answers[0].selectedChoice)
        assertEquals("A", record.grid("booklet")?.value)
        assertNull(record.pageConfidence)
        assertNull(record.decisionConfidence)
        assertEquals(record, repository.saved.single())
    }

    @Test
    fun `empty gallery recognition is never persisted`() {
        val repository = MemoryRepository()
        val recorder = GalleryScanRecorder(repository)

        assertThrows(IllegalArgumentException::class.java) {
            recorder.recordRecognition(
                template = StandardOmrTemplate.SAMPLE_20_ABCD,
                sourceWidth = 100,
                sourceHeight = 100,
                elapsedMs = 1.0,
                bubbleResult = BubbleReadResult(emptyList()),
                markGridResult = MarkGridReadResult.Empty
            )
        }
        assertTrue(repository.saved.isEmpty())
    }

    private class MemoryRepository : ScanRecordRepository {
        val saved = mutableListOf<ScanRecord>()

        override fun save(record: ScanRecord) {
            saved += record
        }

        override fun load(id: String): ScanRecord? = saved.firstOrNull { it.id == id }
        override fun list(): List<ScanRecord> = saved.toList()
        override fun delete(id: String): Boolean = saved.removeAll { it.id == id }
    }
}
