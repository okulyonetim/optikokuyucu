package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkColumn
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkGrid
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkState
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.results.ScanSource
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExamPaperDeduplicationTest {
    private val selection = ActiveTemplateSelection(
        source = ActiveTemplateSource.DESIGNER_DOCUMENT,
        templateId = "form",
        templateVersion = 1
    )

    @Test
    fun registrarRejectsSecondResultForSameStudentEvenWhenNumberFormattingDiffers() {
        val repository = MemoryExamRepository(
            exam(
                papers = listOf(
                    ExamPaperLink(
                        scanRecordId = "old",
                        studentName = "Ali İmran Karagöz",
                        studentNumber = "88",
                        className = "3-A",
                        linkedAtEpochMs = 10L
                    )
                )
            )
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            ExamPaperRegistrar(repository).register(
                examId = "exam",
                record = scan("new", studentNumber = "088", className = "3-B"),
                linkedAtEpochMs = 20L
            )
        }

        assert(error.message.orEmpty().contains("zaten bir sonuç kayıtlı"))
        assertEquals(1, repository.load("exam")!!.papers.size)
    }

    @Test
    fun sameNumberInDifferentInstitutionsIsNotCollapsed() {
        val source = exam(
            papers = listOf(
                ExamPaperLink(
                    scanRecordId = "primary",
                    studentName = "Ali",
                    studentNumber = "88",
                    className = "3-A",
                    linkedAtEpochMs = 10L
                ),
                ExamPaperLink(
                    scanRecordId = "middle",
                    studentName = "Veli",
                    studentNumber = "088",
                    className = "7-A",
                    linkedAtEpochMs = 20L
                )
            )
        )

        val collapsed = ExamPaperDeduplication.collapse(source)

        assertEquals(listOf("primary", "middle"), collapsed.papers.map { it.scanRecordId })
    }

    @Test
    fun legacyDuplicatesKeepNewestLinkedResult() {
        val source = exam(
            papers = listOf(
                ExamPaperLink(
                    scanRecordId = "old",
                    studentName = "Ali İmran Karagöz",
                    studentNumber = "88",
                    className = "3-A",
                    linkedAtEpochMs = 10L
                ),
                ExamPaperLink(
                    scanRecordId = "new",
                    studentName = "ALI İMRAN KARAGÖZ",
                    studentNumber = "088",
                    className = "3-B",
                    linkedAtEpochMs = 30L
                )
            )
        )

        val collapsed = ExamPaperDeduplication.collapse(source)

        assertEquals(1, collapsed.papers.size)
        assertEquals("new", collapsed.papers.single().scanRecordId)
    }

    private fun exam(papers: List<ExamPaperLink>) = Exam(
        id = "exam",
        name = "Test",
        schoolName = "Okul",
        templateSelection = selection,
        examDateEpochDay = 1L,
        createdAtEpochMs = 1L,
        papers = papers
    )

    private fun scan(id: String, studentNumber: String, className: String) = ScanRecord(
        id = id,
        templateId = "form",
        templateVersion = 1,
        capturedAtEpochMs = 1L,
        source = ScanSource.LIVE_CAMERA,
        sourceWidth = 100,
        sourceHeight = 100,
        pageConfidence = 1.0,
        decisionConfidence = 1.0,
        elapsedMs = 1.0,
        answers = emptyList(),
        markGrids = listOf(
            grid("studentNumber", studentNumber),
            grid("class", className)
        )
    )

    private fun grid(id: String, value: String): RecordedMarkGrid = RecordedMarkGrid(
        gridId = id,
        columns = value.mapIndexed { index, character ->
            RecordedMarkColumn(
                columnId = index.toString(),
                state = RecordedMarkState.MARKED,
                selectedValue = character.toString(),
                confidence = 1.0,
                scores = mapOf(character.toString() to 1.0)
            )
        }
    )

    private class MemoryExamRepository(initial: Exam) : ExamRepository {
        private var value: Exam? = initial
        override fun save(exam: Exam) { value = exam }
        override fun load(id: String): Exam? = value?.takeIf { it.id == id }
        override fun list(): List<Exam> = listOfNotNull(value)
        override fun delete(id: String): Boolean {
            if (value?.id == id) value = null
            return true
        }
    }
}
