package com.okulyonetim.optikokuyucu.school

import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluation
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluationState

data class SchoolLessonResultSummary(
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    val doubleMark: Int,
    val suspicious: Int,
    val noKey: Int,
    val net: Double
)

/** Pure Okul Yönetim lesson-result conversion used by cloud upload and unit tests. */
object SchoolLessonResultMapper {
    fun summarize(evaluations: List<QuestionEvaluation>): SchoolLessonResultSummary =
        SchoolLessonResultSummary(
            correct = evaluations.count { it.state == QuestionEvaluationState.CORRECT },
            wrong = evaluations.count { it.state == QuestionEvaluationState.WRONG },
            blank = evaluations.count { it.state == QuestionEvaluationState.BLANK },
            doubleMark = evaluations.count { it.state == QuestionEvaluationState.DOUBLE_MARK },
            suspicious = evaluations.count { it.state == QuestionEvaluationState.SUSPICIOUS },
            noKey = evaluations.count { it.state == QuestionEvaluationState.NO_KEY },
            net = evaluations.sumOf { it.points }
        )

    fun toFirestoreMap(summary: SchoolLessonResultSummary): Map<String, Any?> = linkedMapOf(
        "dogru" to summary.correct,
        "yanlis" to summary.wrong,
        "bos" to summary.blank,
        "cift" to summary.doubleMark,
        "supheli" to summary.suspicious,
        "anahtarsiz" to summary.noKey,
        "net" to summary.net
    )
}

/** Small pure policies that mirror the cached-session/token decisions used by the REST client. */
object SchoolTokenPolicy {
    const val REFRESH_AHEAD_MS = 60_000L

    fun canReuseIdToken(tokens: SchoolAuthTokens, nowEpochMs: Long): Boolean =
        tokens.idToken.isNotBlank() && tokens.expiresAtEpochMs > nowEpochMs + REFRESH_AHEAD_MS

    fun mustRefresh(tokens: SchoolAuthTokens, nowEpochMs: Long): Boolean =
        !canReuseIdToken(tokens, nowEpochMs)
}

object SchoolOfflineSessionPolicy {
    fun canOpenCached(session: SchoolPortalSession?): Boolean =
        session != null && session.profile.uid.isNotBlank() && session.profile.active
}
