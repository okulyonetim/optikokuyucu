package com.okulyonetim.optikokuyucu.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.okulyonetim.optikokuyucu.exam.Exam
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.gallery.GalleryOmrReader
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKey
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeyCapture
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeyChoiceCodec
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeySource
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeySpreadsheetImporter
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeyXlsxExporter
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import com.okulyonetim.optikokuyucu.omr.scoring.ManualAnswerKeyBuilder
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateResolver
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import com.okulyonetim.optikokuyucu.omr.template.OmrRecognitionBindingsResolver
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamAnswerKeyEditor(
    exam: Exam,
    openCvReady: Boolean,
    onChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val feedback = LocalAppFeedback.current
    val repository = remember(context) { FileAnswerKeyRepository(appContext) }
    val documentRepository = remember(context) { FileDesignerDocumentRepository(appContext) }
    val savedDocuments = remember(exam.templateSelection) { documentRepository.list() }
    val starterDocuments = remember { DesignerStarterTemplates.all() }
    val resolved = remember(exam.templateSelection, savedDocuments, starterDocuments) {
        runCatching {
            ActiveOmrTemplateResolver.resolve(
                selection = exam.templateSelection,
                savedDocuments = savedDocuments,
                starterDocuments = starterDocuments
            )
        }.getOrNull()
    }

    if (resolved == null) {
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Cevap anahtarı açılamadı", fontWeight = FontWeight.SemiBold)
                Text(
                    "Sınavın optik formu bulunamadı veya form geometrisi geçersiz. Önce optik formu düzeltin.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        return
    }

    val template = resolved.template
    val designerDocument = remember(exam.templateSelection, savedDocuments, starterDocuments) {
        if (exam.templateSelection.source != ActiveTemplateSource.DESIGNER_DOCUMENT) null
        else savedDocuments.firstOrNull {
            it.id == exam.templateSelection.templateId && it.version == exam.templateSelection.templateVersion
        } ?: starterDocuments.firstOrNull {
            it.id == exam.templateSelection.templateId && it.version == exam.templateSelection.templateVersion
        }
    }
    val sections = remember(designerDocument, template) {
        ManualAnswerKeyBuilder.sections(designerDocument, template)
    }
    val rowsById = remember(template) { template.bubbleRows.associateBy { it.id } }
    val bindings = remember(template) { OmrRecognitionBindingsResolver.fromTemplate(template) }
    val bookletGridId = bindings.bookletGridId
    val bookletChoices = remember(template, bookletGridId) {
        bookletGridId?.let { gridId -> template.markGrids.firstOrNull { it.id == gridId } }
            ?.columns
            ?.flatMap { column -> column.marks.map { it.id } }
            ?.distinct()
            .orEmpty()
    }
    val worker = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    var keys by remember { mutableStateOf(repository.list()) }
    var booklet by remember(exam.id, bookletChoices) { mutableStateOf(bookletChoices.firstOrNull()) }
    var sectionId by remember(exam.id, sections) { mutableStateOf(sections.firstOrNull()?.id) }
    var answers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var sectionMenu by remember { mutableStateOf(false) }
    var bookletMenu by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingXlsx by remember { mutableStateOf<ByteArray?>(null) }

    fun matchingKey(): StoredAnswerKey? = keys.firstOrNull { key ->
        key.templateId == template.id &&
            key.templateVersion == template.version &&
            if (booklet.isNullOrBlank()) {
                key.variantGridId == null && key.variantValue == null
            } else {
                key.variantGridId == bookletGridId && key.variantValue == booklet
            }
    }

    LaunchedEffect(booklet, keys) {
        answers = matchingKey()?.answerKey?.answers.orEmpty()
    }

    DisposableEffect(Unit) {
        onDispose { worker.shutdown() }
    }

    fun refreshKeys(message: String? = null) {
        keys = repository.list()
        message?.let {
            status = it
            feedback.success(it)
        }
        onChanged()
    }

    fun persistAnswers(updated: Map<String, String>) {
        runCatching {
            if (updated.isEmpty()) {
                repository.delete(
                    templateId = template.id,
                    templateVersion = template.version,
                    variantGridId = if (booklet.isNullOrBlank()) null else bookletGridId,
                    variantValue = booklet
                )
            } else {
                StoredAnswerKey(
                    answerKey = AnswerKey(template.id, template.version, updated),
                    variantGridId = if (booklet.isNullOrBlank()) null else bookletGridId,
                    variantValue = booklet,
                    source = AnswerKeySource.MANUAL
                ).also(repository::save)
            }
        }.onSuccess {
            answers = updated
            keys = repository.list()
            status = "Otomatik kaydedildi · ${updated.size}/${template.bubbleRows.size} soru"
            onChanged()
        }.onFailure { error ->
            val message = "Anahtar kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
            status = message
            feedback.error(message)
        }
    }

    fun toggleChoice(questionId: String, choice: String) {
        val rowChoices = rowsById[questionId]?.bubbles?.map { it.id }.orEmpty()
        val selected = AnswerKeyChoiceCodec.decode(answers[questionId]).toMutableSet()
        if (choice in selected) selected.remove(choice) else selected.add(choice)
        val ordered = rowChoices.filter { it in selected }
        val updated = answers.toMutableMap()
        if (ordered.isEmpty()) updated.remove(questionId)
        else updated[questionId] = AnswerKeyChoiceCodec.encode(ordered)
        persistAnswers(updated)
    }

    fun saveCapturedKey(uri: Uri, source: AnswerKeySource) {
        if (busy || !openCvReady) return
        busy = true
        status = "Cevap anahtarı okunuyor…"
        worker.execute {
            runCatching {
                val result = GalleryOmrReader.read(
                    context = context,
                    uri = uri,
                    template = template,
                    allowFullFrameFallback = true
                )
                try {
                    require(result.rectificationReady) {
                        "Form dört köşe marker ile güvenilir biçimde hizalanamadı. Dört markerın da görüntüde olduğundan emin olun."
                    }
                    val capture = AnswerKeyCapture.fromRead(template.id, template.version, result.bubbleResult)
                    require(capture.successful) {
                        "Boş veya şüpheli sorular var: ${capture.invalidQuestionIds.take(8).joinToString(", ")}"
                    }
                    val detectedBooklet = bindings.booklet(result.markGridResult)
                    val targetBooklet = detectedBooklet?.takeIf { it in bookletChoices } ?: booklet
                    if (bookletGridId != null) {
                        require(!targetBooklet.isNullOrBlank()) { "Kitapçık türünü seçin." }
                    }
                    StoredAnswerKey(
                        answerKey = requireNotNull(capture.answerKey),
                        variantGridId = if (targetBooklet.isNullOrBlank()) null else bookletGridId,
                        variantValue = targetBooklet,
                        source = source
                    ).also(repository::save)
                } finally {
                    result.bitmap.recycle()
                }
            }.onSuccess { stored ->
                mainExecutor.execute {
                    if (!stored.variantValue.isNullOrBlank()) booklet = stored.variantValue
                    busy = false
                    refreshKeys("Cevap anahtarı aktarıldı · ${stored.answerKey.answers.size} soru")
                }
            }.onFailure { error ->
                mainExecutor.execute {
                    busy = false
                    val message = "Anahtar okunamadı: ${error.message ?: error.javaClass.simpleName}"
                    status = message
                    feedback.error(message)
                }
            }
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) saveCapturedKey(uri, AnswerKeySource.GALLERY)
    }

    fun createCameraUri(): Uri {
        val directory = File(context.cacheDir, "exam-answer-key-camera").apply { mkdirs() }
        val file = File(directory, "answer-key-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) saveCapturedKey(uri, AnswerKeySource.CAMERA)
    }
    fun launchCamera() {
        runCatching { createCameraUri() }
            .onSuccess { uri -> pendingCameraUri = uri; cameraLauncher.launch(uri) }
            .onFailure { feedback.error("Kamera açılamadı: ${it.message ?: it.javaClass.simpleName}") }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else feedback.warning("Kamera izni verilmedi.")
    }
    fun requestCamera() {
        if (!openCvReady || busy) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val spreadsheetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || busy) return@rememberLauncherForActivityResult
        val requestedBooklet = booklet
        busy = true
        worker.execute {
            runCatching {
                val fileName = examAnswerKeyContentName(context, uri) ?: "cevap-anahtari.xlsx"
                val imported = context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Excel dosyası açılamadı." }
                    AnswerKeySpreadsheetImporter.import(
                        input = input,
                        fileName = fileName,
                        template = template,
                        fallbackVariant = requestedBooklet
                    )
                }
                val targetBooklet = imported.variantValue ?: requestedBooklet
                if (bookletGridId != null) {
                    require(!targetBooklet.isNullOrBlank()) { "Kitapçık türünü seçin." }
                    require(targetBooklet in bookletChoices) { "Excel kitapçık türü forma ait değil: $targetBooklet" }
                }
                StoredAnswerKey(
                    answerKey = imported.answerKey,
                    variantGridId = if (targetBooklet.isNullOrBlank()) null else bookletGridId,
                    variantValue = targetBooklet,
                    source = AnswerKeySource.SPREADSHEET
                ).also(repository::save)
            }.onSuccess { stored ->
                mainExecutor.execute {
                    if (!stored.variantValue.isNullOrBlank()) booklet = stored.variantValue
                    busy = false
                    refreshKeys("Excel anahtarı içe aktarıldı")
                }
            }.onFailure { error ->
                mainExecutor.execute {
                    busy = false
                    val message = "Excel içe aktarılamadı: ${error.message ?: error.javaClass.simpleName}"
                    status = message
                    feedback.error(message)
                }
            }
        }
    }

    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(EXAM_XLSX_MIME)
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
            feedback.success("Cevap anahtarı dışa aktarıldı.")
        }.onFailure {
            feedback.error("Dışa aktarılamadı: ${it.message ?: it.javaClass.simpleName}")
        }
    }

    fun exportCurrent() {
        val key = matchingKey()
        if (key == null) {
            feedback.warning("Önce bu kitapçık için bir cevap anahtarı oluşturun.")
            return
        }
        runCatching { AnswerKeyXlsxExporter.export(key) }
            .onSuccess { bytes ->
                pendingXlsx = bytes
                xlsxLauncher.launch(examAnswerKeyFileName(exam.name, booklet))
            }
            .onFailure { feedback.error("XLSX oluşturulamadı: ${it.message ?: it.javaClass.simpleName}") }
    }

    val selectedSection = sections.firstOrNull { it.id == sectionId } ?: sections.firstOrNull()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                KeySelectorButton(
                    label = "Ders",
                    value = exam.subjectName.ifBlank { selectedSection?.label ?: "Ders yok" },
                    onClick = { sectionMenu = true }
                )
                DropdownMenu(expanded = sectionMenu, onDismissRequest = { sectionMenu = false }) {
                    sections.forEach { section ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    exam.subjectName.ifBlank { section.label },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            onClick = { sectionId = section.id; sectionMenu = false }
                        )
                    }
                }
            }
            if (bookletChoices.isNotEmpty()) {
                Box(modifier = Modifier.weight(0.72f)) {
                    KeySelectorButton(
                        label = "Kitapçık",
                        value = booklet ?: "Seç",
                        onClick = { bookletMenu = true }
                    )
                    DropdownMenu(expanded = bookletMenu, onDismissRequest = { bookletMenu = false }) {
                        bookletChoices.forEach { value ->
                            DropdownMenuItem(
                                text = { Text(value) },
                                onClick = { booklet = value; bookletMenu = false }
                            )
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.size(46.dp).clickable { optionsOpen = true },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("⋮", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (status.isNotBlank()) {
            Text(
                status,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val section = selectedSection
        if (section == null) {
            Text("Bu formda cevap alanı bulunamadı.", modifier = Modifier.padding(18.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(section.questionIds, key = { _, id -> id }) { index, questionId ->
                    val row = rowsById[questionId]
                    val choices = row?.bubbles?.map { it.id }.orEmpty()
                    val selectedChoices = AnswerKeyChoiceCodec.decode(answers[questionId]).toSet()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Text(
                            "${index + 1})",
                            modifier = Modifier.size(width = 40.dp, height = 48.dp),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        choices.forEach { choice ->
                            val selected = choice in selectedChoices
                            Surface(
                                modifier = Modifier.size(48.dp).clickable { toggleChoice(questionId, choice) },
                                shape = CircleShape,
                                color = if (selected) Color(0xFF4CAF50) else Color.Transparent,
                                border = BorderStroke(
                                    1.2.dp,
                                    if (selected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        choice,
                                        fontSize = 16.sp,
                                        color = if (selected) Color(0xFF102313) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(1.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
                    ) {}
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }

    if (optionsOpen) {
        ModalBottomSheet(onDismissRequest = { optionsOpen = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Cevap Anahtarı Seçenekleri", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${exam.name}${booklet?.let { " · Kitapçık $it" }.orEmpty()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                AnswerKeyOption("⇩", "Excel'den içe aktar") {
                    optionsOpen = false
                    spreadsheetPicker.launch(arrayOf(EXAM_XLSX_MIME, "application/vnd.ms-excel", "application/octet-stream"))
                }
                AnswerKeyOption("⇧", "Excel'e dışa aktar") {
                    optionsOpen = false
                    exportCurrent()
                }
                AnswerKeyOption("▣", "Galeriden optik anahtar oku", enabled = openCvReady && !busy) {
                    optionsOpen = false
                    galleryPicker.launch("image/*")
                }
                AnswerKeyOption("⌾", "Kamerayla optik anahtar tara", enabled = openCvReady && !busy) {
                    optionsOpen = false
                    requestCamera()
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun KeySelectorButton(label: String, value: String, onClick: () -> Unit) {
    OutlinedButton(
        modifier = Modifier.fillMaxWidth().height(58.dp),
        onClick = onClick,
        shape = RoundedCornerShape(13.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
        }
    }
}

@Composable
private fun AnswerKeyOption(
    symbol: String,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(symbol, fontSize = 20.sp)
            Text(label, fontSize = 15.sp)
        }
    }
}

private fun examAnswerKeyContentName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}.getOrNull()

private fun examAnswerKeyFileName(examName: String, booklet: String?): String {
    val base = examName.trim()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
        .trim('-')
        .take(42)
        .ifBlank { "sinav" }
    return "$base-cevap-anahtari${booklet?.let { "-$it" }.orEmpty()}.xlsx"
}

private const val EXAM_XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
