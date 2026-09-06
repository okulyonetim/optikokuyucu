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

/** Stable participant identity snapshot captured when an exam is created. */
data class ExamParticipant(
    val studentNumber: String,
    val studentName: String,
    val className: String
) {
    init {
        require(studentNumber.isNotBlank()) { "Öğrenci numarası boş olamaz." }
        require(studentName.isNotBlank()) { "Öğrenci adı boş olamaz." }
        require(className.isNotBlank()) { "Sınıf adı boş olamaz." }
    }

    fun normalized(): ExamParticipant = copy(
        studentNumber = studentNumber.trim(),
        studentName = studentName.trim().replace(Regex("\\s+"), " "),
        className = className.trim().replace(Regex("\\s+"), " ")
    )
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
    val papers: List<ExamPaperLink> = emptyList(),
    val participants: List<ExamParticipant> = emptyList(),
    val bookletCount: Int = 1,
    val personalizedFormsEnabled: Boolean = false
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(schoolName.isNotBlank())
        require(createdAtEpochMs >= 0L)
        require(bookletCount in 1..8) { "Kitapçık sayısı 1–8 arasında olmalıdır." }
        require(papers.map { it.scanRecordId }.toSet().size == papers.size) {
            "Aynı tarama bir sınava birden fazla kez bağlanamaz."
        }
        require(participants.map { it.studentNumber }.toSet().size == participants.size) {
            "Aynı öğrenci sınava birden fazla kez eklenemez."
        }
        require(!personalizedFormsEnabled || participants.isNotEmpty()) {
            "Öğrenciye özel form için en az bir öğrenci seçilmelidir."
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
        participants: List<ExamParticipant> = emptyList(),
        bookletCount: Int = 1,
        personalizedFormsEnabled: Boolean = false,
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
        createdAtEpochMs = createdAtEpochMs,
        participants = participants.map(ExamParticipant::normalized).distinctBy { it.studentNumber },
        bookletCount = bookletCount,
        personalizedFormsEnabled = personalizedFormsEnabled
    )
}
