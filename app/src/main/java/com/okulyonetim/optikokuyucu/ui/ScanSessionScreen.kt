package com.okulyonetim.optikokuyucu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.exam.Exam
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
fun ScanSessionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val examRepository = remember(context) { FileExamRepository(context.applicationContext) }
    val scanRepository = remember(context) { FileScanRecordRepository(context.applicationContext) }
    val answerKeyRepository = remember(context) { FileAnswerKeyRepository(context.applicationContext) }

    var exams by remember { mutableStateOf(examRepository.list()) }
    var records by remember { mutableStateOf(scanRepository.list()) }
    var answerKeys by remember { mutableStateOf(answerKeyRepository.list()) }
    var selectedExamId by remember {
        mutableStateOf(exams.firstOrNull { it.papers.isNotEmpty() }?.id ?: exams.firstOrNull()?.id)
    }
    var query by remember { mutableStateOf("") }
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
            status = "CSV raporu kaydedildi."
        }.onFailure { error ->
            status = "CSV kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    fun refresh() {
        exams = examRepository.list()
        records = scanRepository.list()
        answerKeys = answerKeyRepository.list()
        if (selectedExamId?.let { id -> exams.none { it.id == id } } != false) {
            selectedExamId = exams.firstOrNull { it.papers.isNotEmpty() }?.id ?: exams.firstOrNull()?.id
        }
        status = "Sonuçlar yenilendi."
    }

    val selectedExam = exams.firstOrNull { it.id == selectedExamId }
    val report = selectedExam?.let { exam ->
        remember(exam, records, answerKeys) {
            ExamReportBuilder.build(exam, records, answerKeys)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ProductTopBar(
            title = "Sonuçlar",
            actionText = "↻",
            onActionClick = ::refresh
        )

        if (exams.isEmpty()) {
            ResultsEmptyState(onBack = onBack)
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(2.dp)) }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(exams, key = { it.id }) { exam ->
                        ProductFilterPill(
                            label = exam.name,
                            count = exam.papers.size,
                            selected = selectedExamId == exam.id,
                            onClick = {
                                selectedExamId = exam.id
                                query = ""
                                status = ""
                            }
                        )
                    }
                }
            }

            if (selectedExam != null && report != null) {
                item { ExamResultHeader(selectedExam, report) }

                if (report.rows.isEmpty()) {
                    item {
                        ResultInfoCard(
                            title = "Henüz okunmuş kağıt yok",
                            description = "Bu sınava ilk kağıt eklendiğinde net, başarı, dağılım ve öğrenci sonuçları burada otomatik oluşacak."
                        )
                    }
                } else {
                    item { ResultMetricGrid(report) }
                    item { AnswerDistributionCard(report) }
                    item { ScoreDistributionCard(report) }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Öğrenci Sonuçları", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${report.rows.size} kağıt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    item {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            label = { Text("Öğrenci, numara veya sınıf ara") },
                            leadingIcon = { Text("⌕", fontSize = 20.sp) },
                            shape = RoundedCornerShape(18.dp)
                        )
                    }

                    val normalized = query.trim().lowercase(Locale("tr", "TR"))
                    val rows = report.rows
                        .filter { row ->
                            normalized.isBlank() ||
                                row.studentName.lowercase(Locale("tr", "TR")).contains(normalized) ||
                                row.studentNumber.lowercase(Locale("tr", "TR")).contains(normalized) ||
                                row.className.lowercase(Locale("tr", "TR")).contains(normalized)
                        }
                        .sortedWith(compareByDescending<ExamReportRow> { it.points ?: Double.NEGATIVE_INFINITY }.thenBy { it.ordinal })

                    if (rows.isEmpty()) {
                        item {
                            ResultInfoCard("Sonuç bulunamadı", "Arama metnini değiştirin veya farklı bir sınav seçin.")
                        }
                    } else {
                        items(rows, key = { it.scanRecordId }) { row ->
                            StudentResultCard(row)
                        }
                    }

                    item {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                pendingCsv = ExamReportCsvExporter.export(report)
                                val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                                csvLauncher.launch("${safeFileName(report.examName)}-$timestamp.csv")
                            },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("CSV Raporunu Dışa Aktar")
                        }
                    }
                }

                if (status.isNotBlank()) {
                    item {
                        Text(
                            status,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ResultsEmptyState(onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Henüz sınav sonucu yok", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Bir sınav oluşturup optik kağıtları okuttuğunuzda analizler burada otomatik oluşacak.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                    Text("Anasayfaya Dön")
                }
            }
        }
    }
}

@Composable
private fun ExamResultHeader(exam: Exam, report: ExamReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(exam.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (exam.schoolName.isNotBlank()) {
                Text(exam.schoolName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
            }
            Text(
                "${report.paperCount} kağıt · ${report.scoredCount} puanlandı · ${report.reviewRequiredCount} kontrol",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ResultMetricGrid(report: ExamReport) {
    val scored = report.rows.filter { it.points != null && it.maximumPoints != null && it.maximumPoints > 0.0 }
    val averagePoints = report.rows.mapNotNull { it.points }.averageOrZero()
    val success = scored.map { row -> (row.points!! / row.maximumPoints!! * 100.0).coerceIn(0.0, 100.0) }.averageOrZero()
    val participation = report.rows.count { it.capturedAtEpochMs != null }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        ResultMetricCard(Modifier.weight(1f), "Ort. Net", formatOneDecimal(averagePoints))
        ResultMetricCard(Modifier.weight(1f), "Başarı", "%${formatOneDecimal(success)}")
        ResultMetricCard(Modifier.weight(1f), "Katılım", participation.toString())
        ResultMetricCard(Modifier.weight(1f), "Kontrol", report.reviewRequiredCount.toString())
    }
}

@Composable
private fun ResultMetricCard(modifier: Modifier, label: String, value: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun AnswerDistributionCard(report: ExamReport) {
    val correct = report.rows.sumOf { it.correct ?: 0 }
    val wrong = report.rows.sumOf { it.wrong ?: 0 }
    val blank = report.rows.sumOf { it.blank ?: 0 }
    val total = (correct + wrong + blank).coerceAtLeast(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Doğru / Yanlış / Boş", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            DistributionLine("Doğru", correct, total)
            DistributionLine("Yanlış", wrong, total)
            DistributionLine("Boş", blank, total)
        }
    }
}

@Composable
private fun DistributionLine(label: String, value: Int, total: Int) {
    val ratio = (value.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier.fillMaxWidth(ratio.coerceAtLeast(0.01f)).height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(6.dp)
            ) {}
        }
    }
}

@Composable
private fun ScoreDistributionCard(report: ExamReport) {
    val percentages = report.rows.mapNotNull { row ->
        val points = row.points ?: return@mapNotNull null
        val maximum = row.maximumPoints ?: return@mapNotNull null
        if (maximum <= 0.0) return@mapNotNull null
        (points / maximum * 100.0).coerceIn(0.0, 100.0)
    }
    val buckets = listOf(
        "0–49" to percentages.count { it < 50.0 },
        "50–69" to percentages.count { it in 50.0..<70.0 },
        "70–84" to percentages.count { it in 70.0..<85.0 },
        "85–100" to percentages.count { it >= 85.0 }
    )
    val maxCount = buckets.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Puan Dağılımı", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            buckets.forEach { (label, count) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodySmall)
                    Text("$count öğrenci", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth((count.toFloat() / maxCount.toFloat()).coerceAtLeast(0.01f))
                                .height(6.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(6.dp)
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentResultCard(row: ExamReportRow) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    row.studentName.ifBlank { "Öğrenci ${row.ordinal}" },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(row.className, row.studentNumber).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Bilgi bekleniyor" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                if (row.correct != null) {
                    Text(
                        "D ${row.correct} · Y ${row.wrong ?: 0} · B ${row.blank ?: 0}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    row.points?.let(::formatOneDecimal) ?: "—",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                ProductStatusBadge(
                    text = when (row.status) {
                        ExamReportRowStatus.SCORED -> "PUANLANDI"
                        ExamReportRowStatus.REVIEW_REQUIRED -> "KONTROL"
                        ExamReportRowStatus.NO_ANSWER_KEY -> "ANAHTAR YOK"
                        ExamReportRowStatus.SCAN_MISSING -> "TARAMA YOK"
                    },
                    tone = when (row.status) {
                        ExamReportRowStatus.SCORED -> ProductBadgeTone.GREEN
                        ExamReportRowStatus.REVIEW_REQUIRED -> ProductBadgeTone.ORANGE
                        ExamReportRowStatus.NO_ANSWER_KEY -> ProductBadgeTone.NEUTRAL
                        ExamReportRowStatus.SCAN_MISSING -> ProductBadgeTone.RED
                    }
                )
            }
        }
    }
}

@Composable
private fun ResultInfoCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

private fun formatOneDecimal(value: Double): String = String.format(Locale("tr", "TR"), "%.1f", value)

private fun safeFileName(value: String): String = value
    .trim()
    .lowercase(Locale("tr", "TR"))
    .replace(Regex("[^a-z0-9çğıöşü]+"), "-")
    .trim('-')
    .ifBlank { "sinav-sonuclari" }
