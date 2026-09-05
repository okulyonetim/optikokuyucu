package com.okulyonetim.optikokuyucu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPdfExporter
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.PdfPageProfile
import com.okulyonetim.optikokuyucu.omr.designer.TemplateReadabilityAnalyzer

@Composable
fun DesignerPdfExportCard(document: DesignerDocument) {
    val context = LocalContext.current
    val readability = remember(document) {
        val compiled = DesignerTemplateCompiler.compile(document)
        TemplateReadabilityAnalyzer.analyze(document, compiled)
    }
    var pendingProfile by remember { mutableStateOf<PdfPageProfile?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val profile = pendingProfile
        if (uri == null || profile == null) {
            if (uri == null) status = "PDF oluşturma iptal edildi"
            pendingProfile = null
            return@rememberLauncherForActivityResult
        }

        status = runCatching {
            val stream = requireNotNull(context.contentResolver.openOutputStream(uri, "w")) {
                "PDF çıktı akışı açılamadı."
            }
            stream.use { output ->
                DesignerPdfExporter.export(
                    document = document,
                    output = output,
                    profile = profile
                )
            }
            "${profile.displayName} PDF oluşturuldu ✓"
        }.getOrElse { error ->
            "PDF hatası: ${error.message ?: error.javaClass.simpleName}"
        }
        pendingProfile = null
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("PDF Dışa Aktar", style = MaterialTheme.typography.titleSmall)
            Text(
                "A4 ve A5 aynı canonical OMR geometrisinden üretilir. Kağıt boyutu okuyucu şablonunu değiştirmez.",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = readability.canSave && pendingProfile == null,
                onClick = {
                    val profile = PdfPageProfile.A4
                    pendingProfile = profile
                    status = null
                    launcher.launch(suggestedPdfName(document, profile))
                }
            ) {
                Text("A4 PDF Oluştur")
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = readability.canSave && pendingProfile == null,
                onClick = {
                    val profile = PdfPageProfile.A5
                    pendingProfile = profile
                    status = null
                    launcher.launch(suggestedPdfName(document, profile))
                }
            ) {
                Text("A5 PDF Oluştur")
            }

            if (!readability.canSave) {
                Text(
                    "PDF üretimi okunabilirlik hataları giderilene kadar kapalı.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun suggestedPdfName(
    document: DesignerDocument,
    profile: PdfPageProfile
): String {
    val safeName = document.name
        .replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
        .trim('_')
        .ifBlank { "optik-form" }
    return "$safeName-${profile.displayName}.pdf"
}
