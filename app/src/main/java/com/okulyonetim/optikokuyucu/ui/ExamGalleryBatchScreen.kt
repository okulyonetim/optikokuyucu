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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.okulyonetim.optikokuyucu.exam.ExamGalleryBatchProgress
import com.okulyonetim.optikokuyucu.exam.ExamPaperRegistrar
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.gallery.GalleryOmrReader
import com.okulyonetim.optikokuyucu.omr.results.FileScanImageRepository
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.results.GalleryScanRecorder
import com.okulyonetim.optikokuyucu.omr.results.StoredScanImage
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateResolver
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** User-started production import for multiple student sheets from the Android gallery. */
@Composable
fun ExamGalleryBatchScreen(
    examId: String,
    openCvReady: Boolean,
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
        BatchImportError("Sınav bulunamadı.", onBack)
        return
    }
    if (resolved == null) {
        BatchImportError("Bu sınavın optik formu artık bulunamıyor.", onBack)
        return
    }

    val scanRepository = remember(context) { FileScanRecordRepository(appContext) }
    val galleryRecorder = remember(context) { GalleryScanRecorder(scanRepository) }
    val imageRepository = remember(context) { FileScanImageRepository(appContext) }
    val registrar = remember(context) { ExamPaperRegistrar(examRepository) }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val worker = remember { Executors.newSingleThreadExecutor() }
    val active = remember { AtomicBoolean(true) }
    val template = resolved.template

    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(ExamGalleryBatchProgress()) }
    var failures by remember { mutableStateOf<List<String>>(emptyList()) }
    var status by remember {
        mutableStateOf(if (openCvReady) "Birden fazla öğrenci optiği seçebilirsiniz." else "OpenCV hazır değil.")
    }

    DisposableEffect(Unit) {
        onDispose {
            active.set(false)
            worker.shutdownNow()
        }
    }

    fun processBatch(uris: List<Uri>) {
        if (busy || uris.isEmpty() || !openCvReady) return
        busy = true
        progress = ExamGalleryBatchProgress.start(uris.size)
        failures = emptyList()
        status = "${uris.size} görsel sıraya alındı."
        val templateAtStart = template

        worker.execute {
            var localProgress = ExamGalleryBatchProgress.start(uris.size)
            val localFailures = mutableListOf<String>()

            uris.forEachIndexed { index, uri ->
                if (!active.get()) return@execute
                mainExecutor.execute {
                    if (active.get()) status = "${index + 1}/${uris.size} · optik okunuyor…"
                }

                val outcome = runCatching {
                    val result = GalleryOmrReader.read(context, uri, templateAtStart)
                    try {
                        require(result.rectificationReady) {
                            "Dört köşe marker birlikte bulunamadı veya form hizalanamadı."
                        }
                        require(result.bubbleResult.questions.isNotEmpty()) {
                            "Soru sonucu bulunamadı."
                        }

                        val record = galleryRecorder.record(templateAtStart, result)
                        try {
                            registrar.register(examId = examId, record = record)
                        } catch (error: Throwable) {
                            scanRepository.delete(record.id)
                            throw error
                        }

                        val canonical = result.canonicalLuma
                        if (
                            canonical != null &&
                            result.canonicalWidth > 0 &&
                            result.canonicalHeight > 0 &&
                            canonical.size == result.canonicalWidth * result.canonicalHeight
                        ) {
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
                    } finally {
                        result.bitmap.recycle()
                    }
                }

                if (outcome.isSuccess) {
                    localProgress = localProgress.onImported()
                } else {
                    localProgress = localProgress.onFailed()
                    val error = outcome.exceptionOrNull()
                    localFailures += "${index + 1}. görsel: ${error?.message ?: error?.javaClass?.simpleName ?: "Okunamadı"}"
                }

                val snapshot = localProgress
                val failureSnapshot = localFailures.toList()
                mainExecutor.execute {
                    if (active.get()) {
                        progress = snapshot
                        failures = failureSnapshot
                        status = if (snapshot.completed) {
                            "Toplu okuma tamamlandı · ${snapshot.imported} eklendi · ${snapshot.failed} başarısız"
                        } else {
                            "${snapshot.processed}/${snapshot.total} işlendi"
                        }
                    }
                }
            }

            mainExecutor.execute {
                if (active.get()) busy = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) processBatch(uris)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ProductTopBar(
                title = "${exam.name} · Toplu Galeri",
                leadingText = "‹",
                onLeadingClick = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Galeriden Toplu Kağıt Oku", style = MaterialTheme.typography.titleLarge)
                    Text("Form: ${resolved.name} · ${template.bubbleRows.size} soru")
                    Text(
                        "Seçtiğiniz görseller sırayla okunur. Başarılı okumalar otomatik olarak bu sınava eklenir ve mümkünse canonical kağıt görüntüsü de saklanır.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Boş, çift veya şüpheli cevaplar raw okumada korunur; sonuç ekranında kontrol gerekli olarak görünür.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = openCvReady && !busy,
                onClick = { picker.launch("image/*") }
            ) {
                Text(if (busy) "Toplu okuma sürüyor…" else "Galeriden Birden Fazla Optik Seç")
            }

            if (progress.total > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(status, fontWeight = FontWeight.SemiBold)
                        Text("${progress.processed} / ${progress.total} işlendi · ${progress.remaining} kaldı")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProductStatusBadge("Eklendi ${progress.imported}", ProductBadgeTone.GREEN)
                            ProductStatusBadge("Başarısız ${progress.failed}", if (progress.failed > 0) ProductBadgeTone.RED else ProductBadgeTone.GREEN)
                        }
                    }
                }
            } else {
                Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (failures.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Okunamayan Görseller", fontWeight = FontWeight.SemiBold)
                        failures.take(10).forEach { failure ->
                            Text(failure, color = MaterialTheme.colorScheme.error)
                        }
                        if (failures.size > 10) {
                            Text("+${failures.size - 10} hata daha", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (progress.total > 0 && progress.completed && !busy) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                ) {
                    Text("Sınava Dön")
                }
            }
        }
    }
}

@Composable
private fun BatchImportError(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onBack) { Text("Sınava dön") }
    }
}
