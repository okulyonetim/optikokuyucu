package com.okulyonetim.optikokuyucu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.okulyonetim.optikokuyucu.R
import com.okulyonetim.optikokuyucu.exam.ExamPaperMetadataEditor
import com.okulyonetim.optikokuyucu.exam.ExamPaperMetrics
import com.okulyonetim.optikokuyucu.exam.ExamPaperRemoval
import com.okulyonetim.optikokuyucu.exam.ExamPaperResolution
import com.okulyonetim.optikokuyucu.exam.ExamReportBuilder
import com.okulyonetim.optikokuyucu.exam.ExamScoringPolicyResolver
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.exam.StudentResultPdfExporter
import com.okulyonetim.optikokuyucu.exam.StudentResultPresentationBuilder
import com.okulyonetim.optikokuyucu.exam.examLessonDisplayName
import com.okulyonetim.optikokuyucu.exam.questionDisplayNumber
import com.okulyonetim.optikokuyucu.exam.questionLessonPrefix
import com.okulyonetim.optikokuyucu.omr.results.FileScanImageRepository
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswer
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswerState
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import com.okulyonetim.optikokuyucu.omr.scoring.OmrScorer
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluation
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluationState
import com.okulyonetim.optikokuyucu.omr.template.OmrRecognitionBindingsResolver
import com.okulyonetim.optikokuyucu.student.FileStudentRosterRepository
import java.util.Locale

private enum class StudentPaperTab { CONTENT, IMAGE }

private val CorrectGreen = Color(0xFF2E9D57)
private val WrongRed = Color(0xFFD93B3B)
private val WarningOrange = Color(0xFFD77A00)

@Composable
fun StudentPaperDetailScreen(
    examId: String,
    scanRecordId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val examRepository = remember(context) { FileExamRepository(appContext) }
    val scanRepository = remember(context) { FileScanRecordRepository(appContext) }
    val imageRepository = remember(context) { FileScanImageRepository(appContext) }
    val keyRepository = remember(context) { FileAnswerKeyRepository(appContext) }
    val studentRepository = remember(context) { FileStudentRosterRepository(appContext) }

    var exam by remember(examId) { mutableStateOf(examRepository.load(examId)) }
    val record = remember(scanRecordId) { scanRepository.load(scanRecordId) }
    val keys = remember { keyRepository.list() }
    val currentExam = exam
    val link = currentExam?.paperForScan(scanRecordId)

    if (currentExam == null || record == null || link == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Öğrenci kağıdı bulunamadı.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onBack) { Text("Sınava dön") }
        }
        return
    }

    val recognitionBindings = remember(record.id) { OmrRecognitionBindingsResolver.fromRecord(record) }
    val detectedStudentNumber = link.studentNumber.ifBlank {
        recognitionBindings.studentNumber(record).orEmpty()
    }
    val rosterStudent = remember(examId, scanRecordId, detectedStudentNumber) {
        studentRepository.findByNumber(detectedStudentNumber)
    }

    var tab by remember { mutableStateOf(StudentPaperTab.CONTENT) }
    var studentName by remember(examId, scanRecordId) {
        mutableStateOf(link.studentName.ifBlank { rosterStudent?.fullName.orEmpty() })
    }
    var className by remember(examId, scanRecordId) {
        mutableStateOf(
            link.className.ifBlank {
                rosterStudent?.className ?: recognitionBindings.classCode(record).orEmpty()
            }
        )
    }
    var studentNumber by remember(examId, scanRecordId) {
        mutableStateOf(rosterStudent?.studentNumber ?: detectedStudentNumber)
    }
    var bookletCode by remember(examId, scanRecordId) {
        mutableStateOf(link.bookletCode.ifBlank { recognitionBindings.booklet(record).orEmpty() })
    }
    var status by remember { mutableStateOf("") }
    var deleteDialogOpen by remember { mutableStateOf(false) }
    var pendingStudentPdf by remember { mutableStateOf<ByteArray?>(null) }

    val studentPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(StudentResultPdfExporter.MIME_TYPE)
    ) { uri ->
        val bytes = pendingStudentPdf
        pendingStudentPdf = null
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "PDF çıktı akışı açılamadı." }
                output.write(bytes)
                output.flush()
            }
        }.onSuccess {
            status = "Öğrenci sonuç PDF'i kaydedildi."
        }.onFailure { error ->
            status = "PDF kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    val displayLink = remember(link, bookletCode, studentName, className, studentNumber) {
        link.copy(
            studentName = studentName.trim(),
            className = className.trim(),
            studentNumber = studentNumber.trim(),
            bookletCode = bookletCode.trim()
        )
    }
    val matchingKey = remember(record.id, keys, displayLink.bookletCode, currentExam.id) {
        ExamPaperResolution.answerKey(currentExam.id, displayLink, record, keys)
    }
    val score = remember(
        record.id,
        matchingKey,
        currentExam.wrongAnswerPolicy,
        currentExam.scoringConfiguration
    ) {
        matchingKey?.let { stored ->
            runCatching {
                OmrScorer.score(
                    record = record,
                    answerKey = stored.answerKey,
                    policy = ExamScoringPolicyResolver.resolve(currentExam)
                )
            }.getOrNull()
        }
    }
    val previewExam = remember(currentExam, displayLink) { currentExam.withPaper(displayLink) }
    val calculatedReport = remember(previewExam, keys) {
        ExamReportBuilder.build(
            exam = previewExam,
            records = scanRepository.list(),
            answerKeys = keys
        )
    }
    val presentation = remember(calculatedReport, scanRecordId) {
        StudentResultPresentationBuilder.build(calculatedReport, scanRecordId)
    }
    val metrics = score?.let(ExamPaperMetrics::from)
    val evaluations = score?.evaluations?.associateBy { it.questionId }.orEmpty()
    val lessonPrefixes = remember(record.id) {
        record.answers.mapNotNull { questionLessonPrefix(it.questionId) }.distinct()
    }
    var selectedLesson by remember(record.id) { mutableStateOf(lessonPrefixes.firstOrNull()) }
    var lessonMenuOpen by remember { mutableStateOf(false) }
    val visibleAnswers = record.answers.filter { answer ->
        selectedLesson == null || questionLessonPrefix(answer.questionId) == selectedLesson
    }
    val title = studentName.ifBlank {
        studentNumber.takeIf { it.isNotBlank() }?.let { "Öğrenci $it" } ?: "Öğrenci Sonucu"
    }

    fun lookupStudent(number: String) {
        val student = studentRepository.findByNumber(number) ?: return
        studentNumber = student.studentNumber
        studentName = student.fullName
        className = student.className
        status = "Öğrenci bulundu · ${student.fullName} · ${student.className}"
    }

    fun saveMetadata() {
        runCatching {
            ExamPaperMetadataEditor.update(
                exam = currentExam,
                scanRecordId = scanRecordId,
                studentName = studentName,
                className = className,
                studentNumber = studentNumber,
                bookletCode = bookletCode
            ).also(examRepository::save)
        }.onSuccess { updated ->
            exam = updated
            status = "Öğrenci bilgileri kaydedildi"
        }.onFailure { error ->
            status = "Kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    fun createStudentPdf() {
        val currentPresentation = presentation ?: return
        runCatching {
            StudentResultPdfExporter.exportBytes(
                presentation = currentPresentation,
                typeface = ResourcesCompat.getFont(context, R.font.noto_sans)
            )
        }.onSuccess { bytes ->
            pendingStudentPdf = bytes
            studentPdfLauncher.launch(studentResultFileName(currentExam.name, studentName, studentNumber))
        }.onFailure { error ->
            status = "PDF hazırlanamadı: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    fun deletePaper() {
        runCatching {
            val updated = ExamPaperRemoval.unlink(currentExam, scanRecordId)
            examRepository.save(updated)
            val referencedElsewhere = examRepository.list().any { otherExam ->
                otherExam.id != examId && otherExam.paperForScan(scanRecordId) != null
            }
            if (!referencedElsewhere) {
                runCatching { scanRepository.delete(scanRecordId) }
                runCatching { imageRepository.delete(scanRecordId) }
            }
        }.onSuccess {
            deleteDialogOpen = false
            onBack()
        }.onFailure { error ->
            deleteDialogOpen = false
            status = "Silinemedi: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    if (deleteDialogOpen) {
        AlertDialog(
            onDismissRequest = { deleteDialogOpen = false },
            title = { Text("Kağıt silinsin mi?") },
            text = {
                Text(
                    "Bu kağıt sınavdan kaldırılacak. Tarama başka bir sınava bağlı değilse " +
                        "ham OMR kaydı ve saklanan görüntü de cihazdan silinecek. Bu işlem geri alınamaz."
                )
            },
            confirmButton = {
                TextButton(onClick = ::deletePaper) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogOpen = false }) { Text("Vazgeç") }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ProductTopBar(
                title = title,
                leadingText = "×",
                onLeadingClick = onBack,
                actionText = "Kaydet",
                onActionClick = ::saveMetadata
            )
        },
        bottomBar = { StudentResultSummaryBar(metrics) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(top = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProductFilterPill(
                    label = "İçerik",
                    selected = tab == StudentPaperTab.CONTENT,
                    onClick = { tab = StudentPaperTab.CONTENT }
                )
                ProductFilterPill(
                    label = "Resim",
                    selected = tab == StudentPaperTab.IMAGE,
                    onClick = { tab = StudentPaperTab.IMAGE }
                )
            }

            if (tab == StudentPaperTab.CONTENT) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    item {
                        StudentResultHero(result = presentation, hasKey = matchingKey != null)
                    }
                    presentation?.takeIf { it.lessons.isNotEmpty() }?.let { result ->
                        item { StudentResultLessonDashboard(result) }
                    }
                    item {
                        MetadataEditor(
                            studentNumber = studentNumber,
                            className = className,
                            studentName = studentName,
                            bookletCode = bookletCode,
                            selectedLesson = selectedLesson,
                            lessonPrefixes = lessonPrefixes,
                            lessonMenuOpen = lessonMenuOpen,
                            status = status,
                            pdfEnabled = presentation != null,
                            onNumberChanged = { value ->
                                studentNumber = value.filter(Char::isDigit)
                                lookupStudent(studentNumber)
                            },
                            onClassChanged = { className = it },
                            onNameChanged = { studentName = it },
                            onBookletChanged = { bookletCode = it.uppercase().take(2) },
                            onLessonMenuChanged = { lessonMenuOpen = it },
                            onLessonSelected = { selectedLesson = it; lessonMenuOpen = false },
                            onPdf = ::createStudentPdf,
                            onDelete = { deleteDialogOpen = true }
                        )
                    }
                    if (visibleAnswers.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text("Bu ders için soru kaydı bulunamadı.", modifier = Modifier.padding(14.dp))
                            }
                        }
                    } else {
                        items(visibleAnswers, key = { it.questionId }) { answer ->
                            QuestionAnswerRow(answer, evaluations[answer.questionId])
                        }
                    }
                    item { Spacer(Modifier.height(14.dp)) }
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    StudentResultHero(result = presentation, hasKey = matchingKey != null)
                    Box(modifier = Modifier.weight(1f)) {
                        StudentPaperImagePanel(
                            scanRecordId = scanRecordId,
                            record = record,
                            evaluations = evaluations,
                            templateSelection = currentExam.templateSelection
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataEditor(
    studentNumber: String,
    className: String,
    studentName: String,
    bookletCode: String,
    selectedLesson: String?,
    lessonPrefixes: List<String>,
    lessonMenuOpen: Boolean,
    status: String,
    pdfEnabled: Boolean,
    onNumberChanged: (String) -> Unit,
    onClassChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onBookletChanged: (String) -> Unit,
    onLessonMenuChanged: (Boolean) -> Unit,
    onLessonSelected: (String?) -> Unit,
    onPdf: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = studentNumber,
                onValueChange = onNumberChanged,
                label = { Text("Numara") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = className,
                onValueChange = onClassChanged,
                label = { Text("Sınıf") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = studentName,
            onValueChange = onNameChanged,
            label = { Text("Ad Soyad") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onLessonMenuChanged(true) },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Ders", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(selectedLesson?.let(::examLessonDisplayName) ?: "Tümü", fontSize = 11.sp)
                    }
                }
                DropdownMenu(
                    expanded = lessonMenuOpen,
                    onDismissRequest = { onLessonMenuChanged(false) }
                ) {
                    DropdownMenuItem(text = { Text("Tümü") }, onClick = { onLessonSelected(null) })
                    lessonPrefixes.forEach { prefix ->
                        DropdownMenuItem(
                            text = { Text(examLessonDisplayName(prefix)) },
                            onClick = { onLessonSelected(prefix) }
                        )
                    }
                }
            }
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = bookletCode,
                onValueChange = onBookletChanged,
                label = { Text("Kitapçık") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = pdfEnabled,
            onClick = onPdf,
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Öğrenci PDF Raporu")
        }
        if (status.isNotBlank()) {
            Text(
                status,
                fontSize = 10.sp,
                color = if (
                    status.startsWith("Kaydedilemedi") ||
                    status.startsWith("Silinemedi") ||
                    status.startsWith("PDF hazırlanamadı") ||
                    status.startsWith("PDF kaydedilemedi")
                ) MaterialTheme.colorScheme.error else CorrectGreen
            )
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onDelete,
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Kağıdı Sınavdan Sil", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun QuestionAnswerRow(answer: RecordedAnswer, evaluation: QuestionEvaluation?) {
    val choices = answer.choiceScores.keys.toList().ifEmpty {
        listOfNotNull(answer.selectedChoice, evaluation?.expectedChoice).distinct()
    }
    val stateColor = when (evaluation?.state) {
        QuestionEvaluationState.CORRECT -> CorrectGreen
        QuestionEvaluationState.WRONG -> WrongRed
        QuestionEvaluationState.BLANK -> MaterialTheme.colorScheme.onSurfaceVariant
        QuestionEvaluationState.DOUBLE_MARK,
        QuestionEvaluationState.SUSPICIOUS -> WarningOrange
        QuestionEvaluationState.NO_KEY,
        null -> when (answer.state) {
            RecordedAnswerState.MARKED -> MaterialTheme.colorScheme.primary
            RecordedAnswerState.BLANK -> MaterialTheme.colorScheme.onSurfaceVariant
            RecordedAnswerState.DOUBLE_MARK,
            RecordedAnswerState.SUSPICIOUS -> WarningOrange
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.size(width = 36.dp, height = 40.dp),
            text = "${questionDisplayNumber(answer.questionId)})",
            color = stateColor,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        choices.forEach { choice ->
            ChoiceBubble(
                choice = choice,
                selected = answer.selectedChoice == choice,
                expected = evaluation?.expectedChoice == choice,
                evaluationState = evaluation?.state
            )
        }
        Spacer(Modifier.weight(1f))
        Text(questionStateLabel(answer, evaluation), style = MaterialTheme.typography.labelSmall, color = stateColor)
    }
}

@Composable
private fun ChoiceBubble(
    choice: String,
    selected: Boolean,
    expected: Boolean,
    evaluationState: QuestionEvaluationState?
) {
    val background = when {
        selected && evaluationState == QuestionEvaluationState.CORRECT -> CorrectGreen
        selected && evaluationState == QuestionEvaluationState.WRONG -> WrongRed
        selected && evaluationState == QuestionEvaluationState.SUSPICIOUS -> WarningOrange
        selected && evaluationState == null -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val borderColor = when {
        expected && !(selected && evaluationState == QuestionEvaluationState.CORRECT) -> CorrectGreen
        selected && evaluationState == QuestionEvaluationState.WRONG -> WrongRed
        else -> MaterialTheme.colorScheme.outline
    }
    val textColor = when {
        selected && (evaluationState == QuestionEvaluationState.CORRECT ||
            evaluationState == QuestionEvaluationState.WRONG ||
            evaluationState == QuestionEvaluationState.SUSPICIOUS) -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = background,
        border = BorderStroke(if (expected) 2.dp else 1.dp, borderColor)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(choice, color = textColor, fontSize = 14.sp)
        }
    }
}

@Composable
private fun StudentResultSummaryBar(metrics: ExamPaperMetrics?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("D ${metrics?.correct ?: 0}", color = CorrectGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("Y ${metrics?.wrong ?: 0}", color = WrongRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("B ${metrics?.blank ?: 0}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Text(
                "N ${metrics?.let { formatNet(it.net) } ?: "—"}",
                color = CorrectGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

private fun questionStateLabel(answer: RecordedAnswer, evaluation: QuestionEvaluation?): String =
    when (evaluation?.state) {
        QuestionEvaluationState.CORRECT -> "Doğru"
        QuestionEvaluationState.WRONG -> "Yanlış"
        QuestionEvaluationState.BLANK -> "Boş"
        QuestionEvaluationState.DOUBLE_MARK -> "Çift"
        QuestionEvaluationState.SUSPICIOUS -> "Şüpheli"
        QuestionEvaluationState.NO_KEY -> "Anahtar yok"
        null -> when (answer.state) {
            RecordedAnswerState.MARKED -> "İşaretli"
            RecordedAnswerState.BLANK -> "Boş"
            RecordedAnswerState.DOUBLE_MARK -> "Çift"
            RecordedAnswerState.SUSPICIOUS -> "Şüpheli"
        }
    }

private fun studentResultFileName(examName: String, studentName: String, studentNumber: String): String {
    fun safe(value: String): String = value.trim()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
        .trim('-')
        .take(38)
    val exam = safe(examName).ifBlank { "sinav" }
    val student = safe(studentName).ifBlank { safe(studentNumber).ifBlank { "ogrenci" } }
    return "$exam-$student-sonuc.pdf"
}

private fun formatNet(value: Double): String = String.format(Locale.US, "%.2f", value)
