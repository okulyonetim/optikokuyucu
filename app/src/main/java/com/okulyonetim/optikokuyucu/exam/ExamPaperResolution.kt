package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeyResolver
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey
import com.okulyonetim.optikokuyucu.omr.template.OmrRecognitionBindingsResolver

data class ExamPaperResolvedMetadata(
    val studentNumber: String,
    val className: String,
    val bookletCode: String
)

/**
 * Resolves user-corrected exam-paper metadata without mutating the immutable raw [ScanRecord].
 * Persisted link metadata wins; otherwise semantic OMR bindings provide the raw read fallback.
 */
object ExamPaperResolution {
    fun metadata(link: ExamPaperLink, record: ScanRecord): ExamPaperResolvedMetadata {
        val bindings = OmrRecognitionBindingsResolver.fromRecord(record)
        return ExamPaperResolvedMetadata(
            studentNumber = link.studentNumber.ifBlank { bindings.studentNumber(record).orEmpty() },
            className = link.className.ifBlank { bindings.classCode(record).orEmpty() },
            bookletCode = link.bookletCode.ifBlank { bindings.booklet(record).orEmpty() }
        )
    }

    /**
     * A corrected booklet code must never silently score against another booklet's key.
     * If the corrected variant is missing, only a truly general key may be used.
     */
    fun answerKey(
        link: ExamPaperLink,
        record: ScanRecord,
        keys: List<StoredAnswerKey>
    ): StoredAnswerKey? {
        val compatible = keys.filter {
            it.templateId == record.templateId && it.templateVersion == record.templateVersion
        }
        if (compatible.isEmpty()) return null

        val correctedBooklet = link.bookletCode.trim().takeIf { it.isNotBlank() }
        if (correctedBooklet == null) {
            return AnswerKeyResolver.resolve(record, compatible)
        }

        val bookletGridId = OmrRecognitionBindingsResolver.fromRecord(record).bookletGridId
        if (bookletGridId != null) {
            compatible.firstOrNull {
                it.variantGridId == bookletGridId && it.variantValue == correctedBooklet
            }?.let { return it }
        }

        return compatible.firstOrNull { it.variantGridId == null }
    }
}
