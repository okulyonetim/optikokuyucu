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

class ExamPaperRegistrarTest {
    private val selection = ActiveTemplateSelection(
        source = ActiveTemplateSource.DESIGNER_DOCUMENT,
        templateId = "lgs",
        templateVersion = 2
    )

    @Test
    fun registersDetectedStudentNumberClassAndBooklet() {
        val repository = MemoryExamRepository(
            Exam(
                id = "exam",
                name = "LGS",
                schoolName = "Okul",
                templateSelection = selection,
                examDateEpochDay = 1L,
                createdAtEpochMs = 1L
            )
        )
        val record = scan(
            templateId = "lgs",
            templateVersion = 2,
            grids = listOf(
                grid("studentNumber", "16"),
                grid("class", "8A"),
                grid("booklet", "B")
            )
        )

        val updated = ExamPaperRegistrar(repository).register("exam", record, 99L)

        assertEquals(1, updated.papers.size)
        assertEquals("16", updated.papers.single().studentNumber)
        assertEquals("8A", updated.papers.single().className)
        assertEquals("B", updated.papers.single().bookletCode)
        assertEquals(99L, updated.papers.single().linkedAtEpochMs)
    }

    @Test
    fun rejectsDifferentTemplateVersion() {
        val repository = MemoryExamRepository(
            Exam(
                id = "exam",
                name = "LGS",
                schoolName = "Okul",
                templateSelection = selection,
                examDateEpochDay = 1L,
                createdAtEpochMs = 1L
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            ExamPaperRegistrar(repository).register(
                "exam",
                scan(templateId = "lgs", templateVersion = 3)
            )
        }
    }

    private fun scan(
        templateId: String,
        templateVersion: Int,
        grids: List<RecordedMarkGrid> = emptyList()
    ) = ScanRecord(
        id = "scan-1",
        templateId = templateId,
        templateVersion = templateVersion,
        capturedAtEpochMs = 1L,
        source = ScanSource.LIVE_CAMERA,
        sourceWidth = 100,
        sourceHeight = 100,
        pageConfidence = 1.0,
        decisionConfidence = 1.0,
        elapsedMs = 1.0,
        answers = emptyList(),
        markGrids = grids
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
