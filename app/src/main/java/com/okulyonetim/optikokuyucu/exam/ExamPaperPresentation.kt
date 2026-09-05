package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.scoring.ExamScore
import com.okulyonetim.optikokuyucu.omr.scoring.ScoringPolicy

object ExamPaperMetadataEditor {
    fun update(
        exam: Exam,
        scanRecordId: String,
        studentName: String,
        className: String,
        studentNumber: String,
        bookletCode: String
    ): Exam {
        val previous = requireNotNull(exam.paperForScan(scanRecordId)) {
            "Bu tarama sınava bağlı değil."
        }
        return exam.withPaper(
            previous.copy(
                studentName = studentName.trim(),
                className = className.trim(),
                studentNumber = studentNumber.trim(),
                bookletCode = bookletCode.trim()
            )
        )
    }
}

/** Pure unlink step used before destructive scan/image cleanup. */
object ExamPaperRemoval {
    fun unlink(exam: Exam, scanRecordId: String): Exam {
        requireNotNull(exam.paperForScan(scanRecordId)) {
            "Bu tarama sınava bağlı değil."
        }
        return exam.withoutPaper(scanRecordId)
    }
}

object ExamScoringPolicyResolver {
    fun resolve(policy: WrongAnswerPolicy): ScoringPolicy = when (policy) {
        WrongAnswerPolicy.KEEP_AS_IS -> ScoringPolicy()
        WrongAnswerPolicy.FOUR_WRONG_ONE_CORRECT -> ScoringPolicy(wrongPoints = -0.25)
        WrongAnswerPolicy.THREE_WRONG_ONE_CORRECT -> ScoringPolicy(wrongPoints = -(1.0 / 3.0))
    }
}

data class ExamPaperMetrics(
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    val doubleMark: Int,
    val suspicious: Int,
    val noKey: Int,
    val net: Double
) {
    companion object {
        fun from(score: ExamScore): ExamPaperMetrics = ExamPaperMetrics(
            correct = score.correctCount,
            wrong = score.wrongCount,
            blank = score.blankCount,
            doubleMark = score.doubleMarkCount,
            suspicious = score.suspiciousCount,
            noKey = score.noKeyCount,
            net = score.totalPoints
        )
    }
}

fun questionLessonPrefix(questionId: String): String? =
    questionId.substringBefore(':', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() && ':' in questionId }

fun questionDisplayNumber(questionId: String): String =
    questionId.substringAfterLast(':', missingDelimiterValue = questionId)
