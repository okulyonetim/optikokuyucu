package com.okulyonetim.optikokuyucu.ui

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
import com.okulyonetim.optikokuyucu.exam.ExamPaperLink
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.exam.WrongAnswerPolicy
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeyResolver
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import com.okulyonetim.optikokuyucu.omr.scoring.OmrScorer
import com.okulyonetim.optikokuyucu.omr.scoring.ScoringPolicy
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey
import com.okulyonetim.optikokuyucu.omr.template.FileActiveTemplateSelectionRepository
import java.util.Locale

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
    var exam by remember(examId) { mutableStateOf(examRepository.load(examId)) }
    var scans by remember { mutableStateOf(scanRepository.list().associateBy { it.id }) }
    var keys by remember { mutableStateOf(keyRepository.list()) }
    var tab by remember { mutableStateOf(ExamDetailTab.PAPERS) }
    var query by remember { mutableStateOf("") }
    var classFilter by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

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

    val personalizedExport = rememberExamPersonalizedFormExportAction(current) { status = it }

    val classes = current.papers.map { paperClass(it, scans[it.scanRecordId]) }
        .filter(String::isNotBlank).distinct().sorted()
    val normalizedQuery = query.trim().lowercase(Locale("tr", "TR"))
    val visiblePapers = current.papers.filter { link ->
        val record = scans[link.scanRecordId]
        val name = link.studentName
        val number = paperNumber(link, record)
        val clazz = paperClass(link, record)
        (normalizedQuery.isBlank() || name.lowercase(Locale("tr", "TR")).contains(normalizedQuery) ||
            number.lowercase().contains(normalizedQuery) || clazz.lowercase().contains(normalizedQuery)) &&
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
                    onActionClick = { menuOpen = true }
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    DropdownMenuItem(
                        text = { Text("Öğrenciye Özel Optik Form Oluştur") },
                        enabled = personalizedExport.enabled,
                        onClick = {
                            menuOpen = false
                            personalizedExport.launch()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Sınavı Düzenle") },
                        onClick = {
                            menuOpen = false
                            editOpen = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Cevap Anahtarlarını Yönet") },
                        onClick = {
                            menuOpen = false
                            runCatching { FileActiveTemplateSelectionRepository(appContext).save(current.templateSelection) }
                            onOpenAnswerKeys()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Yenile") },
                        onClick = {
                            menuOpen = false
                            refresh()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Sınavı Sil") },
                        onClick = {
                            menuOpen = false
                            deleteConfirm = true
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == ExamDetailTab.PAPERS) {
                ExtendedFloatingActionButton(onClick = onScan) { Text("▣  Kağıt Oku", fontSize = 17.sp) }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (status.isNotBlank()) {
                Text(
                    status,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProductFilterPill("Kağıtlar", current.papers.size, tab == ExamDetailTab.PAPERS) { tab = ExamDetailTab.PAPERS }
                ProductFilterPill("Anahtarlar", keys.count { keyMatchesExam(it, current) }, tab == ExamDetailTab.KEYS) { tab = ExamDetailTab.KEYS }
                ProductFilterPill("Raporlar", selected = tab == ExamDetailTab.REPORTS) { tab = ExamDetailTab.REPORTS }
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
                            ProductFilterPill("Tümü", current.papers.size, classFilter == null) { classFilter = null }
                            classes.take(3).forEach { clazz ->
                                ProductFilterPill(
                                    clazz,
                                    current.papers.count { paperClass(it, scans[it.scanRecordId]) == clazz },
                                    classFilter == clazz
                                ) { classFilter = clazz }
                            }
                        }
                    }
                    if (visiblePapers.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (current.papers.isEmpty()) "Henüz kağıt okunmadı" else "Eşleşen öğrenci bulunamadı", style = MaterialTheme.typography.titleMedium)
                                Text(if (current.papers.isEmpty()) "Kağıt Oku ile bu sınava öğrenci optiklerini ekleyebilirsiniz." else "Arama veya sınıf filtresini değiştirin.")
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(visiblePapers, key = { it.scanRecordId }) { link ->
                                ExamPaperCard(current, link, scans[link.scanRecordId], keys) { onOpenPaper(link.scanRecordId) }
                            }
                            item { Spacer(Modifier.height(90.dp)) }
                        }
                    }
                }
                ExamDetailTab.KEYS -> ExamKeysTab(current, keys, onOpenAnswerKeys)
                ExamDetailTab.REPORTS -> ExamReportsTab(current, onOpenReports)
            }
        }
    }

    if (editOpen) {
        ExamEditDialog(
            exam = current,
            onDismiss = { editOpen = false },
            onSave = { updated ->
                runCatching { examRepository.save(updated) }
                    .onSuccess {
                        exam = examRepository.load(examId)
                        editOpen = false
                        status = "Sınav bilgileri güncellendi."
                    }
                    .onFailure { status = "Sınav güncellenemedi: ${it.message}" }
            }
        )
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Sınavı Sil") },
            text = { Text("“${current.name}” sınav kaydı silinecek. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    if (examRepository.delete(current.id)) onBack()
                    else status = "Sınav silinemedi."
                    deleteConfirm = false
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("İptal") } }
        )
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
    val name = link.studentName.ifBlank { if (number.isBlank()) "İsimsiz Öğrenci" else "Öğrenci $number" }
    val key = record?.let { AnswerKeyResolver.resolve(it, keys) }
    val score = if (record != null && key != null) runCatching {
        OmrScorer.score(record, key.answerKey, scoringPolicy(exam.wrongAnswerPolicy))
    }.getOrNull() else null

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
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(54.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Text(initials(name), color = MaterialTheme.colorScheme.primary, fontSize = 20.sp) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(clazz, number).filter(String::isNotBlank).joinToString("  •  ").ifBlank { "Tarama kaydı" })
                }
            }
            when {
                record == null -> ProductStatusBadge("KAYIT YOK", ProductBadgeTone.RED)
                score == null -> ProductStatusBadge("ANAHTAR YOK", ProductBadgeTone.ORANGE)
                else -> ProductStatusBadge("Puan: ${"%.2f".format(Locale.US, score.totalPoints)}", if (score.confidentlyEvaluated) ProductBadgeTone.GREEN else ProductBadgeTone.ORANGE)
            }
        }
    }
}

@Composable
private fun ExamKeysTab(exam: Exam, keys: List<StoredAnswerKey>, onOpenAnswerKeys: () -> Unit) {
    val context = LocalContext.current
    val selectionRepository = remember(context) { FileActiveTemplateSelectionRepository(context.applicationContext) }
    val matching = keys.filter { keyMatchesExam(it, exam) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (matching.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bu sınav için cevap anahtarı yok", style = MaterialTheme.typography.titleMedium)
                    Text("Manuel, Excel, galeri veya kamera ile cevap anahtarı ekleyebilirsiniz.")
                }
            }
        } else {
            matching.forEach { key ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(key.variantValue?.let { "Kitapçık $it" } ?: "Genel Anahtar", fontWeight = FontWeight.SemiBold)
                            Text("${key.answerKey.answers.size} soru")
                        }
                        ProductStatusBadge("HAZIR", ProductBadgeTone.GREEN)
                    }
                }
            }
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
            runCatching { selectionRepository.save(exam.templateSelection) }.onSuccess { onOpenAnswerKeys() }
        }) { Text("Cevap Anahtarlarını Yönet") }
    }
}

@Composable
private fun ExamReportsTab(exam: Exam, onOpenReports: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sınav Raporları", style = MaterialTheme.typography.titleMedium)
                Text("${exam.papers.size} kağıt bu sınava bağlı.")
                Text("Öğrenci sonuçlarını CSV, Excel (.xlsx) veya PDF olarak dışa aktarabilirsiniz.")
            }
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenReports) { Text("Sınav Raporunu Aç") }
    }
}

private fun keyMatchesExam(key: StoredAnswerKey, exam: Exam): Boolean =
    key.templateId == exam.templateSelection.templateId && key.templateVersion == exam.templateSelection.templateVersion

private fun paperNumber(link: ExamPaperLink, record: ScanRecord?): String =
    link.studentNumber.ifBlank { record?.grid("studentNumber")?.value.orEmpty() }

private fun paperClass(link: ExamPaperLink, record: ScanRecord?): String =
    link.className.ifBlank { record?.grid("class")?.value.orEmpty() }

private fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter(String::isNotBlank)
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
