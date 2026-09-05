package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.results.ScanRecord

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

        val previous = exam.paperForScan(record.id)
        val detectedNumber = record.grid("studentNumber")?.value.orEmpty()
        val detectedClass = record.grid("class")?.value.orEmpty()
        val detectedBooklet = record.grid("booklet")?.value.orEmpty()
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
