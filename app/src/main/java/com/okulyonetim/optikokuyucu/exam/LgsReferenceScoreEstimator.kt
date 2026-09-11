package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluation
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluationState
import java.util.Locale

/**
 * Deterministic LGS estimate that does not depend on the local cohort size.
 *
 * The official MEB score needs national mean/standard-deviation/TASP values that are not available
 * to the offline app. This estimator therefore keeps the official 3-wrong rule and lesson structure,
 * then applies a fixed 2026 reference calibration. The result is always labelled as an estimate and
 * must never be presented as the official MEB result.
 */
object LgsReferenceScoreEstimator {
    fun calculate(papers: List<ExamPaperScoreInput>): Map<String, ExamPaperCalculatedScore> =
        papers.associate { input -> input.paperId to calculate(input) }

    private fun calculate(input: ExamPaperScoreInput): ExamPaperCalculatedScore {
        val evaluationsByLesson = input.score.evaluations.groupBy { evaluation ->
            canonicalLessonId(questionLessonPrefix(evaluation.questionId))
        }
        val lessons = linkedMapOf<String, ExamLessonScore>()
        val missing = mutableListOf<String>()

        for (rule in RULES) {
            val evaluations = evaluationsByLesson[rule.lessonId].orEmpty()
            if (evaluations.isEmpty()) {
                missing += rule.displayName
                continue
            }
            lessons[rule.lessonId] = lessonScore(rule, evaluations)
        }

        val orderedLessons = RULES.mapNotNull { lessons[it.lessonId] }
        if (missing.isNotEmpty()) {
            return unavailable(
                input.paperId,
                orderedLessons,
                "Tahmini LGS puanı için eksik test bölümü: ${missing.joinToString(", ")}."
            )
        }

        if (orderedLessons.any { it.suspicious > 0 || it.noKey > 0 }) {
            return unavailable(
                input.paperId,
                orderedLessons,
                "Şüpheli veya cevap anahtarı eksik işaretler düzeltilmeden tahmini LGS puanı hesaplanmaz."
            )
        }

        val estimatedScore = BASE_SCORE + RULES.sumOf { rule ->
            requireNotNull(lessons[rule.lessonId]).net * rule.pointsPerNet
        }

        return ExamPaperCalculatedScore(
            paperId = input.paperId,
            net = orderedLessons.sumOf { it.net },
            calculatedScore = estimatedScore.coerceIn(MIN_SCORE, MAX_SCORE),
            maximumScore = MAX_SCORE,
            scope = ExamCalculatedScoreScope.SCALED,
            lessons = orderedLessons,
            note = "Tahmini 2026 LGS puanıdır; 3 yanlış 1 doğru kuralı ve ders bazlı referans katsayılar kullanılır. Resmî MEB sonucu değildir."
        )
    }

    private fun lessonScore(rule: ReferenceRule, evaluations: List<QuestionEvaluation>): ExamLessonScore {
        val correct = evaluations.count { it.state == QuestionEvaluationState.CORRECT }
        val wrong = evaluations.count { it.state == QuestionEvaluationState.WRONG }
        val blank = evaluations.count { it.state == QuestionEvaluationState.BLANK }
        val doubleMark = evaluations.count { it.state == QuestionEvaluationState.DOUBLE_MARK }
        val suspicious = evaluations.count { it.state == QuestionEvaluationState.SUSPICIOUS }
        val noKey = evaluations.count { it.state == QuestionEvaluationState.NO_KEY }
        val penaltyWrong = wrong + doubleMark
        val net = correct - penaltyWrong / 3.0

        return ExamLessonScore(
            lessonId = rule.lessonId,
            correct = correct,
            wrong = wrong,
            blank = blank,
            doubleMark = doubleMark,
            suspicious = suspicious,
            noKey = noKey,
            net = net,
            weight = rule.officialWeight
        )
    }

    private fun unavailable(
        paperId: String,
        lessons: List<ExamLessonScore>,
        reason: String
    ): ExamPaperCalculatedScore = ExamPaperCalculatedScore(
        paperId = paperId,
        net = lessons.sumOf { it.net },
        calculatedScore = null,
        maximumScore = MAX_SCORE,
        scope = ExamCalculatedScoreScope.UNAVAILABLE,
        lessons = lessons,
        note = reason
    )

    private fun canonicalLessonId(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw
            .lowercase(Locale.ROOT)
            .replace('ı', 'i')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')
            .filter { it.isLetterOrDigit() }

        val structuredIndex = Regex("^answers?(\\d+)$")
            .matchEntire(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val structuredLesson = when (structuredIndex) {
            1 -> "turkce"
            2 -> "inkilap"
            3 -> "din"
            4 -> "yabanci"
            5 -> "matematik"
            6 -> "fen"
            else -> null
        }
        if (structuredLesson != null) return structuredLesson

        return when {
            "turk" in normalized -> "turkce"
            normalized == "mat" || "matematik" in normalized -> "matematik"
            normalized == "fen" || "fenbil" in normalized -> "fen"
            "inkilap" in normalized || "ataturk" in normalized -> "inkilap"
            normalized == "din" || "dinkultur" in normalized -> "din"
            "yabanci" in normalized || "ingiliz" in normalized || "foreign" in normalized -> "yabanci"
            else -> null
        }
    }

    private data class ReferenceRule(
        val lessonId: String,
        val displayName: String,
        val questionCount: Int,
        val officialWeight: Double,
        val pointsPerNet: Double
    )

    private val RULES = listOf(
        ReferenceRule("turkce", "Türkçe", 20, 4.0, 4.19),
        ReferenceRule("matematik", "Matematik", 20, 4.0, 4.99),
        ReferenceRule("fen", "Fen Bilimleri", 20, 4.0, 3.83),
        ReferenceRule("inkilap", "T.C. İnkılap Tarihi ve Atatürkçülük", 10, 1.0, 1.70),
        ReferenceRule("din", "Din Kültürü ve Ahlak Bilgisi", 10, 1.0, 1.92),
        ReferenceRule("yabanci", "Yabancı Dil", 10, 1.0, 1.62)
    )

    private const val MIN_SCORE = 100.0
    private const val MAX_SCORE = 500.0
    private val BASE_SCORE = MAX_SCORE - RULES.sumOf { rule -> rule.questionCount * rule.pointsPerNet }
}
