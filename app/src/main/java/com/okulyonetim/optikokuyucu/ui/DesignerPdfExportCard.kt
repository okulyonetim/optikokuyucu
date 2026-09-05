package com.okulyonetim.optikokuyucu.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerGalleryTestAsset
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPdfExporter
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.PdfPageProfile
import com.okulyonetim.optikokuyucu.omr.designer.TemplateReadabilityAnalyzer
import com.okulyonetim.optikokuyucu.omr.gallery.GalleryOmrReader
import com.okulyonetim.optikokuyucu.omr.gallery.GalleryOmrResult
import java.util.Locale
import java.util.concurrent.Executors

@Composable
fun DesignerPdfExportCard(document: DesignerDocument) {
    val context = LocalContext.current
    val compiled = remember(document) { DesignerTemplateCompiler.compile(document) }
    val readability = remember(document, compiled) {
        TemplateReadabilityAnalyzer.analyze(document, compiled)
    }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val worker = remember { Executors.newSingleThreadExecutor() }

    var pendingProfile by remember { mutableStateOf<PdfPageProfile?>(null) }
    var pdfStatus by remember { mutableStateOf<String?>(null) }
    var galleryStatus by remember { mutableStateOf<String?>(null) }
    var galleryBusy by remember { mutableStateOf(false) }
    var gallerySummary by remember { mutableStateOf<DesignerGallerySummary?>(null) }

    DisposableEffect(Unit) {
        onDispose { worker.shutdown() }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val profile = pendingProfile
        if (uri == null || profile == null) {
            if (uri == null) pdfStatus = "PDF oluşturma iptal edildi"
            pendingProfile = null
            return@rememberLauncherForActivityResult
        }

        pdfStatus = runCatching {
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

    fun analyzeGallery(uri: Uri) {
        if (galleryBusy || !readability.canSave) return
        galleryBusy = true
        galleryStatus = "Özel şablon galeriden okunuyor…"
        gallerySummary = null
        val templateAtStart = compiled
        worker.execute {
            runCatching {
                val result = GalleryOmrReader.read(context, uri, templateAtStart)
                try {
                    DesignerGallerySummary.from(result)
                } finally {
                    result.bitmap.recycle()
                }
            }.onSuccess { summary ->
                mainExecutor.execute {
                    gallerySummary = summary
                    galleryBusy = false
                    galleryStatus = when {
                        summary.rectificationReady ->
                            "Canonical okuma tamamlandı · ${summary.questionCount} soru"
                        summary.registrationReady ->
                            "Marker bulundu ancak canonical görüntü üretilemedi"
                        else -> "Dört köşe marker birlikte bulunamadı"
                    }
                }
            }.onFailure { error ->
                mainExecutor.execute {
                    galleryBusy = false
                    galleryStatus = "Galeri okuma hatası: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) analyzeGallery(uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        pdfStatus = null
                        pdfLauncher.launch(suggestedPdfName(document, profile))
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
                        pdfStatus = null
                        pdfLauncher.launch(suggestedPdfName(document, profile))
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

                pdfStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Bu Şablonu Galeride Test Et", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Güncel tasarım markerlar, OMR alanları, etiketler ve görsel öğelerle PNG olarak oluşturulur. " +
                        "Baloncukları telefonun fotoğraf düzenleyicisinde doldurup aynı şablonla yeniden okuyabilirsiniz.",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = readability.canSave && !galleryBusy,
                    onClick = {
                        galleryBusy = true
                        galleryStatus = "Özel şablon PNG oluşturuluyor…"
                        gallerySummary = null
                        val documentAtStart = document
                        worker.execute {
                            runCatching { DesignerGalleryTestAsset.saveToGallery(context, documentAtStart) }
                                .onSuccess { uri ->
                                    mainExecutor.execute {
                                        galleryBusy = false
                                        galleryStatus = "Test PNG Galeriye kaydedildi ✓"
                                        runCatching {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "image/png")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        }
                                    }
                                }
                                .onFailure { error ->
                                    mainExecutor.execute {
                                        galleryBusy = false
                                        galleryStatus = "PNG oluşturma hatası: ${error.message ?: error.javaClass.simpleName}"
                                    }
                                }
                        }
                    }
                ) {
                    Text("1 · Test PNG oluştur ve Galeride aç")
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = readability.canSave && !galleryBusy,
                    onClick = { imagePicker.launch("image/*") }
                ) {
                    Text("2 · Galeriden bu şablonla oku")
                }

                galleryStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                gallerySummary?.let { summary ->
                    Text(
                        String.format(
                            Locale.US,
                            "Marker %d/4 · toplam %.1f ms · canonical %s",
                            summary.markerCount,
                            summary.elapsedMs,
                            if (summary.rectificationReady) "✓" else "!"
                        ),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        "İşaretli ${summary.markedCount} · Boş ${summary.blankCount} · " +
                            "Çift ${summary.doubleMarkCount} · Şüpheli ${summary.suspiciousCount}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (summary.gridSummary.isNotBlank()) {
                        Text(summary.gridSummary, style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (!readability.canSave) {
                    Text(
                        "Galeri testi okunabilirlik hataları giderilene kadar kapalı.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private data class DesignerGallerySummary(
    val markerCount: Int,
    val registrationReady: Boolean,
    val rectificationReady: Boolean,
    val questionCount: Int,
    val markedCount: Int,
    val blankCount: Int,
    val doubleMarkCount: Int,
    val suspiciousCount: Int,
    val gridSummary: String,
    val elapsedMs: Double
) {
    companion object {
        fun from(result: GalleryOmrResult): DesignerGallerySummary {
            val bubbles = result.bubbleResult
            val grids = result.markGridResult.grids.joinToString(separator = " · ") { grid ->
                val value = grid.value ?: when {
                    grid.blankCount == grid.columns.size -> "boş"
                    grid.suspiciousCount > 0 -> "şüpheli"
                    else -> "tamamlanmadı"
                }
                "${grid.gridId}: $value"
            }
            return DesignerGallerySummary(
                markerCount = result.markerCount,
                registrationReady = result.registrationReady,
                rectificationReady = result.rectificationReady,
                questionCount = bubbles.questions.size,
                markedCount = bubbles.markedCount,
                blankCount = bubbles.blankCount,
                doubleMarkCount = bubbles.doubleMarkCount,
                suspiciousCount = bubbles.suspiciousCount,
                gridSummary = grids,
                elapsedMs = result.elapsedMs
            )
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
