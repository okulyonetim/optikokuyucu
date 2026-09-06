package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.DesignerAreaCatalog
import com.okulyonetim.optikokuyucu.omr.designer.DesignerAreaKind
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerExamMode
import com.okulyonetim.optikokuyucu.omr.designer.DesignerExamPreset
import com.okulyonetim.optikokuyucu.omr.designer.DesignerFormSpec
import com.okulyonetim.optikokuyucu.omr.designer.DesignerImageElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPageGeometry
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPageOrientation
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPaperSize
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextElement
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.designer.NumericGridComponent
import com.okulyonetim.optikokuyucu.omr.designer.QuestionGroupComponent
import kotlin.math.roundToInt

@Suppress("UNUSED_PARAMETER")
@Composable
fun StructuredOmrDesignerScreen(openCvReady: Boolean, onBack: () -> Unit, onOpenAdvanced: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) { FileDesignerDocumentRepository(context.applicationContext) }
    var document by remember {
        mutableStateOf(
            DesignerPageGeometry.apply(
                DesignerDocument(
                    id = "form-${System.currentTimeMillis()}",
                    version = 1,
                    name = "Yeni Optik Form",
                    formSpec = DesignerFormSpec()
                )
            )
        )
    }
    var formName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var showAreaPicker by remember { mutableStateOf(false) }
    var numberDraft by remember { mutableStateOf<NumericGridComponent?>(null) }
    var numberPatternText by remember { mutableStateOf("") }
    var answerDraft by remember { mutableStateOf<QuestionGroupComponent?>(null) }
    var answerPatternText by remember { mutableStateOf("") }
    var descriptionDraft by remember { mutableStateOf<DesignerTextElement?>(null) }
    var imageEditorOpen by remember { mutableStateOf(false) }
    var imageDraft by remember { mutableStateOf<DesignerImageElement?>(null) }

    fun saveDocument() {
        val name = formName.trim()
        if (name.isBlank()) {
            status = "Form adı zorunludur."
            return
        }
        status = runCatching {
            val stored = repository.save(document.copy(name = name))
            document = stored
            formName = stored.name
            "Kaydedildi · v${stored.version}"
        }.getOrElse { "Kaydetme hatası: ${it.message ?: it.javaClass.simpleName}" }
    }

    numberDraft?.let { draft ->
        NumberAreaEditorScreen(
            document, draft, numberPatternText,
            { numberDraft = it },
            { text ->
                numberPatternText = text
                DesignerAreaCatalog.parseNumberPattern(text)?.let { numberDraft = numberDraft?.copy(values = it) }
            },
            { numberDraft = null; numberPatternText = "" },
            { completed ->
                document = document.copy(components = document.components + completed)
                numberDraft = null; numberPatternText = ""; status = "Numara alanı eklendi."
            }
        )
        return
    }
    answerDraft?.let { draft ->
        AnswerAreaEditorScreen(
            document, draft, answerPatternText,
            { answerDraft = it },
            { text ->
                answerPatternText = text
                DesignerAreaCatalog.parseAnswerPattern(text)?.let { answerDraft = answerDraft?.copy(choices = it) }
            },
            { answerDraft = null; answerPatternText = "" },
            { completed ->
                document = document.copy(components = document.components + completed)
                answerDraft = null; answerPatternText = ""; status = "Cevaplar alanı eklendi."
            }
        )
        return
    }
    descriptionDraft?.let { draft ->
        DescriptionAreaEditorScreen(
            document = document,
            draft = draft,
            onDraftChange = { descriptionDraft = it },
            onCancel = { descriptionDraft = null },
            onComplete = { completed ->
                document = document.copy(visualElements = document.visualElements + completed)
                descriptionDraft = null
                status = "Açıklama alanı eklendi."
            }
        )
        return
    }
    if (imageEditorOpen) {
        ImageAreaEditorScreen(
            document = document,
            draft = imageDraft,
            onDraftChange = { imageDraft = it },
            onCancel = { imageEditorOpen = false; imageDraft = null },
            onComplete = { completed ->
                document = document.copy(visualElements = document.visualElements + completed)
                imageEditorOpen = false; imageDraft = null; status = "Resim alanı eklendi."
            }
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
    ) {
        EditorTopBar(onBack, ::saveDocument)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FormInformationCard(
                formName = formName,
                onFormNameChange = { formName = it; if (status == "Form adı zorunludur.") status = "" },
                formSpec = document.formSpec,
                onExamModeChange = { document = document.copy(formSpec = document.formSpec.copy(examMode = it)) },
                onExamPresetChange = { document = document.copy(formSpec = document.formSpec.copy(examPreset = it)) },
                onPaperSizeChange = { document = DesignerPageGeometry.apply(document, paperSize = it) },
                onOrientationChange = { document = DesignerPageGeometry.apply(document, orientation = it) }
            )
            OpticalFormAreaHeader { status = ""; showAreaPicker = true }
            PaperWorkspace(document)
            if (status.isNotBlank()) {
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    color = if (status.startsWith("Kaydedildi") || status.endsWith("eklendi.")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.size(8.dp))
        }
    }

    if (showAreaPicker) {
        OpticalFormAreaPicker(
            onDismiss = { showAreaPicker = false },
            onSelected = { kind ->
                showAreaPicker = false
                when (kind) {
                    DesignerAreaKind.NUMBER -> DesignerAreaCatalog.createNumberArea(document).also {
                        numberDraft = it; numberPatternText = DesignerAreaCatalog.numberPatternText(it.values)
                    }
                    DesignerAreaKind.ANSWERS -> DesignerAreaCatalog.createAnswerArea(document).also {
                        answerDraft = it; answerPatternText = DesignerAreaCatalog.answerPatternText(it.choices)
                    }
                    DesignerAreaKind.DESCRIPTION -> descriptionDraft = DesignerAreaCatalog.createDescriptionArea(document)
                    DesignerAreaKind.IMAGE -> { imageDraft = null; imageEditorOpen = true }
                }
            }
        )
    }
}

@Composable
private fun EditorTopBar(onBack: () -> Unit, onSave: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary) {
        Row(modifier = Modifier.fillMaxWidth().padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("×", style = MaterialTheme.typography.titleLarge) }
            Text("Yeni Optik Form", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            TextButton(onClick = onSave, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("Kaydet") }
        }
    }
}

@Composable
private fun FormInformationCard(
    formName: String,
    onFormNameChange: (String) -> Unit,
    formSpec: DesignerFormSpec,
    onExamModeChange: (DesignerExamMode) -> Unit,
    onExamPresetChange: (DesignerExamPreset) -> Unit,
    onPaperSizeChange: (DesignerPaperSize) -> Unit,
    onOrientationChange: (DesignerPageOrientation) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Optik Form Bilgileri", style = MaterialTheme.typography.labelLarge)
            Text("* Zorunlu Alanlar", modifier = Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            OutlinedTextField(
                value = formName,
                onValueChange = onFormNameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ad *") },
                singleLine = true,
                shape = RoundedCornerShape(50.dp)
            )
            DropdownField(
                "Sınav Türü",
                if (formSpec.examMode == DesignerExamMode.UNSPECIFIED) "Seçiniz" else formSpec.examMode.displayName,
                listOf(DesignerExamMode.SINGLE_LESSON, DesignerExamMode.MULTI_LESSON),
                { it.displayName }, onExamModeChange
            )
            if (formSpec.examMode != DesignerExamMode.UNSPECIFIED) {
                DropdownField(
                    "Deneme Türü", formSpec.examPreset.displayName,
                    listOf(DesignerExamPreset.CUSTOM, DesignerExamPreset.LGS, DesignerExamPreset.TYT, DesignerExamPreset.AYT, DesignerExamPreset.YDT, DesignerExamPreset.ALES, DesignerExamPreset.DGS, DesignerExamPreset.KPSS, DesignerExamPreset.TUS, DesignerExamPreset.SCHOLARSHIP),
                    { it.displayName }, onExamPresetChange
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField(
                    "Kağıt", formSpec.paperSize.displayName,
                    listOf(DesignerPaperSize.A3, DesignerPaperSize.A4, DesignerPaperSize.A5, DesignerPaperSize.A6, DesignerPaperSize.A7),
                    { it.displayName }, onPaperSizeChange, Modifier.weight(1f)
                )
                DropdownField(
                    "Yön", formSpec.orientation.displayName,
                    listOf(DesignerPageOrientation.PORTRAIT, DesignerPageOrientation.LANDSCAPE),
                    { it.displayName }, onOrientationChange, Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun <T> DropdownField(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelSmall)
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                shape = RoundedCornerShape(50.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(value, modifier = Modifier.weight(1f), maxLines = 1); Text("⌄")
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { expanded = false; onSelected(option) }) }
        }
    }
}

@Composable
private fun OpticalFormAreaHeader(onAdd: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Optik Form Alanı", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        Surface(modifier = Modifier.size(28.dp).clickable(onClick = onAdd), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(contentAlignment = Alignment.Center) { Text("+", fontWeight = FontWeight.Bold) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpticalFormAreaPicker(onDismiss: () -> Unit, onSelected: (DesignerAreaKind) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Optik Form Alanı", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            DesignerAreaCatalog.sections.forEach { section ->
                Text(section.title, modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                section.kinds.forEach { kind ->
                    Surface(modifier = Modifier.fillMaxWidth().clickable { onSelected(kind) }, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(modifier = Modifier.size(38.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { Box(contentAlignment = Alignment.Center) { Text(areaKindSymbol(kind), fontWeight = FontWeight.Bold) } }
                            Text(kind.displayName, modifier = Modifier.weight(1f)); Text("›")
                        }
                    }
                }
            }
            Spacer(Modifier.size(18.dp))
        }
    }
}

private fun areaKindSymbol(kind: DesignerAreaKind): String = when (kind) {
    DesignerAreaKind.NUMBER -> "123"
    DesignerAreaKind.ANSWERS -> "AB"
    DesignerAreaKind.DESCRIPTION -> "T"
    DesignerAreaKind.IMAGE -> "▧"
}

@Composable
private fun PaperWorkspace(document: DesignerDocument) {
    val dimensions = DesignerPageGeometry.dimensions(document.formSpec.paperSize)
    val physical = dimensions?.let {
        val w = if (document.formSpec.orientation == DesignerPageOrientation.PORTRAIT) it.widthMm else it.heightMm
        val h = if (document.formSpec.orientation == DesignerPageOrientation.PORTRAIT) it.heightMm else it.widthMm
        "${formatMillimetres(w)} × ${formatMillimetres(h)} mm"
    } ?: "Özel ölçü"
    val safe = remember(document.space) { DesignerPageGeometry.safeArea(document.space) }
    val compiled = remember(document) { DesignerTemplateCompiler.compile(document) }
    val rows = remember(compiled) { compiled.bubbleRows.associateBy { it.id } }
    val images = rememberDesignerImageBitmaps(document.visualElements)
    val safeFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.035f)
    val safeStroke = MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
    val minor = Color(0xFFE8EBF2)
    val major = Color(0xFFD4DAE6)
    val omr = Color(0xFFB54848)

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${document.formSpec.paperSize.displayName} · $physical", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(document.formSpec.orientation.displayName, style = MaterialTheme.typography.labelSmall)
            }
            Text("Canonical ${document.space.width.roundToInt()} × ${document.space.height.roundToInt()} · Grid 50 birim", style = MaterialTheme.typography.labelSmall)
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(document.space.aspectRatio.toFloat()).background(Color.White, RoundedCornerShape(6.dp))) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val sx = size.width / document.space.width.toFloat(); val sy = size.height / document.space.height.toFloat()
                    var gx = 50.0; var xi = 1
                    while (gx < document.space.width) {
                        drawLine(if (xi % 2 == 0) major else minor, Offset(gx.toFloat() * sx, 0f), Offset(gx.toFloat() * sx, size.height), if (xi % 2 == 0) 1.15f else 0.7f)
                        gx += 50.0; xi++
                    }
                    var gy = 50.0; var yi = 1
                    while (gy < document.space.height) {
                        drawLine(if (yi % 2 == 0) major else minor, Offset(0f, gy.toFloat() * sy), Offset(size.width, gy.toFloat() * sy), if (yi % 2 == 0) 1.15f else 0.7f)
                        gy += 50.0; yi++
                    }
                    drawRect(safeFill, Offset(safe.left.toFloat() * sx, safe.top.toFloat() * sy), Size(safe.width.toFloat() * sx, safe.height.toFloat() * sy))
                    drawRect(safeStroke, Offset(safe.left.toFloat() * sx, safe.top.toFloat() * sy), Size(safe.width.toFloat() * sx, safe.height.toFloat() * sy), style = Stroke(1.3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))))
                    drawDesignerVisualElements(document.visualElements, images, sx, sy)
                    document.fiducials.forEach { marker -> drawRect(Color.Black, Offset(marker.bounds.left.toFloat() * sx, marker.bounds.top.toFloat() * sy), Size(marker.bounds.width.toFloat() * sx, marker.bounds.height.toFloat() * sy)) }
                    document.components.filterIsInstance<QuestionGroupComponent>().forEach { drawAnswerGroup(it, rows, document.formSpec.answerAppearance, sx, sy, omr) }
                    document.components.filterIsInstance<NumericGridComponent>().forEach { component -> compiled.markGrids.firstOrNull { it.id == component.id }?.let { drawNumberGrid(component, it, sx, sy, omr) } }
                    drawRect(Color(0xFFBFC5D0), style = Stroke(1.2f))
                }
            }
        }
    }
}

private fun formatMillimetres(value: Double): String = if (value == value.roundToInt().toDouble()) value.roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", value)
