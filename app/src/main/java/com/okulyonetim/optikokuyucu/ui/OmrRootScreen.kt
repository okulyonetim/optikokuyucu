package com.okulyonetim.optikokuyucu.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.exam.Exam
import com.okulyonetim.optikokuyucu.exam.ExamStatus
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.omr.diagnostics.OmrSelfTestResult
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class RootDestination {
    HOME,
    EXAMS,
    NEW_EXAM,
    EXAM_DETAIL,
    EXAM_SCANNER,
    EXAM_GALLERY_BATCH,
    STUDENT_PAPER,
    EXAM_REPORT,
    STUDENTS,
    SETTINGS,
    TOOLS,
    SCANNER,
    RESULTS,
    ANSWER_KEYS,
    ACTIVE_TEMPLATE,
    DESIGNER,
    ADVANCED_DESIGNER
}

@Composable
fun OmrRootScreen(
    openCvReady: Boolean,
    selfTest: OmrSelfTestResult
) {
    var destination by remember { mutableStateOf(RootDestination.HOME) }
    var selectedExamId by remember { mutableStateOf<String?>(null) }
    var selectedScanRecordId by remember { mutableStateOf<String?>(null) }

    if (destination != RootDestination.HOME) {
        BackHandler {
            destination = when (destination) {
                RootDestination.NEW_EXAM -> RootDestination.EXAMS
                RootDestination.EXAMS,
                RootDestination.SCANNER,
                RootDestination.RESULTS,
                RootDestination.STUDENTS,
                RootDestination.SETTINGS -> RootDestination.HOME

                RootDestination.EXAM_DETAIL,
                RootDestination.TOOLS -> RootDestination.EXAMS

                RootDestination.EXAM_SCANNER,
                RootDestination.EXAM_GALLERY_BATCH,
                RootDestination.STUDENT_PAPER,
                RootDestination.EXAM_REPORT -> RootDestination.EXAM_DETAIL

                RootDestination.ADVANCED_DESIGNER -> RootDestination.DESIGNER

                RootDestination.ANSWER_KEYS,
                RootDestination.ACTIVE_TEMPLATE,
                RootDestination.DESIGNER -> RootDestination.TOOLS

                RootDestination.HOME -> RootDestination.HOME
            }
        }
    }

    OptikProductTheme {
        val rootTab = destination.toProductTabOrNull()
        if (rootTab != null) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    ProductBottomBar(
                        selected = rootTab,
                        onSelect = { tab -> destination = tab.toRootDestination() }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (destination) {
                        RootDestination.HOME -> ProductHomeScreen(
                            onStartScan = { destination = RootDestination.SCANNER },
                            onOpenExams = { destination = RootDestination.EXAMS },
                            onNewExam = { destination = RootDestination.NEW_EXAM },
                            onOpenStudents = { destination = RootDestination.STUDENTS },
                            onOpenResults = { destination = RootDestination.RESULTS },
                            onOpenForms = { destination = RootDestination.DESIGNER },
                            onOpenExam = { examId ->
                                selectedExamId = examId
                                selectedScanRecordId = null
                                destination = RootDestination.EXAM_DETAIL
                            }
                        )

                        RootDestination.SCANNER -> OmrCameraScreen(
                            openCvReady = openCvReady,
                            selfTest = selfTest
                        )

                        RootDestination.RESULTS -> ScanSessionScreen(
                            onBack = { destination = RootDestination.HOME }
                        )

                        RootDestination.STUDENTS -> ProductStudentsScreen(
                            onOpenPaper = { examId, scanRecordId ->
                                selectedExamId = examId
                                selectedScanRecordId = scanRecordId
                                destination = RootDestination.STUDENT_PAPER
                            }
                        )

                        RootDestination.SETTINGS -> RootSettingsScreen(
                            onOpenTools = { destination = RootDestination.TOOLS },
                            onOpenForms = { destination = RootDestination.DESIGNER }
                        )

                        else -> Unit
                    }
                }
            }
        } else {
            when (destination) {
                RootDestination.EXAMS -> ExamListScreen(
                    onNewExam = { destination = RootDestination.NEW_EXAM },
                    onOpenExam = { examId ->
                        selectedExamId = examId
                        selectedScanRecordId = null
                        destination = RootDestination.EXAM_DETAIL
                    },
                    onOpenTools = { destination = RootDestination.TOOLS }
                )

                RootDestination.NEW_EXAM -> NewExamScreen(
                    onBack = { destination = RootDestination.EXAMS },
                    onSaved = { examId ->
                        selectedExamId = examId
                        selectedScanRecordId = null
                        destination = RootDestination.EXAM_DETAIL
                    }
                )

                RootDestination.EXAM_DETAIL -> {
                    val examId = selectedExamId
                    if (examId == null) {
                        destination = RootDestination.EXAMS
                    } else {
                        ExamDetailScreen(
                            examId = examId,
                            onBack = {
                                selectedScanRecordId = null
                                destination = RootDestination.EXAMS
                            },
                            onScan = { destination = RootDestination.EXAM_SCANNER },
                            onOpenPaper = { scanRecordId ->
                                selectedScanRecordId = scanRecordId
                                destination = RootDestination.STUDENT_PAPER
                            },
                            onOpenAnswerKeys = { destination = RootDestination.ANSWER_KEYS },
                            onOpenReports = { destination = RootDestination.EXAM_REPORT }
                        )
                    }
                }

                RootDestination.EXAM_SCANNER -> {
                    val examId = selectedExamId
                    if (examId == null) {
                        destination = RootDestination.EXAMS
                    } else {
                        ExamScannerScreen(
                            examId = examId,
                            openCvReady = openCvReady,
                            selfTest = selfTest,
                            onBack = { destination = RootDestination.EXAM_DETAIL },
                            onOpenGalleryBatch = { destination = RootDestination.EXAM_GALLERY_BATCH }
                        )
                    }
                }

                RootDestination.EXAM_GALLERY_BATCH -> {
                    val examId = selectedExamId
                    if (examId == null) {
                        destination = RootDestination.EXAMS
                    } else {
                        ExamGalleryBatchScreen(
                            examId = examId,
                            openCvReady = openCvReady,
                            onBack = { destination = RootDestination.EXAM_DETAIL }
                        )
                    }
                }

                RootDestination.STUDENT_PAPER -> {
                    val examId = selectedExamId
                    val scanRecordId = selectedScanRecordId
                    if (examId == null || scanRecordId == null) {
                        destination = RootDestination.EXAM_DETAIL
                    } else {
                        StudentPaperDetailScreen(
                            examId = examId,
                            scanRecordId = scanRecordId,
                            onBack = { destination = RootDestination.EXAM_DETAIL }
                        )
                    }
                }

                RootDestination.EXAM_REPORT -> {
                    val examId = selectedExamId
                    if (examId == null) {
                        destination = RootDestination.EXAMS
                    } else {
                        ExamReportScreen(
                            examId = examId,
                            onBack = { destination = RootDestination.EXAM_DETAIL }
                        )
                    }
                }

                RootDestination.TOOLS -> RootToolsScreen(
                    onBackToExams = { destination = RootDestination.EXAMS },
                    onOpenScanner = { destination = RootDestination.SCANNER },
                    onOpenResults = { destination = RootDestination.RESULTS },
                    onOpenAnswerKeys = { destination = RootDestination.ANSWER_KEYS },
                    onOpenActiveTemplate = { destination = RootDestination.ACTIVE_TEMPLATE },
                    onOpenDesigner = { destination = RootDestination.DESIGNER }
                )

                RootDestination.ANSWER_KEYS -> AnswerKeyScreen(
                    openCvReady = openCvReady,
                    onBack = {
                        destination = if (selectedExamId != null) RootDestination.EXAM_DETAIL else RootDestination.TOOLS
                    }
                )

                RootDestination.ACTIVE_TEMPLATE -> ActiveTemplateScreen(
                    onBack = { destination = RootDestination.TOOLS }
                )

                RootDestination.DESIGNER -> StructuredOmrDesignerScreen(
                    openCvReady = openCvReady,
                    onBack = { destination = RootDestination.TOOLS },
                    onOpenAdvanced = { destination = RootDestination.ADVANCED_DESIGNER }
                )

                RootDestination.ADVANCED_DESIGNER -> OmrDesignerScreen(
                    openCvReady = openCvReady,
                    selfTest = selfTest,
                    onBack = { destination = RootDestination.DESIGNER }
                )

                RootDestination.HOME,
                RootDestination.SCANNER,
                RootDestination.RESULTS,
                RootDestination.STUDENTS,
                RootDestination.SETTINGS -> Unit
            }
        }
    }
}

private fun RootDestination.toProductTabOrNull(): ProductTab? = when (this) {
    RootDestination.HOME -> ProductTab.HOME
    RootDestination.SCANNER -> ProductTab.CAMERA
    RootDestination.STUDENTS -> ProductTab.STUDENTS
    RootDestination.RESULTS -> ProductTab.RESULTS
    RootDestination.SETTINGS -> ProductTab.SETTINGS
    else -> null
}

private fun ProductTab.toRootDestination(): RootDestination = when (this) {
    ProductTab.HOME -> RootDestination.HOME
    ProductTab.CAMERA -> RootDestination.SCANNER
    ProductTab.STUDENTS -> RootDestination.STUDENTS
    ProductTab.RESULTS -> RootDestination.RESULTS
    ProductTab.SETTINGS -> RootDestination.SETTINGS
}

@Composable
private fun ProductHomeScreen(
    onStartScan: () -> Unit,
    onOpenExams: () -> Unit,
    onNewExam: () -> Unit,
    onOpenStudents: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenForms: () -> Unit,
    onOpenExam: (String) -> Unit
) {
    val context = LocalContext.current
    val exams = remember(context) { FileExamRepository(context.applicationContext).list() }
    val scans = remember(context) { FileScanRecordRepository(context.applicationContext).list() }
    val readExams = exams.count { it.status == ExamStatus.READ }
    val waitingExams = exams.count { it.status == ExamStatus.WAITING }
    val linkedPapers = exams.sumOf { it.papers.size }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(10.dp)) }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Optik Okuyucu", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Hızlı, doğru ve çevrimdışı optik değerlendirme",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f)
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        onClick = onStartScan,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("▣  Yeni Tarama Başlat", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text("Genel Durum", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeStatCard(Modifier.weight(1f), "Sınav", exams.size.toString())
                HomeStatCard(Modifier.weight(1f), "Taranan Kağıt", scans.size.toString())
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeStatCard(Modifier.weight(1f), "Okunan Sınav", readExams.toString())
                HomeStatCard(Modifier.weight(1f), "Bekleyen", waitingExams.toString())
            }
        }

        item {
            Text("Hızlı İşlemler", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HomeActionCard(Modifier.weight(1f), "▤", "Sınavlar", onOpenExams)
                HomeActionCard(Modifier.weight(1f), "＋", "Yeni Sınav", onNewExam)
                HomeActionCard(Modifier.weight(1f), "◎", "Optik Formlar", onOpenForms)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HomeActionCard(Modifier.weight(1f), "◉", "Öğrenciler", onOpenStudents)
                HomeActionCard(Modifier.weight(1f), "▥", "Sonuçlar", onOpenResults)
                HomeActionCard(Modifier.weight(1f), "✓", "Bağlı Kağıt", onOpenExams, linkedPapers.toString())
            }
        }

        item {
            Text("Son Sınavlar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        if (exams.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Henüz sınav yok", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Yeni Sınav ile ilk sınavınızı oluşturabilirsiniz.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(exams.take(3), key = { it.id }) { exam ->
                HomeExamCard(exam = exam, onClick = { onOpenExam(exam.id) })
            }
        }

        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun HomeStatCard(modifier: Modifier, label: String, value: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeActionCard(
    modifier: Modifier,
    symbol: String,
    label: String,
    onClick: () -> Unit,
    badge: String? = null
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(symbol, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (badge != null) {
                ProductStatusBadge(text = badge, tone = ProductBadgeTone.NEUTRAL)
            }
        }
    }
}

@Composable
private fun HomeExamCard(exam: Exam, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(17.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    exam.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatHomeExamDate(exam.examDateEpochDay),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            ProductStatusBadge(
                text = if (exam.status == ExamStatus.READ) "OKUNDU ${exam.papers.size}" else "BEKLİYOR",
                tone = if (exam.status == ExamStatus.READ) ProductBadgeTone.GREEN else ProductBadgeTone.ORANGE
            )
        }
    }
}

private data class StudentOverview(
    val key: String,
    val name: String,
    val number: String,
    val className: String,
    val scanCount: Int,
    val latestExamId: String,
    val latestScanRecordId: String,
    val latestLinkedAtEpochMs: Long
)

private fun buildStudentOverviews(exams: List<Exam>): List<StudentOverview> {
    val byKey = linkedMapOf<String, StudentOverview>()
    exams.forEach { exam ->
        exam.papers.forEach { link ->
            val name = link.studentName.trim()
            val number = link.studentNumber.trim()
            val className = link.className.trim()
            val key = when {
                number.isNotBlank() -> "number:${number.lowercase()}"
                name.isNotBlank() -> "name:${name.lowercase()}|class:${className.lowercase()}"
                else -> "scan:${link.scanRecordId}"
            }
            val previous = byKey[key]
            val isLatest = previous == null || link.linkedAtEpochMs >= previous.latestLinkedAtEpochMs
            byKey[key] = StudentOverview(
                key = key,
                name = previous?.name?.takeIf { it.isNotBlank() } ?: name,
                number = previous?.number?.takeIf { it.isNotBlank() } ?: number,
                className = previous?.className?.takeIf { it.isNotBlank() } ?: className,
                scanCount = (previous?.scanCount ?: 0) + 1,
                latestExamId = if (isLatest) exam.id else previous!!.latestExamId,
                latestScanRecordId = if (isLatest) link.scanRecordId else previous!!.latestScanRecordId,
                latestLinkedAtEpochMs = if (isLatest) link.linkedAtEpochMs else previous!!.latestLinkedAtEpochMs
            )
        }
    }
    return byKey.values.sortedWith(
        compareBy<StudentOverview> { it.className.ifBlank { "~" } }
            .thenBy { it.name.ifBlank { "~" } }
            .thenBy { it.number }
    )
}

@Composable
private fun ProductStudentsScreen(
    onOpenPaper: (String, String) -> Unit
) {
    val context = LocalContext.current
    val exams = remember(context) { FileExamRepository(context.applicationContext).list() }
    val students = remember(exams) { buildStudentOverviews(exams) }
    var query by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf<String?>(null) }
    val classes = remember(students) {
        students.map { it.className }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val normalizedQuery = query.trim().lowercase()
    val filtered = students.filter { student ->
        val matchesClass = selectedClass == null || student.className == selectedClass
        val matchesQuery = normalizedQuery.isBlank() ||
            student.name.lowercase().contains(normalizedQuery) ||
            student.number.lowercase().contains(normalizedQuery) ||
            student.className.lowercase().contains(normalizedQuery)
        matchesClass && matchesQuery
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ProductTopBar(title = "Öğrenciler")
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Öğrenci, numara veya sınıf ara") },
                    leadingIcon = { Text("⌕", fontSize = 24.sp) },
                    shape = RoundedCornerShape(28.dp)
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeStatCard(Modifier.weight(1f), "Öğrenci", students.size.toString())
                    HomeStatCard(Modifier.weight(1f), "Sınıf", classes.size.toString())
                    HomeStatCard(Modifier.weight(1f), "Kağıt", students.sumOf { it.scanCount }.toString())
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        ProductFilterPill(
                            label = "Tümü",
                            count = students.size,
                            selected = selectedClass == null,
                            onClick = { selectedClass = null }
                        )
                    }
                    items(classes, key = { it }) { className ->
                        ProductFilterPill(
                            label = className,
                            count = students.count { it.className == className },
                            selected = selectedClass == className,
                            onClick = { selectedClass = className }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (students.isEmpty()) "Henüz öğrenci kaydı oluşmadı" else "Öğrenci bulunamadı",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (students.isEmpty())
                                    "Sınava bağlanan optik kağıtlardaki öğrenci bilgileri burada otomatik birleşir."
                                else "Arama metnini veya sınıf filtresini değiştirin.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filtered, key = { it.key }) { student ->
                    StudentOverviewCard(
                        student = student,
                        onClick = {
                            onOpenPaper(student.latestExamId, student.latestScanRecordId)
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun StudentOverviewCard(
    student: StudentOverview,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(17.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    student.name.ifBlank { "Öğrenci bilgisi bekliyor" },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${student.className.ifBlank { "Sınıf —" }}  ·  No: ${student.number.ifBlank { "—" }}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ProductStatusBadge(
                text = "${student.scanCount} KAĞIT",
                tone = ProductBadgeTone.NEUTRAL
            )
        }
    }
}

private fun formatHomeExamDate(epochDay: Long): String = runCatching {
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}.getOrDefault("-")

@Composable
private fun RootSettingsScreen(
    onOpenTools: () -> Unit,
    onOpenForms: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Optik ve form araçları", fontWeight = FontWeight.SemiBold)
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenForms) {
                    Text("Optik Form Tasarımcısı")
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenTools) {
                    Text("Gelişmiş Optik Araçları")
                }
            }
        }
    }
}

@Composable
private fun RootToolsScreen(
    onBackToExams: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenAnswerKeys: () -> Unit,
    onOpenActiveTemplate: () -> Unit,
    onOpenDesigner: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextButton(onClick = onBackToExams) { Text("‹ Sınavlar") }
        Text("Optik Araçları", style = MaterialTheme.typography.headlineMedium)
        Text(
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
            text = "Okuma, anahtar ve form tasarımı",
            style = MaterialTheme.typography.bodyMedium
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenScanner
        ) {
            Text("Tara · Kamera ve OMR")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            onClick = onOpenResults
        ) {
            Text("Tarama Oturumu · Sonuçlar")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            onClick = onOpenAnswerKeys
        ) {
            Text("Cevap Anahtarları")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            onClick = onOpenActiveTemplate
        ) {
            Text("Aktif Form / Şablon")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            onClick = onOpenDesigner
        ) {
            Text("Optik Form Tasarımcısı")
        }
    }
}
