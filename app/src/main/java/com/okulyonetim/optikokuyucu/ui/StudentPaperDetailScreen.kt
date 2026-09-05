package com.okulyonetim.optikokuyucu.ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.okulyonetim.optikokuyucu.exam.ExamPaperMetadataEditor
import com.okulyonetim.optikokuyucu.exam.ExamPaperMetrics
import com.okulyonetim.optikokuyucu.exam.ExamScoringPolicyResolver
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.exam.questionDisplayNumber
import com.okulyonetim.optikokuyucu.exam.questionLessonPrefix
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextElement
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswer
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswerState
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeyResolver
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import com.okulyonetim.optikokuyucu.omr.scoring.OmrScorer
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluation
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluationState
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import java.util.Locale

private enum class StudentPaperTab { CONTENT, IMAGE }

private val CorrectGreen = Color(0xFF42B653)
private val WrongRed = Color(0xFFF0443E)
private val WarningOrange = Color(0xFFF39B25)

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
    val keyRepository = remember(context) { FileAnswerKeyRepository(appContext) }

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

    var tab by remember { mutableStateOf(StudentPaperTab.CONTENT) }
    var studentName by remember(examId, scanRecordId) { mutableStateOf(link.studentName) }
    var className by remember(examId, scanRecordId) {
        mutableStateOf(link.className.ifBlank { record.grid("class")?.value.orEmpty() })
    }
    var studentNumber by remember(examId, scanRecordId) {
        mutableStateOf(link.studentNumber.ifBlank { record.grid("studentNumber")?.value.orEmpty() })
    }
    var bookletCode by remember(examId, scanRecordId) {
        mutableStateOf(link.bookletCode.ifBlank { record.grid("booklet")?.value.orEmpty() })
    }
    var status by remember { mutableStateOf("") }

    val matchingKey = remember(record.id, keys) { AnswerKeyResolver.resolve(record, keys) }
    val score = remember(record.id, matchingKey, currentExam.wrongAnswerPolicy) {
        matchingKey?.let { stored ->
            runCatching {
                OmrScorer.score(
                    record = record,
                    answerKey = stored.answerKey,
                    policy = ExamScoringPolicyResolver.resolve(currentExam.wrongAnswerPolicy)
                )
            }.getOrNull()
        }
    }
    val metrics = score?.let(ExamPaperMetrics::from)
    val evaluations = score?.evaluations?.associateBy { it.questionId }.orEmpty()
    val lessonNames = remember(currentExam.templateSelection) {
        resolveLessonNames(appContext, currentExam.templateSelection)
    }
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
        bottomBar = {
            StudentResultSummaryBar(metrics)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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

            ScoreHeader(metrics = metrics, hasKey = matchingKey != null)

            if (tab == StudentPaperTab.CONTENT) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    modifier = Modifier.weight(1f),
                                    value = studentNumber,
                                    onValueChange = { studentNumber = it },
                                    label = { Text("Numara") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(28.dp)
                                )
                                OutlinedTextField(
                                    modifier = Modifier.weight(1f),
                                    value = className,
                                    onValueChange = { className = it },
                                    label = { Text("Sınıf") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(28.dp)
                                )
                            }

                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = studentName,
                                onValueChange = { studentName = it },
                                label = { Text("Ad Soyad") },
                                singleLine = true,
                                shape = RoundedCornerShape(28.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { lessonMenuOpen = true },
                                        shape = RoundedCornerShape(28.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                                        ) {
                                            Text("Ders", style = MaterialTheme.typography.labelSmall)
                                            Text(
                                                selectedLesson?.let { lessonNames[it] ?: humanizeLesson(it) }
                                                    ?: "Tümü"
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = lessonMenuOpen,
                                        onDismissRequest = { lessonMenuOpen = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Tümü") },
                                            onClick = {
                                                selectedLesson = null
                                                lessonMenuOpen = false
                                            }
                                        )
                                        lessonPrefixes.forEach { prefix ->
                                            DropdownMenuItem(
                                                text = { Text(lessonNames[prefix] ?: humanizeLesson(prefix)) },
                                                onClick = {
                                                    selectedLesson = prefix
                                                    lessonMenuOpen = false
                                                }
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    modifier = Modifier.weight(1f),
                                    value = bookletCode,
                                    onValueChange = { bookletCode = it.uppercase().take(2) },
                                    label = { Text("Kitapçık") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(28.dp)
                                )
                            }

                            if (status.isNotBlank()) {
                                Text(
                                    status,
                                    color = if (status.startsWith("Kaydedilemedi")) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        CorrectGreen
                                    }
                                )
                            }
                        }
                    }

                    if (visibleAnswers.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                shape = RoundedCornerShape(22.dp)
                            ) {
                                Text(
                                    modifier = Modifier.padding(18.dp),
                                    text = "Bu ders için soru kaydı bulunamadı."
                                )
                            }
                        }
                    } else {
                        items(visibleAnswers, key = { it.questionId }) { answer ->
                            QuestionAnswerRow(
                                answer = answer,
                                evaluation = evaluations[answer.questionId]
                            )
                        }
                    }
                    item { Spacer(Modifier.height(18.dp)) }
                }
            } else {
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

@Composable
private fun ScoreHeader(metrics: ExamPaperMetrics?, hasKey: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (metrics != null) "Toplam Net: ${formatNet(metrics.net)}" else "Toplam Net: —",
                    color = if (metrics != null) CorrectGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    if (hasKey) "Detaylı değerlendirme" else "Cevap anahtarı bekleniyor",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            metrics?.let {
                Text(
                    "D: ${it.correct}",
                    color = CorrectGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }
    }
}

@Composable
private fun QuestionAnswerRow(
    answer: RecordedAnswer,
    evaluation: QuestionEvaluation?
) {
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.size(width = 42.dp, height = 44.dp),
            text = "${questionDisplayNumber(answer.questionId)})",
            color = stateColor,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
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
        Text(
            text = questionStateLabel(answer, evaluation),
            style = MaterialTheme.typography.labelSmall,
            color = stateColor
        )
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
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = background,
        border = BorderStroke(if (expected) 3.dp else 1.dp, borderColor)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(choice, color = textColor, fontSize = 16.sp)
        }
    }
}

@Composable
private fun StudentResultSummaryBar(metrics: ExamPaperMetrics?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 5.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("D: ${metrics?.correct ?: 0}", color = CorrectGreen, fontSize = 17.sp)
            Text("Y: ${metrics?.wrong ?: 0}", color = WrongRed, fontSize = 17.sp)
            Text("B: ${metrics?.blank ?: 0}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
            Text(
                "N: ${metrics?.let { formatNet(it.net) } ?: "—"}",
                color = CorrectGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }
    }
}

@Composable
private fun ImageNotStoredState(sourceWidth: Int, sourceHeight: Int) {
    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Resim", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text("Kaynak ölçüsü: $sourceWidth × $sourceHeight")
                Text(
                    "Bu tarama sürümünde kabul edilen kamera görüntüsü henüz saklanmıyordu. " +
                        "Yeni taramalarda canonical görüntü bu sekmede gösterilecek.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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

private fun resolveLessonNames(
    context: android.content.Context,
    selection: ActiveTemplateSelection
): Map<String, String> {
    if (selection.source != ActiveTemplateSource.DESIGNER_DOCUMENT) return emptyMap()
    val documents = FileDesignerDocumentRepository(context).list() + DesignerStarterTemplates.all()
    val document = documents.firstOrNull {
        it.id == selection.templateId && it.version == selection.templateVersion
    } ?: return emptyMap()

    return document.visualElements
        .filterIsInstance<DesignerTextElement>()
        .mapNotNull { element ->
            val prefix = element.id.removePrefix("structured:lesson-title:")
            if (prefix != element.id && prefix.isNotBlank()) prefix to element.text else null
        }
        .toMap()
}

private fun humanizeLesson(prefix: String): String = prefix
    .replace('-', ' ')
    .replace('_', ' ')
    .split(' ')
    .filter { it.isNotBlank() }
    .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }

private fun formatNet(value: Double): String = String.format(Locale.US, "%.2f", value)
