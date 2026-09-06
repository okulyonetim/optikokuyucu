package com.okulyonetim.optikokuyucu.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.gallery.GalleryOmrReader
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeyCapture
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeySource
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeySpreadsheetImporter
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeyXlsxExporter
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import com.okulyonetim.optikokuyucu.omr.scoring.ManualAnswerKeyBuilder
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import com.okulyonetim.optikokuyucu.omr.template.AnswerKeyTemplateTargetResolver
import com.okulyonetim.optikokuyucu.omr.template.FileActiveTemplateSelectionRepository
import com.okulyonetim.optikokuyucu.omr.template.OmrRecognitionBindingsResolver
import java.io.File
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
    val appContext = context.applicationContext
    val repository = remember(context) { FileAnswerKeyRepository(appContext) }
    val activeSelection = remember(context) { FileActiveTemplateSelectionRepository(appContext).load() }
    val savedDocuments = remember(context) { FileDesignerDocumentRepository(appContext).list() }
    val starterDocuments = remember { DesignerStarterTemplates.all() }
    val activeTemplate = remember(activeSelection, savedDocuments) {
        AnswerKeyTemplateTargetResolver.resolve(
            selection = activeSelection,
            savedDocuments = savedDocuments
        )
    }

    if (activeTemplate == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Seçili optik form sürümü bulunamadı. Cevap anahtarı oluşturulmadı.",
                color = MaterialTheme.colorScheme.error
            )
            OutlinedButton(onClick = onBack) { Text("Geri dön") }
        }
        return
    }

    val template = activeTemplate.template
    val recognitionBindings = remember(template) { OmrRecognitionBindingsResolver.fromTemplate(template) }
    val bookletGridId = recognitionBindings.bookletGridId
    val bookletChoices = remember(template, bookletGridId) {
        bookletGridId?.let { id -> template.markGrids.firstOrNull { it.id == id } }
            ?.columns
            ?.flatMap { column -> column.marks.map { it.id } }
            ?.distinct()
            .orEmpty()
    }
    val designerDocument = remember(activeSelection, savedDocuments, starterDocuments) {
        if (activeSelection.source != ActiveTemplateSource.DESIGNER_DOCUMENT) {
            null
        } else {
            savedDocuments.firstOrNull {
                it.id == activeSelection.templateId && it.version == activeSelection.templateVersion
            } ?: starterDocuments.firstOrNull {
                it.id == activeSelection.templateId && it.version == activeSelection.templateVersion
            }
        }
    }
    val manualSections = remember(designerDocument, template) {
        ManualAnswerKeyBuilder.sections(designerDocument, template)
    }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val worker = remember { Executors.newSingleThreadExecutor() }

    var keys by remember { mutableStateOf(repository.list()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(if (openCvReady) "Cevap anahtarı hazır" else "OpenCV başlatılamadı")
    }
    var pendingXlsx by remember { mutableStateOf<ByteArray?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var bookletSelection by remember(template.id, template.version, bookletChoices) {
        mutableStateOf(bookletChoices.firstOrNull())
    }
    var bookletMenuOpen by remember { mutableStateOf(false) }
    var manualEntries by remember(manualSections) {
        mutableStateOf(ManualAnswerKeyBuilder.entriesFor(null, manualSections))
    }

    val matchingKeys = keys.filter {
        it.templateId == template.id && it.templateVersion == template.version
    }

    LaunchedEffect(bookletSelection, matchingKeys, manualSections) {
        val selectedKey = matchingKeys.firstOrNull { key ->
            if (bookletSelection.isNullOrBlank()) {
                key.variantGridId == null && key.variantValue == null
            } else {
                key.variantGridId == bookletGridId && key.variantValue == bookletSelection
            }
        }
        manualEntries = ManualAnswerKeyBuilder.entriesFor(selectedKey?.answerKey, manualSections)
    }

    DisposableEffect(Unit) {
        onDispose { worker.shutdown() }
    }

    fun saveCapturedKey(uri: Uri, source: AnswerKeySource) {
        if (busy || !openCvReady) return
        busy = true
        status = if (source == AnswerKeySource.CAMERA) {
            "Kameradaki cevap anahtarı okunuyor…"
        } else {
            "Cevap anahtarı okunuyor…"
        }
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
                        "Anahtar kabul edilmedi. Boş/çift/şüpheli sorular: " +
                            capture.invalidQuestionIds.joinToString(", ")
                    }
                    val booklet = recognitionBindings.booklet(result.markGridResult)
                    if (bookletGridId != null) {
                        require(!booklet.isNullOrBlank()) { "Kitapçık türü net okunamadı." }
                        require(booklet in bookletChoices) {
                            "Okunan kitapçık türü seçili forma ait değil: $booklet"
                        }
                    }
                    StoredAnswerKey(
                        answerKey = requireNotNull(capture.answerKey),
                        variantGridId = if (booklet.isNullOrBlank()) null else bookletGridId,
                        variantValue = booklet,
                        source = source
                    ).also(repository::save)
                } finally {
                    result.bitmap.recycle()
                }
            }.onSuccess { stored ->
                mainExecutor.execute {
                    keys = repository.list()
                    if (stored.variantValue != null) bookletSelection = stored.variantValue
                    busy = false
                    status = buildString {
                        append("Cevap anahtarı kaydedildi")
                        stored.variantValue?.let { append(" · Kitapçık $it") } ?: append(" · Genel")
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

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) saveCapturedKey(uri, AnswerKeySource.GALLERY)
    }

    fun createCameraUri(): Uri {
        val directory = File(context.cacheDir, "answer-key-camera").apply { mkdirs() }
        val file = File(directory, "answer-key-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            saveCapturedKey(uri, AnswerKeySource.CAMERA)
        } else if (!success) {
            status = "Kamera işlemi iptal edildi."
        }
    }

    fun launchCameraCapture() {
        runCatching { createCameraUri() }
            .onSuccess { uri ->
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            }
            .onFailure { error -> status = "Kamera açılamadı: ${error.message}" }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCameraCapture() else status = "Kamera izni verilmedi."
    }

    fun requestCamera() {
        if (!openCvReady || busy) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraCapture()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val spreadsheetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || busy) return@rememberLauncherForActivityResult
        val requestedBooklet = bookletSelection
        if (bookletGridId != null && requestedBooklet.isNullOrBlank()) {
            status = "Excel içe aktarmadan önce kitapçık türünü seçin."
            return@rememberLauncherForActivityResult
        }
        busy = true
        status = requestedBooklet?.let { "Kitapçık $it Excel cevap anahtarı içe aktarılıyor…" }
            ?: "Excel cevap anahtarı içe aktarılıyor…"
        worker.execute {
            runCatching {
                val fileName = contentDisplayName(context, uri) ?: "cevap-anahtari.xlsx"
                val imported = context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Excel dosyası açılamadı." }
                    AnswerKeySpreadsheetImporter.import(
                        input = input,
                        fileName = fileName,
                        template = template,
                        fallbackVariant = requestedBooklet
                    )
                }
                if (bookletGridId != null) {
                    require(!imported.variantValue.isNullOrBlank()) {
                        "Bu form için kitapçık türü seçilmelidir."
                    }
                    require(imported.variantValue in bookletChoices) {
                        "Excel'deki kitapçık türü seçili formda yok: ${imported.variantValue}"
                    }
                    require(imported.variantValue == requestedBooklet) {
                        "Excel Kitapçık ${imported.variantValue} için; ekranda Kitapçık $requestedBooklet seçili. " +
                            "Doğru kitapçığı seçip tekrar içe aktarın."
                    }
                }
                StoredAnswerKey(
                    answerKey = imported.answerKey,
                    variantGridId = if (imported.variantValue.isNullOrBlank()) null else bookletGridId,
                    variantValue = imported.variantValue,
                    source = AnswerKeySource.SPREADSHEET
                ).also(repository::save)
            }.onSuccess { stored ->
                mainExecutor.execute {
                    keys = repository.list()
                    if (stored.variantValue != null) bookletSelection = stored.variantValue
                    busy = false
                    status = buildString {
                        append("Excel anahtarı içe aktarıldı · ${stored.answerKey.answers.size} soru")
                        stored.variantValue?.let { append(" · Kitapçık $it") }
                    }
                }
            }.onFailure { error ->
                mainExecutor.execute {
                    busy = false
                    status = error.message ?: "Excel cevap anahtarı içe aktarılamadı."
                }
            }
        }
    }

    fun saveManual() {
        runCatching {
            val answerKey = ManualAnswerKeyBuilder.build(template, manualSections, manualEntries)
            if (bookletGridId != null) {
                require(!bookletSelection.isNullOrBlank()) { "Kitapçık türü seçilmelidir." }
            }
            StoredAnswerKey(
                answerKey = answerKey,
                variantGridId = if (bookletSelection.isNullOrBlank()) null else bookletGridId,
                variantValue = bookletSelection,
                source = AnswerKeySource.MANUAL
            ).also(repository::save)
        }.onSuccess { stored ->
            keys = repository.list()
            status = buildString {
                append("Manuel cevap anahtarı kaydedildi · ${stored.answerKey.answers.size} soru")
                stored.variantValue?.let { append(" · Kitapçık $it") }
            }
        }.onFailure { error ->
            status = error.message ?: "Manuel cevap anahtarı kaydedilemedi."
        }
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(activeTemplate.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("${template.bubbleRows.size} soru · v${template.version}")
                Text("Cevap anahtarı manuel, Excel (.xls/.xlsx), galeri veya kamera ile oluşturulabilir.")
                if (bookletChoices.isNotEmpty()) {
                    Text(
                        "Her kitapçık ayrı cevap anahtarıdır. Seçili kitapçığın manuel ve Excel işlemleri " +
                            "diğer kitapçıkları değiştirmez."
                    )
                }
                Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (bookletChoices.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { bookletMenuOpen = true }
                ) {
                    Text("Düzenlenen Kitapçık: ${bookletSelection ?: "Seçiniz"}")
                }
                DropdownMenu(
                    expanded = bookletMenuOpen,
                    onDismissRequest = { bookletMenuOpen = false }
                ) {
                    bookletChoices.forEach { value ->
                        DropdownMenuItem(
                            text = { Text("Kitapçık $value") },
                            onClick = {
                                bookletSelection = value
                                bookletMenuOpen = false
                                val hasSaved = matchingKeys.any {
                                    it.variantGridId == bookletGridId && it.variantValue == value
                                }
                                status = if (hasSaved) {
                                    "Kitapçık $value kayıtlı anahtarı düzenlemeye yüklendi."
                                } else {
                                    "Kitapçık $value için yeni cevap anahtarı giriliyor."
                                }
                            }
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    bookletSelection?.let { "Kitapçık $it · Manuel Cevap Girişi" } ?: "Manuel Cevap Girişi",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Her ders için cevapları sırayla girin. ABCD… şeklinde veya virgülle ayırarak girebilirsiniz. " +
                        "Daha önce kaydedilmiş seçili kitapçık varsa cevapları otomatik yüklenir ve düzenlenebilir.",
                    style = MaterialTheme.typography.bodySmall
                )
                manualSections.forEach { section ->
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = manualEntries[section.id].orEmpty(),
                        onValueChange = { value ->
                            manualEntries = manualEntries +
                                (section.id to value.uppercase(Locale("tr", "TR")))
                        },
                        label = { Text("${section.label} · ${section.questionIds.size} soru") },
                        supportingText = { Text("Seçenekler: ${section.allowedChoices.joinToString("/")}") },
                        minLines = 1,
                        maxLines = 3
                    )
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = ::saveManual
                ) {
                    Text(bookletSelection?.let { "Kitapçık $it Anahtarını Kaydet" } ?: "Manuel Anahtarı Kaydet")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dosyadan / Formdan Aktar", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = {
                        spreadsheetPicker.launch(
                            arrayOf(XLS_MIME_TYPE, XLSX_MIME_TYPE, "application/octet-stream")
                        )
                    }
                ) {
                    Text(
                        bookletSelection?.let { "Kitapçık $it için Excel (.xls / .xlsx) İçe Aktar" }
                            ?: "Excel (.xls / .xlsx) İçe Aktar"
                    )
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = openCvReady && !busy,
                    onClick = { imagePicker.launch("image/*") }
                ) { Text("Galeriden Optik Anahtar Oku") }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = openCvReady && !busy,
                    onClick = ::requestCamera
                ) { Text("Kamerayla Optik Anahtar Oku") }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Kayıtlı Anahtarlar · ${matchingKeys.size}", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = {
                keys = repository.list()
                status = "Anahtarlar yenilendi"
            }) { Text("Yenile") }
        }

        if (matchingKeys.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("Henüz bu forma ait cevap anahtarı yok.", modifier = Modifier.padding(16.dp))
            }
        } else {
            matchingKeys
                .sortedBy { it.variantValue ?: "" }
                .forEach { key ->
                    AnswerKeyCard(
                        key = key,
                        onExportXlsx = { exportXlsx(key) },
                        onDelete = {
                            repository.delete(
                                key.templateId,
                                key.templateVersion,
                                key.variantGridId,
                                key.variantValue
                            )
                            keys = repository.list()
                            status = key.variantValue?.let { "Kitapçık $it cevap anahtarı silindi" }
                                ?: "Cevap anahtarı silindi"
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
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                "${key.answerKey.answers.size} soru · Kaynak: ${answerKeySourceLabel(key.source)}",
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
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onExportXlsx) {
                Text(key.variantValue?.let { "Kitapçık $it XLSX olarak dışa aktar" } ?: "XLSX olarak dışa aktar")
            }
            TextButton(onClick = onDelete) {
                Text(key.variantValue?.let { "Kitapçık $it anahtarını sil" } ?: "Bu anahtarı sil")
            }
        }
    }
}

private fun answerKeySourceLabel(source: AnswerKeySource): String = when (source) {
    AnswerKeySource.GALLERY -> "Galeri"
    AnswerKeySource.CAMERA -> "Kamera"
    AnswerKeySource.MANUAL -> "Manuel"
    AnswerKeySource.SPREADSHEET -> "Excel"
    AnswerKeySource.SCAN_RECORD -> "Tarama kaydı"
}

private fun contentDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index < 0) null else cursor.getString(index)
    }

private fun answerKeyFileName(key: StoredAnswerKey): String {
    val variant = key.variantValue?.let { "-$it" } ?: "-genel"
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(key.createdAtEpochMs))
    return "cevap-anahtari$variant-$timestamp.xlsx"
}

private const val XLS_MIME_TYPE = "application/vnd.ms-excel"
private const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
