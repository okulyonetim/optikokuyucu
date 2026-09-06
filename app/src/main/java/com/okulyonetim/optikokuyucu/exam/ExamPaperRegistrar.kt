package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.template.OmrRecognitionBindingsResolver

/** Associates an immutable raw ScanRecord with one offline exam. */
class ExamPaperRegistrar(
    private val examRepository: ExamRepository
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
        val link = if (previous == null) {
            ExamPaperLink(
                scanRecordId = record.id,
                studentNumber = detectedNumber,
                className = detectedClass,
                bookletCode = detectedBooklet,
                linkedAtEpochMs = linkedAtEpochMs
            )
        } else {
            previous.copy(
                studentNumber = previous.studentNumber.ifBlank { detectedNumber },
                className = previous.className.ifBlank { detectedClass },
                bookletCode = previous.bookletCode.ifBlank { detectedBooklet }
            )
        }
        val updated = exam.withPaper(link)
        examRepository.save(updated)
        return updated
    }
}
