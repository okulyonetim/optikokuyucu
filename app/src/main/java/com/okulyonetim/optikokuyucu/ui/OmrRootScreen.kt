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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.omr.diagnostics.OmrSelfTestResult
import com.okulyonetim.optikokuyucu.settings.AppSettings
import com.okulyonetim.optikokuyucu.settings.AppSettingsRepository

private enum class RootDestination {
    HOME,
    EXAMS,
    NEW_EXAM,
    EXAM_DETAIL,
    EXAM_SCANNER,
    EXAM_GALLERY_BATCH,
    STUDENT_PAPER,
    REPORT_BUILDER,
    PARENT_RESULT_SHARE,
    MINI_ANSWER_KEY,
    STUDENTS,
    SETTINGS,
    TOOLS,
    SCANNER,
    RESULTS,
    ANSWER_KEYS,
    OCR,
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
    var reportReturnDestination by remember { mutableStateOf(RootDestination.HOME) }
    var ocrReturnDestination by remember { mutableStateOf(RootDestination.TOOLS) }
    var refreshGeneration by remember { mutableStateOf(0) }

    fun openForms(returnTo: RootDestination) {
        formsReturnDestination = returnTo
        destination = RootDestination.ACTIVE_TEMPLATE
    }

    fun openDesigner(returnTo: RootDestination) {
        designerReturnDestination = returnTo
        destination = RootDestination.DESIGNER
    }

    fun openOcr(returnTo: RootDestination) {
        ocrReturnDestination = returnTo
        destination = RootDestination.OCR
    }

    if (destination != RootDestination.HOME) {
        BackHandler {
            destination = when (destination) {
                RootDestination.NEW_EXAM -> RootDestination.EXAMS
                RootDestination.EXAMS,
                RootDestination.SCANNER,
                RootDestination.RESULTS,
                RootDestination.STUDENTS,
                RootDestination.SETTINGS,
                RootDestination.MINI_ANSWER_KEY -> RootDestination.HOME

                RootDestination.EXAM_DETAIL,
                RootDestination.TOOLS -> RootDestination.EXAMS

                RootDestination.EXAM_SCANNER,
                RootDestination.EXAM_GALLERY_BATCH,
                RootDestination.STUDENT_PAPER -> RootDestination.EXAM_DETAIL

                RootDestination.REPORT_BUILDER -> reportReturnDestination
                RootDestination.PARENT_RESULT_SHARE -> RootDestination.REPORT_BUILDER

                RootDestination.ANSWER_KEYS -> {
                    if (selectedExamId != null) RootDestination.EXAM_DETAIL else RootDestination.TOOLS
                }

                RootDestination.OCR -> ocrReturnDestination
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
                        onSelect = { tab ->
                            if (tab == ProductTab.FORMS) formsReturnDestination = RootDestination.HOME
                            destination = tab.toRootDestination()
                        }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    AppPullToRefresh(
                        enabled = destination.supportsPullRefresh(),
                        onRefresh = { refreshGeneration++ }
                    ) {
                        key(destination, refreshGeneration) {
                            when (destination) {
                                RootDestination.HOME -> ProductHomeScreen(
                                    onNewExam = { destination = RootDestination.NEW_EXAM },
                                    onOpenReportBuilder = {
                                        selectedExamId = null
                                        reportReturnDestination = RootDestination.HOME
                                        destination = RootDestination.REPORT_BUILDER
                                    },
                                    onOpenMiniAnswerKey = { destination = RootDestination.MINI_ANSWER_KEY },
                                    onOpenOcr = { openOcr(RootDestination.HOME) },
                                    onOpenExams = { destination = RootDestination.EXAMS },
                                    onOpenExam = { examId ->
                                        selectedExamId = examId
                                        selectedScanRecordId = null
                                        destination = RootDestination.EXAM_DETAIL
                                    }
                                )

                                RootDestination.EXAMS -> ExamListScreen(
                                    onNewExam = { destination = RootDestination.NEW_EXAM },
                                    onOpenExam = { examId ->
                                        selectedExamId = examId
                                        selectedScanRecordId = null
                                        destination = RootDestination.EXAM_DETAIL
                                    },
                                    onOpenTools = { destination = RootDestination.TOOLS }
                                )

                                RootDestination.STUDENTS -> StudentRosterScreen(
                                    onOpenPaper = { examId, scanRecordId ->
                                        selectedExamId = examId
                                        selectedScanRecordId = scanRecordId
                                        destination = RootDestination.STUDENT_PAPER
                                    }
                                )

                                RootDestination.ACTIVE_TEMPLATE -> ActiveTemplateScreen(
                                    onBack = { destination = formsReturnDestination },
                                    onCreateForm = { openDesigner(RootDestination.ACTIVE_TEMPLATE) }
                                )

                                RootDestination.SETTINGS -> RootSettingsScreen()

                                else -> Unit
                            }
                        }
                    }
                }
            }
        } else {
            AppPullToRefresh(
                enabled = destination.supportsPullRefresh(),
                onRefresh = { refreshGeneration++ }
            ) {
                key(destination, refreshGeneration) {
                    when (destination) {
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
                                    onOpenReports = {
                                        reportReturnDestination = RootDestination.EXAM_DETAIL
                                        destination = RootDestination.REPORT_BUILDER
                                    }
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

                        RootDestination.REPORT_BUILDER -> ReportBuilderScreen(
                            initialExamId = selectedExamId,
                            onBack = { destination = reportReturnDestination },
                            onExamChanged = { selectedExamId = it },
                            onShareParents = { examId ->
                                selectedExamId = examId
                                destination = RootDestination.PARENT_RESULT_SHARE
                            }
                        )

                        RootDestination.PARENT_RESULT_SHARE -> {
                            val examId = selectedExamId
                            if (examId == null) {
                                destination = RootDestination.REPORT_BUILDER
                            } else {
                                ParentResultShareScreen(
                                    examId = examId,
                                    onBack = { destination = RootDestination.REPORT_BUILDER }
                                )
                            }
                        }

                        RootDestination.MINI_ANSWER_KEY -> MiniAnswerKeyScreen(
                            onBack = { destination = RootDestination.HOME }
                        )

                        RootDestination.TOOLS -> RootToolsScreen(
                            onBackToExams = { destination = RootDestination.EXAMS },
                            onOpenScanner = { destination = RootDestination.SCANNER },
                            onOpenResults = { destination = RootDestination.RESULTS },
                            onOpenAnswerKeys = { destination = RootDestination.ANSWER_KEYS },
                            onOpenOcr = { openOcr(RootDestination.TOOLS) },
                            onOpenActiveTemplate = { openForms(RootDestination.TOOLS) },
                            onOpenDesigner = { openDesigner(RootDestination.TOOLS) }
                        )

                        RootDestination.SCANNER -> OmrCameraScreen(
                            openCvReady = openCvReady,
                            selfTest = selfTest
                        )

                        RootDestination.RESULTS -> ScanSessionScreen(
                            onBack = { destination = RootDestination.HOME }
                        )

                        RootDestination.ANSWER_KEYS -> AnswerKeyScreen(
                            openCvReady = openCvReady,
                            onBack = {
                                destination = if (selectedExamId != null) RootDestination.EXAM_DETAIL else RootDestination.TOOLS
                            }
                        )

                        RootDestination.OCR -> OcrWorkspaceScreen(
                            onBack = { destination = ocrReturnDestination }
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
                        RootDestination.EXAMS,
                        RootDestination.STUDENTS,
                        RootDestination.SETTINGS -> Unit
                    }
                }
            }
        }
    }
}

private fun RootDestination.supportsPullRefresh(): Boolean = when (this) {
    RootDestination.HOME,
    RootDestination.EXAMS,
    RootDestination.EXAM_DETAIL,
    RootDestination.STUDENT_PAPER,
    RootDestination.REPORT_BUILDER,
    RootDestination.PARENT_RESULT_SHARE,
    RootDestination.MINI_ANSWER_KEY,
    RootDestination.STUDENTS,
    RootDestination.SETTINGS,
    RootDestination.TOOLS,
    RootDestination.RESULTS,
    RootDestination.ANSWER_KEYS,
    RootDestination.OCR,
    RootDestination.ACTIVE_TEMPLATE -> true

    RootDestination.NEW_EXAM,
    RootDestination.EXAM_SCANNER,
    RootDestination.EXAM_GALLERY_BATCH,
    RootDestination.SCANNER,
    RootDestination.DESIGNER,
    RootDestination.ADVANCED_DESIGNER -> false
}

private fun RootDestination.toProductTabOrNull(): ProductTab? = when (this) {
    RootDestination.HOME -> ProductTab.HOME
    RootDestination.EXAMS -> ProductTab.EXAMS
    RootDestination.STUDENTS -> ProductTab.STUDENTS
    RootDestination.ACTIVE_TEMPLATE -> ProductTab.FORMS
    RootDestination.SETTINGS -> ProductTab.SETTINGS
    else -> null
}

private fun ProductTab.toRootDestination(): RootDestination = when (this) {
    ProductTab.HOME -> RootDestination.HOME
    ProductTab.EXAMS -> RootDestination.EXAMS
    ProductTab.STUDENTS -> RootDestination.STUDENTS
    ProductTab.FORMS -> RootDestination.ACTIVE_TEMPLATE
    ProductTab.SETTINGS -> RootDestination.SETTINGS
}

@Composable
private fun RootSettingsScreen() {
    val context = LocalContext.current
    val settingsRepository = remember(context) { AppSettingsRepository(context.applicationContext) }
    var schoolName by remember { mutableStateOf(settingsRepository.load().schoolName) }
    var schoolStatus by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        ProductTopBar(title = "Ayarlar")
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.padding(1.dp)) }
            item { SchoolAccountSettingsCard() }
            item {
                ProductSettingsSection(
                    title = "Kurum Bilgileri",
                    description = "Okul adı yeni sınav oluştururken otomatik doldurulur."
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = schoolName,
                        onValueChange = {
                            schoolName = it
                            schoolStatus = ""
                        },
                        label = { Text("Okul Adı") },
                        singleLine = true,
                        shape = RoundedCornerShape(13.dp)
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            runCatching { settingsRepository.save(AppSettings(schoolName)) }
                                .onSuccess {
                                    schoolName = schoolName.trim()
                                    schoolStatus = "Okul adı kaydedildi."
                                }
                                .onFailure { error ->
                                    schoolStatus = "Kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
                                }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Kaydet", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (schoolStatus.isNotBlank()) {
                        Text(
                            schoolStatus,
                            fontSize = 9.sp,
                            color = if (schoolStatus.startsWith("Kaydedilemedi")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            item { SettingsSubjectsCard(settingsRepository) }
            item {
                Text(
                    "Tema seçimi için sağ üstteki ◐ simgesini kullanın.",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { Spacer(Modifier.padding(4.dp)) }
        }
    }
}

@Composable
private fun RootToolsScreen(
    onBackToExams: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenAnswerKeys: () -> Unit,
    onOpenOcr: () -> Unit,
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
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item { Spacer(Modifier.padding(1.dp)) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("OMR çalışma merkezi", fontWeight = FontWeight.Bold)
                        Text(
                            "Tarama, sonuç, cevap anahtarı, OCR ve form araçlarını tek noktadan yönetin.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            item { ToolActionCard("▣", "Kamera ile Tara", "Aktif optik form ile canlı OMR okuma", onOpenScanner, true) }
            item { ToolActionCard("OCR", "Belge / OCR", "Türkçe belge, el yazısı ve cevap anahtarı görsellerini oku", onOpenOcr) }
            item { ToolActionCard("▥", "Sonuçlar", "Sınav analizleri ve öğrenci sonuçları", onOpenResults) }
            item { ToolActionCard("✓", "Cevap Anahtarları", "Sınav cevap anahtarlarını oluştur ve yönet", onOpenAnswerKeys) }
            item { ToolActionCard("◎", "Optik Formlar", "Aktif, hazır ve kurum formlarını yönet", onOpenActiveTemplate) }
            item { ToolActionCard("✎", "Form Editörü", "Yeni form oluştur veya yerleşimi düzenle", onOpenDesigner) }
            item { Spacer(Modifier.padding(6.dp)) }
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (primary) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
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
