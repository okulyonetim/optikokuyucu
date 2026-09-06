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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    var formsReturnDestination by remember { mutableStateOf(RootDestination.HOME) }
    var designerReturnDestination by remember { mutableStateOf(RootDestination.ACTIVE_TEMPLATE) }

    fun openForms(returnTo: RootDestination) {
        formsReturnDestination = returnTo
        destination = RootDestination.ACTIVE_TEMPLATE
    }

    fun openDesigner(returnTo: RootDestination) {
        designerReturnDestination = returnTo
        destination = RootDestination.DESIGNER
    }

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

                RootDestination.ANSWER_KEYS -> {
                    if (selectedExamId != null) RootDestination.EXAM_DETAIL else RootDestination.TOOLS
                }

                RootDestination.ACTIVE_TEMPLATE -> formsReturnDestination
                RootDestination.DESIGNER -> designerReturnDestination
                RootDestination.ADVANCED_DESIGNER -> RootDestination.DESIGNER
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
                            onStartScan = { destination = RootDestination.EXAMS },
                            onOpenExams = { destination = RootDestination.EXAMS },
                            onNewExam = { destination = RootDestination.NEW_EXAM },
                            onOpenStudents = { destination = RootDestination.STUDENTS },
                            onOpenResults = { destination = RootDestination.RESULTS },
                            onOpenForms = { openForms(RootDestination.HOME) },
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

                        RootDestination.STUDENTS -> StudentRosterScreen(
                            onOpenPaper = { examId, scanRecordId ->
                                selectedExamId = examId
                                selectedScanRecordId = scanRecordId
                                destination = RootDestination.STUDENT_PAPER
                            }
                        )

                        RootDestination.SETTINGS -> RootSettingsScreen(
                            onOpenTools = { destination = RootDestination.TOOLS },
                            onOpenForms = { openForms(RootDestination.SETTINGS) }
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
                    onOpenActiveTemplate = { openForms(RootDestination.TOOLS) },
                    onOpenDesigner = { openDesigner(RootDestination.TOOLS) }
                )

                RootDestination.ANSWER_KEYS -> AnswerKeyScreen(
                    openCvReady = openCvReady,
                    onBack = {
                        destination = if (selectedExamId != null) RootDestination.EXAM_DETAIL else RootDestination.TOOLS
                    }
                )

                RootDestination.ACTIVE_TEMPLATE -> ActiveTemplateScreen(
                    onBack = { destination = formsReturnDestination },
                    onCreateForm = { openDesigner(RootDestination.ACTIVE_TEMPLATE) }
                )

                RootDestination.DESIGNER -> StructuredOmrDesignerScreen(
                    openCvReady = openCvReady,
                    onBack = { destination = designerReturnDestination },
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
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(Modifier.height(5.dp)) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Optik Okuyucu", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Optik değerlendirme merkezi",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        text = "OMR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("Yeni Tarama", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Sınavı seç, kamerayı aç ve kağıdı oku",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "$linkedPapers bağlı kağıt",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                        )
                    }
                    Button(
                        onClick = onStartScan,
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Text("Başlat", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                HomeStatCard(Modifier.weight(1f), "Sınav", exams.size.toString())
                HomeStatCard(Modifier.weight(1f), "Taranan", scans.size.toString())
                HomeStatCard(Modifier.weight(1f), "Okunan", readExams.toString())
                HomeStatCard(Modifier.weight(1f), "Bekleyen", waitingExams.toString())
            }
        }

        item {
            Text("Hızlı İşlemler", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { HomeActionCard(Modifier.width(106.dp), "＋", "Yeni Sınav", onNewExam) }
                item { HomeActionCard(Modifier.width(106.dp), "▤", "Sınavlar", onOpenExams) }
                item { HomeActionCard(Modifier.width(112.dp), "◎", "Optik Formlar", onOpenForms) }
                item { HomeActionCard(Modifier.width(106.dp), "●", "Öğrenciler", onOpenStudents) }
                item { HomeActionCard(Modifier.width(106.dp), "▥", "Sonuçlar", onOpenResults) }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Son Sınavlar", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onOpenExams) {
                    Text("Tümü", fontSize = 12.sp)
                }
            }
        }

        if (exams.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Henüz sınav yok", fontWeight = FontWeight.SemiBold)
                            Text(
                                "İlk sınavınızı oluşturun.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = onNewExam, shape = RoundedCornerShape(12.dp)) {
                            Text("Yeni Sınav", fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            items(exams.take(4), key = { it.id }) { exam ->
                HomeExamCard(exam = exam, onClick = { onOpenExam(exam.id) })
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun HomeStatCard(modifier: Modifier, label: String, value: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                fontSize = 9.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HomeActionCard(
    modifier: Modifier,
    symbol: String,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    text = symbol,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun HomeExamCard(exam: Exam, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    exam.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatHomeExamDate(exam.examDateEpochDay)} · ${exam.papers.size} kağıt",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ProductStatusBadge(
                text = if (exam.status == ExamStatus.READ) "OKUNDU" else "BEKLİYOR",
                tone = if (exam.status == ExamStatus.READ) ProductBadgeTone.GREEN else ProductBadgeTone.ORANGE
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
    Column(modifier = Modifier.fillMaxSize()) {
        ProductTopBar(title = "Ayarlar")
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Optik Formlar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Aktif formu seçin, hazır şablonları görüntüleyin veya yeni form oluşturun.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onOpenForms,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Optik Formları Yönet")
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Gelişmiş Araçlar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Cevap anahtarı, test ve gelişmiş OMR araçlarına erişin.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onOpenTools,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Gelişmiş Optik Araçları")
                        }
                    }
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
    Column(modifier = Modifier.fillMaxSize()) {
        ProductTopBar(
            title = "Optik Araçları",
            leadingText = "‹",
            onLeadingClick = onBackToExams
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item { Spacer(Modifier.height(3.dp)) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("OMR çalışma merkezi", fontWeight = FontWeight.Bold)
                        Text(
                            "Tarama, sonuç, cevap anahtarı ve form araçlarını tek noktadan yönetin.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            item {
                ToolActionCard("▣", "Kamera ile Tara", "Aktif optik form ile canlı OMR okuma", onOpenScanner, true)
            }
            item {
                ToolActionCard("▥", "Sonuçlar", "Sınav analizleri ve öğrenci sonuçları", onOpenResults)
            }
            item {
                ToolActionCard("✓", "Cevap Anahtarları", "Sınav cevap anahtarlarını oluştur ve yönet", onOpenAnswerKeys)
            }
            item {
                ToolActionCard("◎", "Optik Formlar", "Aktif, hazır ve kurum formlarını yönet", onOpenActiveTemplate)
            }
            item {
                ToolActionCard("✎", "Form Editörü", "Yeni form oluştur veya yerleşimi düzenle", onOpenDesigner)
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ToolActionCard(
    symbol: String,
    title: String,
    description: String,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (primary) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (primary) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(11.dp)
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    text = symbol,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    fontSize = 10.sp,
                    color = if (primary) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("›", fontSize = 20.sp, color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
        }
    }
}
