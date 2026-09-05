package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.exam.ExamPaperRegistrar
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.diagnostics.OmrSelfTestResult
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.results.LiveScanRecorder
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateResolver

/** Production exam scanner: every temporally accepted read is persisted and linked to this exam. */
@Composable
fun ExamScannerScreen(
    examId: String,
    openCvReady: Boolean,
    selfTest: OmrSelfTestResult,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
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

    val scanRecorder = remember(context) {
        LiveScanRecorder(FileScanRecordRepository(appContext))
    }
    val registrar = remember(context) { ExamPaperRegistrar(examRepository) }
    val template = resolved.template

    Box(modifier = Modifier.fillMaxSize()) {
        OmrCameraScreen(
            openCvReady = openCvReady,
            selfTest = selfTest,
            template = template,
            onAcceptedRead = { result ->
                runCatching {
                    val record = scanRecorder.record(template = template, result = result)
                    registrar.register(examId = examId, record = record)
                }
            }
        )

        TextButton(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    RoundedCornerShape(14.dp)
                ),
            onClick = onBack
        ) {
            Text("‹ ${exam.name}")
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("${resolved.name} · ${template.bubbleRows.size} soru")
            Text(
                "Kabul edilen kağıtlar otomatik olarak bu sınava eklenir.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

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
