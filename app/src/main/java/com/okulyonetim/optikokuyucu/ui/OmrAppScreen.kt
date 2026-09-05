package com.okulyonetim.optikokuyucu.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionRead
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionState
import com.okulyonetim.optikokuyucu.omr.diagnostics.GalleryTestFormGenerator
import com.okulyonetim.optikokuyucu.omr.diagnostics.OmrSelfTestResult
import com.okulyonetim.optikokuyucu.omr.gallery.GalleryOmrReader
import com.okulyonetim.optikokuyucu.omr.gallery.GalleryOmrResult
import java.util.Locale
import java.util.concurrent.Executors

@Composable
fun OmrAppScreen(
    openCvReady: Boolean,
    selfTest: OmrSelfTestResult
) {
    var liveCamera by remember { mutableStateOf(false) }

    MaterialTheme {
        if (liveCamera) {
            Box(modifier = Modifier.fillMaxSize()) {
                OmrCameraScreen(
                    openCvReady = openCvReady,
                    selfTest = selfTest
                )
                TextButton(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 42.dp, end = 16.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                            RoundedCornerShape(12.dp)
                        ),
                    onClick = { liveCamera = false }
                ) {
                    Text("Galeri testi")
                }
            }
        } else {
            GalleryTestScreen(
                openCvReady = openCvReady,
                selfTest = selfTest,
                onOpenCamera = { liveCamera = true }
            )
        }
    }
}

@Composable
private fun GalleryTestScreen(
    openCvReady: Boolean,
    selfTest: OmrSelfTestResult,
    onOpenCamera: () -> Unit
) {
    val context = LocalContext.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val worker = remember { Executors.newSingleThreadExecutor() }

    var status by remember {
        mutableStateOf(
            if (openCvReady) "Galeri testi hazır" else "OpenCV başlatılamadı"
        )
    }
    var result by remember { mutableStateOf<GalleryOmrResult?>(null) }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            worker.shutdown()
            result?.bitmap?.recycle()
        }
    }

    fun analyze(uri: Uri) {
        if (busy || !openCvReady) return
        busy = true
        status = "Görsel okunuyor…"
        worker.execute {
            runCatching { GalleryOmrReader.read(context, uri) }
                .onSuccess { newResult ->
                    mainExecutor.execute {
                        result?.bitmap?.recycle()
                        result = newResult
                        busy = false
                        status = if (newResult.registrationReady) {
                            "Form kaydedildi ve 20 soru analiz edildi"
                        } else {
                            "Dört köşe işareti birlikte bulunamadı"
                        }
                    }
                }
                .onFailure { error ->
                    mainExecutor.execute {
                        busy = false
                        status = "Okuma hatası: ${error.message ?: error.javaClass.simpleName}"
                    }
                }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) analyze(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Optik Okuyucu",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Önce Galeri ile OMR Doğruluk Testi",
            style = MaterialTheme.typography.titleMedium
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(if (openCvReady) "OpenCV 5 ✓" else "OpenCV !")
                Text(
                    if (selfTest.passed) {
                        String.format(
                            Locale.US,
                            "Marker testi ✓ %d/4 · %.1f ms",
                            selfTest.detectedExpectedCount,
                            selfTest.elapsedMs
                        )
                    } else {
                        "Marker testi ! ${selfTest.detectedExpectedCount}/4"
                    }
                )
                Text(status)
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && openCvReady,
            onClick = {
                busy = true
                status = "Örnek form oluşturuluyor…"
                worker.execute {
                    runCatching { GalleryTestFormGenerator.saveToGallery(context) }
                        .onSuccess { uri ->
                            mainExecutor.execute {
                                busy = false
                                status = "Örnek form Galeriye kaydedildi"
                                openImage(context, uri)
                            }
                        }
                        .onFailure { error ->
                            mainExecutor.execute {
                                busy = false
                                status = "Form oluşturulamadı: ${error.message ?: error.javaClass.simpleName}"
                            }
                        }
                }
            }
        ) {
            Text("1 · Örnek formu oluştur ve aç")
        }

        Text(
            text = "Galeride açılan formda istediğiniz baloncukların içini siyaha boyayın. " +
                "Köşe karelerini silmeyin; düzenlenmiş görseli kaydedin.",
            style = MaterialTheme.typography.bodyMedium
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && openCvReady,
            onClick = { imagePicker.launch("image/*") }
        ) {
            Text("2 · Galeriden işaretli formu oku")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            onClick = onOpenCamera
        ) {
            Text("Canlı kameraya geç")
        }

        result?.let { galleryResult ->
            GalleryResultCard(galleryResult)

            Image(
                bitmap = galleryResult.bitmap.asImageBitmap(),
                contentDescription = "Seçilen optik form",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                contentScale = ContentScale.Fit
            )

            if (galleryResult.bubbleResult.questions.isNotEmpty()) {
                Text(
                    text = "Okunan Cevaplar",
                    style = MaterialTheme.typography.titleMedium
                )
                galleryResult.bubbleResult.questions.forEach { question ->
                    QuestionResultRow(question)
                }
            }
        }
    }
}

@Composable
private fun GalleryResultCard(result: GalleryOmrResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text("${result.width} × ${result.height}")
            Text("Marker: ${result.markerCount}/4")
            Text(if (result.registrationReady) "Canonical kayıt ✓" else "Canonical kayıt bekleniyor")
            Text(
                String.format(
                    Locale.US,
                    "Toplam analiz: %.1f ms",
                    result.elapsedMs
                )
            )
            if (result.bubbleResult.questions.isNotEmpty()) {
                Text(
                    "Kesin işaret: ${result.bubbleResult.markedCount} · " +
                        "Şüpheli/çift: ${result.bubbleResult.suspiciousCount}"
                )
            }
        }
    }
}

@Composable
private fun QuestionResultRow(question: QuestionRead) {
    val resultText = when (question.state) {
        QuestionState.MARKED -> question.selectedChoice ?: "?"
        QuestionState.BLANK -> "Boş"
        QuestionState.DOUBLE_MARK -> "Çift işaret"
        QuestionState.SUSPICIOUS -> "${question.selectedChoice ?: "?"} ?"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("${question.questionId}. soru")
        Text(
            String.format(
                Locale.US,
                "%s · güven %.0f%%",
                resultText,
                question.confidence * 100.0
            )
        )
    }
}

private fun openImage(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "image/png")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}
