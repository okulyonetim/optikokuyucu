package com.okulyonetim.optikokuyucu.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.exam.ExamReport
import com.okulyonetim.optikokuyucu.exam.ExamReportBuilder
import com.okulyonetim.optikokuyucu.exam.ExamReportCsvExporter
import com.okulyonetim.optikokuyucu.exam.ExamReportRow
import com.okulyonetim.optikokuyucu.exam.ExamReportRowStatus
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            status = "Sınav CSV raporu kaydedildi"
        }.onFailure { error ->
            status = "CSV kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    val current = exam
    if (current == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Sınav kaydı bulunamadı.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onBack) { Text("Sınava dön") }
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
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ExamReportSummary(report = report, status = status)
            }

            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    enabled = report.rows.isNotEmpty(),
                    onClick = {
                        pendingCsv = ExamReportCsvExporter.export(report)
                        csvLauncher.launch(reportFileName(current.name))
                    }
                ) {
                    Text("CSV Sonuç Raporunu Dışa Aktar")
                }
            }

            if (report.rows.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Henüz raporlanacak kağıt yok", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Öğrenci kağıtları bu sınava bağlandıkça sonuçlar burada otomatik oluşur.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Sınav Özeti", style = MaterialTheme.typography.titleLarge)
            Text(
                "${report.schoolName} · ${report.paperCount} kağıt",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProductStatusBadge("Puanlandı ${report.scoredCount}", ProductBadgeTone.GREEN)
                ProductStatusBadge("Kontrol ${report.reviewRequiredCount}", ProductBadgeTone.ORANGE)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProductStatusBadge("Anahtar yok ${report.noAnswerKeyCount}", ProductBadgeTone.ORANGE)
                ProductStatusBadge("Kayıt yok ${report.missingScanCount}", ProductBadgeTone.RED)
            }

            if (status.isNotBlank()) {
                Text(status, color = MaterialTheme.colorScheme.primary)
            }
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
        ExamReportRowStatus.REVIEW_REQUIRED -> "KONTROL GEREKLİ"
        ExamReportRowStatus.NO_ANSWER_KEY -> "ANAHTAR YOK"
        ExamReportRowStatus.SCAN_MISSING -> "KAYIT YOK"
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${row.ordinal}. $title",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                ProductStatusBadge(statusText, tone)
            }

            Text(
                listOf(
                    row.className.takeIf { it.isNotBlank() },
                    row.studentNumber.takeIf { it.isNotBlank() }?.let { "No $it" },
                    row.bookletCode.takeIf { it.isNotBlank() }?.let { "Kitapçık $it" }
                ).filterNotNull().joinToString(" · ").ifBlank { "Öğrenci bilgisi girilmedi" },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (row.points != null) {
                Text(
                    "D ${row.correct ?: 0} · Y ${row.wrong ?: 0} · B ${row.blank ?: 0} · " +
                        "Net/Puan ${formatReportNumber(row.points)}" +
                        (row.maximumPoints?.let { " / ${formatReportNumber(it)}" } ?: ""),
                    fontWeight = FontWeight.Medium
                )
                if ((row.doubleMark ?: 0) > 0 || (row.suspicious ?: 0) > 0 || (row.noKey ?: 0) > 0) {
                    Text(
                        "Çift ${row.doubleMark ?: 0} · Şüpheli ${row.suspicious ?: 0} · Anahtarsız ${row.noKey ?: 0}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            row.capturedAtEpochMs?.let {
                Text(
                    "Tarama: ${formatReportDate(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun reportFileName(examName: String): String {
    val safe = examName.trim()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
        .trim('-')
        .take(48)
        .ifBlank { "sinav" }
    return "$safe-sonuclar.csv"
}

private fun formatReportNumber(value: Double): String =
    String.format(Locale("tr", "TR"), "%.2f", value)

private fun formatReportDate(epochMs: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR")).format(Date(epochMs))
