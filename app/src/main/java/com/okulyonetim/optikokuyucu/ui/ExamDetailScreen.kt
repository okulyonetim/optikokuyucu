package com.okulyonetim.optikokuyucu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.okulyonetim.optikokuyucu.exam.ExamParticipant
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
import com.okulyonetim.optikokuyucu.student.FileStudentRosterRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

private enum class ExamDetailTab { PAPERS, KEYS, REPORTS }

private data class EditExamTemplateOption(
    val name: String,
    val selection: ActiveTemplateSelection
)

@OptIn(ExperimentalMaterial3Api::class)
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
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProductEmptyState(
                title = "Sınav kaydı bulunamadı",
                body = "Sınav silinmiş veya artık erişilebilir olmayabilir."
            )
            Spacer(Modifier.height(10.dp))
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
    val answerKeyCount = keys.count { keyMatchesExam(it, current) }

    fun refresh(message: String = "Sınav yenilendi") {
        exam = examRepository.load(examId)
        scans = scanRepository.list().associateBy { it.id }
        keys = keyRepository.list()
        status = message
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ProductTopBar(
                title = current.name,
                leadingText = "‹",
                onLeadingClick = onBack,
                actionText = "⋮",
                onActionClick = { menuExpanded = true }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            ProductMetricStrip(
                modifier = Modifier.padding(horizontal = 14.dp),
                metrics = listOf(
                    "Kağıt" to current.papers.size.toString(),
                    "Anahtar" to answerKeyCount.toString(),
                    "Öğrenci" to current.participants.size.toString(),
                    "Kitapçık" to current.bookletCount.toString()
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
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
                    count = answerKeyCount,
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
                ProductCompactCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    Text(
                        status,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp
                    )
                }
            }

            when (tab) {
                ExamDetailTab.PAPERS -> {
                    ProductSearchField(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Öğrenci, numara veya sınıf ara"
                    )

                    Button(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        onClick = onScan
                    ) {
                        Text("▣  Kağıt Oku", fontWeight = FontWeight.SemiBold)
                    }

                    if (classes.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                ProductFilterPill(
                                    label = "Tümü",
                                    count = current.papers.size,
                                    selected = classFilter == null,
                                    onClick = { classFilter = null }
                                )
                            }
                            items(classes, key = { it }) { clazz ->
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
                        ProductEmptyState(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            title = if (current.papers.isEmpty()) "Henüz kağıt okunmadı" else "Eşleşen öğrenci bulunamadı",
                            body = if (current.papers.isEmpty()) {
                                "Kağıt Oku ile bu sınava öğrenci optiklerini ekleyebilirsiniz."
                            } else {
                                "Arama veya sınıf filtresini değiştirin."
                            }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
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
                            item { Spacer(Modifier.height(12.dp)) }
                        }
                    }
                }

                ExamDetailTab.KEYS -> ExamAnswerKeyEditor(
                    exam = current,
                    openCvReady = true,
                    onChanged = { keys = keyRepository.list() }
                )

                ExamDetailTab.REPORTS -> ExamReportsTab(
                    exam = current,
                    onOpenReports = onOpenReports
                )
            }
        }
    }

    if (menuExpanded) {
        ModalBottomSheet(onDismissRequest = { menuExpanded = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("Sınav Seçenekleri", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { menuExpanded = false; refresh() }
                ) { Text("Yenile", modifier = Modifier.fillMaxWidth()) }
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = personalizedReady && !personalizedBusy,
                    onClick = {
                        menuExpanded = false
                        personalizedPdfLauncher.launch(personalizedFormFileName(current.name))
                    }
                ) {
                    Text(
                        if (personalizedBusy) "Öğrenci formları hazırlanıyor…"
                        else "Öğrenciye Özel Formları Oluştur (${current.participants.size})",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { menuExpanded = false; showEditDialog = true }
                ) { Text("Sınavı Düzenle", modifier = Modifier.fillMaxWidth()) }
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { menuExpanded = false; showDeleteDialog = true }
                ) { Text("Sınavı Sil", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(18.dp))
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
    val rosterParticipants = remember(context, exam.id) {
        FileStudentRosterRepository(context.applicationContext).list().map { student ->
            ExamParticipant(
                studentNumber = student.studentNumber,
                studentName = student.fullName,
                className = student.className
            ).normalized()
        }
    }
    val participantOptions = remember(rosterParticipants, exam.participants) {
        (rosterParticipants + exam.participants.map(ExamParticipant::normalized))
            .distinctBy { it.identityKey }
            .sortedWith(compareBy<ExamParticipant> { it.className }.thenBy { it.studentName })
    }
    val participantClasses = remember(participantOptions) {
        participantOptions.map { it.className }.distinct().sorted()
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
    var selectedParticipantKeys by remember(exam.id) {
        mutableStateOf(exam.participants.map { it.normalized().identityKey }.toSet())
    }
    var participantClassMenu by remember { mutableStateOf(false) }
    var participantStudentMenu by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf("") }

    val selectedParticipants = participantOptions.filter { it.identityKey in selectedParticipantKeys }
    val selectedClassNames = selectedParticipants.map { it.className }.distinct().sorted()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sınavı Düzenle") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()),
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
                            Text(selectedTemplate.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    DropdownMenu(expanded = templateMenu, onDismissRequest = { templateMenu = false }) {
                        templateOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
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
                    color = if (exam.papers.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
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
                    DropdownMenu(expanded = wrongPolicyMenu, onDismissRequest = { wrongPolicyMenu = false }) {
                        WrongAnswerPolicy.entries.forEach { policy ->
                            DropdownMenuItem(
                                text = { Text(wrongPolicyLabel(policy)) },
                                onClick = { wrongPolicy = policy; wrongPolicyMenu = false }
                            )
                        }
                    }
                }

                Text("Sınıf ve Öğrenciler", fontWeight = FontWeight.SemiBold)
                if (participantOptions.isEmpty()) {
                    Text(
                        "Öğrenci listesi boş. Önce Öğrenciler sayfasından öğrenci ekleyin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { participantClassMenu = true }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("Toplu Sınıf Seçimi", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    if (selectedClassNames.isEmpty()) {
                                        "Sınıf seçin · Seçili ${selectedParticipants.size}"
                                    } else {
                                        "${selectedClassNames.joinToString(", ")} · Seçili ${selectedParticipants.size}"
                                    },
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = participantClassMenu,
                            onDismissRequest = { participantClassMenu = false }
                        ) {
                            participantClasses.forEach { clazz ->
                                val classParticipants = participantOptions.filter { it.className == clazz }
                                val classKeys = classParticipants.map { it.identityKey }.toSet()
                                val allClassSelected = classKeys.isNotEmpty() && classKeys.all { it in selectedParticipantKeys }
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = allClassSelected, onCheckedChange = null)
                                            Text("$clazz · ${classParticipants.size} öğrenci")
                                        }
                                    },
                                    onClick = {
                                        selectedParticipantKeys = if (allClassSelected) {
                                            selectedParticipantKeys - classKeys
                                        } else {
                                            selectedParticipantKeys + classKeys
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { participantStudentMenu = true }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("Bireysel Öğrenci Seçimi", style = MaterialTheme.typography.labelSmall)
                                Text("${selectedParticipants.size} öğrenci seçili")
                            }
                        }
                        DropdownMenu(
                            expanded = participantStudentMenu,
                            onDismissRequest = { participantStudentMenu = false }
                        ) {
                            participantOptions.forEach { participant ->
                                val key = participant.identityKey
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = key in selectedParticipantKeys,
                                                onCheckedChange = null
                                            )
                                            Column {
                                                Text(
                                                    participant.studentName,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    "${participant.className} · No: ${participant.studentNumber}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedParticipantKeys = if (key in selectedParticipantKeys) {
                                            selectedParticipantKeys - key
                                        } else {
                                            selectedParticipantKeys + key
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        if (selectedParticipants.isEmpty()) {
                            "Katılımcı seçilmedi. Sınav serbest taramaya açık kalır."
                        } else {
                            "${selectedClassNames.size} sınıftan toplam ${selectedParticipants.size} öğrenci seçili."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Öğrenciye Özel Form")
                        Text(
                            "Seçili öğrenci: ${selectedParticipants.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = personalizedEnabled,
                        enabled = selectedParticipants.isNotEmpty() && selectedTemplate.selection.source == ActiveTemplateSource.DESIGNER_DOCUMENT,
                        onCheckedChange = { personalizedEnabled = it }
                    )
                }
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
                        personalizedEnabled && selectedParticipants.isEmpty() -> "Öğrenciye özel form için seçili öğrenci gerekir."
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
                                participants = selectedParticipants,
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
    val key = record?.let { ExamPaperResolution.answerKey(exam.id, link, it, keys) }
    val score = if (record != null && key != null) {
        runCatching { OmrScorer.score(record, key.answerKey, scoringPolicy(exam.wrongAnswerPolicy)) }.getOrNull()
    } else null

    ProductCompactCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductInitialBadge(initials(name))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(clazz, number).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Tarama kaydı" },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when {
                record == null -> ProductStatusBadge("KAYIT YOK", ProductBadgeTone.RED)
                score == null -> ProductStatusBadge("ANAHTAR YOK", ProductBadgeTone.ORANGE)
                else -> ProductStatusBadge(
                    text = formatScore(score.totalPoints),
                    tone = if (score.confidentlyEvaluated) ProductBadgeTone.GREEN else ProductBadgeTone.ORANGE
                )
            }
        }
    }
}

@Composable
private fun ExamReportsTab(exam: Exam, onOpenReports: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProductSettingsSection(
            title = "Sınav Raporları",
            description = "${exam.papers.size} kağıdın öğrenci sonuçlarını inceleyin ve raporları dışa aktarın."
        ) {
            ProductMetricStrip(
                metrics = listOf(
                    "Kağıt" to exam.papers.size.toString(),
                    "Öğrenci" to exam.participants.size.toString(),
                    "Kitapçık" to exam.bookletCount.toString()
                )
            )
        }
        ProductSettingsLink(
            symbol = "↗",
            title = "Sınav Raporunu Aç",
            description = "Sonuçları görüntüleyin; CSV, Excel veya PDF olarak dışa aktarın.",
            onClick = onOpenReports
        )
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
    key.examId == exam.id &&
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
