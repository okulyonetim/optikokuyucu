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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.content.ContextCompat
import com.okulyonetim.optikokuyucu.exam.Exam
import com.okulyonetim.optikokuyucu.exam.ExamPaperLink
import com.okulyonetim.optikokuyucu.exam.ExamPersonalizedForms
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.exam.WrongAnswerPolicy
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPdfExporter
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.designer.pdfProfile
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeyResolver
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import com.okulyonetim.optikokuyucu.omr.scoring.OmrScorer
import com.okulyonetim.optikokuyucu.omr.scoring.ScoringPolicy
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import com.okulyonetim.optikokuyucu.omr.template.FileActiveTemplateSelectionRepository
import java.util.Locale
import java.util.concurrent.Executors

private enum class ExamDetailTab { PAPERS, KEYS, REPORTS }

@Composable
fun ExamDetailScreen(
    examId: String,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onOpenPaper: (String) -> Unit,
    onOpenAnswerKeys: () -> Unit,
    onOpenReports: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val examRepository = remember(context) { FileExamRepository(appContext) }
    val scanRepository = remember(context) { FileScanRecordRepository(appContext) }
    val keyRepository = remember(context) { FileAnswerKeyRepository(appContext) }
    val designerRepository = remember(context) { FileDesignerDocumentRepository(appContext) }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val worker = remember { Executors.newSingleThreadExecutor() }
    var exam by remember(examId) { mutableStateOf(examRepository.load(examId)) }
    var scans by remember { mutableStateOf(scanRepository.list().associateBy { it.id }) }
    var keys by remember { mutableStateOf(keyRepository.list()) }
    var tab by remember { mutableStateOf(ExamDetailTab.PAPERS) }
    var query by remember { mutableStateOf("") }
    var classFilter by remember { mutableStateOf<String?>(null) }
    var actionMenuOpen by remember { mutableStateOf(false) }
    var deleteDialogOpen by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var actionStatus by remember { mutableStateOf("") }
    var personalizedBusy by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { worker.shutdown() }
    }

    val personalizedPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val examAtStart = exam
        if (uri == null || examAtStart == null) return@rememberLauncherForActivityResult
        val document = resolveExamDesignerDocument(examAtStart, designerRepository.list())
        if (document == null) {
            actionStatus = "Seçili optik form öğrenciye özel PDF üretimini desteklemiyor."
            return@rememberLauncherForActivityResult
        }
        val profile = document.formSpec.pdfProfile()
        personalizedBusy = true
        actionStatus = "${examAtStart.participants.size} öğrenci için optik formlar hazırlanıyor…"
        worker.execute {
            runCatching {
                context.contentResolver.openOutputStream(uri, "w").use { output ->
                    requireNotNull(output) { "Öğrenci formu PDF çıktı akışı açılamadı." }
                    DesignerPdfExporter.exportBatch(
                        document = document,
                        pages = ExamPersonalizedForms.pages(examAtStart, document),
                        output = output,
                        profile = profile
                    )
                }
            }.onSuccess {
                mainExecutor.execute {
                    personalizedBusy = false
                    actionStatus = "${examAtStart.participants.size} öğrenci için kişisel optik form PDF'i oluşturuldu."
                }
            }.onFailure { error ->
                mainExecutor.execute {
                    personalizedBusy = false
                    actionStatus = "Öğrenci formları oluşturulamadı: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }
    }

    if (editMode) {
        NewExamScreen(
            examId = examId,
            onBack = { editMode = false },
            onSaved = {
                exam = examRepository.load(examId)
                scans = scanRepository.list().associateBy { it.id }
                keys = keyRepository.list()
                editMode = false
                actionStatus = "Sınav bilgileri güncellendi."
            }
        )
        return
    }

    val current = exam
    if (current == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Sınav kaydı bulunamadı.")
            OutlinedButton(onClick = onBack) { Text("Geri dön") }
        }
        return
    }

    if (deleteDialogOpen) {
        AlertDialog(
            onDismissRequest = { deleteDialogOpen = false },
            title = { Text("Sınav silinsin mi?") },
            text = {
                Text("${current.name} sınav kaydı silinecek. Tarama kayıtları cihazda korunur.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDialogOpen = false
                        if (examRepository.delete(examId)) {
                            onBack()
                        } else {
                            actionStatus = "Sınav silinemedi."
                        }
                    }
                ) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogOpen = false }) { Text("Vazgeç") }
            }
        )
    }

    val classes = current.papers.map { paperClass(it, scans[it.scanRecordId]) }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()
    val normalizedQuery = query.trim().lowercase()
    val visiblePapers = current.papers.filter { link ->
        val record = scans[link.scanRecordId]
        val name = link.studentName
        val number = paperNumber(link, record)
        val clazz = paperClass(link, record)
        (normalizedQuery.isBlank() ||
            name.lowercase().contains(normalizedQuery) ||
            number.lowercase().contains(normalizedQuery) ||
            clazz.lowercase().contains(normalizedQuery)) &&
            (classFilter == null || clazz == classFilter)
    }
    val personalizedDocument = remember(current.templateSelection, designerRepository) {
        resolveExamDesignerDocument(current, designerRepository.list())
    }
    val canCreatePersonalized = current.personalizedFormsEnabled &&
        current.participants.isNotEmpty() &&
        personalizedDocument != null &&
        !personalizedBusy

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ProductTopBar(
                title = current.name,
                leadingText = "‹",
                onLeadingClick = onBack,
                actionText = "⋮",
                onActionClick = { actionMenuOpen = true },
                actionMenu = {
                    DropdownMenu(
                        expanded = actionMenuOpen,
                        onDismissRequest = { actionMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (personalizedBusy) "Öğrenci Formları Hazırlanıyor…"
                                    else "Öğrenciye Özel Formları Oluştur (${current.participants.size})"
                                )
                            },
                            enabled = canCreatePersonalized,
                            onClick = {
                                actionMenuOpen = false
                                personalizedPdfLauncher.launch(personalizedFormFileName(current.name))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sınavı Düzenle") },
                            onClick = {
                                actionMenuOpen = false
                                editMode = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sınavı Sil") },
                            onClick = {
                                actionMenuOpen = false
                                deleteDialogOpen = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Yenile") },
                            onClick = {
                                actionMenuOpen = false
                                exam = examRepository.load(examId)
                                scans = scanRepository.list().associateBy { it.id }
                                keys = keyRepository.list()
                                actionStatus = "Sınav yenilendi."
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (tab == ExamDetailTab.PAPERS) {
                ExtendedFloatingActionButton(onClick = onScan) {
                    Text("▣  Kağıt Oku", fontSize = 17.sp)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProductFilterPill(
                    label = "Kağıtlar",
                    count = current.papers.size,
                    selected = tab == ExamDetailTab.PAPERS,
                    onClick = { tab = ExamDetailTab.PAPERS }
                )
                ProductFilterPill(
                    label = "Anahtarlar",
                    count = keys.count { keyMatchesExam(it, current) },
                    selected = tab == ExamDetailTab.KEYS,
                    onClick = { tab = ExamDetailTab.KEYS }
                )
                ProductFilterPill(
                    label = "Raporlar",
                    selected = tab == ExamDetailTab.REPORTS,
                    onClick = { tab = ExamDetailTab.REPORTS }
                )
            }

            if (actionStatus.isNotBlank()) {
                Text(
                    actionStatus,
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            when (tab) {
                ExamDetailTab.PAPERS -> {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        leadingIcon = { Text("⌕", fontSize = 24.sp) },
                        label = { Text("Öğrenci, numara veya sınıf ara") },
                        shape = RoundedCornerShape(30.dp)
                    )
                    if (classes.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProductFilterPill(
                                label = "Tümü",
                                count = current.papers.size,
                                selected = classFilter == null,
                                onClick = { classFilter = null }
                            )
                            classes.take(3).forEach { clazz ->
                                ProductFilterPill(
                                    label = clazz,
                                    count = current.papers.count { paperClass(it, scans[it.scanRecordId]) == clazz },
                                    selected = classFilter == clazz,
                                    onClick = { classFilter = clazz }
                                )
                            }
                        }
                    }

                    if (visiblePapers.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    if (current.papers.isEmpty()) "Henüz kağıt okunmadı" else "Eşleşen öğrenci bulunamadı",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    if (current.papers.isEmpty()) "Kağıt Oku ile bu sınava öğrenci optiklerini ekleyebilirsiniz."
                                    else "Arama veya sınıf filtresini değiştirin.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(visiblePapers, key = { it.scanRecordId }) { link ->
                                ExamPaperCard(
                                    exam = current,
                                    link = link,
                                    record = scans[link.scanRecordId],
                                    keys = keys,
                                    onClick = { onOpenPaper(link.scanRecordId) }
                                )
                            }
                            item { Spacer(Modifier.height(90.dp)) }
                        }
                    }
                }

                ExamDetailTab.KEYS -> ExamKeysTab(
                    exam = current,
                    keys = keys,
                    onOpenAnswerKeys = onOpenAnswerKeys
                )

                ExamDetailTab.REPORTS -> ExamReportsTab(
                    exam = current,
                    onOpenReports = onOpenReports
                )
            }
        }
    }
}

@Composable
private fun ExamPaperCard(
    exam: Exam,
    link: ExamPaperLink,
    record: ScanRecord?,
    keys: List<StoredAnswerKey>,
    onClick: () -> Unit
) {
    val number = paperNumber(link, record)
    val clazz = paperClass(link, record)
    val name = link.studentName.ifBlank {
        if (number.isBlank()) "İsimsiz Öğrenci" else "Öğrenci $number"
    }
    val key = record?.let { AnswerKeyResolver.resolve(it, keys) }
    val score = if (record != null && key != null) {
        runCatching { OmrScorer.score(record, key.answerKey, scoringPolicy(exam.wrongAnswerPolicy)) }.getOrNull()
    } else null

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(initials(name), color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOf(clazz, number).filter { it.isNotBlank() }.joinToString("  •  ").ifBlank { "Tarama kaydı" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("⋮", color = MaterialTheme.colorScheme.outline, fontSize = 22.sp)
                when {
                    record == null -> ProductStatusBadge("KAYIT YOK", ProductBadgeTone.RED)
                    score == null -> ProductStatusBadge("ANAHTAR YOK", ProductBadgeTone.ORANGE)
                    else -> ProductStatusBadge(
                        text = "Puan: ${formatScore(score.totalPoints)}",
                        tone = if (score.confidentlyEvaluated) ProductBadgeTone.GREEN else ProductBadgeTone.ORANGE
                    )
                }
            }
        }
    }
}

@Composable
private fun ExamKeysTab(exam: Exam, keys: List<StoredAnswerKey>, onOpenAnswerKeys: () -> Unit) {
    val context = LocalContext.current
    val selectionRepository = remember(context) {
        FileActiveTemplateSelectionRepository(context.applicationContext)
    }
    val matching = keys.filter { keyMatchesExam(it, exam) }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (matching.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bu sınav için cevap anahtarı yok", style = MaterialTheme.typography.titleMedium)
                    Text("Şablon ve sürüm eşleşen anahtar eklenince kağıtlar otomatik puanlanır.")
                }
            }
        } else {
            matching.forEach { key ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Cevap Anahtarı", fontWeight = FontWeight.SemiBold)
                            Text(
                                key.variantValue?.let { "Kitapçık $it" } ?: "Genel anahtar",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ProductStatusBadge("${key.answerKey.answers.size} soru", ProductBadgeTone.GREEN)
                    }
                }
            }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                runCatching { selectionRepository.save(exam.templateSelection) }
                    .onSuccess { onOpenAnswerKeys() }
            }
        ) {
            Text("Cevap Anahtarlarını Yönet")
        }
    }
}

@Composable
private fun ExamReportsTab(exam: Exam, onOpenReports: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sınav Raporları", style = MaterialTheme.typography.titleMedium)
                Text("${exam.papers.size} kağıt bu sınava bağlı.")
                Text("Öğrenci sonuçlarını inceleyebilir; CSV, Excel (.xlsx) veya PDF olarak dışa aktarabilirsiniz.")
            }
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenReports) {
            Text("Sınav Raporunu Aç")
        }
    }
}

private fun resolveExamDesignerDocument(exam: Exam, saved: List<DesignerDocument>): DesignerDocument? {
    if (exam.templateSelection.source != ActiveTemplateSource.DESIGNER_DOCUMENT) return null
    return saved.firstOrNull {
        it.id == exam.templateSelection.templateId && it.version == exam.templateSelection.templateVersion
    } ?: DesignerStarterTemplates.all().firstOrNull {
        it.id == exam.templateSelection.templateId && it.version == exam.templateSelection.templateVersion
    }
}

private fun personalizedFormFileName(examName: String): String {
    val safe = examName.trim()
        .replace(Regex("[^\\p{L}\\p{N}._-]+"), "-")
        .trim('-')
        .ifBlank { "sinav" }
    return "$safe-ogrenci-formlari.pdf"
}

private fun keyMatchesExam(key: StoredAnswerKey, exam: Exam): Boolean =
    key.templateId == exam.templateSelection.templateId &&
        key.templateVersion == exam.templateSelection.templateVersion

private fun paperNumber(link: ExamPaperLink, record: ScanRecord?): String =
    link.studentNumber.ifBlank { record?.grid("studentNumber")?.value.orEmpty() }

private fun paperClass(link: ExamPaperLink, record: ScanRecord?): String =
    link.className.ifBlank { record?.grid("class")?.value.orEmpty() }

private fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words.first().take(2).uppercase()
        else -> "${words.first().first()}${words.last().first()}".uppercase()
    }
}

private fun scoringPolicy(policy: WrongAnswerPolicy): ScoringPolicy = when (policy) {
    WrongAnswerPolicy.KEEP_AS_IS -> ScoringPolicy()
    WrongAnswerPolicy.FOUR_WRONG_ONE_CORRECT -> ScoringPolicy(wrongPoints = -0.25)
    WrongAnswerPolicy.THREE_WRONG_ONE_CORRECT -> ScoringPolicy(wrongPoints = -(1.0 / 3.0))
}

private fun formatScore(value: Double): String = String.format(Locale.US, "%.2f", value)
