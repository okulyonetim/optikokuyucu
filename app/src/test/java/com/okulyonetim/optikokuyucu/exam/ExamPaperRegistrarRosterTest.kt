package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkColumn
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkGrid
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkState
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.results.ScanSource
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import com.okulyonetim.optikokuyucu.student.StudentGender
import com.okulyonetim.optikokuyucu.student.StudentImportSummary
import com.okulyonetim.optikokuyucu.student.StudentRosterEntry
import com.okulyonetim.optikokuyucu.student.StudentRosterRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class ExamPaperRegistrarRosterTest {
    @Test
    fun `recognized student number resolves imported roster identity without mutating raw record`() {
        val selection = ActiveTemplateSelection(
            source = ActiveTemplateSource.DESIGNER_DOCUMENT,
            templateId = "template-1",
            templateVersion = 3
        )
        val exam = ExamFactory.create(
            name = "Sınav",
            schoolName = "Test Okulu",
            templateSelection = selection,
            examDateEpochDay = 1L,
            id = "exam-1",
            createdAtEpochMs = 2L
        )
        val examRepository = InMemoryExamRepository(exam)
        val rosterStudent = StudentRosterEntry(
            studentNumber = "16",
            fullName = "ALİ TEST",
            gender = StudentGender.BOY,
            gradeLevel = 5,
            branch = "A",
            guardianName = "VELİ TEST",
            guardianPhone = "05320000000",
            updatedAtEpochMs = 3L
        )
        val studentRepository = InMemoryStudentRepository(rosterStudent)
        val record = ScanRecord(
            id = "scan-1",
            templateId = selection.templateId,
            templateVersion = selection.templateVersion,
            capturedAtEpochMs = 4L,
            source = ScanSource.LIVE_CAMERA,
            sourceWidth = 100,
            sourceHeight = 100,
            pageConfidence = 1.0,
            decisionConfidence = 1.0,
            elapsedMs = 10.0,
            answers = emptyList(),
            markGrids = listOf(studentNumberGrid("000016"))
        )

        val updated = ExamPaperRegistrar(examRepository, studentRepository)
            .register(examId = exam.id, record = record, linkedAtEpochMs = 5L)
        val link = requireNotNull(updated.paperForScan(record.id))

        assertEquals("ALİ TEST", link.studentName)
        assertEquals("5-A", link.className)
        assertEquals("16", link.studentNumber)
        assertEquals("000016", record.grid("studentNumber")?.value)
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
        private var value: StudentRosterEntry
    ) : StudentRosterRepository {
        override fun save(entry: StudentRosterEntry) { value = entry.normalized() }
        override fun findByNumber(studentNumber: String): StudentRosterEntry? =
            value.takeIf { it.studentNumber == com.okulyonetim.optikokuyucu.student.StudentNumber.normalize(studentNumber) }
        override fun list(): List<StudentRosterEntry> = listOf(value)
        override fun upsertImported(entries: List<StudentRosterEntry>): StudentImportSummary =
            StudentImportSummary(0, 0, entries.size, entries.size)
        override fun delete(studentNumber: String): Boolean = true
    }
}
