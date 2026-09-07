package com.okulyonetim.optikokuyucu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.DesignerA4MultiUpLayout
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPdfExporter
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.PdfPageProfile
import com.okulyonetim.optikokuyucu.omr.designer.TemplateReadabilityAnalyzer
import com.okulyonetim.optikokuyucu.omr.designer.pdfProfile

@Suppress("UNUSED_PARAMETER")
@Composable
fun DesignerPdfExportCard(
    document: DesignerDocument,
    openCvReady: Boolean
) {
    val context = LocalContext.current
    val feedback = LocalAppFeedback.current
    val compileResult = remember(document) { runCatching { DesignerTemplateCompiler.compile(document) } }
    val compiled = compileResult.getOrNull()
    val compileIssue = compileResult.exceptionOrNull()
    val selectedProfile = remember(document.formSpec.paperSize, document.formSpec.orientation) {
        document.formSpec.pdfProfile()
    }
    val multiUpPlan = remember(selectedProfile) {
        selectedProfile?.let(DesignerA4MultiUpLayout::planFor)
    }

    if (compiled == null) {
        LaunchedEffect(document.id, document.version, compileIssue?.message) {
            feedback.warning(
                "Formdaki bir öğe sayfa sınırını aşıyor. PDF dışa aktarma geçici olarak kapatıldı; öğeyi küçültün veya sayfanın içine taşıyın."
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Form geometrisi düzeltilmeli", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Bir veya daha fazla OMR işareti kullanılabilir sayfa alanının dışında. Taşan öğeyi küçültün veya sayfanın içine taşıyın. Düzeltilene kadar PDF dışa aktarma kapalıdır.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                compileIssue?.message?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(message, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        return
    }

    val readability = remember(document, compiled) {
        TemplateReadabilityAnalyzer.analyze(document, compiled)
    }
    var pendingProfile by remember { mutableStateOf<PdfPageProfile?>(null) }
    var pendingA4Multi by remember { mutableStateOf(false) }
    var pdfStatus by remember { mutableStateOf<String?>(null) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val profile = pendingProfile
        if (uri == null || profile == null) {
            if (uri == null) pdfStatus = "PDF oluşturma iptal edildi"
            pendingProfile = null
            pendingA4Multi = false
            return@rememberLauncherForActivityResult
        }

        val isA4Multi = pendingA4Multi
        pdfStatus = runCatching {
            val stream = requireNotNull(context.contentResolver.openOutputStream(uri, "w")) {
                "PDF çıktı akışı açılamadı."
            }
            stream.use { output ->
                if (isA4Multi) {
                    DesignerPdfExporter.exportA4MultiUpCopies(
                        document = document,
                        output = output,
                        sourceProfile = profile
                    )
                } else {
                    DesignerPdfExporter.export(
                        document = document,
                        output = output,
                        profile = profile
                    )
                }
            }
            if (isA4Multi) {
                val count = DesignerA4MultiUpLayout.planFor(profile)?.itemsPerSheet ?: 1
                "A4 çoklu PDF oluşturuldu · $count form/sayfa ✓"
            } else {
                "${profile.displayName} PDF oluşturuldu ✓"
            }
        }.getOrElse { error ->
            "PDF hatası: ${error.message ?: error.javaClass.simpleName}"
        }
        pendingProfile = null
        pendingA4Multi = false
    }

    val canExport = readability.canSave && pendingProfile == null && selectedProfile != null
    val borderColor = if (canExport) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.78f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("PDF Dışa Aktar", style = MaterialTheme.typography.titleSmall)
            Text(
                if (selectedProfile != null) {
                    "Seçili kağıt ${selectedProfile.displayName}. Normal PDF gerçek seçili fiziksel sayfa boyutunda üretilir; canonical OMR geometrisi değişmez."
                } else {
                    "Bu kağıt türü için fiziksel PDF profili tanımlı değil."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = canExport,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, borderColor),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
                ),
                onClick = {
                    selectedProfile?.let { profile ->
                        pendingProfile = profile
                        pendingA4Multi = false
                        pdfStatus = null
                        pdfLauncher.launch(suggestedPdfName(document, profile, a4Multi = false))
                    }
                }
            ) {
                Text(selectedProfile?.let { "${it.displayName} PDF Oluştur" } ?: "PDF Profili Yok")
            }

            if (multiUpPlan != null) {
                Text(
                    "A4 çoklu çıktı: ${multiUpPlan.itemsPerSheet} adet ${selectedProfile?.displayName} form tek A4'e yerleştirilir ve kesim çizgileri eklenir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canExport,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, borderColor),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
                    ),
                    onClick = {
                        selectedProfile?.let { profile ->
                            pendingProfile = profile
                            pendingA4Multi = true
                            pdfStatus = null
                            pdfLauncher.launch(suggestedPdfName(document, profile, a4Multi = true))
                        }
                    }
                ) {
                    Text("A4 Çoklu PDF Oluştur · ${multiUpPlan.itemsPerSheet} Form")
                }
            }

            if (!readability.canSave) {
                Text(
                    "PDF üretimi okunabilirlik hataları giderilene kadar kapalı.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            pdfStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun suggestedPdfName(
    document: DesignerDocument,
    profile: PdfPageProfile,
    a4Multi: Boolean
): String {
    val safeName = document.name
        .replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
        .trim('_')
        .ifBlank { "optik-form" }
    return if (a4Multi) {
        "$safeName-${profile.displayName}-A4-coklu.pdf"
    } else {
        "$safeName-${profile.displayName}.pdf"
    }
}
