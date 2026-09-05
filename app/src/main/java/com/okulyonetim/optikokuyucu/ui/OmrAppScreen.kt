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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.rememberUpdatedState
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
import com.okulyonetim.optikokuyucu.omr.diagnostics.Omr100Benchmark
import com.okulyonetim.optikokuyucu.omr.diagnostics.Omr100BenchmarkResult
import com.okulyonetim.optikokuyucu.omr.diagnostics.OmrSelfTestResult
import com.okulyonetim.optikokuyucu.omr.diagnostics.OmrStressBenchmark
import com.okulyonetim.optikokuyucu.omr.diagnostics.OmrStressBenchmarkResult
import com.okulyonetim.optikokuyucu.omr.gallery.GalleryOmrReader
import com.okulyonetim.optikokuyucu.omr.gallery.GalleryOmrResult
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.results.GalleryScanRecorder
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateDefaults
import com.okulyonetim.optikokuyucu.omr.template.resolveActiveOmrTemplate
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
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 16.dp)
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
    val activeTemplate = remember(context) { resolveActiveOmrTemplate(context) }
    val template = activeTemplate.template
    val defaultDiagnosticTemplate = activeTemplate.selection == ActiveOmrTemplateDefaults.selection
    val galleryRecorder = remember(context) {
        GalleryScanRecorder(FileScanRecordRepository(context.applicationContext))
    }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val worker = remember { Executors.newSingleThreadExecutor() }

    var status by remember {
        mutableStateOf(if (openCvReady) "Galeri testi hazır" else "OpenCV başlatılamadı")
    }
    var result by remember { mutableStateOf<GalleryOmrResult?>(null) }
    var savedGalleryRecordId by remember { mutableStateOf<String?>(null) }
    var benchmark by remember { mutableStateOf<OmrStressBenchmarkResult?>(null) }
    var benchmark100 by remember { mutableStateOf<Omr100BenchmarkResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    val latestResult by rememberUpdatedState(result)

    DisposableEffect(Unit) {
        onDispose {
            worker.shutdown()
            latestResult?.bitmap?.recycle()
        }
    }

    fun analyze(uri: Uri) {
        if (busy || !openCvReady) return
        busy = true
        status = "Görsel okunuyor…"
        worker.execute {
            runCatching { GalleryOmrReader.read(context, uri, template) }
                .onSuccess { newResult ->
                    mainExecutor.execute {
                        result?.bitmap?.recycle()
                        result = newResult
                        savedGalleryRecordId = null
                        busy = false
                        status = when {
                            newResult.rectificationReady ->
                                "Canonical düzeltme tamamlandı · ${newResult.bubbleResult.questions.size} soru analiz edildi"
                            newResult.registrationReady -> "Marker bulundu ancak canonical görüntü üretilemedi"
                            else -> "Dört köşe işareti birlikte bulunamadı"
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
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Optik Okuyucu", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Galeri ile OMR Okuma",
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
                Text("Aktif form: ${activeTemplate.name}")
                Text(
                    "${template.bubbleRows.size} soru · ${template.markGrids.size} işaret alanı · v${template.version}",
                    style = MaterialTheme.typography.bodySmall
                )
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
            enabled = !busy && openCvReady && defaultDiagnosticTemplate,
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
            Text(
                if (defaultDiagnosticTemplate) {
                    "1 · Örnek formu oluştur ve aç"
                } else {
                    "1 · Örnek test formu yalnız varsayılan şablonda"
                }
            )
        }

        Text(
            text = if (defaultDiagnosticTemplate) {
                "Galeride açılan örnek formda istediğiniz baloncukların içini siyaha boyayın. " +
                    "Köşe karelerini silmeyin; düzenlenmiş görseli kaydedin."
            } else {
                "Seçili özel formu galeri veya kameradan okuyabilirsiniz. Örnek test görseli sabit varsayılan forma ait olduğu için bu şablonda devre dışıdır."
            },
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
            enabled = !busy && openCvReady,
            onClick = {
                busy = true
                benchmark = null
                status = "16 senaryolu gelişmiş stres testi çalışıyor…"
                worker.execute {
                    runCatching { OmrStressBenchmark.run() }
                        .onSuccess { value ->
                            mainExecutor.execute {
                                benchmark = value
                                busy = false
                                status = if (value.allPassed) {
                                    "Gelişmiş stres testi ✓ ${value.passedCount}/${value.scenarios.size} senaryo"
                                } else {
                                    "Gelişmiş stres testi: ${value.passedCount}/${value.scenarios.size} senaryo geçti"
                                }
                            }
                        }
                        .onFailure { error ->
                            mainExecutor.execute {
                                busy = false
                                status = "Stres testi hatası: ${error.message ?: error.javaClass.simpleName}"
                            }
                        }
                }
            }
        ) {
            Text("3 · Gelişmiş stres testini çalıştır")
        }

        benchmark?.let { BenchmarkResultCard(it) }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && openCvReady,
            onClick = {
                busy = true
                benchmark100 = null
                status = "100 soruluk hız/doğruluk testi çalışıyor…"
                worker.execute {
                    runCatching { Omr100Benchmark.run() }
                        .onSuccess { value ->
                            mainExecutor.execute {
                                benchmark100 = value
                                busy = false
                                status = if (value.allPassed) {
                                    "100 soru testi ✓ ${value.passedRuns}/${value.totalRuns} tur"
                                } else {
                                    "100 soru testi: ${value.passedRuns}/${value.totalRuns} tur geçti"
                                }
                            }
                        }
                        .onFailure { error ->
                            mainExecutor.execute {
                                busy = false
                                status = "100 soru testi hatası: ${error.message ?: error.javaClass.simpleName}"
                            }
                        }
                }
            }
        ) {
            Text("4 · 100 soruluk hız/doğruluk testini çalıştır")
        }

        benchmark100?.let { Benchmark100ResultCard(it) }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            onClick = onOpenCamera
        ) {
            Text("Canlı kameraya geç")
        }

        result?.let { galleryResult ->
            GalleryResultCard(galleryResult)

            val studentNumber = galleryResult.markGridResult.grid("studentNumber")?.value
            val booklet = galleryResult.markGridResult.grid("booklet")?.value
            if (template.markGrids.any { it.id == "studentNumber" } || template.markGrids.any { it.id == "booklet" }) {
                Text(
                    "Öğrenci No: ${studentNumber ?: "—"} · Kitapçık: ${booklet ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = galleryResult.rectificationReady &&
                    galleryResult.bubbleResult.questions.isNotEmpty() &&
                    savedGalleryRecordId == null,
                onClick = {
                    runCatching { galleryRecorder.record(template, galleryResult) }
                        .onSuccess { record ->
                            savedGalleryRecordId = record.id
                            val savedStudent = record.grid("studentNumber")?.value
                            val savedBooklet = record.grid("booklet")?.value
                            status = buildString {
                                append("Galeri okuması tarama kaydına eklendi")
                                savedStudent?.let { append(" · Öğrenci $it") }
                                savedBooklet?.let { append(" · Kitapçık $it") }
                            }
                        }
                        .onFailure { error ->
                            status = "Tarama kaydı oluşturulamadı: ${error.message ?: error.javaClass.simpleName}"
                        }
                }
            ) {
                Text(
                    if (savedGalleryRecordId == null) {
                        "Tarama kaydı olarak kaydet"
                    } else {
                        "Tarama kaydı kaydedildi ✓"
                    }
                )
            }

            Text(
                "Galeri analizi otomatik kaydedilmez. Yalnız bu düğmeye bastığınız okumalar Sonuçlar ekranına eklenir.",
                style = MaterialTheme.typography.bodySmall
            )

            Image(
                bitmap = galleryResult.bitmap.asImageBitmap(),
                contentDescription = "Seçilen optik form",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                contentScale = ContentScale.Fit
            )

            if (galleryResult.bubbleResult.questions.isNotEmpty()) {
                Text("Okunan Cevaplar", style = MaterialTheme.typography.titleMedium)
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
            Text("Kaynak: ${result.width} × ${result.height}")
            Text("Marker: ${result.markerCount}/4")
            Text(if (result.registrationReady) "Canonical kayıt ✓" else "Canonical kayıt bekleniyor")
            Text(
                if (result.rectificationReady) {
                    "Düzeltilmiş form: ${result.canonicalWidth} × ${result.canonicalHeight} ✓"
                } else {
                    "Düzeltilmiş form üretilemedi"
                }
            )
            Text(String.format(Locale.US, "Toplam analiz: %.1f ms", result.elapsedMs))
            Text(
                String.format(
                    Locale.US,
                    "Marker %.1f · düzeltme %.1f · baloncuk %.1f ms",
                    result.markerMs,
                    result.rectificationMs,
                    result.bubbleMs
                ),
                style = MaterialTheme.typography.bodySmall
            )
            if (result.bubbleResult.questions.isNotEmpty()) {
                Text(
                    "İşaretli: ${result.bubbleResult.markedCount} · " +
                        "Boş: ${result.bubbleResult.blankCount} · " +
                        "Çift: ${result.bubbleResult.doubleMarkCount} · " +
                        "Şüpheli: ${result.bubbleResult.suspiciousCount}"
                )
            }
        }
    }
}

@Composable
private fun BenchmarkResultCard(result: OmrStressBenchmarkResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (result.allPassed) {
                    "Gelişmiş OMR stres testi ✓"
                } else {
                    "Gelişmiş OMR stres testi"
                },
                style = MaterialTheme.typography.titleSmall
            )
            result.scenarios.forEach { scenario ->
                val suffix = if (scenario.mismatchQuestionIds.isEmpty()) {
                    ""
                } else {
                    " · Hatalı: ${scenario.mismatchQuestionIds.joinToString(",")}"
                }
                Text(
                    String.format(
                        Locale.US,
                        "%s %s · %d/%d · M%d/4 · %.0f ms%s",
                        if (scenario.passed) "✓" else "!",
                        scenario.name,
                        scenario.correctAnswers,
                        scenario.totalAnswers,
                        scenario.markerCount,
                        scenario.elapsedMs,
                        suffix
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                String.format(Locale.US, "Benchmark toplam: %.0f ms", result.elapsedMs),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun Benchmark100ResultCard(result: Omr100BenchmarkResult) {
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
            Text(
                if (result.allPassed) "100 Soru OMR Benchmark ✓" else "100 Soru OMR Benchmark",
                style = MaterialTheme.typography.titleSmall
            )

            (result.cleanRuns + result.stressRuns).forEach { run ->
                val suffix = if (run.mismatchQuestionIds.isEmpty()) "" else
                    " · Hatalı: ${run.mismatchQuestionIds.joinToString(",")}"
                Text(
                    String.format(
                        Locale.US,
                        "%s %s · %d/%d · M%d/4 · %.0f ms%s",
                        if (run.passed) "✓" else "!",
                        run.name,
                        run.correctAnswers,
                        run.totalAnswers,
                        run.markerCount,
                        run.timing.totalMs,
                        suffix
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            val timing = result.medianTiming
            Text("Temel 3 turun medyanı", style = MaterialTheme.typography.labelMedium)
            Text(
                String.format(
                    Locale.US,
                    "Ön işleme %.1f ms · Marker %.1f ms · Warp %.1f ms",
                    timing.preprocessingMs,
                    timing.markerMs,
                    timing.rectificationMs
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                String.format(
                    Locale.US,
                    "400 baloncuk / 100 soru: %.1f ms · Toplam %.1f ms",
                    timing.bubbleMs,
                    timing.totalMs
                ),
                style = MaterialTheme.typography.bodySmall
            )
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
                "%s · karar güveni %.0f%%",
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
