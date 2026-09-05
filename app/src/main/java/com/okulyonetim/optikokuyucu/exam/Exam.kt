package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import java.util.UUID

enum class WrongAnswerPolicy {
    KEEP_AS_IS,
    FOUR_WRONG_ONE_CORRECT,
    THREE_WRONG_ONE_CORRECT
}

enum class ExamStatus {
    WAITING,
    READ
}

data class ExamPaperLink(
    val scanRecordId: String,
    val studentName: String = "",
    val className: String = "",
    val studentNumber: String = "",
    val bookletCode: String = "",
    val linkedAtEpochMs: Long = System.currentTimeMillis()
) {
    init {
        require(scanRecordId.isNotBlank())
        require(linkedAtEpochMs >= 0L)
    }
}

/**
 * Offline exam container. Raw OMR answers remain in ScanRecord; this class only associates a scan
 * with an exam and stores user-editable identity metadata.
 */
data class Exam(
    val id: String,
    val name: String,
    val schoolName: String,
    val templateSelection: ActiveTemplateSelection,
    val wrongAnswerPolicy: WrongAnswerPolicy = WrongAnswerPolicy.KEEP_AS_IS,
    val folderName: String = "",
    val examDateEpochDay: Long,
    val createdAtEpochMs: Long,
    val papers: List<ExamPaperLink> = emptyList()
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(schoolName.isNotBlank())
        require(createdAtEpochMs >= 0L)
        require(papers.map { it.scanRecordId }.toSet().size == papers.size) {
            "Aynı tarama bir sınava birden fazla kez bağlanamaz."
        }
    }

    val status: ExamStatus
        get() = if (papers.isEmpty()) ExamStatus.WAITING else ExamStatus.READ

    fun paperForScan(scanRecordId: String): ExamPaperLink? =
        papers.firstOrNull { it.scanRecordId == scanRecordId }

    fun withPaper(link: ExamPaperLink): Exam = copy(
        papers = papers.filterNot { it.scanRecordId == link.scanRecordId } + link
    )

    fun withoutPaper(scanRecordId: String): Exam = copy(
        papers = papers.filterNot { it.scanRecordId == scanRecordId }
    )
}

object ExamFactory {
    fun create(
        name: String,
        schoolName: String,
        templateSelection: ActiveTemplateSelection,
        examDateEpochDay: Long,
        wrongAnswerPolicy: WrongAnswerPolicy = WrongAnswerPolicy.KEEP_AS_IS,
        folderName: String = "",
        id: String = UUID.randomUUID().toString(),
        createdAtEpochMs: Long = System.currentTimeMillis()
    ): Exam = Exam(
        id = id,
        name = name.trim(),
        schoolName = schoolName.trim(),
        templateSelection = templateSelection,
        wrongAnswerPolicy = wrongAnswerPolicy,
        folderName = folderName.trim(),
        examDateEpochDay = examDateEpochDay,
        createdAtEpochMs = createdAtEpochMs
    )
}
