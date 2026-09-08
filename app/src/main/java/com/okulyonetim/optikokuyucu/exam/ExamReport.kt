package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.scoring.ExamScore
import com.okulyonetim.optikokuyucu.omr.scoring.OmrScorer
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.round

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
    /** User-facing result: raw net or configured/scaled score depending on the exam scoring setup. */
    val points: Double?,
    val maximumPoints: Double?,
    val status: ExamReportRowStatus,
    val net: Double? = null,
    val overallRank: Int? = null,
    val classRank: Int? = null,
    val scoreScope: ExamCalculatedScoreScope? = null,
    val scoreNote: String = "",
    val lessons: List<ExamLessonScore> = emptyList()
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
}

private data class ReportDraft(
    val ordinal: Int,
    val scanRecordId: String,
    val studentName: String,
    val className: String,
    val studentNumber: String,
    val bookletCode: String,
    val capturedAtEpochMs: Long?,
    val score: ExamScore?,
    val status: ExamReportRowStatus
)

object ExamReportBuilder {
    fun build(
        exam: Exam,
        records: List<ScanRecord>,
        answerKeys: List<StoredAnswerKey>,
        generatedAtEpochMs: Long = System.currentTimeMillis()
    ): ExamReport {
        val recordsById = records.associateBy { it.id }
        val scoringPolicy = ExamScoringPolicyResolver.resolve(exam)

        val drafts = exam.papers.mapIndexed { index, link ->
            val record = recordsById[link.scanRecordId]
            if (record == null) {
                ReportDraft(
                    ordinal = index + 1,
                    scanRecordId = link.scanRecordId,
                    studentName = link.studentName,
                    className = link.className,
                    studentNumber = link.studentNumber,
                    bookletCode = link.bookletCode,
                    capturedAtEpochMs = null,
                    score = null,
                    status = ExamReportRowStatus.SCAN_MISSING
                )
            } else {
                val metadata = ExamPaperResolution.metadata(link, record)
                val key = ExamPaperResolution.answerKey(link, record, answerKeys)
                val score = key?.let { stored ->
                    runCatching {
                        OmrScorer.score(record, stored.answerKey, scoringPolicy)
                    }.getOrNull()
                }
                val status = when {
                    score == null -> ExamReportRowStatus.NO_ANSWER_KEY
                    score.confidentlyEvaluated -> ExamReportRowStatus.SCORED
                    else -> ExamReportRowStatus.REVIEW_REQUIRED
                }
                ReportDraft(
                    ordinal = index + 1,
                    scanRecordId = record.id,
                    studentName = link.studentName,
                    className = metadata.className,
                    studentNumber = metadata.studentNumber,
                    bookletCode = metadata.bookletCode,
                    capturedAtEpochMs = record.capturedAtEpochMs,
                    score = score,
                    status = status
                )
            }
        }

        val calculatedScores = ExamScoreEngine.calculate(
            exam = exam,
            papers = drafts.mapNotNull { draft ->
                draft.score?.let { score -> ExamPaperScoreInput(draft.scanRecordId, score) }
            }
        )

        val baseRows = drafts.map { draft ->
            val score = draft.score
            val calculated = calculatedScores[draft.scanRecordId]
            ExamReportRow(
                ordinal = draft.ordinal,
                scanRecordId = draft.scanRecordId,
                studentName = draft.studentName,
                className = draft.className,
                studentNumber = draft.studentNumber,
                bookletCode = draft.bookletCode,
                capturedAtEpochMs = draft.capturedAtEpochMs,
                correct = score?.correctCount,
                wrong = score?.wrongCount,
                blank = score?.blankCount,
                doubleMark = score?.doubleMarkCount,
                suspicious = score?.suspiciousCount,
                noKey = score?.noKeyCount,
                points = calculated?.calculatedScore,
                maximumPoints = calculated?.maximumScore,
                status = draft.status,
                net = calculated?.net ?: score?.totalPoints,
                scoreScope = calculated?.scope,
                scoreNote = calculated?.note.orEmpty(),
                lessons = calculated?.lessons.orEmpty()
            )
        }

        val overallRanks = rankByScore(baseRows)
        val classRanks = baseRows
            .filter { it.className.isNotBlank() }
            .groupBy { it.className }
            .values
            .flatMap { classRows -> rankByScore(classRows).entries }
            .associate { it.key to it.value }
        val rows = baseRows.map { row ->
            row.copy(
                overallRank = overallRanks[row.scanRecordId],
                classRank = classRanks[row.scanRecordId]
            )
        }

        return ExamReport(
            examId = exam.id,
            examName = exam.name,
            schoolName = exam.schoolName,
            generatedAtEpochMs = generatedAtEpochMs,
            rows = rows,
            scoringType = exam.scoringConfiguration.type
        )
    }

    /**
     * Uses standard competition ranking (1, 2, 2, 4) and the same two-decimal precision shown in
     * reports. Students whose displayed score is equal therefore never receive contradictory ranks.
     */
    private fun rankByScore(rows: List<ExamReportRow>): Map<String, Int> {
        val eligible = rows
            .filter { it.status == ExamReportRowStatus.SCORED && it.points != null }
            .sortedWith(
                compareByDescending<ExamReportRow> { rankingScore(requireNotNull(it.points)) }
                    .thenBy { it.ordinal }
            )
        val result = linkedMapOf<String, Int>()
        var previousScore: Double? = null
        var currentRank = 0
        eligible.forEachIndexed { index, row ->
            val score = rankingScore(requireNotNull(row.points))
            if (previousScore == null || score != previousScore) {
                currentRank = index + 1
                previousScore = score
            }
            result[row.scanRecordId] = currentRank
        }
        return result
    }

    private fun rankingScore(value: Double): Double = round(value * 100.0) / 100.0
}

fun examLessonDisplayName(lessonId: String): String {
    val normalized = lessonId.trim().lowercase(Locale.ROOT)
    return when (normalized) {
        "genel" -> "Genel"
        "turkce" -> "Türkçe"
        "matematik" -> "Matematik"
        "fen" -> "Fen Bilimleri"
        "inkilap" -> "İnkılap Tarihi"
        "din" -> "Din Kültürü"
        "yabanci" -> "Yabancı Dil"
        "sosyal" -> "Sosyal Bilgiler"
        else -> lessonId
            .replace('-', ' ')
            .replace('_', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.forLanguageTag("tr-TR")) } }
            .ifBlank { lessonId }
    }
}

fun examLessonDetailsText(lessons: List<ExamLessonScore>): String =
    lessons.joinToString(" | ") { lesson ->
        buildString {
            append(examLessonDisplayName(lesson.lessonId))
            append(": D ").append(lesson.correct)
            append(" Y ").append(lesson.wrong)
            append(" B ").append(lesson.blank)
            append(" N ").append(formatLessonNumber(lesson.net))
            if (lesson.weight != 1.0) {
                append(" K ").append(formatLessonNumber(lesson.weight))
            }
            lesson.standardScore?.let { append(" SP ").append(formatLessonNumber(it)) }
            lesson.weightedStandardScore?.let { append(" ASP ").append(formatLessonNumber(it)) }
        }
    }

private fun formatLessonNumber(value: Double): String =
    String.format(Locale("tr", "TR"), "%.2f", value)

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
                "Puan",
                "Maksimum",
                "Genel Sıra",
                "Sınıf Sırası",
                "Durum",
                "Ders Detayları",
                "Puan Notu",
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
                row.points?.let(::formatNumber).orEmpty(),
                row.maximumPoints?.let(::formatNumber).orEmpty(),
                row.overallRank?.toString().orEmpty(),
                row.classRank?.toString().orEmpty(),
                statusLabel(row.status),
                examLessonDetailsText(row.lessons),
                row.scoreNote,
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
