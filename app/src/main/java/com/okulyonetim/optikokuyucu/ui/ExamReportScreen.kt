package com.okulyonetim.optikokuyucu.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.exam.ExamReport
import com.okulyonetim.optikokuyucu.exam.ExamReportBuilder
import com.okulyonetim.optikokuyucu.exam.ExamReportCsvExporter
import com.okulyonetim.optikokuyucu.exam.ExamReportPdfExporter
import com.okulyonetim.optikokuyucu.exam.ExamReportRow
import com.okulyonetim.optikokuyucu.exam.ExamReportRowStatus
import com.okulyonetim.optikokuyucu.exam.ExamReportXlsxExporter
import com.okulyonetim.optikokuyucu.exam.ExamScoringType
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class SavedExamReport(
    val uri: Uri,
    val mimeType: String
)

@Composable
fun ExamReportScreen(
    examId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val examRepository = remember(context) { FileExamRepository(appContext) }
    val scanRepository = remember(context) { FileScanRecordRepository(appContext) }
    val keyRepository = remember(context) { FileAnswerKeyRepository(appContext) }

    var exam by remember(examId) { mutableStateOf(examRepository.load(examId)) }
    var records by remember { mutableStateOf(scanRepository.list()) }
    var keys by remember { mutableStateOf(keyRepository.list()) }
    var status by remember { mutableStateOf("") }
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    var pendingXlsx by remember { mutableStateOf<ByteArray?>(null) }
    var pendingPdf by remember { mutableStateOf<ExamReport?>(null) }
    var lastSavedReport by remember { mutableStateOf<SavedExamReport?>(null) }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val csv = pendingCsv
        pendingCsv = null
        if (uri == null || csv == null) return@rememberLauncherForActivityResult

        runCatching {
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "CSV çıktı akışı açılamadı." }
                output.write(csv.toByteArray(Charsets.UTF_8))
                output.flush()
            }
        }.onSuccess {
            lastSavedReport = SavedExamReport(uri, "text/csv")
            status = "CSV raporu kaydedildi · paylaşmaya hazır"
        }.onFailure { error ->
            status = "CSV kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    val xlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExamReportXlsxExporter.MIME_TYPE)
    ) { uri ->
        val workbook = pendingXlsx
        pendingXlsx = null
        if (uri == null || workbook == null) return@rememberLauncherForActivityResult

        runCatching {
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "Excel çıktı akışı açılamadı." }
                output.write(workbook)
                output.flush()
            }
        }.onSuccess {
            lastSavedReport = SavedExamReport(uri, ExamReportXlsxExporter.MIME_TYPE)
            status = "Excel raporu kaydedildi · paylaşmaya hazır"
        }.onFailure { error ->
            status = "Excel kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExamReportPdfExporter.MIME_TYPE)
    ) { uri ->
        val pdfReport = pendingPdf
        pendingPdf = null
        if (uri == null || pdfReport == null) return@rememberLauncherForActivityResult

        runCatching {
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "PDF çıktı akışı açılamadı." }
                ExamReportPdfExporter.export(pdfReport, output)
            }
        }.onSuccess {
            lastSavedReport = SavedExamReport(uri, ExamReportPdfExporter.MIME_TYPE)
            status = "PDF raporu kaydedildi · paylaşmaya hazır"
        }.onFailure { error ->
            status = "PDF kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    val current = exam
    if (current == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProductEmptyState(
                title = "Sınav kaydı bulunamadı",
                body = "Raporu açmak için geçerli bir sınav kaydı gerekir."
            )
            TextButton(onClick = onBack) { Text("Sınava dön") }
        }
        return
    }

    val report = remember(current, records, keys) {
        ExamReportBuilder.build(
            exam = current,
            records = records,
            answerKeys = keys
        )
    }

    fun refresh() {
        exam = examRepository.load(examId)
        records = scanRepository.list()
        keys = keyRepository.list()
        status = "Rapor yenilendi"
    }

    fun shareLastSavedReport() {
        val saved = lastSavedReport ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = saved.mimeType
            putExtra(Intent.EXTRA_STREAM, saved.uri)
            putExtra(Intent.EXTRA_SUBJECT, "Sınav Sonuç Raporu")
            clipData = ClipData.newRawUri("Sınav sonuç raporu", saved.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(shareIntent, "Sınav raporunu paylaş"))
        }.onFailure { error ->
            status = "Paylaşım açılamadı: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ProductTopBar(
                title = "${current.name} · Rapor",
                leadingText = "‹",
                onLeadingClick = onBack,
                actionText = "↻",
                onActionClick = ::refresh
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                ExamReportSummary(report = report, status = status)
            }

            item {
                ProductMetricStrip(
                    metrics = listOf(
                        "Puanlandı" to report.scoredCount.toString(),
                        "Kontrol" to report.reviewRequiredCount.toString(),
                        "Anahtar yok" to report.noAnswerKeyCount.toString(),
                        "Kayıt yok" to report.missingScanCount.toString()
                    )
                )
            }

            item {
                ProductSettingsSection(
                    title = "Raporu Dışa Aktar",
                    description = "Sonuçları CSV, Excel veya PDF olarak kaydedin."
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = report.rows.isNotEmpty(),
                            onClick = {
                                pendingCsv = ExamReportCsvExporter.export(report)
                                csvLauncher.launch(reportFileName(current.name, "csv"))
                            }
                        ) {
                            Text("CSV", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = report.rows.isNotEmpty(),
                            onClick = {
                                pendingXlsx = ExamReportXlsxExporter.export(report)
                                xlsxLauncher.launch(reportFileName(current.name, "xlsx"))
                            }
                        ) {
                            Text("Excel", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = report.rows.isNotEmpty(),
                            onClick = {
                                pendingPdf = report
                                pdfLauncher.launch(reportFileName(current.name, "pdf"))
                            }
                        ) {
                            Text("PDF", fontSize = 11.sp)
                        }
                    }

                    if (lastSavedReport != null) {
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = ::shareLastSavedReport
                        ) {
                            Text("Son kaydedilen raporu paylaş", fontSize = 11.sp)
                        }
                    }
                }
            }

            if (report.rows.isEmpty()) {
                item {
                    ProductEmptyState(
                        title = "Henüz raporlanacak kağıt yok",
                        body = "Öğrenci kağıtları bu sınava bağlandıkça sonuçlar burada otomatik oluşur."
                    )
                }
            } else {
                items(report.rows, key = { it.scanRecordId }) { row ->
                    ExamReportRowCard(row)
                }
            }
        }
    }
}

@Composable
private fun ExamReportSummary(report: ExamReport, status: String) {
    ProductSettingsSection(
        title = "Sınav Özeti",
        description = "${report.schoolName} · ${report.paperCount} kağıt"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductStatusBadge("Puanlandı ${report.scoredCount}", ProductBadgeTone.GREEN)
            ProductStatusBadge(scoringTypeLabel(report.scoringType), ProductBadgeTone.NEUTRAL)
            if (report.reviewRequiredCount > 0) {
                ProductStatusBadge("Kontrol ${report.reviewRequiredCount}", ProductBadgeTone.ORANGE)
            }
        }
        if (report.scoringType == ExamScoringType.LGS || report.scoringType == ExamScoringType.IOKBS) {
            Text(
                "MEB yöntemi yerel sınav grubunun istatistikleriyle uygulanır; gösterilen puan resmî ulusal MEB sonucu değildir.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp
            )
        }
        if (status.isNotBlank()) {
            Text(
                text = status,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExamReportRowCard(row: ExamReportRow) {
    val title = row.studentName.ifBlank {
        row.studentNumber.takeIf { it.isNotBlank() }?.let { "Öğrenci $it" } ?: "İsimsiz Öğrenci"
    }
    val tone = when (row.status) {
        ExamReportRowStatus.SCORED -> ProductBadgeTone.GREEN
        ExamReportRowStatus.REVIEW_REQUIRED,
        ExamReportRowStatus.NO_ANSWER_KEY -> ProductBadgeTone.ORANGE
        ExamReportRowStatus.SCAN_MISSING -> ProductBadgeTone.RED
    }
    val statusText = when (row.status) {
        ExamReportRowStatus.SCORED -> "PUANLANDI"
        ExamReportRowStatus.REVIEW_REQUIRED -> "KONTROL"
        ExamReportRowStatus.NO_ANSWER_KEY -> "ANAHTAR YOK"
        ExamReportRowStatus.SCAN_MISSING -> "KAYIT YOK"
    }

    ProductCompactCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProductInitialBadge(row.ordinal.toString())
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOf(
                            row.className.takeIf { it.isNotBlank() },
                            row.studentNumber.takeIf { it.isNotBlank() }?.let { "No $it" },
                            row.bookletCode.takeIf { it.isNotBlank() }?.let { "Kitapçık $it" }
                        ).filterNotNull().joinToString(" · ").ifBlank { "Öğrenci bilgisi girilmedi" },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ProductStatusBadge(statusText, tone)
            }

            if (row.net != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "D ${row.correct ?: 0} · Y ${row.wrong ?: 0} · B ${row.blank ?: 0}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        row.points?.let { score ->
                            "${formatReportNumber(score)}${row.maximumPoints?.let { " / ${formatReportNumber(it)}" } ?: ""}"
                        } ?: "Puan —",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    buildString {
                        append("Net ").append(formatReportNumber(row.net))
                        row.overallRank?.let { append(" · Genel ").append(it).append(".") }
                        row.classRank?.let { append(" · Sınıf ").append(it).append(".") }
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                if ((row.doubleMark ?: 0) > 0 || (row.suspicious ?: 0) > 0 || (row.noKey ?: 0) > 0) {
                    Text(
                        "Çift ${row.doubleMark ?: 0} · Şüpheli ${row.suspicious ?: 0} · Anahtarsız ${row.noKey ?: 0}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (row.scoreNote.isNotBlank()) {
                    Text(
                        row.scoreNote,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            row.capturedAtEpochMs?.let {
                Text(
                    "Tarama · ${formatReportDate(it)}",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun scoringTypeLabel(type: ExamScoringType): String = when (type) {
    ExamScoringType.NORMAL -> "NORMAL"
    ExamScoringType.SINGLE_SUBJECT -> "TEK DERS"
    ExamScoringType.LGS -> "LGS"
    ExamScoringType.IOKBS -> "İOKBS"
    ExamScoringType.CUSTOM -> "ÖZEL"
}

private fun reportFileName(examName: String, extension: String): String {
    val safe = examName.trim()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
        .trim('-')
        .take(48)
        .ifBlank { "sinav" }
    return "$safe-sonuclar.$extension"
}

private fun formatReportNumber(value: Double): String =
    String.format(Locale("tr", "TR"), "%.2f", value)

private fun formatReportDate(epochMs: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR")).format(Date(epochMs))
