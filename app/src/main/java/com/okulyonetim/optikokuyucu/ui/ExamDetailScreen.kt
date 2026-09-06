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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Switch
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
import com.okulyonetim.optikokuyucu.exam.ExamPaperResolution
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
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import com.okulyonetim.optikokuyucu.omr.scoring.OmrScorer
import com.okulyonetim.optikokuyucu.omr.scoring.ScoringPolicy
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateDefaults
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import com.okulyonetim.optikokuyucu.omr.template.FileActiveTemplateSelectionRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

private enum class ExamDetailTab { PAPERS, KEYS, REPORTS }

private data class EditExamTemplateOption(
    val name: String,
    val selection: ActiveTemplateSelection
)

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
    var menuExpanded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var personalizedBusy by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { worker.shutdown() }
    }

    val personalizedPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val examAtStart = exam
        if (uri == null || examAtStart == null) return@rememberLauncherForActivityResult
        val document = runCatching {
            resolveExamDesignerDocument(
                source = examAtStart.templateSelection.source,
                templateId = examAtStart.templateSelection.templateId,
                templateVersion = examAtStart.templateSelection.templateVersion,
                saved = designerRepository.list()
            )
        }.getOrNull()
        val profile = document?.formSpec?.pdfProfile()
        if (document == null || profile == null) {
            status = "Seçili form öğrenciye özel PDF üretimini desteklemiyor."
            return@rememberLauncherForActivityResult
        }

        personalizedBusy = true
        status = "${examAtStart.participants.size} öğrenci için optik formlar hazırlanıyor…"
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
                    status = "${examAtStart.participants.size} öğrenci için kişisel optik form PDF'i oluşturuldu."
                }
            }.onFailure { error ->
                mainExecutor.execute {
                    personalizedBusy = false
                    status = "Öğrenci formları oluşturulamadı: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }
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

    val personalizedDocument = remember(current.templateSelection, designerRepository) {
        runCatching {
            resolveExamDesignerDocument(
                source = current.templateSelection.source,
                templateId = current.templateSelection.templateId,
                templateVersion = current.templateSelection.templateVersion,
                saved = designerRepository.list()
            )
        }.getOrNull()
    }
    val personalizedReady = current.personalizedFormsEnabled &&
        current.participants.isNotEmpty() &&
        personalizedDocument?.formSpec?.pdfProfile() != null

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

    fun refresh() {
        exam = examRepository.load(examId)
        scans = scanRepository.list().associateBy { it.id }
        keys = keyRepository.list()
        status = "Sınav yenilendi"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                ProductTopBar(
                    title = current.name,
                    leadingText = "‹",
                    onLeadingClick = onBack,
                    actionText = "⋮",
                    onActionClick = { menuExpanded = true }
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    DropdownMenuItem(
                        text = { Text("Yenile") },
                        onClick = {
                            menuExpanded = false
                            refresh()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (personalizedBusy) "Öğrenci formları hazırlanıyor…"
                                else "Öğrenciye Özel Formları Oluştur (${current.participants.size})"
                            )
                        },
                        enabled = personalizedReady && !personalizedBusy,
                        onClick = {
                            menuExpanded = false
                            personalizedPdfLauncher.launch(personalizedFormFileName(current.name))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Sınavı Düzenle") },
                        onClick = {
                            menuExpanded = false
                            showEditDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Sınavı Sil", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            showDeleteDialog = true
                        }
                    )
                }
            }
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

            if (status.isNotBlank()) {
                Text(
                    status,
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

    if (showEditDialog) {
        EditExamDialog(
            exam = current,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                runCatching { examRepository.save(updated) }
                    .onSuccess {
                        exam = updated
                        showEditDialog = false
                        status = "Sınav bilgileri güncellendi."
                    }
                    .onFailure { error ->
                        status = "Sınav güncellenemedi: ${error.message ?: error.javaClass.simpleName}"
                    }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Sınavı Sil") },
            text = { Text("${current.name} sınav kaydı silinecek. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        if (examRepository.delete(examId)) {
                            exam = null
                            onBack()
                        } else {
                            status = "Sınav silinemedi."
                        }
                    }
                ) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("İptal") }
            }
        )
    }
}

@Composable
private fun EditExamDialog(
    exam: Exam,
    onDismiss: () -> Unit,
    onSave: (Exam) -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale("tr", "TR")) }
    val templateOptions = remember(context, exam.id, exam.templateSelection) {
        loadEditExamTemplateOptions(context.applicationContext, exam.templateSelection)
    }
    val initialTemplate = remember(templateOptions, exam.templateSelection) {
        templateOptions.first { it.selection == exam.templateSelection }
    }
    var examName by remember(exam.id) { mutableStateOf(exam.name) }
    var schoolName by remember(exam.id) { mutableStateOf(exam.schoolName) }
    var folderName by remember(exam.id) { mutableStateOf(exam.folderName) }
    var dateText by remember(exam.id) { mutableStateOf(LocalDate.ofEpochDay(exam.examDateEpochDay).format(dateFormatter)) }
    var bookletText by remember(exam.id) { mutableStateOf(exam.bookletCount.toString()) }
    var selectedTemplate by remember(exam.id, exam.templateSelection) { mutableStateOf(initialTemplate) }
    var templateMenu by remember { mutableStateOf(false) }
    var wrongPolicy by remember(exam.id) { mutableStateOf(exam.wrongAnswerPolicy) }
    var wrongPolicyMenu by remember { mutableStateOf(false) }
    var personalizedEnabled by remember(exam.id) { mutableStateOf(exam.personalizedFormsEnabled) }
    var validationError by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sınavı Düzenle") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = examName,
                    onValueChange = { examName = it },
                    label = { Text("Sınav Adı") },
                    singleLine = true
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = schoolName,
                    onValueChange = { schoolName = it },
                    label = { Text("Okul Adı") },
                    singleLine = true
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = exam.papers.isEmpty(),
                        onClick = { templateMenu = true }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("Optik Form", style = MaterialTheme.typography.labelSmall)
                            Text(
                                selectedTemplate.name,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = templateMenu,
                        onDismissRequest = { templateMenu = false }
                    ) {
                        templateOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(option.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                },
                                onClick = {
                                    selectedTemplate = option
                                    if (option.selection.source != ActiveTemplateSource.DESIGNER_DOCUMENT) {
                                        personalizedEnabled = false
                                    }
                                    templateMenu = false
                                }
                            )
                        }
                    }
                }
                Text(
                    if (exam.papers.isEmpty()) {
                        "Optik form değiştirilebilir. Yeni okunacak kağıtlar seçilen formu kullanır."
                    } else {
                        "Bu sınavda ${exam.papers.size} okunmuş kağıt var. Okuma geometrisini bozmamak için optik form değişikliği kilitlidir."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (exam.papers.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Sınav Tarihi (GG.AA.YYYY)") },
                    singleLine = true
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Klasör Adı") },
                    singleLine = true
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = bookletText,
                    onValueChange = { bookletText = it.filter(Char::isDigit).take(1) },
                    label = { Text("Kitapçık Sayısı (1-8)") },
                    singleLine = true
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { wrongPolicyMenu = true }
                    ) {
                        Text("Yanlış Cevap: ${wrongPolicyLabel(wrongPolicy)}")
                    }
                    DropdownMenu(
                        expanded = wrongPolicyMenu,
                        onDismissRequest = { wrongPolicyMenu = false }
                    ) {
                        WrongAnswerPolicy.entries.forEach { policy ->
                            DropdownMenuItem(
                                text = { Text(wrongPolicyLabel(policy)) },
                                onClick = {
                                    wrongPolicy = policy
                                    wrongPolicyMenu = false
                                }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Öğrenciye Özel Form")
                        Text(
                            if (exam.participants.isEmpty()) "Bu sınavda seçili öğrenci yok."
                            else "${exam.participants.size} öğrencilik mevcut liste korunur.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = personalizedEnabled,
                        enabled = exam.participants.isNotEmpty() && selectedTemplate.selection.source == ActiveTemplateSource.DESIGNER_DOCUMENT,
                        onCheckedChange = { personalizedEnabled = it }
                    )
                }
                Text(
                    "Sınav adı, okul, tarih, klasör, optik form, kitapçık sayısı, yanlış cevap kuralı ve kişiselleştirme ayarı bu ekrandan güncellenir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (validationError.isNotBlank()) {
                    Text(validationError, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedDate = runCatching { LocalDate.parse(dateText.trim(), dateFormatter) }.getOrNull()
                    val bookletCount = bookletText.toIntOrNull()
                    validationError = when {
                        examName.isBlank() -> "Sınav adı zorunludur."
                        schoolName.isBlank() -> "Okul adı zorunludur."
                        parsedDate == null -> "Sınav tarihini GG.AA.YYYY biçiminde girin."
                        bookletCount !in 1..8 -> "Kitapçık sayısı 1-8 arasında olmalıdır."
                        selectedTemplate.selection != exam.templateSelection && exam.papers.isNotEmpty() ->
                            "Okunmuş kağıdı bulunan sınavın optik formu değiştirilemez."
                        personalizedEnabled && exam.participants.isEmpty() -> "Öğrenciye özel form için seçili öğrenci gerekir."
                        personalizedEnabled && selectedTemplate.selection.source != ActiveTemplateSource.DESIGNER_DOCUMENT ->
                            "Öğrenciye özel form için Form Editörü ile hazırlanmış bir form seçin."
                        else -> ""
                    }
                    if (validationError.isEmpty() && parsedDate != null && bookletCount != null) {
                        onSave(
                            exam.copy(
                                name = examName.trim(),
                                schoolName = schoolName.trim(),
                                templateSelection = selectedTemplate.selection,
                                folderName = folderName.trim(),
                                examDateEpochDay = parsedDate.toEpochDay(),
                                wrongAnswerPolicy = wrongPolicy,
                                bookletCount = bookletCount,
                                personalizedFormsEnabled = personalizedEnabled
                            )
                        )
                    }
                }
            ) { Text("Kaydet") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
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
    val key = record?.let { ExamPaperResolution.answerKey(link, it, keys) }
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

private fun loadEditExamTemplateOptions(
    context: android.content.Context,
    currentSelection: ActiveTemplateSelection
): List<EditExamTemplateOption> {
    val starter = DesignerStarterTemplates.all().map { document ->
        EditExamTemplateOption(
            name = document.name,
            selection = ActiveTemplateSelection(
                source = ActiveTemplateSource.DESIGNER_DOCUMENT,
                templateId = document.id,
                templateVersion = document.version
            )
        )
    }
    val saved = FileDesignerDocumentRepository(context).list().map { document ->
        EditExamTemplateOption(
            name = "${document.name} · Kayıtlı",
            selection = ActiveTemplateSelection(
                source = ActiveTemplateSource.DESIGNER_DOCUMENT,
                templateId = document.id,
                templateVersion = document.version
            )
        )
    }
    val available = (listOf(
        EditExamTemplateOption(
            name = ActiveOmrTemplateDefaults.displayName,
            selection = ActiveOmrTemplateDefaults.selection
        )
    ) + starter + saved).distinctBy {
        Triple(it.selection.source, it.selection.templateId, it.selection.templateVersion)
    }
    return if (available.any { it.selection == currentSelection }) {
        available
    } else {
        listOf(
            EditExamTemplateOption(
                name = "Mevcut Form · ${currentSelection.templateId} · v${currentSelection.templateVersion}",
                selection = currentSelection
            )
        ) + available
    }
}

private fun resolveExamDesignerDocument(
    source: ActiveTemplateSource,
    templateId: String,
    templateVersion: Int,
    saved: List<DesignerDocument>
): DesignerDocument? {
    if (source != ActiveTemplateSource.DESIGNER_DOCUMENT) return null
    return saved.firstOrNull { it.id == templateId && it.version == templateVersion }
        ?: DesignerStarterTemplates.all().firstOrNull { it.id == templateId && it.version == templateVersion }
}

private fun personalizedFormFileName(examName: String): String {
    val safe = examName.trim()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
        .trim('-')
        .take(48)
        .ifBlank { "sinav" }
    return "$safe-ogrenci-formlari.pdf"
}

private fun wrongPolicyLabel(policy: WrongAnswerPolicy): String = when (policy) {
    WrongAnswerPolicy.KEEP_AS_IS -> "Yanlışlar Doğruyu Götürmez"
    WrongAnswerPolicy.FOUR_WRONG_ONE_CORRECT -> "4 Yanlış 1 Doğru"
    WrongAnswerPolicy.THREE_WRONG_ONE_CORRECT -> "3 Yanlış 1 Doğru"
}

private fun keyMatchesExam(key: StoredAnswerKey, exam: Exam): Boolean =
    key.templateId == exam.templateSelection.templateId &&
        key.templateVersion == exam.templateSelection.templateVersion

private fun paperNumber(link: ExamPaperLink, record: ScanRecord?): String =
    if (record == null) link.studentNumber else ExamPaperResolution.metadata(link, record).studentNumber

private fun paperClass(link: ExamPaperLink, record: ScanRecord?): String =
    if (record == null) link.className else ExamPaperResolution.metadata(link, record).className

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