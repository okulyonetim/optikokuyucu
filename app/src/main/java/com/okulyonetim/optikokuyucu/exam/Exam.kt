package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.student.StudentNumber
import com.okulyonetim.optikokuyucu.student.StudentSchoolIdentity
import java.util.UUID

enum class WrongAnswerPolicy {
    KEEP_AS_IS,
    FOUR_WRONG_ONE_CORRECT,
    THREE_WRONG_ONE_CORRECT
}

/** High-level scoring family. OMR recognition stays independent from this selection. */
enum class ExamScoringType {
    NORMAL,
    SINGLE_SUBJECT,
    LGS,
    IOKBS,
    CUSTOM
}

enum class ExamScoreMode {
    RAW_NET,
    SCALED
}

/**
 * Persisted scoring settings for one exam.
 *
 * LGS/IOKBS deliberately keep the official 100–500 range fixed. Their final score is cohort based;
 * the scoring engine therefore labels locally calculated values as local-cohort MEB-method scores,
 * never as the student's official national MEB result.
 */
data class ExamScoringConfiguration(
    val type: ExamScoringType = ExamScoringType.NORMAL,
    val scoreMode: ExamScoreMode = ExamScoreMode.SCALED,
    val minimumScore: Double = 0.0,
    val maximumScore: Double = 100.0,
    val customWrongAnswerDivisor: Double? = null,
    val lessonWeights: Map<String, Double> = emptyMap()
) {
    init {
        require(minimumScore.isFinite()) { "Taban puan sonlu bir sayı olmalıdır." }
        require(maximumScore.isFinite()) { "Tavan puan sonlu bir sayı olmalıdır." }
        require(maximumScore > minimumScore) { "Tavan puan taban puandan büyük olmalıdır." }
        require(customWrongAnswerDivisor == null ||
            (customWrongAnswerDivisor.isFinite() && customWrongAnswerDivisor > 0.0)) {
            "Özel yanlış götürme oranı sıfırdan büyük olmalıdır."
        }
        require(lessonWeights.keys.all { it.isNotBlank() }) { "Ders ağırlığı anahtarı boş olamaz." }
        require(lessonWeights.values.all { it.isFinite() && it > 0.0 }) {
            "Ders ağırlıkları sıfırdan büyük sonlu sayılar olmalıdır."
        }
        if (type != ExamScoringType.CUSTOM) {
            require(customWrongAnswerDivisor == null) {
                "Özel yanlış götürme oranı yalnız özel puanlamada kullanılabilir."
            }
            require(lessonWeights.isEmpty()) {
                "Ders ağırlıkları yalnız özel puanlamada değiştirilebilir."
            }
        }
        if (type == ExamScoringType.LGS || type == ExamScoringType.IOKBS) {
            require(scoreMode == ExamScoreMode.SCALED) { "LGS/İOKBS puanı 100–500 ölçeğinde hesaplanır." }
            require(minimumScore == 100.0 && maximumScore == 500.0) {
                "LGS/İOKBS puan aralığı 100–500 olmalıdır."
            }
        }
    }

    companion object {
        fun forType(type: ExamScoringType): ExamScoringConfiguration = when (type) {
            ExamScoringType.LGS,
            ExamScoringType.IOKBS -> ExamScoringConfiguration(
                type = type,
                scoreMode = ExamScoreMode.SCALED,
                minimumScore = 100.0,
                maximumScore = 500.0
            )
            ExamScoringType.SINGLE_SUBJECT -> ExamScoringConfiguration(type = type)
            ExamScoringType.CUSTOM -> ExamScoringConfiguration(type = type)
            ExamScoringType.NORMAL -> ExamScoringConfiguration()
        }
    }
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

    val identityKey: String
        get() {
            val number = StudentNumber.normalize(studentNumber)
            val grade = StudentSchoolIdentity.gradeLevelFromClassName(className)
            return grade?.let { StudentSchoolIdentity.identityKey(number, it) }
                ?.takeIf(String::isNotBlank)
                ?: "number:$number"
        }

    fun normalized(): ExamParticipant = copy(
        studentNumber = StudentNumber.normalize(studentNumber),
        studentName = studentName.trim().replace(Regex("\\s+"), " "),
        className = className.trim().replace(Regex("\\s+"), " ")
    )
}

/**
 * Offline exam container. Raw OMR answers remain in ScanRecord; this class only associates a scan
 * with an exam and stores user-editable identity metadata.
 *
 * ownerUid is the immutable Okul Yönetim account identity for account-scoped visibility. isPublic
 * only changes who may see the exam; edit/delete ownership remains with the owner or an admin.
 */
data class Exam(
    val id: String,
    val name: String,
    val schoolName: String,
    val templateSelection: ActiveTemplateSelection,
    val subjectName: String = "",
    val wrongAnswerPolicy: WrongAnswerPolicy = WrongAnswerPolicy.KEEP_AS_IS,
    val scoringConfiguration: ExamScoringConfiguration = ExamScoringConfiguration(),
    val folderName: String = "",
    val examDateEpochDay: Long,
    val createdAtEpochMs: Long,
    val papers: List<ExamPaperLink> = emptyList(),
    val participants: List<ExamParticipant> = emptyList(),
    val bookletCount: Int = 1,
    val personalizedFormsEnabled: Boolean = false,
    val ownerUid: String = "",
    val ownerDisplayName: String = "",
    val isPublic: Boolean = false
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
        require(participants.map { it.identityKey }.toSet().size == participants.size) {
            "Aynı kurum içindeki aynı öğrenci sınava birden fazla kez eklenemez."
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
        subjectName: String = "",
        wrongAnswerPolicy: WrongAnswerPolicy = WrongAnswerPolicy.KEEP_AS_IS,
        scoringConfiguration: ExamScoringConfiguration = ExamScoringConfiguration(),
        folderName: String = "",
        participants: List<ExamParticipant> = emptyList(),
        bookletCount: Int = 1,
        personalizedFormsEnabled: Boolean = false,
        ownerUid: String = "",
        ownerDisplayName: String = "",
        isPublic: Boolean = false,
        id: String = UUID.randomUUID().toString(),
        createdAtEpochMs: Long = System.currentTimeMillis()
    ): Exam {
        val normalizedParticipants = participants.map(ExamParticipant::normalized)
            .distinctBy { it.identityKey }
        return Exam(
            id = id,
            name = name.trim(),
            schoolName = schoolName.trim(),
            templateSelection = templateSelection,
            subjectName = subjectName.trim(),
            wrongAnswerPolicy = wrongAnswerPolicy,
            scoringConfiguration = scoringConfiguration,
            folderName = folderName.trim(),
            examDateEpochDay = examDateEpochDay,
            createdAtEpochMs = createdAtEpochMs,
            participants = normalizedParticipants,
            bookletCount = bookletCount,
            personalizedFormsEnabled = personalizedFormsEnabled,
            ownerUid = ownerUid.trim(),
            ownerDisplayName = ownerDisplayName.trim(),
            isPublic = isPublic
        )
    }
}
