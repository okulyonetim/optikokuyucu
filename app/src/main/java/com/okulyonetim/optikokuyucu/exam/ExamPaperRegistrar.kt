package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.template.OmrRecognitionBindingsResolver
import com.okulyonetim.optikokuyucu.student.StudentNumber
import com.okulyonetim.optikokuyucu.student.StudentRosterRepository

/** Associates an immutable raw ScanRecord with one offline exam. */
class ExamPaperRegistrar(
    private val examRepository: ExamRepository,
    private val studentRepository: StudentRosterRepository? = null
) {
    fun register(
        examId: String,
        record: ScanRecord,
        linkedAtEpochMs: Long = System.currentTimeMillis()
    ): Exam {
        val exam = requireNotNull(examRepository.load(examId)) { "Sınav bulunamadı." }
        require(record.templateId == exam.templateSelection.templateId) {
            "Tarama sınavın optik formuyla eşleşmiyor."
        }
        require(record.templateVersion == exam.templateSelection.templateVersion) {
            "Tarama sınavın optik form sürümüyle eşleşmiyor."
        }

        val bindings = OmrRecognitionBindingsResolver.fromRecord(record)
        val previous = exam.paperForScan(record.id)
        val detectedNumber = bindings.studentNumber(record).orEmpty()
        val detectedClass = bindings.classCode(record).orEmpty()
        val detectedBooklet = bindings.booklet(record).orEmpty()
        val rosterStudent = StudentNumber.normalize(detectedNumber)
            .takeIf { it.isNotBlank() }
            ?.let { studentRepository?.findByNumber(it) }
        val resolvedNumber = rosterStudent?.studentNumber ?: detectedNumber
        val resolvedName = rosterStudent?.fullName.orEmpty()
        val resolvedClass = rosterStudent?.className?.takeIf { it.isNotBlank() } ?: detectedClass

        val link = if (previous == null) {
            ExamPaperLink(
                scanRecordId = record.id,
                studentName = resolvedName,
                studentNumber = resolvedNumber,
                className = resolvedClass,
                bookletCode = detectedBooklet,
                linkedAtEpochMs = linkedAtEpochMs
            )
        } else {
            previous.copy(
                studentName = previous.studentName.ifBlank { resolvedName },
                studentNumber = previous.studentNumber.ifBlank { resolvedNumber },
                className = previous.className.ifBlank { resolvedClass },
                bookletCode = previous.bookletCode.ifBlank { detectedBooklet }
            )
        }
        val updated = exam.withPaper(link)
        examRepository.save(updated)
        return updated
    }
}
