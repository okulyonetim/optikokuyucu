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
import com.okulyonetim.optikokuyucu.exam.ExamReportPdfExporter
import com.okulyonetim.optikokuyucu.exam.ExamReportRow
import com.okulyonetim.optikokuyucu.exam.ExamReportRowStatus
import com.okulyonetim.optikokuyucu.exam.ExamReportXlsxExporter
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class SavedExamReport(val uri: Uri, val mimeType: String)

@Composable
fun ExamReportScreen(examId: String, onBack: () -> Unit) {
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

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
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
        }.onFailure { status = "CSV kaydedilemedi: ${it.message}" }
    }

    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExamReportXlsxExporter.MIME_TYPE)
    ) { uri ->
        val bytes = pendingXlsx
        pendingXlsx = null
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "Excel çıktı akışı açılamadı." }
                output.write(bytes)
                output.flush()
            }
        }.onSuccess {
            lastSavedReport = SavedExamReport(uri, ExamReportXlsxExporter.MIME_TYPE)
            status = "Excel raporu kaydedildi · paylaşmaya hazır"
        }.onFailure { status = "Excel kaydedilemedi: ${it.message}" }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExamReportPdfExporter.MIME_TYPE)
    ) { uri ->
        val report = pendingPdf
        pendingPdf = null
        if (uri == null || report == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "PDF çıktı akışı açılamadı." }
                ExamReportPdfExporter.export(report, output)
            }
        }.onSuccess {
            lastSavedReport = SavedExamReport(uri, ExamReportPdfExporter.MIME_TYPE)
            status = "PDF raporu kaydedildi · paylaşmaya hazır"
        }.onFailure { status = "PDF kaydedilemedi: ${it.message}" }
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
        ExamReportBuilder.build(current, records, keys)
    }

    fun refresh() {
        exam = examRepository.load(examId)
        records = scanRepository.list()
        keys = keyRepository.list()
        status = "Rapor yenilendi"
    }

    fun shareLastSavedReport() {
        val saved = lastSavedReport ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = saved.mimeType
            putExtra(Intent.EXTRA_STREAM, saved.uri)
            putExtra(Intent.EXTRA_SUBJECT, "Sınav Sonuç Raporu")
            clipData = ClipData.newRawUri("Sınav sonuç raporu", saved.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Sınav raporunu paylaş")) }
            .onFailure { status = "Paylaşım açılamadı: ${it.message}" }
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
            item { ExamReportSummary(report, status) }
            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    enabled = report.rows.isNotEmpty(),
                    onClick = {
                        pendingCsv = ExamReportCsvExporter.export(report)
                        csvLauncher.launch(reportFileName(current.name, "csv"))
                    }
                ) { Text("CSV Sonuç Raporunu Dışa Aktar") }
            }
            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    enabled = report.rows.isNotEmpty(),
                    onClick = {
                        pendingXlsx = ExamReportXlsxExporter.export(report)
                        xlsxLauncher.launch(reportFileName(current.name, "xlsx"))
                    }
                ) { Text("Excel (.xlsx) Sonuç Raporunu Dışa Aktar") }
            }
            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    enabled = report.rows.isNotEmpty(),
                    onClick = {
                        pendingPdf = report
                        pdfLauncher.launch(reportFileName(current.name, "pdf"))
                    }
                ) { Text("PDF Sonuç Raporunu Dışa Aktar") }
            }
            if (lastSavedReport != null) item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    onClick = ::shareLastSavedReport
                ) { Text("Son Kaydedilen Raporu Paylaş") }
            }
            if (report.rows.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Henüz raporlanacak kağıt yok", style = MaterialTheme.typography.titleMedium)
                            Text("Öğrenci kağıtları bu sınava bağlandıkça sonuçlar burada otomatik oluşur.")
                        }
                    }
                }
            } else {
                items(report.rows, key = { it.scanRecordId }) { row -> ExamReportRowCard(row) }
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
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sınav Özeti", style = MaterialTheme.typography.titleLarge)
            Text("${report.schoolName} · ${report.paperCount} kağıt")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProductStatusBadge("Puanlandı ${report.scoredCount}", ProductBadgeTone.GREEN)
                ProductStatusBadge("Kontrol ${report.reviewRequiredCount}", ProductBadgeTone.ORANGE)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProductStatusBadge("Anahtar yok ${report.noAnswerKeyCount}", ProductBadgeTone.ORANGE)
                ProductStatusBadge("Kayıt yok ${report.missingScanCount}", ProductBadgeTone.RED)
            }
            if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ExamReportRowCard(row: ExamReportRow) {
    val title = row.studentName.ifBlank {
        row.studentNumber.takeIf(String::isNotBlank)?.let { "Öğrenci $it" } ?: "İsimsiz Öğrenci"
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${row.ordinal}. $title", fontWeight = FontWeight.SemiBold)
                    Text(
                        listOf(row.className, row.studentNumber, row.bookletCode.takeIf(String::isNotBlank)?.let { "Kitapçık $it" })
                            .filterNotNull().filter(String::isNotBlank).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                ProductStatusBadge(reportStatusLabel(row.status), reportStatusTone(row.status))
            }
            if (row.points != null) {
                Text(
                    "Doğru ${row.correct ?: 0} · Yanlış ${row.wrong ?: 0} · Boş ${row.blank ?: 0} · Puan ${formatReportNumber(row.points)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun reportStatusLabel(status: ExamReportRowStatus): String = when (status) {
    ExamReportRowStatus.SCORED -> "PUANLANDI"
    ExamReportRowStatus.REVIEW_REQUIRED -> "KONTROL"
    ExamReportRowStatus.NO_ANSWER_KEY -> "ANAHTAR YOK"
    ExamReportRowStatus.SCAN_MISSING -> "KAYIT YOK"
}

private fun reportStatusTone(status: ExamReportRowStatus): ProductBadgeTone = when (status) {
    ExamReportRowStatus.SCORED -> ProductBadgeTone.GREEN
    ExamReportRowStatus.REVIEW_REQUIRED -> ProductBadgeTone.ORANGE
    ExamReportRowStatus.NO_ANSWER_KEY -> ProductBadgeTone.ORANGE
    ExamReportRowStatus.SCAN_MISSING -> ProductBadgeTone.RED
}

private fun reportFileName(name: String, extension: String): String {
    val safe = name.trim().replace(Regex("[^A-Za-z0-9ÇĞİÖŞÜçğıöşü._-]+"), "-").trim('-').ifBlank { "sinav" }
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    return "$safe-$stamp.$extension"
}

private fun formatReportNumber(value: Double): String = String.format(Locale("tr", "TR"), "%.2f", value)
