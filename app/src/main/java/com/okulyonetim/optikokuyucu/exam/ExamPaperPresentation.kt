package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.scoring.ExamScore
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluation
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluationState
import com.okulyonetim.optikokuyucu.omr.scoring.ScoringPolicy
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

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

    /** Low-level OMR point policy. Official exam families force the MEB 3-wrong rule. */
    fun resolve(exam: Exam): ScoringPolicy {
        val divisor = effectiveWrongDivisor(exam)
        return ScoringPolicy(wrongPoints = divisor?.let { -(1.0 / it) } ?: 0.0)
    }

    fun effectiveWrongDivisor(exam: Exam): Double? = when (exam.scoringConfiguration.type) {
        ExamScoringType.LGS,
        ExamScoringType.IOKBS -> 3.0
        ExamScoringType.CUSTOM -> exam.scoringConfiguration.customWrongAnswerDivisor
            ?: divisorFor(exam.wrongAnswerPolicy)
        ExamScoringType.NORMAL,
        ExamScoringType.SINGLE_SUBJECT -> divisorFor(exam.wrongAnswerPolicy)
    }

    private fun divisorFor(policy: WrongAnswerPolicy): Double? = when (policy) {
        WrongAnswerPolicy.KEEP_AS_IS -> null
        WrongAnswerPolicy.FOUR_WRONG_ONE_CORRECT -> 4.0
        WrongAnswerPolicy.THREE_WRONG_ONE_CORRECT -> 3.0
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

enum class ExamCalculatedScoreScope {
    RAW_NET,
    SCALED,
    LOCAL_COHORT_MEB_METHOD,
    UNAVAILABLE
}

data class ExamLessonScore(
    val lessonId: String,
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    val doubleMark: Int,
    val suspicious: Int,
    val noKey: Int,
    val net: Double,
    val weight: Double = 1.0,
    val standardScore: Double? = null,
    val weightedStandardScore: Double? = null
)

data class ExamPaperScoreInput(
    val paperId: String,
    val score: ExamScore
) {
    init {
        require(paperId.isNotBlank())
    }
}

data class ExamPaperCalculatedScore(
    val paperId: String,
    val net: Double,
    val calculatedScore: Double?,
    val maximumScore: Double?,
    val scope: ExamCalculatedScoreScope,
    val lessons: List<ExamLessonScore>,
    val note: String = ""
)

/**
 * Exam-level scoring layer built above OMR recognition/evaluation.
 *
 * NORMAL/SINGLE_SUBJECT/CUSTOM are deterministic from one paper. LGS and IOKBS follow the current
 * MEB standard-score/TASP method, which needs cohort statistics. Because national MEB cohort
 * statistics are not available to this offline app, these values are explicitly marked as local-cohort
 * MEB-method scores and must not be presented as official national result scores.
 */
object ExamScoreEngine {
    fun calculate(
        exam: Exam,
        papers: List<ExamPaperScoreInput>
    ): Map<String, ExamPaperCalculatedScore> = when (exam.scoringConfiguration.type) {
        ExamScoringType.LGS -> calculateMebCohort(exam, papers, LGS_RULES)
        ExamScoringType.IOKBS -> calculateMebCohort(exam, papers, IOKBS_RULES)
        ExamScoringType.NORMAL,
        ExamScoringType.SINGLE_SUBJECT,
        ExamScoringType.CUSTOM -> papers.associate { input ->
            input.paperId to calculateBasic(exam, input)
        }
    }

    private fun calculateBasic(exam: Exam, input: ExamPaperScoreInput): ExamPaperCalculatedScore {
        val divisor = ExamScoringPolicyResolver.effectiveWrongDivisor(exam)
        val evaluations = input.score.evaluations
        val grouped = evaluations.groupBy { questionLessonPrefix(it.questionId) ?: GENERAL_LESSON_ID }
        val configuredWeights = exam.scoringConfiguration.lessonWeights
        val lessons = grouped.entries
            .sortedBy { it.key }
            .map { (lessonId, lessonEvaluations) ->
                lessonScore(
                    lessonId = lessonId,
                    evaluations = lessonEvaluations,
                    wrongDivisor = divisor,
                    doubleMarkIsWrong = false,
                    weight = configuredWeights[lessonId] ?: 1.0
                )
            }
        val net = lessons.sumOf { it.net }
        val scorableCount = lessons.sumOf(::scorableQuestionCount)
        val configuration = exam.scoringConfiguration

        if (configuration.scoreMode == ExamScoreMode.RAW_NET) {
            return ExamPaperCalculatedScore(
                paperId = input.paperId,
                net = net,
                calculatedScore = net,
                maximumScore = scorableCount.toDouble(),
                scope = ExamCalculatedScoreScope.RAW_NET,
                lessons = lessons
            )
        }

        if (scorableCount <= 0) {
            return ExamPaperCalculatedScore(
                paperId = input.paperId,
                net = net,
                calculatedScore = null,
                maximumScore = configuration.maximumScore,
                scope = ExamCalculatedScoreScope.UNAVAILABLE,
                lessons = lessons,
                note = "Puanlanabilir cevap anahtarı sorusu bulunamadı."
            )
        }

        val normalizedRatio = if (
            configuration.type == ExamScoringType.CUSTOM && configuredWeights.isNotEmpty()
        ) {
            val weightedLessons = lessons.filter { scorableQuestionCount(it) > 0 }
            val weightTotal = weightedLessons.sumOf { it.weight }
            if (weightTotal <= 0.0) 0.0 else weightedLessons.sumOf { lesson ->
                val lessonMaximum = scorableQuestionCount(lesson).toDouble()
                lesson.weight * (lesson.net / lessonMaximum)
            } / weightTotal
        } else {
            net / scorableCount.toDouble()
        }

        val boundedRatio = normalizedRatio.coerceIn(0.0, 1.0)
        val calculated = configuration.minimumScore +
            boundedRatio * (configuration.maximumScore - configuration.minimumScore)
        return ExamPaperCalculatedScore(
            paperId = input.paperId,
            net = net,
            calculatedScore = calculated,
            maximumScore = configuration.maximumScore,
            scope = ExamCalculatedScoreScope.SCALED,
            lessons = lessons
        )
    }

    private fun calculateMebCohort(
        exam: Exam,
        papers: List<ExamPaperScoreInput>,
        rules: List<MebLessonRule>
    ): Map<String, ExamPaperCalculatedScore> {
        if (papers.isEmpty()) return emptyMap()

        val prepared = papers.associate { input ->
            input.paperId to prepareMebPaper(input, rules)
        }
        val eligible = prepared.values.filter { it.unavailableReason == null }
        if (eligible.size < 2) {
            return prepared.mapValues { (_, paper) ->
                unavailableMebScore(
                    paper,
                    paper.unavailableReason ?: "MEB standart puanı için en az iki geçerli öğrenci kağıdı gerekir."
                )
            }
        }

        val statistics = linkedMapOf<String, LessonStatistics>()
        for (rule in rules) {
            val values = eligible.map { requireNotNull(it.lessons[rule.lessonId]).net }
            val mean = values.average()
            val variance = values.sumOf { value ->
                val delta = value - mean
                delta * delta
            } / values.size.toDouble()
            val standardDeviation = sqrt(variance)
            if (standardDeviation <= EPSILON) {
                return prepared.mapValues { (_, paper) ->
                    unavailableMebScore(
                        paper,
                        "${rule.displayName} testinde standart sapma sıfır olduğu için yerel MEB puanı hesaplanamadı."
                    )
                }
            }
            statistics[rule.lessonId] = LessonStatistics(mean, standardDeviation)
        }

        val withStandards = eligible.associate { paper ->
            val scoredLessons = rules.map { rule ->
                val lesson = requireNotNull(paper.lessons[rule.lessonId])
                val stat = requireNotNull(statistics[rule.lessonId])
                val standard = 10.0 * ((lesson.net - stat.mean) / stat.standardDeviation) + 50.0
                lesson.copy(
                    weight = rule.weight,
                    standardScore = standard,
                    weightedStandardScore = standard * rule.weight
                )
            }
            paper.paperId to PreparedMebPaper(
                paperId = paper.paperId,
                lessons = scoredLessons.associateBy { it.lessonId },
                unavailableReason = null
            )
        }
        val taspByPaper = withStandards.mapValues { (_, paper) ->
            paper.lessons.values.sumOf { requireNotNull(it.weightedStandardScore) }
        }
        val smallestTasp = taspByPaper.values.minOrNull() ?: return emptyMap()
        val largestTasp = taspByPaper.values.maxOrNull() ?: return emptyMap()
        if (abs(largestTasp - smallestTasp) <= EPSILON) {
            return prepared.mapValues { (_, paper) ->
                unavailableMebScore(paper, "Yerel grupta TASP aralığı oluşmadığı için 100–500 puanı hesaplanamadı.")
            }
        }

        val configuration = exam.scoringConfiguration
        return prepared.mapValues { (paperId, original) ->
            original.unavailableReason?.let { reason ->
                return@mapValues unavailableMebScore(original, reason)
            }
            val scored = requireNotNull(withStandards[paperId])
            val tasp = requireNotNull(taspByPaper[paperId])
            val ratio = (tasp - smallestTasp) / (largestTasp - smallestTasp)
            val finalScore = configuration.minimumScore +
                ratio * (configuration.maximumScore - configuration.minimumScore)
            ExamPaperCalculatedScore(
                paperId = paperId,
                net = scored.lessons.values.sumOf { it.net },
                calculatedScore = finalScore.coerceIn(configuration.minimumScore, configuration.maximumScore),
                maximumScore = configuration.maximumScore,
                scope = ExamCalculatedScoreScope.LOCAL_COHORT_MEB_METHOD,
                lessons = rules.map { requireNotNull(scored.lessons[it.lessonId]) },
                note = "MEB formülü yerel sınav grubunun ortalama, standart sapma ve TASP aralığıyla uygulanmıştır; resmî ulusal sonuç değildir."
            )
        }
    }

    private fun prepareMebPaper(
        input: ExamPaperScoreInput,
        rules: List<MebLessonRule>
    ): PreparedMebPaper {
        val evaluationsByLesson = input.score.evaluations.groupBy { evaluation ->
            canonicalMebLessonId(questionLessonPrefix(evaluation.questionId))
        }
        val lessons = linkedMapOf<String, ExamLessonScore>()
        val missing = mutableListOf<String>()
        for (rule in rules) {
            val evaluations = evaluationsByLesson[rule.lessonId].orEmpty()
            if (evaluations.isEmpty()) {
                missing += rule.displayName
                continue
            }
            lessons[rule.lessonId] = lessonScore(
                lessonId = rule.lessonId,
                evaluations = evaluations,
                wrongDivisor = 3.0,
                doubleMarkIsWrong = true,
                weight = rule.weight
            )
        }
        val reviewLesson = lessons.values.firstOrNull { it.suspicious > 0 || it.noKey > 0 }
        val reason = when {
            missing.isNotEmpty() -> "MEB puanı için eksik test bölümü: ${missing.joinToString(", ")}."
            reviewLesson != null -> "Şüpheli veya cevap anahtarı eksik işaretler düzeltilmeden MEB puanı hesaplanmaz."
            else -> null
        }
        return PreparedMebPaper(
            paperId = input.paperId,
            lessons = lessons,
            unavailableReason = reason
        )
    }

    private fun unavailableMebScore(
        paper: PreparedMebPaper,
        reason: String
    ): ExamPaperCalculatedScore = ExamPaperCalculatedScore(
        paperId = paper.paperId,
        net = paper.lessons.values.sumOf { it.net },
        calculatedScore = null,
        maximumScore = 500.0,
        scope = ExamCalculatedScoreScope.UNAVAILABLE,
        lessons = paper.lessons.values.toList(),
        note = reason
    )

    private fun lessonScore(
        lessonId: String,
        evaluations: List<QuestionEvaluation>,
        wrongDivisor: Double?,
        doubleMarkIsWrong: Boolean,
        weight: Double
    ): ExamLessonScore {
        val correct = evaluations.count { it.state == QuestionEvaluationState.CORRECT }
        val plainWrong = evaluations.count { it.state == QuestionEvaluationState.WRONG }
        val blank = evaluations.count { it.state == QuestionEvaluationState.BLANK }
        val doubleMark = evaluations.count { it.state == QuestionEvaluationState.DOUBLE_MARK }
        val suspicious = evaluations.count { it.state == QuestionEvaluationState.SUSPICIOUS }
        val noKey = evaluations.count { it.state == QuestionEvaluationState.NO_KEY }
        val penaltyWrong = plainWrong + if (doubleMarkIsWrong) doubleMark else 0
        val net = if (wrongDivisor == null) {
            correct.toDouble()
        } else {
            correct - penaltyWrong / wrongDivisor
        }
        return ExamLessonScore(
            lessonId = lessonId,
            correct = correct,
            wrong = plainWrong,
            blank = blank,
            doubleMark = doubleMark,
            suspicious = suspicious,
            noKey = noKey,
            net = net,
            weight = weight
        )
    }

    private fun scorableQuestionCount(lesson: ExamLessonScore): Int =
        lesson.correct + lesson.wrong + lesson.blank + lesson.doubleMark + lesson.suspicious

    private data class PreparedMebPaper(
        val paperId: String,
        val lessons: Map<String, ExamLessonScore>,
        val unavailableReason: String?
    )

    private data class LessonStatistics(
        val mean: Double,
        val standardDeviation: Double
    )

    private data class MebLessonRule(
        val lessonId: String,
        val displayName: String,
        val weight: Double
    )

    private val LGS_RULES = listOf(
        MebLessonRule("turkce", "Türkçe", 4.0),
        MebLessonRule("matematik", "Matematik", 4.0),
        MebLessonRule("fen", "Fen Bilimleri", 4.0),
        MebLessonRule("inkilap", "T.C. İnkılap Tarihi ve Atatürkçülük", 1.0),
        MebLessonRule("din", "Din Kültürü ve Ahlak Bilgisi", 1.0),
        MebLessonRule("yabanci", "Yabancı Dil", 1.0)
    )

    private val IOKBS_RULES = listOf(
        MebLessonRule("turkce", "Türkçe/Türk Dili ve Edebiyatı", 3.0),
        MebLessonRule("matematik", "Matematik", 3.0),
        MebLessonRule("fen", "Fen Bilimleri", 3.0),
        MebLessonRule("sosyal", "Sosyal Bilgiler/Sosyal Bilimler", 3.0)
    )

    private const val GENERAL_LESSON_ID = "genel"
    private const val EPSILON = 1e-9
}

private fun canonicalMebLessonId(raw: String?): String? {
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
    return when {
        "turk" in normalized -> "turkce"
        normalized == "mat" || "matematik" in normalized -> "matematik"
        normalized == "fen" || "fenbil" in normalized -> "fen"
        "inkilap" in normalized || "ataturk" in normalized -> "inkilap"
        normalized == "din" || "dinkultur" in normalized -> "din"
        "yabanci" in normalized || "ingiliz" in normalized || "foreign" in normalized -> "yabanci"
        "sosyal" in normalized -> "sosyal"
        else -> normalized
    }
}

fun questionLessonPrefix(questionId: String): String? =
    questionId.substringBefore(':', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() && ':' in questionId }

fun questionDisplayNumber(questionId: String): String =
    questionId.substringAfterLast(':', missingDelimiterValue = questionId)
