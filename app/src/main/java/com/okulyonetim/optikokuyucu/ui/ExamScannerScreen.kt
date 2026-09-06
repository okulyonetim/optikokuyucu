package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.okulyonetim.optikokuyucu.omr.results.FileScanImageRepository
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.results.LiveScanRecorder
import com.okulyonetim.optikokuyucu.omr.results.StoredScanImage
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateResolver
import com.okulyonetim.optikokuyucu.student.FileStudentRosterRepository
import java.util.UUID

/** Production exam scanner: every temporally accepted read is persisted and linked to this exam. */
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
    val imageRepository = remember(context) { FileScanImageRepository(appContext) }
    val studentRepository = remember(context) { FileStudentRosterRepository(appContext) }
    val registrar = remember(context) { ExamPaperRegistrar(examRepository, studentRepository) }
    val template = resolved.template

    OmrCameraScreen(
        openCvReady = openCvReady,
        selfTest = selfTest,
        template = template,
        title = exam.name,
        subtitle = "${resolved.name} · ${template.bubbleRows.size} soru",
        onBack = onBack,
        onOpenGallery = onOpenGalleryBatch,
        onAcceptedRead = { result ->
            runCatching {
                val recordId = UUID.randomUUID().toString()
                val record = scanRecorder.record(
                    template = template,
                    result = result,
                    id = recordId
                )
                registrar.register(examId = examId, record = record)

                val canonical = result.canonicalLuma
                if (canonical != null && result.canonicalWidth > 0 && result.canonicalHeight > 0) {
                    // Image persistence is best-effort: an I/O error must never discard a valid OMR record.
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
            }
        }
    )
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
