package com.okulyonetim.optikokuyucu.ui

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.DesignerBoxElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDraftHandoff
import com.okulyonetim.optikokuyucu.omr.designer.DesignerLineElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPdfExporter
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextAlignment
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.designer.StructuredFormBuildResult
import com.okulyonetim.optikokuyucu.omr.designer.StructuredFormConfig
import com.okulyonetim.optikokuyucu.omr.designer.StructuredFormDocumentFactory
import com.okulyonetim.optikokuyucu.omr.designer.StructuredFormPresets
import com.okulyonetim.optikokuyucu.omr.designer.StructuredInfoField
import com.okulyonetim.optikokuyucu.omr.designer.StructuredLesson
import com.okulyonetim.optikokuyucu.omr.designer.StructuredOrientation
import com.okulyonetim.optikokuyucu.omr.designer.StructuredPaperSize
import com.okulyonetim.optikokuyucu.omr.designer.pdfProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class StructuredEditorTab(val label: String) {
    PREVIEW("Önizleme"),
    FORM("Form"),
    LESSONS("Dersler"),
    LAYOUT("Yerleşim")
}

@Composable
fun StructuredOmrDesignerScreen(
    openCvReady: Boolean,
    onBack: () -> Unit,
    onOpenAdvanced: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { FileDesignerDocumentRepository(context.applicationContext) }
    val presets = remember { StructuredFormPresets.all() }
    var selectedTab by remember { mutableStateOf(StructuredEditorTab.PREVIEW) }
    var showProtectedZones by remember { mutableStateOf(false) }
    var config by remember {
        mutableStateOf(
            StructuredFormConfig(
                id = "structured-${System.currentTimeMillis()}",
                name = "Yeni Optik Form"
            )
        )
    }
    var status by remember { mutableStateOf("Form hazır. Önizleme değişikliklerle birlikte anında güncellenir.") }
    val buildAttempt = remember(config) { runCatching { StructuredFormDocumentFactory.build(config) } }
    val buildResult = buildAttempt.getOrNull()
    val buildError = buildAttempt.exceptionOrNull()?.message
    var pendingPdf by remember {
        mutableStateOf<Pair<DesignerDocument, com.okulyonetim.optikokuyucu.omr.designer.PdfPageProfile>?>(null)
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val pending = pendingPdf
        pendingPdf = null
        if (uri == null || pending == null) return@rememberLauncherForActivityResult
        status = runCatching {
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "PDF çıktı akışı açılamadı." }
                DesignerPdfExporter.export(
                    document = pending.first,
                    output = output,
                    profile = pending.second
                )
            }
            "${pending.second.displayName} PDF oluşturuldu ✓"
        }.getOrElse { error ->
            "PDF oluşturulamadı: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Geri") }
            Column(horizontalAlignment = Alignment.End) {
                Text("Optik Form Editörü", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(config.name, style = MaterialTheme.typography.bodySmall)
            }
        }

        TabRow(selectedTabIndex = selectedTab.ordinal) {
            StructuredEditorTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                StructuredEditorTab.PREVIEW -> PreviewTab(
                    result = buildResult,
                    buildError = buildError,
                    showProtectedZones = showProtectedZones,
                    onShowProtectedZonesChange = { showProtectedZones = it }
                )
                StructuredEditorTab.FORM -> FormTab(
                    config = config,
                    presets = presets,
                    onConfigChange = { config = it }
                )
                StructuredEditorTab.LESSONS -> LessonsTab(
                    config = config,
                    onConfigChange = { config = it }
                )
                StructuredEditorTab.LAYOUT -> LayoutTab(
                    config = config,
                    buildError = buildError,
                    onConfigChange = { config = it }
                )
            }
        }

        Text(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp),
            text = status,
            style = MaterialTheme.typography.bodySmall
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = buildResult != null,
                onClick = {
                    val result = buildResult ?: return@OutlinedButton
                    DesignerDraftHandoff.offer(result.document)
                    onOpenAdvanced()
                }
            ) { Text("Serbest Düzen") }

            Button(
                modifier = Modifier.weight(1f),
                enabled = buildResult != null,
                onClick = {
                    val result = buildResult ?: return@Button
                    status = runCatching {
                        val stored = repository.save(result.document)
                        config = config.copy(version = stored.version)
                        "Kaydedildi · v${stored.version} ✓"
                    }.getOrElse { error ->
                        "Kaydetme hatası: ${error.message ?: error.javaClass.simpleName}"
                    }
                }
            ) { Text("Kaydet") }

            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = buildResult != null && openCvReady,
                onClick = {
                    val result = buildResult ?: return@OutlinedButton
                    val profile = config.pdfProfile()
                    pendingPdf = result.document to profile
                    pdfLauncher.launch(structuredPdfName(config.name, profile.displayName))
                }
            ) { Text("PDF") }
        }
    }
}

@Composable
private fun PreviewTab(
    result: StructuredFormBuildResult?,
    buildError: String?,
    showProtectedZones: Boolean,
    onShowProtectedZonesChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Canlı Form Önizleme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Form artık editör açılır açılmaz burada görünür.", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Güvenli alan", style = MaterialTheme.typography.labelSmall)
                Switch(checked = showProtectedZones, onCheckedChange = onShowProtectedZonesChange)
            }
        }

        if (result != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE9E8ED))
            ) {
                Box(modifier = Modifier.padding(10.dp)) {
                    StructuredFormPreview(
                        result = result,
                        showProtectedZones = showProtectedZones
                    )
                }
            }
            Text(
                "Öğeleri tek tek taşımak veya yeniden boyutlandırmak için alttaki Serbest Düzen düğmesine dokunun.",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Form oluşturulamadı", fontWeight = FontWeight.Bold)
                    Text(buildError ?: "Geçerli bir yerleşim oluşturmak için ayarları düzenleyin.")
                }
            }
        }
    }
}

@Composable
private fun FormTab(
    config: StructuredFormConfig,
    presets: List<com.okulyonetim.optikokuyucu.omr.designer.StructuredFormPreset>,
    onConfigChange: (StructuredFormConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DesignerSectionCard("Hazır Şablonlar") {
            Text("Hazır form seçildiğinde aynı form doğrudan düzenlenebilir; kopya ekran açılmaz.", style = MaterialTheme.typography.bodySmall)
            presets.forEach { preset ->
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onConfigChange(preset.instantiate()) }
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                        Text(preset.displayName)
                        Text(preset.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        DesignerSectionCard("Form Bilgileri") {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = config.name,
                onValueChange = { if (it.isNotBlank()) onConfigChange(config.copy(name = it)) },
                label = { Text("Form adı") },
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = config.title,
                onValueChange = { if (it.isNotBlank()) onConfigChange(config.copy(title = it)) },
                label = { Text("Form başlığı") },
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = config.subtitle,
                onValueChange = { onConfigChange(config.copy(subtitle = it)) },
                label = { Text("Alt başlık") },
                singleLine = true
            )
        }

        DesignerSectionCard("Kağıt") {
            ChoiceButtonRow(
                labels = StructuredPaperSize.entries.map { it.displayName },
                selectedIndex = StructuredPaperSize.entries.indexOf(config.paperSize),
                onSelect = { onConfigChange(config.copy(paperSize = StructuredPaperSize.entries[it])) }
            )
            ChoiceButtonRow(
                labels = StructuredOrientation.entries.map { it.displayName },
                selectedIndex = StructuredOrientation.entries.indexOf(config.orientation),
                onSelect = { onConfigChange(config.copy(orientation = StructuredOrientation.entries[it])) }
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesignerSectionCard("Kitapçık", Modifier.weight(1f)) {
                NumberStepper(
                    value = config.bookletTypeCount,
                    min = 2,
                    max = 8,
                    suffix = " tür",
                    onValueChange = { onConfigChange(config.copy(bookletTypeCount = it)) }
                )
            }
            DesignerSectionCard("Öğrenci No", Modifier.weight(1f)) {
                NumberStepper(
                    value = config.studentNumberDigits,
                    min = 1,
                    max = 12,
                    suffix = " hane",
                    onValueChange = { onConfigChange(config.copy(studentNumberDigits = it)) }
                )
            }
        }

        DesignerSectionCard("Bilgi Alanları") {
            config.infoFields.forEach { field ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Checkbox(
                        checked = field.enabled,
                        onCheckedChange = { checked ->
                            onConfigChange(
                                config.copy(
                                    infoFields = config.infoFields.map {
                                        if (it.id == field.id) it.copy(enabled = checked) else it
                                    }
                                )
                            )
                        }
                    )
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = field.label,
                        onValueChange = { text ->
                            if (text.isNotBlank()) {
                                onConfigChange(
                                    config.copy(
                                        infoFields = config.infoFields.map {
                                            if (it.id == field.id) it.copy(label = text) else it
                                        }
                                    )
                                )
                            }
                        },
                        label = { Text("Alan") },
                        singleLine = true
                    )
                    if (config.infoFields.size > 1) {
                        TextButton(
                            onClick = {
                                onConfigChange(config.copy(infoFields = config.infoFields.filterNot { it.id == field.id }))
                            }
                        ) { Text("Sil") }
                    }
                }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val id = nextUniqueId("info", config.infoFields.map { it.id }.toSet())
                    onConfigChange(config.copy(infoFields = config.infoFields + StructuredInfoField(id, "YENİ ALAN")))
                }
            ) { Text("+ Bilgi alanı ekle") }
        }
    }
}

@Composable
private fun LessonsTab(
    config: StructuredFormConfig,
    onConfigChange: (StructuredFormConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Dersler ve Sorular", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Her ders tek sütun, çok sütun veya otomatik yerleşim kullanabilir.", style = MaterialTheme.typography.bodySmall)

        config.lessons.forEach { lesson ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = lesson.name,
                            onValueChange = { name ->
                                if (name.isNotBlank()) {
                                    onConfigChange(config.copy(lessons = config.lessons.map {
                                        if (it.id == lesson.id) it.copy(name = name) else it
                                    }))
                                }
                            },
                            label = { Text("Ders adı") },
                            singleLine = true
                        )
                        if (config.lessons.size > 1) {
                            TextButton(onClick = {
                                onConfigChange(config.copy(lessons = config.lessons.filterNot { it.id == lesson.id }))
                            }) { Text("Sil") }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Soru", style = MaterialTheme.typography.labelMedium)
                            NumberStepper(
                                value = lesson.questionCount,
                                min = 1,
                                max = 100,
                                suffix = "",
                                onValueChange = { count ->
                                    onConfigChange(config.copy(lessons = config.lessons.map {
                                        if (it.id == lesson.id) it.copy(
                                            questionCount = count,
                                            questionColumns = when {
                                                it.questionColumns == 0 -> 0
                                                it.questionColumns > count -> count
                                                else -> it.questionColumns
                                            }
                                        ) else it
                                    }))
                                }
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Seçenek", style = MaterialTheme.typography.labelMedium)
                            NumberStepper(
                                value = lesson.choices.size,
                                min = 2,
                                max = 6,
                                suffix = "",
                                onValueChange = { count ->
                                    val choices = (0 until count).map { ('A'.code + it).toChar().toString() }
                                    onConfigChange(config.copy(lessons = config.lessons.map {
                                        if (it.id == lesson.id) it.copy(choices = choices) else it
                                    }))
                                }
                            )
                        }
                    }

                    Text("Soru sütunları", style = MaterialTheme.typography.labelMedium)
                    val labels = if (lesson.questionCount > 1) listOf("Otomatik", "Tek", "Çok") else listOf("Otomatik", "Tek")
                    ChoiceButtonRow(
                        labels = labels,
                        selectedIndex = when {
                            lesson.questionColumns == 0 -> 0
                            lesson.questionColumns == 1 -> 1
                            else -> 2
                        }.coerceAtMost(labels.lastIndex),
                        onSelect = { index ->
                            val columns = when (index) {
                                0 -> 0
                                1 -> 1
                                else -> maxOf(2, lesson.questionColumns).coerceAtMost(minOf(8, lesson.questionCount))
                            }
                            onConfigChange(config.copy(lessons = config.lessons.map {
                                if (it.id == lesson.id) it.copy(questionColumns = columns) else it
                            }))
                        }
                    )
                    if (lesson.questionColumns >= 2) {
                        NumberStepper(
                            value = lesson.questionColumns,
                            min = 2,
                            max = minOf(8, lesson.questionCount),
                            suffix = " sütun",
                            onValueChange = { columns ->
                                onConfigChange(config.copy(lessons = config.lessons.map {
                                    if (it.id == lesson.id) it.copy(questionColumns = columns) else it
                                }))
                            }
                        )
                    }

                    Text("Ders başlığı hizası", style = MaterialTheme.typography.labelMedium)
                    ChoiceButtonRow(
                        labels = listOf("Sol", "Orta", "Sağ"),
                        selectedIndex = when (lesson.titleAlignment) {
                            DesignerTextAlignment.START -> 0
                            DesignerTextAlignment.CENTER -> 1
                            DesignerTextAlignment.END -> 2
                        },
                        onSelect = { index ->
                            val alignment = when (index) {
                                0 -> DesignerTextAlignment.START
                                1 -> DesignerTextAlignment.CENTER
                                else -> DesignerTextAlignment.END
                            }
                            onConfigChange(config.copy(lessons = config.lessons.map {
                                if (it.id == lesson.id) it.copy(titleAlignment = alignment) else it
                            }))
                        }
                    )
                }
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = config.lessons.size < 12,
            onClick = {
                val id = nextUniqueId("ders", config.lessons.map { it.id }.toSet())
                onConfigChange(config.copy(lessons = config.lessons + StructuredLesson(id, "Yeni Ders", 10)))
            }
        ) { Text("+ Ders ekle") }
    }
}

@Composable
private fun LayoutTab(
    config: StructuredFormConfig,
    buildError: String?,
    onConfigChange: (StructuredFormConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DesignerSectionCard("Kağıt Alanını Kullan") {
            Text("Kenar boşlukları daraltılabilir; gerçek OMR güvenlik denetimi kaydetme sırasında yine çalışır.", style = MaterialTheme.typography.bodySmall)
            Text("Kenar boşluğu", style = MaterialTheme.typography.labelMedium)
            NumberStepper(
                value = config.pageMargin.toInt(),
                min = 44,
                max = 120,
                step = 4,
                suffix = " birim",
                onValueChange = { onConfigChange(config.copy(pageMargin = it.toDouble())) }
            )
            Text("Köşe kareleri", style = MaterialTheme.typography.labelMedium)
            NumberStepper(
                value = config.markerSize.toInt(),
                min = 44,
                max = 70,
                step = 2,
                suffix = " birim",
                onValueChange = { onConfigChange(config.copy(markerSize = it.toDouble())) }
            )
            Text("Marker kenar mesafesi", style = MaterialTheme.typography.labelMedium)
            NumberStepper(
                value = config.markerInset.toInt(),
                min = 16,
                max = 60,
                step = 2,
                suffix = " birim",
                onValueChange = { onConfigChange(config.copy(markerInset = it.toDouble())) }
            )
            Text("Editör güvenli alan tamponu", style = MaterialTheme.typography.labelMedium)
            NumberStepper(
                value = (config.protectedPaddingRatio * 1000.0).toInt(),
                min = 10,
                max = 30,
                suffix = "‰",
                onValueChange = { onConfigChange(config.copy(protectedPaddingRatio = it / 1000.0)) }
            )
        }

        DesignerSectionCard("Metin Stili") {
            Text("Bilgi alanı yazısı · ${config.infoTextStyle.fontSize.toInt()}")
            Slider(
                value = config.infoTextStyle.fontSize.toFloat(),
                onValueChange = { onConfigChange(config.copy(infoTextStyle = config.infoTextStyle.copy(fontSize = it.toDouble()))) },
                valueRange = 10f..28f
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bilgi alanları kalın")
                Switch(
                    checked = config.infoTextStyle.bold,
                    onCheckedChange = { onConfigChange(config.copy(infoTextStyle = config.infoTextStyle.copy(bold = it))) }
                )
            }
            Text("Bilgi alanı hizası")
            ChoiceButtonRow(
                labels = listOf("Sol", "Orta", "Sağ"),
                selectedIndex = when (config.infoTextStyle.alignment) {
                    DesignerTextAlignment.START -> 0
                    DesignerTextAlignment.CENTER -> 1
                    DesignerTextAlignment.END -> 2
                },
                onSelect = { index ->
                    val alignment = when (index) {
                        0 -> DesignerTextAlignment.START
                        1 -> DesignerTextAlignment.CENTER
                        else -> DesignerTextAlignment.END
                    }
                    onConfigChange(config.copy(infoTextStyle = config.infoTextStyle.copy(alignment = alignment)))
                }
            )
            Text("Ana başlık · ${config.titleTextStyle.fontSize.toInt()}")
            Slider(
                value = config.titleTextStyle.fontSize.toFloat(),
                onValueChange = { onConfigChange(config.copy(titleTextStyle = config.titleTextStyle.copy(fontSize = it.toDouble()))) },
                valueRange = 18f..44f
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (buildError == null) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("OMR Güvenliği", fontWeight = FontWeight.Bold)
                Text("Serbest yerleşimde bile baloncuk ve marker çakışmaları okunabilirlik kapısından geçmeden kaydedilemez.", style = MaterialTheme.typography.bodySmall)
                buildError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun DesignerSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ChoiceButtonRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        labels.forEachIndexed { index, label ->
            if (index == selectedIndex) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(index) }
                ) { Text(label) }
            } else {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(index) }
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun NumberStepper(
    value: Int,
    min: Int,
    max: Int,
    suffix: String,
    step: Int = 1,
    onValueChange: (Int) -> Unit
) {
    require(step > 0)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            enabled = value > min,
            onClick = { onValueChange((value - step).coerceAtLeast(min)) }
        ) { Text("−") }
        Text("$value$suffix", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            enabled = value < max,
            onClick = { onValueChange((value + step).coerceAtMost(max)) }
        ) { Text("+") }
    }
}

@Composable
private fun StructuredFormPreview(
    result: StructuredFormBuildResult,
    showProtectedZones: Boolean
) {
    val document = result.document
    val template = remember(document) { DesignerTemplateCompiler.compile(document) }
    val aspect = (template.space.width / template.space.height).toFloat()
    val omrColor = Color(0xFF7B4DFF)
    val safeFill = Color(0xFFFFD9E8).copy(alpha = 0.26f)
    val safeStroke = Color(0xFFE83E8C).copy(alpha = 0.72f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .background(Color.White, RoundedCornerShape(5.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sx = size.width / template.space.width.toFloat()
            val sy = size.height / template.space.height.toFloat()
            val avg = (sx + sy) / 2f
            fun x(v: Double) = v.toFloat() * sx
            fun y(v: Double) = v.toFloat() * sy
            fun r(v: Double) = v.toFloat() * avg

            if (showProtectedZones) {
                result.protectedZones.forEach { zone ->
                    drawRect(
                        color = safeFill,
                        topLeft = Offset(x(zone.left), y(zone.top)),
                        size = Size(x(zone.width), y(zone.height))
                    )
                    drawRect(
                        color = safeStroke,
                        topLeft = Offset(x(zone.left), y(zone.top)),
                        size = Size(x(zone.width), y(zone.height)),
                        style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
                    )
                }
            }

            document.visualElements.forEach { element ->
                when (element) {
                    is DesignerTextElement -> drawIntoCanvas { canvas ->
                        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.BLACK
                            textSize = (element.fontSize.toFloat() * avg).coerceAtLeast(6f)
                            typeface = Typeface.create(Typeface.DEFAULT, if (element.bold) Typeface.BOLD else Typeface.NORMAL)
                            textAlign = when (element.alignment) {
                                DesignerTextAlignment.START -> AndroidPaint.Align.LEFT
                                DesignerTextAlignment.CENTER -> AndroidPaint.Align.CENTER
                                DesignerTextAlignment.END -> AndroidPaint.Align.RIGHT
                            }
                        }
                        val tx = when (element.alignment) {
                            DesignerTextAlignment.START -> x(element.bounds.left)
                            DesignerTextAlignment.CENTER -> x(element.bounds.center.x)
                            DesignerTextAlignment.END -> x(element.bounds.right)
                        }
                        canvas.nativeCanvas.drawText(element.text, tx, y(element.bounds.top) + paint.textSize, paint)
                    }
                    is DesignerBoxElement -> drawRect(
                        color = Color.Black,
                        topLeft = Offset(x(element.bounds.left), y(element.bounds.top)),
                        size = Size(x(element.bounds.width), y(element.bounds.height)),
                        style = Stroke(width = (element.strokeWidth.toFloat() * avg).coerceAtLeast(1f))
                    )
                    is DesignerLineElement -> drawLine(
                        color = Color.Black,
                        start = Offset(x(element.start.x), y(element.start.y)),
                        end = Offset(x(element.end.x), y(element.end.y)),
                        strokeWidth = (element.strokeWidth.toFloat() * avg).coerceAtLeast(1f)
                    )
                }
            }

            template.fiducials.forEach { marker ->
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(x(marker.bounds.left), y(marker.bounds.top)),
                    size = Size(x(marker.bounds.width), y(marker.bounds.height))
                )
            }

            template.bubbleRows.forEach { row ->
                val number = row.id.substringAfterLast(':')
                row.bubbles.forEachIndexed { index, bubble ->
                    drawCircle(
                        color = omrColor,
                        radius = r(bubble.radius),
                        center = Offset(x(bubble.center.x), y(bubble.center.y)),
                        style = Stroke(width = 1.2f)
                    )
                    drawPreviewText(
                        bubble.id,
                        x(bubble.center.x),
                        y(bubble.center.y) + r(bubble.radius) * 0.30f,
                        (r(bubble.radius) * 0.82f).coerceAtLeast(5f),
                        omrColor
                    )
                    if (index == 0) {
                        drawPreviewText(
                            number,
                            x(bubble.center.x) - r(bubble.radius) * 2.0f,
                            y(bubble.center.y) + r(bubble.radius) * 0.32f,
                            (r(bubble.radius) * 0.92f).coerceAtLeast(5f),
                            omrColor,
                            AndroidPaint.Align.RIGHT
                        )
                    }
                }
            }

            template.markGrids.forEach { grid ->
                grid.columns.forEach { column ->
                    column.marks.forEach { mark ->
                        drawCircle(
                            color = omrColor,
                            radius = r(mark.radius),
                            center = Offset(x(mark.center.x), y(mark.center.y)),
                            style = Stroke(width = 1.2f)
                        )
                        drawPreviewText(
                            mark.id,
                            x(mark.center.x),
                            y(mark.center.y) + r(mark.radius) * 0.30f,
                            (r(mark.radius) * 0.78f).coerceAtLeast(5f),
                            omrColor
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewText(
    text: String,
    x: Float,
    y: Float,
    textSize: Float,
    color: Color,
    align: AndroidPaint.Align = AndroidPaint.Align.CENTER
) {
    drawIntoCanvas { canvas ->
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgbCompat()
            this.textSize = textSize
            textAlign = align
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.nativeCanvas.drawText(text, x, y, paint)
    }
}

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt().coerceIn(0, 255),
    (red * 255).toInt().coerceIn(0, 255),
    (green * 255).toInt().coerceIn(0, 255),
    (blue * 255).toInt().coerceIn(0, 255)
)

private fun nextUniqueId(prefix: String, existing: Set<String>): String {
    var index = 1
    var candidate = "$prefix-$index"
    while (candidate in existing) {
        index += 1
        candidate = "$prefix-$index"
    }
    return candidate
}

private fun structuredPdfName(name: String, profile: String): String {
    val safe = name.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").trim('_').ifBlank { "optik-form" }
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val profileSafe = profile.replace(' ', '-')
    return "$safe-$profileSafe-$timestamp.pdf"
}
