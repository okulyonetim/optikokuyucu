package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.scoring.ExamScore
import com.okulyonetim.optikokuyucu.omr.scoring.OmrScorer
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluationState
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class ExamReportRowStatus {
    SCORED,
    REVIEW_REQUIRED,
    NO_ANSWER_KEY,
    SCAN_MISSING
}

data class ExamReportRow(
    val ordinal: Int,
    val scanRecordId: String,
    val studentName: String,
    val className: String,
    val studentNumber: String,
    val bookletCode: String,
    val capturedAtEpochMs: Long?,
    val correct: Int?,
    val wrong: Int?,
    val blank: Int?,
    val doubleMark: Int?,
    val suspicious: Int?,
    val noKey: Int?,
    /** Legacy low-level OMR point total retained for compatibility. Prefer [net]. */
    val points: Double?,
    /** Legacy low-level maximum retained for compatibility. */
    val maximumPoints: Double?,
    val status: ExamReportRowStatus,
    val net: Double? = null,
    val calculatedScore: Double? = null,
    val calculatedMaximum: Double? = null,
    val scoreScope: ExamCalculatedScoreScope? = null,
    val scoreNote: String = "",
    val lessonScores: List<ExamLessonScore> = emptyList(),
    val overallRank: Int? = null,
    val classRank: Int? = null,
    val institutionRank: Int? = null
)

data class ExamReport(
    val examId: String,
    val examName: String,
    val schoolName: String,
    val generatedAtEpochMs: Long,
    val rows: List<ExamReportRow>,
    val scoringType: ExamScoringType = ExamScoringType.NORMAL
) {
    val paperCount: Int get() = rows.size
    val scoredCount: Int get() = rows.count { it.status == ExamReportRowStatus.SCORED }
    val reviewRequiredCount: Int get() = rows.count { it.status == ExamReportRowStatus.REVIEW_REQUIRED }
    val noAnswerKeyCount: Int get() = rows.count { it.status == ExamReportRowStatus.NO_ANSWER_KEY }
    val missingScanCount: Int get() = rows.count { it.status == ExamReportRowStatus.SCAN_MISSING }
    val calculatedScoreCount: Int get() = rows.count { it.calculatedScore != null }
}

object ExamReportBuilder {
    fun build(
        exam: Exam,
        records: List<ScanRecord>,
        answerKeys: List<StoredAnswerKey>,
        generatedAtEpochMs: Long = System.currentTimeMillis()
    ): ExamReport {
        val recordsById = records.associateBy { it.id }
        val scoringPolicy = ExamScoringPolicyResolver.resolve(exam)
        val scoresByScanId = linkedMapOf<String, ExamScore>()

        val baseRows = exam.papers.mapIndexed { index, link ->
            val record = recordsById[link.scanRecordId]
            if (record == null) {
                return@mapIndexed ExamReportRow(
                    ordinal = index + 1,
                    scanRecordId = link.scanRecordId,
                    studentName = link.studentName,
                    className = link.className,
                    studentNumber = link.studentNumber,
                    bookletCode = link.bookletCode,
                    capturedAtEpochMs = null,
                    correct = null,
                    wrong = null,
                    blank = null,
                    doubleMark = null,
                    suspicious = null,
                    noKey = null,
                    points = null,
                    maximumPoints = null,
                    status = ExamReportRowStatus.SCAN_MISSING
                )
            }

            val metadata = ExamPaperResolution.metadata(link, record)
            val key = ExamPaperResolution.answerKey(link, record, answerKeys)
            val score = key?.let { stored ->
                runCatching {
                    OmrScorer.score(record, stored.answerKey, scoringPolicy)
                }.getOrNull()
            }
            if (score != null) scoresByScanId[record.id] = score
            val status = when {
                score == null -> ExamReportRowStatus.NO_ANSWER_KEY
                isOfficialMebScoring(exam) && score.isMebDeterministic -> ExamReportRowStatus.SCORED
                score.confidentlyEvaluated -> ExamReportRowStatus.SCORED
                else -> ExamReportRowStatus.REVIEW_REQUIRED
            }

            ExamReportRow(
                ordinal = index + 1,
                scanRecordId = record.id,
                studentName = link.studentName,
                className = metadata.className,
                studentNumber = metadata.studentNumber,
                bookletCode = metadata.bookletCode,
                capturedAtEpochMs = record.capturedAtEpochMs,
                correct = score?.correctCount,
                wrong = score?.wrongCount,
                blank = score?.blankCount,
                doubleMark = score?.doubleMarkCount,
                suspicious = score?.suspiciousCount,
                noKey = score?.noKeyCount,
                points = score?.totalPoints,
                maximumPoints = key?.answerKey?.answers?.size?.times(scoringPolicy.correctPoints),
                status = status
            )
        }

        val calculatedByScanId = ExamScoreEngine.calculate(
            exam = exam,
            papers = scoresByScanId.map { (scanRecordId, score) ->
                ExamPaperScoreInput(paperId = scanRecordId, score = score)
            }
        )

        val calculatedRows = baseRows.map { row ->
            val calculated = calculatedByScanId[row.scanRecordId]
            row.copy(
                net = calculated?.net ?: row.points,
                calculatedScore = calculated?.calculatedScore,
                calculatedMaximum = calculated?.maximumScore,
                scoreScope = calculated?.scope,
                scoreNote = calculated?.note.orEmpty(),
                lessonScores = calculated?.lessons.orEmpty()
            )
        }
        val rankedRows = applyReportRanks(calculatedRows)

        return ExamReport(
            examId = exam.id,
            examName = exam.name,
            schoolName = exam.schoolName,
            generatedAtEpochMs = generatedAtEpochMs,
            rows = rankedRows,
            scoringType = exam.scoringConfiguration.type
        )
    }

    private fun isOfficialMebScoring(exam: Exam): Boolean =
        exam.scoringConfiguration.type == ExamScoringType.LGS ||
            exam.scoringConfiguration.type == ExamScoringType.IOKBS

    private val ExamScore.isMebDeterministic: Boolean
        get() = evaluations.all { evaluation ->
            evaluation.state != QuestionEvaluationState.SUSPICIOUS &&
                evaluation.state != QuestionEvaluationState.NO_KEY
        }

    private fun applyReportRanks(rows: List<ExamReportRow>): List<ExamReportRow> {
        val scoreable = rows.filter {
            it.status == ExamReportRowStatus.SCORED && it.calculatedScore != null
        }
        val overallRanks = rankMap(scoreable)
        val classRanks = scoreable
            .filter { it.className.isNotBlank() }
            .groupBy { normalizeClassKey(it.className) }
            .mapValues { (_, classRows) -> rankMap(classRows) }

        return rows.map { row ->
            val overall = overallRanks[row.scanRecordId]
            row.copy(
                overallRank = overall,
                classRank = if (row.className.isBlank()) {
                    null
                } else {
                    classRanks[normalizeClassKey(row.className)]?.get(row.scanRecordId)
                },
                institutionRank = overall
            )
        }
    }

    private fun rankMap(rows: List<ExamReportRow>): Map<String, Int> {
        val sorted = rows.sortedWith(
            compareByDescending<ExamReportRow> { it.calculatedScore }
                .thenBy { it.ordinal }
        )
        val result = linkedMapOf<String, Int>()
        var previousScore: Double? = null
        var currentRank = 0
        sorted.forEachIndexed { index, row ->
            val score = requireNotNull(row.calculatedScore)
            if (previousScore == null || abs(score - requireNotNull(previousScore)) > RANK_EPSILON) {
                currentRank = index + 1
                previousScore = score
            }
            result[row.scanRecordId] = currentRank
        }
        return result
    }

    private fun normalizeClassKey(value: String): String =
        value.trim().uppercase(Locale.forLanguageTag("tr-TR"))

    private const val RANK_EPSILON = 1e-9
}

/** Excel-friendly UTF-8/semicolon CSV for one offline exam. */
object ExamReportCsvExporter {
    fun export(report: ExamReport): String = buildString {
        append('\uFEFF')
        appendLine(
            listOf(
                "Sıra",
                "Öğrenci",
                "Sınıf",
                "Numara",
                "Kitapçık",
                "Tarama Tarihi",
                "Doğru",
                "Yanlış",
                "Boş",
                "Çift İşaret",
                "Şüpheli",
                "Anahtarsız",
                "Net",
                "Hesaplanan Puan",
                "Puan Tavanı",
                "Puan Türü",
                "Genel Sıra",
                "Sınıf Sıra",
                "Kurum Sıra",
                "Puan Notu",
                "Durum",
                "Kayıt ID"
            ).joinToString(";") { escape(it) }
        )

        report.rows.forEach { row ->
            val fields = listOf(
                row.ordinal.toString(),
                row.studentName,
                row.className,
                row.studentNumber,
                row.bookletCode,
                row.capturedAtEpochMs?.let(::formatDate).orEmpty(),
                row.correct?.toString().orEmpty(),
                row.wrong?.toString().orEmpty(),
                row.blank?.toString().orEmpty(),
                row.doubleMark?.toString().orEmpty(),
                row.suspicious?.toString().orEmpty(),
                row.noKey?.toString().orEmpty(),
                row.net?.let(::formatNumber).orEmpty(),
                row.calculatedScore?.let(::formatNumber).orEmpty(),
                row.calculatedMaximum?.let(::formatNumber).orEmpty(),
                examScoreScopeLabel(row.scoreScope),
                row.overallRank?.toString().orEmpty(),
                row.classRank?.toString().orEmpty(),
                row.institutionRank?.toString().orEmpty(),
                row.scoreNote,
                statusLabel(row.status),
                row.scanRecordId
            )
            appendLine(fields.joinToString(";") { escape(it) })
        }
    }

    private fun statusLabel(status: ExamReportRowStatus): String = when (status) {
        ExamReportRowStatus.SCORED -> "PUANLANDI"
        ExamReportRowStatus.REVIEW_REQUIRED -> "KONTROL_GEREKLİ"
        ExamReportRowStatus.NO_ANSWER_KEY -> "ANAHTAR_YOK"
        ExamReportRowStatus.SCAN_MISSING -> "TARAMA_YOK"
    }

    private fun formatDate(epochMs: Long): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("tr", "TR")).format(Date(epochMs))

    private fun formatNumber(value: Double): String =
        String.format(Locale("tr", "TR"), "%.2f", value)

    private fun escape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ';' || it == '\n' || it == '\r' || it == '\"' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}

fun examScoreScopeLabel(scope: ExamCalculatedScoreScope?): String = when (scope) {
    ExamCalculatedScoreScope.RAW_NET -> "NET"
    ExamCalculatedScoreScope.SCALED -> "ÖLÇEKLİ PUAN"
    ExamCalculatedScoreScope.LOCAL_COHORT_MEB_METHOD -> "YEREL MEB YÖNTEMİ"
    ExamCalculatedScoreScope.UNAVAILABLE -> "HESAPLANAMADI"
    null -> ""
}
