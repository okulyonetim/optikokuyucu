package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkColumn
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkGrid
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkState
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.results.ScanSource
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import com.okulyonetim.optikokuyucu.student.StudentImportSummary
import com.okulyonetim.optikokuyucu.student.StudentNumber
import com.okulyonetim.optikokuyucu.student.StudentRosterEntry
import com.okulyonetim.optikokuyucu.student.StudentRosterRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class ExamPaperRegistrarSchoolIdentityTest {
    @Test
    fun `exam participant school resolves correct student when number exists in both schools`() {
        val selection = ActiveTemplateSelection(
            source = ActiveTemplateSource.DESIGNER_DOCUMENT,
            templateId = "template-1",
            templateVersion = 1
        )
        val exam = ExamFactory.create(
            name = "Ortaokul Sınavı",
            schoolName = "Koruk Ortaokulu",
            templateSelection = selection,
            examDateEpochDay = 1L,
            participants = listOf(ExamParticipant("3", "ORTAOKUL ÖĞRENCİSİ", "6-A")),
            id = "exam-1",
            createdAtEpochMs = 1L
        )
        val primary = StudentRosterEntry(
            studentNumber = "3",
            fullName = "İLKOKUL ÖĞRENCİSİ",
            gradeLevel = 3,
            branch = "A",
            updatedAtEpochMs = 1L
        )
        val middle = StudentRosterEntry(
            studentNumber = "3",
            fullName = "ORTAOKUL ÖĞRENCİSİ",
            gradeLevel = 6,
            branch = "A",
            updatedAtEpochMs = 1L
        )
        val record = ScanRecord(
            id = "scan-1",
            templateId = selection.templateId,
            templateVersion = selection.templateVersion,
            capturedAtEpochMs = 1L,
            source = ScanSource.LIVE_CAMERA,
            sourceWidth = 100,
            sourceHeight = 100,
            pageConfidence = 1.0,
            decisionConfidence = 1.0,
            elapsedMs = 1.0,
            answers = emptyList(),
            markGrids = listOf(studentNumberGrid("000003"))
        )
        val examRepository = InMemoryExamRepository(exam)
        val rosterRepository = InMemoryStudentRepository(listOf(primary, middle))

        val updated = ExamPaperRegistrar(examRepository, rosterRepository)
            .register(exam.id, record, linkedAtEpochMs = 2L)
        val link = requireNotNull(updated.paperForScan(record.id))

        assertEquals("3", link.studentNumber)
        assertEquals("ORTAOKUL ÖĞRENCİSİ", link.studentName)
        assertEquals("6-A", link.className)
    }

    private fun studentNumberGrid(number: String): RecordedMarkGrid = RecordedMarkGrid(
        gridId = "studentNumber",
        columns = number.mapIndexed { index, digit ->
            RecordedMarkColumn(
                columnId = "digit-$index",
                state = RecordedMarkState.MARKED,
                selectedValue = digit.toString(),
                confidence = 1.0,
                scores = mapOf(digit.toString() to 1.0)
            )
        }
    )

    private class InMemoryExamRepository(initial: Exam) : ExamRepository {
        private var value: Exam? = initial
        override fun save(exam: Exam) { value = exam }
        override fun load(id: String): Exam? = value?.takeIf { it.id == id }
        override fun list(): List<Exam> = listOfNotNull(value)
        override fun delete(id: String): Boolean {
            if (value?.id == id) value = null
            return true
        }
    }

    private class InMemoryStudentRepository(
        private val values: List<StudentRosterEntry>
    ) : StudentRosterRepository {
        override fun save(entry: StudentRosterEntry) = Unit
        override fun findByNumber(studentNumber: String): StudentRosterEntry? {
            val number = StudentNumber.normalize(studentNumber)
            return values.filter { it.studentNumber == number }.singleOrNull()
        }
        override fun list(): List<StudentRosterEntry> = values
        override fun upsertImported(entries: List<StudentRosterEntry>): StudentImportSummary =
            StudentImportSummary(0, 0, entries.size, entries.size)
        override fun delete(studentNumber: String): Boolean = true
    }
}
