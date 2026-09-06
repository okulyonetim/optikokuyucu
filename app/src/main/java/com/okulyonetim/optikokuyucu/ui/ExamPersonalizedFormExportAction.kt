package com.okulyonetim.optikokuyucu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.okulyonetim.optikokuyucu.exam.Exam
import com.okulyonetim.optikokuyucu.exam.ExamPersonalizedForms
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPdfExporter
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.designer.pdfProfile
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import java.util.concurrent.Executors

data class ExamPersonalizedFormExportAction(
    val enabled: Boolean,
    val launch: () -> Unit
)

@Composable
internal fun rememberExamPersonalizedFormExportAction(
    exam: Exam,
    onStatus: (String) -> Unit
): ExamPersonalizedFormExportAction {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(context) { FileDesignerDocumentRepository(appContext) }
    val worker = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    DisposableEffect(Unit) { onDispose { worker.shutdown() } }

    val document = remember(exam.templateSelection, repository) {
        resolvePersonalizedExamDocument(exam, repository.list())
    }
    val profile = document?.formSpec?.pdfProfile()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val currentDocument = document
        val currentProfile = profile
        if (uri == null || currentDocument == null || currentProfile == null) return@rememberLauncherForActivityResult
        onStatus("${exam.participants.size} öğrenci için kişisel optik formlar hazırlanıyor…")
        worker.execute {
            runCatching {
                context.contentResolver.openOutputStream(uri, "w").use { output ->
                    requireNotNull(output) { "PDF çıktı akışı açılamadı." }
                    DesignerPdfExporter.exportBatch(
                        document = currentDocument,
                        pages = ExamPersonalizedForms.pages(exam, currentDocument),
                        output = output,
                        profile = currentProfile
                    )
                }
            }.onSuccess {
                mainExecutor.execute {
                    onStatus("${exam.participants.size} öğrenci için kişisel optik form PDF'i kaydedildi.")
                }
            }.onFailure { error ->
                mainExecutor.execute {
                    onStatus("Öğrenci formları oluşturulamadı: ${error.message ?: error.javaClass.simpleName}")
                }
            }
        }
    }

    val enabled = exam.personalizedFormsEnabled && exam.participants.isNotEmpty() && document != null && profile != null
    return ExamPersonalizedFormExportAction(
        enabled = enabled,
        launch = {
            if (!enabled) {
                onStatus("Bu sınav öğrenciye özel form üretimi için hazır değil.")
            } else {
                val safe = exam.name.replace(Regex("[^A-Za-z0-9ÇĞİÖŞÜçğıöşü._-]+"), "-").trim('-')
                launcher.launch("${safe.ifBlank { "sinav" }}-ogrenci-formlari.pdf")
            }
        }
    )
}

private fun resolvePersonalizedExamDocument(exam: Exam, saved: List<DesignerDocument>): DesignerDocument? {
    if (exam.templateSelection.source != ActiveTemplateSource.DESIGNER_DOCUMENT) return null
    return saved.firstOrNull {
        it.id == exam.templateSelection.templateId && it.version == exam.templateSelection.templateVersion
    } ?: DesignerStarterTemplates.all().firstOrNull {
        it.id == exam.templateSelection.templateId && it.version == exam.templateSelection.templateVersion
    }
}
