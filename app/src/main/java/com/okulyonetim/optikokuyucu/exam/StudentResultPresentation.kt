package com.okulyonetim.optikokuyucu.exam

import kotlin.math.round

/** A rank plus the number of students who actually participated in that ranking. */
data class ExamRankPlacement(
    val rank: Int,
    val participantCount: Int
) {
    init {
        require(rank > 0)
        require(participantCount > 0)
        require(rank <= participantCount)
    }

    val displayText: String get() = "$rank/$participantCount"
}

data class StudentLessonResultPresentation(
    val lessonId: String,
    val lessonName: String,
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    val net: Double,
    val rank: ExamRankPlacement?,
    val correctPercent: Double,
    val wrongPercent: Double
)

data class StudentResultPresentation(
    val examId: String,
    val examName: String,
    val schoolName: String,
    val scoringType: ExamScoringType,
    val scanRecordId: String,
    val studentName: String,
    val className: String,
    val studentNumber: String,
    val bookletCode: String,
    val correct: Int?,
    val wrong: Int?,
    val blank: Int?,
    val net: Double?,
    val score: Double?,
    val maximumScore: Double?,
    val scoreLabel: String,
    val scoreNote: String,
    val overallRank: ExamRankPlacement?,
    val classRank: ExamRankPlacement?,
    val lessons: List<StudentLessonResultPresentation>
)

object StudentResultPresentationBuilder {
    fun build(report: ExamReport, scanRecordId: String): StudentResultPresentation? {
        val row = report.rows.firstOrNull { it.scanRecordId == scanRecordId } ?: return null
        val overallEligible = report.rows.filter(::eligibleForScoreRanking)
        val classEligible = report.rows.filter { candidate ->
            candidate.className.isNotBlank() &&
                candidate.className == row.className &&
                eligibleForScoreRanking(candidate)
        }

        val lessons = row.lessons.map { lesson ->
            val candidates = report.rows.mapNotNull { candidate ->
                if (candidate.status != ExamReportRowStatus.SCORED) return@mapNotNull null
                val candidateLesson = candidate.lessons.firstOrNull { it.lessonId == lesson.lessonId }
                    ?: return@mapNotNull null
                CandidateLesson(candidate.scanRecordId, candidate.ordinal, candidateLesson.net)
            }
            val questionCount = lesson.correct + lesson.wrong + lesson.blank +
                lesson.doubleMark + lesson.suspicious
            StudentLessonResultPresentation(
                lessonId = lesson.lessonId,
                lessonName = examLessonDisplayName(lesson.lessonId),
                correct = lesson.correct,
                wrong = lesson.wrong,
                blank = lesson.blank,
                net = lesson.net,
                rank = rankLesson(candidates, row.scanRecordId),
                correctPercent = percentage(lesson.correct, questionCount),
                wrongPercent = percentage(lesson.wrong, questionCount)
            )
        }

        return StudentResultPresentation(
            examId = report.examId,
            examName = report.examName,
            schoolName = report.schoolName,
            scoringType = report.scoringType,
            scanRecordId = row.scanRecordId,
            studentName = row.studentName,
            className = row.className,
            studentNumber = row.studentNumber,
            bookletCode = row.bookletCode,
            correct = row.correct,
            wrong = row.wrong,
            blank = row.blank,
            net = row.net,
            score = row.points,
            maximumScore = row.maximumPoints,
            scoreLabel = scoreLabel(report.scoringType),
            scoreNote = row.scoreNote,
            overallRank = row.overallRank?.takeIf { overallEligible.any { candidate -> candidate.scanRecordId == row.scanRecordId } }
                ?.let { ExamRankPlacement(it, overallEligible.size) },
            classRank = row.classRank?.takeIf { classEligible.any { candidate -> candidate.scanRecordId == row.scanRecordId } }
                ?.let { ExamRankPlacement(it, classEligible.size) },
            lessons = lessons
        )
    }

    fun scoreLabel(type: ExamScoringType): String = when (type) {
        ExamScoringType.LGS -> "LGS Puanı"
        ExamScoringType.IOKBS -> "İOKBS Puanı"
        ExamScoringType.NORMAL,
        ExamScoringType.SINGLE_SUBJECT,
        ExamScoringType.CUSTOM -> "Puan"
    }

    private fun eligibleForScoreRanking(row: ExamReportRow): Boolean =
        row.status == ExamReportRowStatus.SCORED && row.points != null

    private fun rankLesson(
        candidates: List<CandidateLesson>,
        scanRecordId: String
    ): ExamRankPlacement? {
        if (candidates.none { it.scanRecordId == scanRecordId }) return null
        val sorted = candidates.sortedWith(
            compareByDescending<CandidateLesson> { rankingValue(it.net) }
                .thenBy { it.ordinal }
        )
        var previous: Double? = null
        var currentRank = 0
        sorted.forEachIndexed { index, candidate ->
            val value = rankingValue(candidate.net)
            if (previous == null || value != previous) {
                currentRank = index + 1
                previous = value
            }
            if (candidate.scanRecordId == scanRecordId) {
                return ExamRankPlacement(currentRank, sorted.size)
            }
        }
        return null
    }

    private fun percentage(value: Int, total: Int): Double =
        if (total <= 0) 0.0 else value.toDouble() * 100.0 / total.toDouble()

    private fun rankingValue(value: Double): Double = round(value * 100.0) / 100.0

    private data class CandidateLesson(
        val scanRecordId: String,
        val ordinal: Int,
        val net: Double
    )
}
