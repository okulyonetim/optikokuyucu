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
        linkedAtEpochMs: Long = System.currentTimeMillis(),
        replaceScanRecordId: String? = null
    ): Exam {
        val exam = requireNotNull(examRepository.load(examId)) { "Sınav bulunamadı." }
        require(record.templateId == exam.templateSelection.templateId) {
            "Tarama sınavın optik formuyla eşleşmiyor."
        }
        require(record.templateVersion == exam.templateSelection.templateVersion) {
            "Tarama sınavın optik form sürümüyle eşleşmiyor."
        }
        if (replaceScanRecordId != null) {
            require(replaceScanRecordId != record.id) { "Yeni tarama eski kayıtla aynı kimliği kullanamaz." }
            requireNotNull(exam.paperForScan(replaceScanRecordId)) {
                "Güncellenecek eski öğrenci kağıdı sınavda bulunamadı."
            }
        }

        val bindings = OmrRecognitionBindingsResolver.fromRecord(record)
        val previous = exam.paperForScan(record.id)
        val detectedNumber = bindings.studentNumber(record).orEmpty()
        val detectedClass = bindings.classCode(record).orEmpty()
        val detectedBooklet = bindings.booklet(record).orEmpty()
        val normalizedNumber = StudentNumber.normalize(detectedNumber)
        val rosterStudent = normalizedNumber
            .takeIf { it.isNotBlank() }
            ?.let { studentRepository?.findByNumber(it) }
        val participant = normalizedNumber
            .takeIf { it.isNotBlank() }
            ?.let { number ->
                exam.participants.firstOrNull {
                    StudentNumber.normalize(it.studentNumber) == number
                }
            }
        val resolvedNumber = rosterStudent?.studentNumber
            ?: participant?.studentNumber
            ?: detectedNumber
        val resolvedName = rosterStudent?.fullName
            ?: participant?.studentName
            ?: ""
        val resolvedClass = rosterStudent?.className?.takeIf { it.isNotBlank() }
            ?: participant?.className?.takeIf { it.isNotBlank() }
            ?: detectedClass

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

        val baseExam = replaceScanRecordId?.let(exam::withoutPaper) ?: exam
        val updated = baseExam.withPaper(link)
        examRepository.save(updated)
        return updated
    }
}
