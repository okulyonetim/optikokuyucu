package com.okulyonetim.optikokuyucu.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.okulyonetim.optikokuyucu.omr.gallery.GalleryOmrReader
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeyCapture
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeySource
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeyXlsxExporter
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@Composable
fun AnswerKeyScreen(
    openCvReady: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) {
        FileAnswerKeyRepository(context.applicationContext)
    }
    val template = StandardOmrTemplate.SAMPLE_20_ABCD_STUDENT_6_BOOKLET_AB
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val worker = remember { Executors.newSingleThreadExecutor() }

    var keys by remember { mutableStateOf(repository.list()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(if (openCvReady) "Cevap anahtarı hazır" else "OpenCV başlatılamadı")
    }
    var pendingXlsx by remember { mutableStateOf<ByteArray?>(null) }

    DisposableEffect(Unit) {
        onDispose { worker.shutdown() }
    }

    fun importFromGallery(uri: Uri) {
        if (busy || !openCvReady) return
        busy = true
        status = "Cevap anahtarı okunuyor…"
        worker.execute {
            runCatching {
                val result = GalleryOmrReader.read(context, uri, template)
                try {
                    require(result.rectificationReady) {
                        "Form dört köşe işaretiyle güvenilir biçimde hizalanamadı."
                    }
                    val capture = AnswerKeyCapture.fromRead(
                        templateId = template.id,
                        templateVersion = template.version,
                        read = result.bubbleResult
                    )
                    require(capture.successful) {
                        val invalid = capture.invalidQuestionIds.joinToString(", ")
                        "Anahtar kabul edilmedi. Boş/çift/şüpheli sorular: $invalid"
                    }

                    val bookletGrid = template.markGrids.firstOrNull { it.id == BOOKLET_GRID_ID }
                    val booklet = result.markGridResult.grid(BOOKLET_GRID_ID)?.value
                    if (bookletGrid != null) {
                        require(!booklet.isNullOrBlank()) {
                            "Kitapçık alanı net okunamadı. Anahtar A/B kitapçığıyla kaydedilmedi."
                        }
                    }

                    val stored = StoredAnswerKey(
                        answerKey = requireNotNull(capture.answerKey),
                        variantGridId = booklet?.let { BOOKLET_GRID_ID },
                        variantValue = booklet,
                        source = AnswerKeySource.GALLERY
                    )
                    repository.save(stored)
                    stored
                } finally {
                    result.bitmap.recycle()
                }
            }.onSuccess { stored ->
                mainExecutor.execute {
                    keys = repository.list()
                    busy = false
                    status = buildString {
                        append("Cevap anahtarı kaydedildi")
                        stored.variantValue?.let { append(" · Kitapçık $it") }
                        append(" · ${stored.answerKey.answers.size} soru")
                    }
                }
            }.onFailure { error ->
                mainExecutor.execute {
                    busy = false
                    status = error.message ?: "Cevap anahtarı okunamadı."
                }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) importFromGallery(uri)
    }

    val xlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(XLSX_MIME_TYPE)
    ) { uri ->
        val bytes = pendingXlsx
        pendingXlsx = null
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult

        runCatching {
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "XLSX çıktı akışı açılamadı." }
                output.write(bytes)
                output.flush()
            }
        }.onSuccess {
            status = "Cevap anahtarı XLSX olarak kaydedildi"
        }.onFailure { error ->
            status = "XLSX kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    fun exportXlsx(key: StoredAnswerKey) {
        runCatching { AnswerKeyXlsxExporter.export(key) }
            .onSuccess { bytes ->
                pendingXlsx = bytes
                xlsxLauncher.launch(answerKeyFileName(key))
            }
            .onFailure { error ->
                pendingXlsx = null
                status = "XLSX oluşturulamadı: ${error.message ?: error.javaClass.simpleName}"
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Geri") }
            Text("Cevap Anahtarları", style = MaterialTheme.typography.titleLarge)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("Güvenli anahtar yakalama", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Galeriden gerçek optik formu seçin. Tüm sorular tek ve net işaretli olmalı. " +
                        "A/B kitapçık alanı bulunan formda kitapçık da net okunmadan anahtar kaydedilmez.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Kamerayla okunan bir formu cevap anahtarı yapmak için Tarama Oturumu ekranındaki ilgili kaydı kullanabilirsiniz.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Kayıtlı her cevap anahtarı Excel uyumlu XLSX dosyası olarak dışa aktarılabilir.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = openCvReady && !busy,
            onClick = { imagePicker.launch("image/*") }
        ) {
            Text(if (busy) "Okunuyor…" else "Galeriden cevap anahtarı oku")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            onClick = {
                keys = repository.list()
                status = "Cevap anahtarları yenilendi"
            }
        ) {
            Text("Anahtarları yenile")
        }

        Text(
            "Kayıtlı anahtarlar · ${keys.size}",
            style = MaterialTheme.typography.titleMedium
        )

        if (keys.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = "Henüz kayıtlı cevap anahtarı yok."
                )
            }
        } else {
            keys.forEach { key ->
                AnswerKeyCard(
                    key = key,
                    onExportXlsx = { exportXlsx(key) },
                    onDelete = {
                        repository.delete(
                            templateId = key.templateId,
                            templateVersion = key.templateVersion,
                            variantGridId = key.variantGridId,
                            variantValue = key.variantValue
                        )
                        keys = repository.list()
                        status = "Cevap anahtarı silindi"
                    }
                )
            }
        }
    }
}

@Composable
private fun AnswerKeyCard(
    key: StoredAnswerKey,
    onExportXlsx: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    key.variantValue?.let { "Kitapçık $it" } ?: "Genel anahtar",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
                        .format(Date(key.createdAtEpochMs)),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(
                "${key.templateId} · v${key.templateVersion} · ${key.answerKey.answers.size} soru",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Kaynak: ${if (key.source == AnswerKeySource.GALLERY) "Galeri" else "Kamera kaydı"}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                key.answerKey.answers.entries
                    .chunked(10)
                    .joinToString("\n") { chunk ->
                        chunk.joinToString("  ") { (question, answer) -> "$question:$answer" }
                    },
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onExportXlsx
            ) {
                Text("XLSX olarak dışa aktar")
            }
            TextButton(onClick = onDelete) {
                Text("Bu anahtarı sil")
            }
        }
    }
}

private fun answerKeyFileName(key: StoredAnswerKey): String {
    val variant = key.variantValue?.let { "-$it" } ?: "-genel"
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(key.createdAtEpochMs))
    return "cevap-anahtari$variant-$timestamp.xlsx"
}

private const val BOOKLET_GRID_ID = "booklet"
private const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
