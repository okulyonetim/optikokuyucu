package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.scoring.OmrScorer
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val points: Double?,
    val maximumPoints: Double?,
    val status: ExamReportRowStatus
)

data class ExamReport(
    val examId: String,
    val examName: String,
    val schoolName: String,
    val generatedAtEpochMs: Long,
    val rows: List<ExamReportRow>
) {
    val paperCount: Int get() = rows.size
    val scoredCount: Int get() = rows.count { it.status == ExamReportRowStatus.SCORED }
    val reviewRequiredCount: Int get() = rows.count { it.status == ExamReportRowStatus.REVIEW_REQUIRED }
    val noAnswerKeyCount: Int get() = rows.count { it.status == ExamReportRowStatus.NO_ANSWER_KEY }
    val missingScanCount: Int get() = rows.count { it.status == ExamReportRowStatus.SCAN_MISSING }
}

object ExamReportBuilder {
    fun build(
        exam: Exam,
        records: List<ScanRecord>,
        answerKeys: List<StoredAnswerKey>,
        generatedAtEpochMs: Long = System.currentTimeMillis()
    ): ExamReport {
        val recordsById = records.associateBy { it.id }
        val scoringPolicy = ExamScoringPolicyResolver.resolve(exam.wrongAnswerPolicy)

        val rows = exam.papers.mapIndexed { index, link ->
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
            val status = when {
                score == null -> ExamReportRowStatus.NO_ANSWER_KEY
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

        return ExamReport(
            examId = exam.id,
            examName = exam.name,
            schoolName = exam.schoolName,
            generatedAtEpochMs = generatedAtEpochMs,
            rows = rows
        )
    }
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
                "Net / Puan",
                "Maksimum",
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
                row.points?.let(::formatNumber).orEmpty(),
                row.maximumPoints?.let(::formatNumber).orEmpty(),
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
