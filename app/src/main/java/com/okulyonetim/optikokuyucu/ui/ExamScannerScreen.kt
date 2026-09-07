package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.okulyonetim.optikokuyucu.camera.LiveOmrReadResult
import com.okulyonetim.optikokuyucu.exam.ExamPaperLink
import com.okulyonetim.optikokuyucu.exam.ExamPaperMetrics
import com.okulyonetim.optikokuyucu.exam.ExamPaperRegistrar
import com.okulyonetim.optikokuyucu.exam.ExamPaperResolution
import com.okulyonetim.optikokuyucu.exam.ExamScoringPolicyResolver
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.exam.paperForStudentNumber
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.diagnostics.OmrSelfTestResult
import com.okulyonetim.optikokuyucu.omr.results.FileScanImageRepository
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.results.LiveScanRecorder
import com.okulyonetim.optikokuyucu.omr.results.StoredScanImage
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import com.okulyonetim.optikokuyucu.omr.scoring.OmrScorer
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateResolver
import com.okulyonetim.optikokuyucu.omr.template.OmrRecognitionBindingsResolver
import com.okulyonetim.optikokuyucu.settings.CameraScanSettings
import com.okulyonetim.optikokuyucu.settings.CameraScanSettingsRepository
import com.okulyonetim.optikokuyucu.student.FileStudentRosterRepository
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.delay

/** Production exam scanner with duplicate-student confirmation and a short result overlay. */
@Composable
fun ExamScannerScreen(
    examId: String,
    openCvReady: Boolean,
    selfTest: OmrSelfTestResult,
    onBack: () -> Unit,
    onOpenGalleryBatch: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val worker = remember { Executors.newSingleThreadExecutor() }
    val examRepository = remember(context) { FileExamRepository(appContext) }
    val exam = remember(examId) { examRepository.load(examId) }
    val resolved = remember(examId, exam) {
        exam?.let {
            ActiveOmrTemplateResolver.resolve(
                selection = it.templateSelection,
                savedDocuments = FileDesignerDocumentRepository(appContext).list()
            )
        }
    }

    if (exam == null) {
        ScannerError("Sınav bulunamadı.", onBack)
        return
    }
    if (resolved == null) {
        ScannerError("Bu sınavın optik formu artık bulunamıyor. Formu yeniden seçin.", onBack)
        return
    }

    val scanRepository = remember(context) { FileScanRecordRepository(appContext) }
    val scanRecorder = remember(context) { LiveScanRecorder(scanRepository) }
    val imageRepository = remember(context) { FileScanImageRepository(appContext) }
    val studentRepository = remember(context) { FileStudentRosterRepository(appContext) }
    val keyRepository = remember(context) { FileAnswerKeyRepository(appContext) }
    val registrar = remember(context) { ExamPaperRegistrar(examRepository, studentRepository) }
    val cameraSettingsRepository = remember(context) { CameraScanSettingsRepository(appContext) }
    val template = resolved.template
    val recognitionBindings = remember(template) { OmrRecognitionBindingsResolver.fromTemplate(template) }

    var cameraSettings by remember { mutableStateOf(cameraSettingsRepository.load()) }
    var pendingDuplicate by remember { mutableStateOf<PendingDuplicateScan?>(null) }
    var resultSummary by remember { mutableStateOf<ExamCameraSummary?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var updatingDuplicate by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { worker.shutdownNow() }
    }

    LaunchedEffect(resultSummary?.id) {
        val id = resultSummary?.id ?: return@LaunchedEffect
        delay(3_000)
        if (resultSummary?.id == id) resultSummary = null
    }

    fun persistRead(
        result: LiveOmrReadResult,
        replaceScanRecordId: String? = null
    ): ExamCameraSummary {
        val recordId = UUID.randomUUID().toString()
        val record = scanRecorder.record(
            template = template,
            result = result,
            id = recordId
        )

        val updatedExam = try {
            registrar.register(
                examId = examId,
                record = record,
                replaceScanRecordId = replaceScanRecordId
            )
        } catch (error: Throwable) {
            scanRepository.delete(record.id)
            throw error
        }

        val canonical = result.canonicalLuma
        if (canonical != null && result.canonicalWidth > 0 && result.canonicalHeight > 0) {
            runCatching {
                imageRepository.save(
                    StoredScanImage(
                        scanRecordId = record.id,
                        width = result.canonicalWidth,
                        height = result.canonicalHeight,
                        luma = canonical
                    )
                )
            }
        }

        if (replaceScanRecordId != null) {
            scanRepository.delete(replaceScanRecordId)
            imageRepository.delete(replaceScanRecordId)
        }

        val link = requireNotNull(updatedExam.paperForScan(record.id)) {
            "Yeni öğrenci kağıdı sınava bağlanamadı."
        }
        val storedKey = ExamPaperResolution.answerKey(
            link = link,
            record = record,
            keys = keyRepository.list()
        )
        val metrics = storedKey?.let { key ->
            ExamPaperMetrics.from(
                OmrScorer.score(
                    record = record,
                    answerKey = key.answerKey,
                    policy = ExamScoringPolicyResolver.resolve(updatedExam.wrongAnswerPolicy)
                )
            )
        }

        return ExamCameraSummary(
            id = record.id,
            studentName = link.studentName.ifBlank { "İsimsiz Öğrenci" },
            studentNumber = link.studentNumber,
            className = link.className,
            correct = metrics?.correct,
            wrong = metrics?.wrong,
            blank = metrics?.blank ?: result.bubbleResult.blankCount,
            net = metrics?.net,
            hasAnswerKey = metrics != null
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        OmrCameraScreen(
            openCvReady = openCvReady,
            selfTest = selfTest,
            template = template,
            title = exam.name,
            subtitle = "${resolved.name} · ${template.bubbleRows.size} soru",
            onBack = onBack,
            onOpenGallery = onOpenGalleryBatch,
            showRawReadCard = false,
            onCameraSettingsChanged = { cameraSettings = it },
            onAcceptedRead = { result ->
                val detectedNumber = recognitionBindings.studentNumber(result.markGridResult).orEmpty()
                val latestExam = examRepository.load(examId)
                val existing = latestExam?.paperForStudentNumber(detectedNumber)

                if (existing != null) {
                    mainExecutor.execute {
                        pendingDuplicate = PendingDuplicateScan(
                            result = result,
                            existing = existing,
                            detectedStudentNumber = detectedNumber
                        )
                    }
                } else {
                    val outcome = runCatching { persistRead(result) }
                    mainExecutor.execute {
                        outcome.onSuccess { resultSummary = it }
                            .onFailure { errorMessage = it.message ?: "Kağıt kaydedilemedi." }
                    }
                }
            }
        )

        if (cameraSettings.showStudentSummary) {
            resultSummary?.let { summary ->
                ExamCameraSummaryPopup(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 126.dp, start = 18.dp, end = 18.dp),
                    summary = summary
                )
            }
        }
    }

    pendingDuplicate?.let { pending ->
        AlertDialog(
            onDismissRequest = {
                if (!updatingDuplicate) pendingDuplicate = null
            },
            title = { Text("Öğrenci zaten kayıtlı") },
            text = {
                val identity = pending.existing.studentName.ifBlank {
                    "Öğrenci no ${pending.detectedStudentNumber}"
                }
                val classText = pending.existing.className.takeIf { it.isNotBlank() }
                    ?.let { " · $it" }
                    .orEmpty()
                Text(
                    "$identity$classText için bu sınavda zaten bir kağıt kayıtlı. " +
                        "Yeni okuma eski kağıdın yerine güncellensin mi?"
                )
            },
            dismissButton = {
                TextButton(
                    enabled = !updatingDuplicate,
                    onClick = { pendingDuplicate = null }
                ) { Text("Vazgeç") }
            },
            confirmButton = {
                TextButton(
                    enabled = !updatingDuplicate,
                    onClick = {
                        updatingDuplicate = true
                        worker.execute {
                            val outcome = runCatching {
                                persistRead(
                                    result = pending.result,
                                    replaceScanRecordId = pending.existing.scanRecordId
                                )
                            }
                            mainExecutor.execute {
                                updatingDuplicate = false
                                pendingDuplicate = null
                                outcome.onSuccess { resultSummary = it }
                                    .onFailure { errorMessage = it.message ?: "Kağıt güncellenemedi." }
                            }
                        }
                    }
                ) { Text(if (updatingDuplicate) "Güncelleniyor…" else "Güncelle") }
            }
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Kayıt yapılamadı") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("Tamam") }
            }
        )
    }
}

@Composable
private fun ExamCameraSummaryPopup(
    modifier: Modifier,
    summary: ExamCameraSummary
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.72f),
        contentColor = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Text("✓ ${summary.studentName}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            val identityParts = buildList {
                if (summary.className.isNotBlank()) add("Sınıf ${summary.className}")
                if (summary.studentNumber.isNotBlank()) add("No ${summary.studentNumber}")
            }
            if (identityParts.isNotEmpty()) {
                Text(
                    identityParts.joinToString(" · "),
                    color = Color.White.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (summary.hasAnswerKey) {
                Text(
                    "D ${summary.correct}   Y ${summary.wrong}   B ${summary.blank}   N ${formatNet(summary.net)}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            } else {
                Text(
                    "D —   Y —   B ${summary.blank}   N —",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    "Cevap anahtarı bulunamadı",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private data class PendingDuplicateScan(
    val result: LiveOmrReadResult,
    val existing: ExamPaperLink,
    val detectedStudentNumber: String
)

private data class ExamCameraSummary(
    val id: String,
    val studentName: String,
    val studentNumber: String,
    val className: String,
    val correct: Int?,
    val wrong: Int?,
    val blank: Int,
    val net: Double?,
    val hasAnswerKey: Boolean
)

private fun formatNet(value: Double?): String =
    value?.let { String.format(Locale.US, "%.2f", it) } ?: "—"

@Composable
private fun ScannerError(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onBack) { Text("Sınava dön") }
    }
}
