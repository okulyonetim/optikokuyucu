package com.okulyonetim.optikokuyucu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.okulyonetim.optikokuyucu.exam.ConfiguredExamReport
import com.okulyonetim.optikokuyucu.exam.ConfiguredExamReportExporter
import com.okulyonetim.optikokuyucu.exam.ExamReport
import com.okulyonetim.optikokuyucu.exam.ExamReportBuilder
import com.okulyonetim.optikokuyucu.exam.ExamReportRow
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.exam.ReportColumn
import com.okulyonetim.optikokuyucu.exam.ReportPageOrientation
import com.okulyonetim.optikokuyucu.exam.examLessonDisplayName
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import java.util.Locale

private enum class BuilderReportType(val label: String, val description: String) {
    EXAM("Sınav Raporu", "Genel sınav, sınıf ve ders sonuçları"),
    STUDENT("Öğrenci Raporu", "Seçili öğrenci veya öğrenciler için rapor")
}

private enum class BuilderDetail(val label: String, val description: String) {
    SIMPLE("Basit", "Puan, net ve sıralama odaklı sade rapor"),
    DETAILED("Detaylı", "D/Y/B, kitapçık ve ders bazlı ayrıntılar")
}

private enum class BuilderSort(val label: String) {
    SCORE_DESC("Puan · yüksekten düşüğe"),
    NET_DESC("Net · yüksekten düşüğe"),
    NAME("Ad Soyad · A-Z"),
    NUMBER("Öğrenci No"),
    CLASS_SCORE("Sınıf → puan")
}

@Composable
fun ReportBuilderScreen(
    initialExamId: String? = null,
    onBack: () -> Unit,
    onExamChanged: (String?) -> Unit = {},
    onShareParents: (String) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val examRepository = remember(context) { FileExamRepository(appContext) }
    val scanRepository = remember(context) { FileScanRecordRepository(appContext) }
    val keyRepository = remember(context) { FileAnswerKeyRepository(appContext) }
    val exams = remember { examRepository.list() }

    var selectedExamId by remember(initialExamId, exams) {
        mutableStateOf(initialExamId?.takeIf { id -> exams.any { it.id == id } })
    }
    var step by remember(selectedExamId) { mutableStateOf(if (selectedExamId == null) 0 else 1) }
    var reportType by remember { mutableStateOf(BuilderReportType.EXAM) }
    var detail by remember { mutableStateOf(BuilderDetail.SIMPLE) }
    var selectedClasses by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedStudents by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedLessons by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sort by remember { mutableStateOf(BuilderSort.SCORE_DESC) }
    var orientation by remember { mutableStateOf(ReportPageOrientation.PORTRAIT) }
    var columns by remember { mutableStateOf(simpleColumns()) }
    var status by remember { mutableStateOf("") }
    var pendingPdf by remember { mutableStateOf<ConfiguredExamReport?>(null) }
    var pendingXlsx by remember { mutableStateOf<ByteArray?>(null) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ConfiguredExamReportExporter.PDF_MIME_TYPE)
    ) { uri ->
        val config = pendingPdf
        pendingPdf = null
        if (uri == null || config == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "PDF çıktı akışı açılamadı." }
                ConfiguredExamReportExporter.exportPdf(config, output)
            }
        }.onSuccess { status = "PDF raporu kaydedildi." }
            .onFailure { error -> status = "PDF oluşturulamadı: ${error.message ?: error.javaClass.simpleName}" }
    }

    val xlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ConfiguredExamReportExporter.XLSX_MIME_TYPE)
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
        }.onSuccess { status = "Excel raporu kaydedildi." }
            .onFailure { error -> status = "Excel oluşturulamadı: ${error.message ?: error.javaClass.simpleName}" }
    }

    val selectedExam = selectedExamId?.let(examRepository::load)
    val report = remember(selectedExam, selectedExamId) {
        selectedExam?.let { exam ->
            ExamReportBuilder.build(exam, scanRepository.list(), keyRepository.list())
        }
    }
    val classes = report?.rows.orEmpty().map { it.className }.filter(String::isNotBlank).distinct().sorted()
    val lessonIds = report?.rows.orEmpty().flatMap { it.lessons }.map { it.lessonId }.distinct()
    val configuredRows = remember(report, reportType, selectedClasses, selectedStudents, sort) {
        report?.rows.orEmpty()
            .filter { row -> selectedClasses.isEmpty() || row.className in selectedClasses }
            .filter { row -> reportType != BuilderReportType.STUDENT || selectedStudents.isEmpty() || row.scanRecordId in selectedStudents }
            .let { rows -> sortRows(rows, sort) }
    }
    val config = report?.let {
        ConfiguredExamReport(
            report = it,
            rows = configuredRows,
            columns = columns.toList(),
            selectedLessonIds = selectedLessons,
            orientation = orientation,
            titleSuffix = when (reportType) {
                BuilderReportType.EXAM -> if (detail == BuilderDetail.SIMPLE) "Basit Sınav Raporu" else "Detaylı Sınav Raporu"
                BuilderReportType.STUDENT -> if (detail == BuilderDetail.SIMPLE) "Basit Öğrenci Raporu" else "Detaylı Öğrenci Raporu"
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ProductTopBar(
            title = "Rapor Oluştur",
            leadingText = "‹",
            onLeadingClick = {
                if (step > 0) step-- else onBack()
            },
            actionText = "${step + 1}/5"
        )

        when (step) {
            0 -> ExamSelectionStep(
                exams = exams,
                selectedId = selectedExamId,
                onSelect = { id ->
                    selectedExamId = id
                    selectedClasses = emptySet()
                    selectedStudents = emptySet()
                    selectedLessons = emptySet()
                    onExamChanged(id)
                    step = 1
                }
            )
            1 -> ChoiceStep(
                title = "Rapor Türü",
                description = selectedExam?.name.orEmpty(),
                choices = BuilderReportType.entries.map { it.label to it.description },
                selectedIndex = reportType.ordinal,
                onSelect = { reportType = BuilderReportType.entries[it] },
                onNext = { step = 2 }
            )
            2 -> ChoiceStep(
                title = "Rapor Seviyesi",
                description = "Raporun ayrıntı düzeyini belirleyin.",
                choices = BuilderDetail.entries.map { it.label to it.description },
                selectedIndex = detail.ordinal,
                onSelect = { index ->
                    detail = BuilderDetail.entries[index]
                    columns = if (detail == BuilderDetail.SIMPLE) simpleColumns() else detailedColumns()
                },
                onNext = { step = 3 }
            )
            3 -> ReportCustomizeStep(
                report = report,
                reportType = reportType,
                classes = classes,
                lessonIds = lessonIds,
                selectedClasses = selectedClasses,
                selectedStudents = selectedStudents,
                selectedLessons = selectedLessons,
                columns = columns,
                sort = sort,
                orientation = orientation,
                onClassesChanged = { selectedClasses = it },
                onStudentsChanged = { selectedStudents = it },
                onLessonsChanged = { selectedLessons = it },
                onColumnsChanged = { columns = it },
                onSortChanged = { sort = it },
                onOrientationChanged = { orientation = it },
                onNext = { step = 4 }
            )
            else -> ReportPreviewStep(
                config = config,
                status = status,
                onPdf = {
                    val current = config ?: return@ReportPreviewStep
                    pendingPdf = current
                    pdfLauncher.launch(reportFileName(current.report.examName, "pdf"))
                },
                onExcel = {
                    val current = config ?: return@ReportPreviewStep
                    runCatching { ConfiguredExamReportExporter.exportXlsx(current) }
                        .onSuccess { bytes ->
                            pendingXlsx = bytes
                            xlsxLauncher.launch(reportFileName(current.report.examName, "xlsx"))
                        }
                        .onFailure { error -> status = "Excel hazırlanamadı: ${error.message ?: error.javaClass.simpleName}" }
                },
                onShareParents = {
                    selectedExamId?.let(onShareParents)
                }
            )
        }
    }
}

@Composable
private fun ExamSelectionStep(
    exams: List<com.okulyonetim.optikokuyucu.exam.Exam>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("1. Sınav Seçimi", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Rapor oluşturulacak sınavı seçin.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (exams.isEmpty()) {
            item { ProductEmptyState("Sınav bulunamadı", "Önce bir sınav oluşturun.") }
        } else {
            items(exams.sortedByDescending { it.examDateEpochDay }, key = { it.id }) { exam ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(exam.id) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (exam.id == selectedId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(exam.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${exam.papers.size} kağıt · ${exam.schoolName}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ChoiceStep(
    title: String,
    description: String,
    choices: List<Pair<String, String>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onNext: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(choices.indices.toList()) { index ->
            val choice = choices[index]
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(index) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (index == selectedIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(choice.first, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(choice.second, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Button(modifier = Modifier.fillMaxWidth(), onClick = onNext, shape = RoundedCornerShape(13.dp)) {
                Text("Devam")
            }
        }
    }
}

@Composable
private fun ReportCustomizeStep(
    report: ExamReport?,
    reportType: BuilderReportType,
    classes: List<String>,
    lessonIds: List<String>,
    selectedClasses: Set<String>,
    selectedStudents: Set<String>,
    selectedLessons: Set<String>,
    columns: Set<ReportColumn>,
    sort: BuilderSort,
    orientation: ReportPageOrientation,
    onClassesChanged: (Set<String>) -> Unit,
    onStudentsChanged: (Set<String>) -> Unit,
    onLessonsChanged: (Set<String>) -> Unit,
    onColumnsChanged: (Set<ReportColumn>) -> Unit,
    onSortChanged: (BuilderSort) -> Unit,
    onOrientationChanged: (ReportPageOrientation) -> Unit,
    onNext: () -> Unit
) {
    val rows = report?.rows.orEmpty().filter { selectedClasses.isEmpty() || it.className in selectedClasses }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("4. Rapor Düzenleme", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Kapsamı, alanları, sıralamayı ve sayfa yönünü belirleyin.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ProductSettingsSection("Kapsam", "Boş seçim tüm okulu/sınavı kapsar.") {
                Text("Sınıflar", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(classes) { className ->
                        FilterChip(
                            selected = className in selectedClasses,
                            onClick = { onClassesChanged(selectedClasses.toggle(className)) },
                            label = { Text(className, fontSize = 9.sp) }
                        )
                    }
                }
                if (reportType == BuilderReportType.STUDENT) {
                    Text("Öğrenciler", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(rows, key = { it.scanRecordId }) { row ->
                            FilterChip(
                                selected = row.scanRecordId in selectedStudents,
                                onClick = { onStudentsChanged(selectedStudents.toggle(row.scanRecordId)) },
                                label = { Text(row.studentName.ifBlank { row.studentNumber.ifBlank { "İsimsiz" } }, fontSize = 9.sp) }
                            )
                        }
                    }
                }
                if (lessonIds.isNotEmpty()) {
                    Text("Dersler", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(lessonIds) { lessonId ->
                            FilterChip(
                                selected = lessonId in selectedLessons,
                                onClick = { onLessonsChanged(selectedLessons.toggle(lessonId)) },
                                label = { Text(examLessonDisplayName(lessonId), fontSize = 9.sp) }
                            )
                        }
                    }
                }
            }
        }
        item {
            ProductSettingsSection("Raporda Olacak Alanlar", "İstediğiniz sütunları açıp kapatın.") {
                ReportColumn.entries.chunked(2).forEach { pair ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { column ->
                            FilterChip(
                                modifier = Modifier.weight(1f),
                                selected = column in columns,
                                onClick = {
                                    val updated = columns.toggle(column)
                                    if (updated.isNotEmpty()) onColumnsChanged(updated)
                                },
                                label = { Text(column.label, fontSize = 9.sp, maxLines = 1) }
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            ProductSettingsSection("Sıralama", "Rapor satırlarının sırası.") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(BuilderSort.entries) { mode ->
                        FilterChip(
                            selected = mode == sort,
                            onClick = { onSortChanged(mode) },
                            label = { Text(mode.label, fontSize = 9.sp) }
                        )
                    }
                }
            }
        }
        item {
            ProductSettingsSection("Sayfa", "PDF ve Excel yazdırma yönü.") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = orientation == ReportPageOrientation.PORTRAIT,
                        onClick = { onOrientationChanged(ReportPageOrientation.PORTRAIT) },
                        label = { Text("Dikey") }
                    )
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = orientation == ReportPageOrientation.LANDSCAPE,
                        onClick = { onOrientationChanged(ReportPageOrientation.LANDSCAPE) },
                        label = { Text("Yatay") }
                    )
                }
            }
        }
        item {
            Button(modifier = Modifier.fillMaxWidth(), onClick = onNext, shape = RoundedCornerShape(13.dp)) {
                Text("Önizleme")
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ReportPreviewStep(
    config: ConfiguredExamReport?,
    status: String,
    onPdf: () -> Unit,
    onExcel: () -> Unit,
    onShareParents: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Text("5. Önizleme", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                config?.let { "${it.rows.size} kayıt · ${it.columns.size} alan · ${if (it.orientation == ReportPageOrientation.LANDSCAPE) "Yatay" else "Dikey"}" }
                    ?: "Rapor hazırlanamadı.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (config == null || config.rows.isEmpty()) {
            item { ProductEmptyState("Rapor verisi yok", "Seçtiğiniz kapsamda raporlanacak öğrenci bulunamadı.") }
        } else {
            item {
                ProductSettingsSection("Seçili Alanlar", config.columns.joinToString(" · ") { it.label }) {
                    Text(
                        if (config.selectedLessonIds.isEmpty()) "Ders kapsamı: tüm dersler" else "Ders kapsamı: ${config.selectedLessonIds.joinToString { examLessonDisplayName(it) }}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(config.rows.take(12), key = { it.scanRecordId }) { row ->
                PreviewRow(row)
            }
            if (config.rows.size > 12) {
                item { Text("+ ${config.rows.size - 12} kayıt daha", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(modifier = Modifier.weight(1f), onClick = onPdf, shape = RoundedCornerShape(12.dp)) { Text("PDF") }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = onExcel, shape = RoundedCornerShape(12.dp)) { Text("Excel") }
                }
            }
            item {
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onShareParents, shape = RoundedCornerShape(12.dp)) {
                    Text("Velilere Sonuç Gönder")
                }
            }
        }
        if (status.isNotBlank()) {
            item { Text(status, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun PreviewRow(row: ExamReportRow) {
    ProductCompactCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.studentName.ifBlank { row.studentNumber.ifBlank { "İsimsiz Öğrenci" } }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("${row.className} · No ${row.studentNumber}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(row.points?.let(::formatBuilderNumber) ?: "—", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Net ${row.net?.let(::formatBuilderNumber) ?: "—"}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun simpleColumns(): Set<ReportColumn> = linkedSetOf(
    ReportColumn.STUDENT,
    ReportColumn.CLASS,
    ReportColumn.NUMBER,
    ReportColumn.SCORE,
    ReportColumn.NET,
    ReportColumn.OVERALL_RANK,
    ReportColumn.CLASS_RANK
)

private fun detailedColumns(): Set<ReportColumn> = linkedSetOf(
    ReportColumn.STUDENT,
    ReportColumn.CLASS,
    ReportColumn.NUMBER,
    ReportColumn.BOOKLET,
    ReportColumn.CORRECT,
    ReportColumn.WRONG,
    ReportColumn.BLANK,
    ReportColumn.NET,
    ReportColumn.SCORE,
    ReportColumn.OVERALL_RANK,
    ReportColumn.CLASS_RANK,
    ReportColumn.LESSONS
)

private fun <T> Set<T>.toggle(value: T): Set<T> = toMutableSet().apply {
    if (!add(value)) remove(value)
}.toSet()

private fun sortRows(rows: List<ExamReportRow>, sort: BuilderSort): List<ExamReportRow> = when (sort) {
    BuilderSort.SCORE_DESC -> rows.sortedWith(compareByDescending<ExamReportRow> { it.points ?: Double.NEGATIVE_INFINITY }.thenBy { it.studentName })
    BuilderSort.NET_DESC -> rows.sortedWith(compareByDescending<ExamReportRow> { it.net ?: Double.NEGATIVE_INFINITY }.thenBy { it.studentName })
    BuilderSort.NAME -> rows.sortedBy { it.studentName.lowercase(Locale.forLanguageTag("tr-TR")) }
    BuilderSort.NUMBER -> rows.sortedWith(compareBy<ExamReportRow> { it.studentNumber.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.studentNumber })
    BuilderSort.CLASS_SCORE -> rows.sortedWith(compareBy<ExamReportRow> { it.className }.thenByDescending { it.points ?: Double.NEGATIVE_INFINITY })
}

private fun reportFileName(examName: String, extension: String): String {
    val safe = examName.trim().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(48).ifBlank { "sinav" }
    return "$safe-rapor.$extension"
}

private fun formatBuilderNumber(value: Double): String = String.format(Locale("tr", "TR"), "%.2f", value)
