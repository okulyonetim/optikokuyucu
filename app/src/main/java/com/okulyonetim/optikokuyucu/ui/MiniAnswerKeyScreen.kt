package com.okulyonetim.optikokuyucu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeyPdfExporter
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import com.okulyonetim.optikokuyucu.omr.scoring.ManualAnswerKeyBuilder
import com.okulyonetim.optikokuyucu.omr.scoring.MiniAnswerKeyLayout
import com.okulyonetim.optikokuyucu.omr.scoring.MiniAnswerKeyPdfExporter
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateResolver
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import com.okulyonetim.optikokuyucu.omr.template.OmrRecognitionBindingsResolver
import java.io.ByteArrayOutputStream

@Composable
fun MiniAnswerKeyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val examRepository = remember(context) { FileExamRepository(appContext) }
    val keyRepository = remember(context) { FileAnswerKeyRepository(appContext) }
    val documentRepository = remember(context) { FileDesignerDocumentRepository(appContext) }
    val exams = remember { examRepository.list().sortedByDescending { it.examDateEpochDay } }
    val keys = remember { keyRepository.list() }

    var selectedExamId by remember { mutableStateOf<String?>(null) }
    var copies by remember { mutableStateOf(6) }
    var orientation by remember { mutableStateOf(MiniAnswerKeyPdfExporter.Orientation.PORTRAIT) }
    var selectedBooklets by remember { mutableStateOf<Set<String>>(emptySet()) }
    var status by remember { mutableStateOf("") }
    var previewBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingPdfBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingCopyCount by remember { mutableStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val bytes = pendingPdfBytes
        val savedCopies = pendingCopyCount
        pendingPdfBytes = null
        pendingCopyCount = 0
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "PDF çıktı akışı açılamadı." }
                output.write(bytes)
                output.flush()
            }
        }.onSuccess { status = "Mini cevap anahtarı PDF kaydedildi · $savedCopies adet" }
            .onFailure { error -> status = "PDF kaydedilemedi: ${error.message ?: error.javaClass.simpleName}" }
    }

    val exam = selectedExamId?.let(examRepository::load)
    val resolvedData = remember(exam, selectedExamId) {
        exam?.let { current ->
            val savedDocuments = documentRepository.list()
            val starterDocuments = DesignerStarterTemplates.all()
            val resolved = runCatching {
                ActiveOmrTemplateResolver.resolve(
                    selection = current.templateSelection,
                    savedDocuments = savedDocuments,
                    starterDocuments = starterDocuments
                )
            }.getOrNull() ?: return@let null
            val document = if (current.templateSelection.source != ActiveTemplateSource.DESIGNER_DOCUMENT) null
            else savedDocuments.firstOrNull {
                it.id == current.templateSelection.templateId && it.version == current.templateSelection.templateVersion
            } ?: starterDocuments.firstOrNull {
                it.id == current.templateSelection.templateId && it.version == current.templateSelection.templateVersion
            }
            val sections = ManualAnswerKeyBuilder.sections(document, resolved.template)
            val bindings = OmrRecognitionBindingsResolver.fromTemplate(resolved.template)
            val bookletGridId = bindings.bookletGridId
            val booklets = bookletGridId?.let { gridId -> resolved.template.markGrids.firstOrNull { it.id == gridId } }
                ?.columns
                ?.flatMap { column -> column.marks.map { it.id } }
                ?.distinct()
                .orEmpty()
            MiniAnswerKeyData(resolved.template.id, resolved.template.version, bookletGridId, booklets, sections)
        }
    }

    val data = resolvedData
    val availableEntries = remember(data, keys, exam) {
        if (data == null || exam == null) emptyList() else {
            val matching = keys.filter { key ->
                key.examId == exam.id &&
                    key.templateId == data.templateId &&
                    key.templateVersion == data.templateVersion
            }
            if (data.booklets.isEmpty()) {
                matching.firstOrNull { it.variantValue == null }?.let { key ->
                    listOf(AnswerKeyPdfExporter.SheetEntry(key, exam.name, data.sections))
                }.orEmpty()
            } else {
                data.booklets.mapNotNull { booklet ->
                    matching.firstOrNull { key -> key.variantGridId == data.bookletGridId && key.variantValue == booklet }
                        ?.let { key -> AnswerKeyPdfExporter.SheetEntry(key, exam.name, data.sections) }
                }
            }
        }
    }
    val effectiveEntries = if (selectedBooklets.isEmpty()) availableEntries else {
        availableEntries.filter { it.key.variantValue in selectedBooklets }
    }
    val availableCopyCounts = remember(data?.sections, orientation) {
        data?.let { MiniAnswerKeyLayout.availableCopies(it.sections, orientation) }.orEmpty()
    }
    val recommendedCopies = remember(data?.sections, orientation) {
        data?.let { MiniAnswerKeyLayout.recommendedCopies(it.sections, orientation) } ?: 6
    }

    LaunchedEffect(selectedExamId, data?.templateId, data?.templateVersion, orientation) {
        if (data != null) copies = recommendedCopies
    }
    LaunchedEffect(selectedExamId, selectedBooklets, copies, orientation, effectiveEntries.size) {
        previewBytes = null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ProductTopBar(title = "Mini Cevap Anahtarı", leadingText = "‹", onLeadingClick = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item {
                Text("Sınav seçin", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Soru sayısına göre A4'e mümkün olan en fazla okunaklı mini anahtar otomatik yerleştirilir.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (exams.isEmpty()) {
                item { ProductEmptyState("Sınav bulunamadı", "Mini cevap anahtarı için önce bir sınav oluşturun.") }
            } else {
                items(exams, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedExamId = item.id
                            selectedBooklets = emptySet()
                            previewBytes = null
                            status = ""
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.id == selectedExamId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${item.papers.size} kağıt", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (item.id == selectedExamId) ProductStatusBadge("SEÇİLDİ", ProductBadgeTone.GREEN)
                        }
                    }
                }
            }

            if (exam != null) {
                item {
                    ProductSettingsSection("Kitapçık Türleri", "Boş seçim mevcut tüm kitapçıkları kullanır; A/B gibi türler sayfada sırayla dağıtılır.") {
                        if (data == null) {
                            Text("Sınavın optik formu çözümlenemedi.", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                        } else if (data.booklets.isEmpty()) {
                            Text("Tek genel cevap anahtarı", fontSize = 10.sp)
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(data.booklets) { booklet ->
                                    val ready = availableEntries.any { it.key.variantValue == booklet }
                                    FilterChip(
                                        selected = booklet in selectedBooklets,
                                        enabled = ready,
                                        onClick = { selectedBooklets = selectedBooklets.toggleMini(booklet) },
                                        label = { Text(if (ready) "$booklet Kitapçığı" else "$booklet · eksik", fontSize = 9.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    ProductSettingsSection(
                        "A4 Yerleşimi",
                        "Kısa cevap anahtarlarında kopya sayısı artar; içerik hiçbir zaman hücreleri uzatarak sayfayı doldurmaz."
                    ) {
                        if (data != null) {
                            Text(
                                "Otomatik öneri: A4 başına $recommendedCopies adet",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(MiniAnswerKeyLayout.supportedCopies) { count ->
                                FilterChip(
                                    selected = copies == count,
                                    enabled = count in availableCopyCounts,
                                    onClick = { copies = count },
                                    label = { Text(count.toString()) }
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                modifier = Modifier.weight(1f),
                                selected = orientation == MiniAnswerKeyPdfExporter.Orientation.PORTRAIT,
                                onClick = { orientation = MiniAnswerKeyPdfExporter.Orientation.PORTRAIT },
                                label = { Text("Dikey") }
                            )
                            FilterChip(
                                modifier = Modifier.weight(1f),
                                selected = orientation == MiniAnswerKeyPdfExporter.Orientation.LANDSCAPE,
                                onClick = { orientation = MiniAnswerKeyPdfExporter.Orientation.LANDSCAPE },
                                label = { Text("Yatay") }
                            )
                        }
                    }
                }
                item {
                    ProductSettingsSection("Önizleme Özeti", "Kesim çizgileri PDF üzerinde otomatik oluşturulur.") {
                        Text(
                            "${effectiveEntries.size} kitapçık türü · A4 üzerinde $copies mini anahtar · ${if (orientation == MiniAnswerKeyPdfExporter.Orientation.PORTRAIT) "dikey" else "yatay"}",
                            fontSize = 10.sp
                        )
                        if (effectiveEntries.isEmpty()) {
                            Text("Önce sınavın cevap anahtarını kaydedin.", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                        } else if (copies !in availableCopyCounts) {
                            Text("Bu soru yapısı için daha az kopya seçin.", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                        }
                    }
                }
                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = effectiveEntries.isNotEmpty() && copies in availableCopyCounts,
                        shape = RoundedCornerShape(13.dp),
                        onClick = {
                            runCatching {
                                ByteArrayOutputStream().use { output ->
                                    MiniAnswerKeyPdfExporter.export(effectiveEntries, copies, orientation, output)
                                    output.toByteArray()
                                }
                            }.onSuccess { bytes ->
                                previewBytes = bytes
                                status = "PDF önizleme hazır · $copies adet"
                            }.onFailure { error ->
                                previewBytes = null
                                status = "PDF önizleme oluşturulamadı: ${error.message ?: error.javaClass.simpleName}"
                            }
                        }
                    ) {
                        Text("PDF Önizle")
                    }
                }

                previewBytes?.let { bytes ->
                    item {
                        ProductSettingsSection(
                            "PDF Önizleme",
                            "Aşağıdaki görüntü kaydedilecek PDF'nin birebir önizlemesidir."
                        ) {
                            PdfReportPreview(pdfBytes = bytes, modifier = Modifier.fillMaxWidth())
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(13.dp),
                                onClick = {
                                    pendingPdfBytes = bytes
                                    pendingCopyCount = copies
                                    launcher.launch(miniAnswerKeyFileName(exam.name, copies))
                                }
                            ) {
                                Text("PDF'yi Kaydet")
                            }
                        }
                    }
                }
            }
            if (status.isNotBlank()) {
                item { Text(status, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary) }
            }
            item { Spacer(Modifier.padding(4.dp)) }
        }
    }
}

private data class MiniAnswerKeyData(
    val templateId: String,
    val templateVersion: Int,
    val bookletGridId: String?,
    val booklets: List<String>,
    val sections: List<com.okulyonetim.optikokuyucu.omr.scoring.ManualAnswerSection>
)

private fun <T> Set<T>.toggleMini(value: T): Set<T> = toMutableSet().apply {
    if (!add(value)) remove(value)
}.toSet()

private fun miniAnswerKeyFileName(examName: String, copies: Int): String {
    val safe = examName.trim().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(48).ifBlank { "sinav" }
    return "$safe-mini-cevap-anahtari-${copies}li.pdf"
}
