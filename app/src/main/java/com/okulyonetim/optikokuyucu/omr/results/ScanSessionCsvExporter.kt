package com.okulyonetim.optikokuyucu.omr.results

import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeyResolver
import com.okulyonetim.optikokuyucu.omr.scoring.OmrScorer
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Excel-friendly UTF-8/semicolon CSV export for an offline scan session. */
object ScanSessionCsvExporter {
    fun export(
        records: List<ScanRecord>,
        answerKeys: List<StoredAnswerKey>
    ): String = buildString {
        append('\uFEFF')
        appendLine(
            listOf(
                "Sıra",
                "Kayıt ID",
                "Tarih",
                "Öğrenci No",
                "Kitapçık",
                "Şablon",
                "Sürüm",
                "Kaynak",
                "Doğru",
                "Yanlış",
                "Boş",
                "Çift İşaret",
                "Şüpheli",
                "Anahtarsız",
                "Puan",
                "Maksimum",
                "Durum"
            ).joinToString(";") { escape(it) }
        )

        records.sortedWith(
            compareBy<ScanRecord> { it.capturedAtEpochMs }.thenBy { it.id }
        ).forEachIndexed { index, record ->
            val key = AnswerKeyResolver.resolve(record, answerKeys)
            val score = key?.let { stored ->
                runCatching { OmrScorer.score(record, stored.answerKey) }.getOrNull()
            }
            val status = when {
                score == null -> "ANAHTAR_YOK"
                score.confidentlyEvaluated -> "PUANLANDI"
                else -> "KONTROL_GEREKLİ"
            }
            val fields = listOf(
                (index + 1).toString(),
                record.id,
                formatDate(record.capturedAtEpochMs),
                record.grid("studentNumber")?.value.orEmpty(),
                record.grid("booklet")?.value.orEmpty(),
                record.templateId,
                record.templateVersion.toString(),
                when (record.source) {
                    ScanSource.LIVE_CAMERA -> "Canlı kamera"
                    ScanSource.GALLERY -> "Galeri"
                },
                score?.correctCount?.toString().orEmpty(),
                score?.wrongCount?.toString().orEmpty(),
                score?.blankCount?.toString().orEmpty(),
                score?.doubleMarkCount?.toString().orEmpty(),
                score?.suspiciousCount?.toString().orEmpty(),
                score?.noKeyCount?.toString().orEmpty(),
                score?.totalPoints?.let(::formatNumber).orEmpty(),
                key?.answerKey?.answers?.size?.toDouble()?.let(::formatNumber).orEmpty(),
                status
            )
            appendLine(fields.joinToString(";") { escape(it) })
        }
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
