package com.okulyonetim.optikokuyucu.ui

import android.graphics.Paint as AndroidPaint
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.DesignerAreaCatalog
import com.okulyonetim.optikokuyucu.omr.designer.DesignerAreaKind
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerExamMode
import com.okulyonetim.optikokuyucu.omr.designer.DesignerExamPreset
import com.okulyonetim.optikokuyucu.omr.designer.DesignerFormSpec
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPageGeometry
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPageOrientation
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPaperSize
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.designer.NumericGridComponent
import com.okulyonetim.optikokuyucu.omr.designer.NumericGridOrientation
import com.okulyonetim.optikokuyucu.omr.template.MarkGridSpec
import kotlin.math.roundToInt

/**
 * Friendly optical-form entry screen.
 *
 * DesignerDocument remains the single source of truth. Paper/orientation changes update the same
 * canonical document space and fiducials that field editing and recognition consume.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun StructuredOmrDesignerScreen(
    openCvReady: Boolean,
    onBack: () -> Unit,
    onOpenAdvanced: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) { FileDesignerDocumentRepository(context.applicationContext) }
    var document by remember {
        val base = DesignerDocument(
            id = "form-${System.currentTimeMillis()}",
            version = 1,
            name = "Yeni Optik Form",
            components = emptyList(),
            visualElements = emptyList(),
            formSpec = DesignerFormSpec()
        )
        mutableStateOf(DesignerPageGeometry.apply(base))
    }
    var formName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var showAreaPicker by remember { mutableStateOf(false) }
    var numberDraft by remember { mutableStateOf<NumericGridComponent?>(null) }
    var numberPatternText by remember { mutableStateOf("") }

    fun updateFormSpec(transform: (DesignerFormSpec) -> DesignerFormSpec) {
        document = document.copy(formSpec = transform(document.formSpec))
    }

    fun saveDocument() {
        val normalizedName = formName.trim()
        if (normalizedName.isBlank()) {
            status = "Form adı zorunludur."
            return
        }
        status = runCatching {
            val stored = repository.save(document.copy(name = normalizedName))
            document = stored
            formName = stored.name
            "Kaydedildi · v${stored.version}"
        }.getOrElse { error ->
            "Kaydetme hatası: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    val activeNumberDraft = numberDraft
    if (activeNumberDraft != null) {
        NumberAreaEditor(
            document = document,
            draft = activeNumberDraft,
            patternText = numberPatternText,
            onDraftChange = { numberDraft = it },
            onPatternTextChange = { text ->
                numberPatternText = text
                DesignerAreaCatalog.parseNumberPattern(text)?.let { values ->
                    numberDraft = numberDraft?.copy(values = values)
                }
            },
            onCancel = {
                numberDraft = null
                numberPatternText = ""
            },
            onComplete = { completed ->
                document = document.copy(components = document.components + completed)
                numberDraft = null
                numberPatternText = ""
                status = "Numara alanı eklendi."
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
    ) {
        ReferenceEditorTopBar(
            onBack = onBack,
            onSave = ::saveDocument
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FormInformationCard(
                formName = formName,
                onFormNameChange = {
                    formName = it
                    if (status == "Form adı zorunludur.") status = ""
                },
                formSpec = document.formSpec,
                onExamModeChange = { selected ->
                    updateFormSpec { it.copy(examMode = selected) }
                },
                onExamPresetChange = { selected ->
                    updateFormSpec { it.copy(examPreset = selected) }
                },
                onPaperSizeChange = { selected ->
                    document = DesignerPageGeometry.apply(document, paperSize = selected)
                },
                onOrientationChange = { selected ->
                    document = DesignerPageGeometry.apply(document, orientation = selected)
                }
            )

            OpticalFormAreaHeader(
                onAdd = {
                    status = ""
                    showAreaPicker = true
                }
            )

            PaperWorkspace(document)

            if (status.isNotBlank()) {
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    color = if (status.startsWith("Kaydedildi") || status.endsWith("eklendi.")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.size(8.dp))
        }
    }

    if (showAreaPicker) {
        OpticalFormAreaPicker(
            onDismiss = { showAreaPicker = false },
            onSelected = { selected ->
                showAreaPicker = false
                when (selected) {
                    DesignerAreaKind.NUMBER -> {
                        val draft = DesignerAreaCatalog.createNumberArea(document)
                        numberDraft = draft
                        numberPatternText = DesignerAreaCatalog.numberPatternText(draft.values)
                    }
                    DesignerAreaKind.ANSWERS -> status = "Cevaplar alanı Aşama 6'da etkinleştirilecek."
                    DesignerAreaKind.DESCRIPTION,
                    DesignerAreaKind.IMAGE -> status = "${selected.displayName} alanı bilgi alanları aşamasında etkinleştirilecek."
                }
            }
        )
    }
}

@Composable
private fun ReferenceEditorTopBar(
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text("×", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "Yeni Optik Form",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            TextButton(
                onClick = onSave,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text("Kaydet", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun AreaEditorTopBar(
    title: String,
    completeEnabled: Boolean,
    onCancel: () -> Unit,
    onComplete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text("×", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            TextButton(
                enabled = completeEnabled,
                onClick = onComplete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text("Tamam", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun NumberAreaEditor(
    document: DesignerDocument,
    draft: NumericGridComponent,
    patternText: String,
    onDraftChange: (NumericGridComponent) -> Unit,
    onPatternTextChange: (String) -> Unit,
    onCancel: () -> Unit,
    onComplete: (NumericGridComponent) -> Unit
) {
    val parsedPattern = DesignerAreaCatalog.parseNumberPattern(patternText)
    val effectiveDraft = if (parsedPattern == null) draft else draft.copy(values = parsedPattern)
    val patternIssue = if (parsedPattern == null) {
        "Desen en az 2 benzersiz değer içermelidir. Virgülle özel değer listesi girebilirsiniz."
    } else {
        null
    }
    val geometryIssue = DesignerAreaCatalog.numberAreaIssue(document, effectiveDraft)
    val issue = patternIssue ?: geometryIssue

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
    ) {
        AreaEditorTopBar(
            title = "Yeni Optik Form Alanı",
            completeEnabled = issue == null,
            onCancel = onCancel,
            onComplete = { onComplete(effectiveDraft) }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Optik Form Alanı Bilgileri",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ReadOnlyEditorField(label = "Tür", value = "Numara")

                    Text(
                        text = "Yön",
                        modifier = Modifier.padding(start = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EditorChoiceButton(
                            modifier = Modifier.weight(1f),
                            label = "Yatay",
                            selected = draft.orientation == NumericGridOrientation.DIGITS_HORIZONTAL,
                            onClick = {
                                onDraftChange(draft.copy(orientation = NumericGridOrientation.DIGITS_HORIZONTAL))
                            }
                        )
                        EditorChoiceButton(
                            modifier = Modifier.weight(1f),
                            label = "Dikey",
                            selected = draft.orientation == NumericGridOrientation.DIGITS_VERTICAL,
                            onClick = {
                                onDraftChange(draft.copy(orientation = NumericGridOrientation.DIGITS_VERTICAL))
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Etiketi Gizle", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Kapalıyken etiket form üzerinde görünür.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = !draft.showLabel,
                            onCheckedChange = { hidden ->
                                onDraftChange(draft.copy(showLabel = !hidden))
                            }
                        )
                    }

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = draft.label,
                        onValueChange = { text ->
                            if ('\n' !in text && '\r' !in text && text.length <= 60) {
                                onDraftChange(draft.copy(label = text))
                            }
                        },
                        label = { Text("Etiket") },
                        enabled = draft.showLabel,
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = patternText,
                        onValueChange = onPatternTextChange,
                        label = { Text("Desen") },
                        supportingText = {
                            Text("Örn. 0123456789, ABCD veya 01,02,03")
                        },
                        isError = patternIssue != null,
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )

                    Text("Hazır desenler", style = MaterialTheme.typography.labelMedium)
                    NumberPatternPresetRows(
                        selectedText = patternText,
                        onSelected = onPatternTextChange
                    )

                    EditorNumberInput(
                        label = "Sol Boşluk",
                        value = draft.startX,
                        min = 0.0,
                        max = document.space.width,
                        step = 5.0,
                        onValueChange = { onDraftChange(draft.copy(startX = it)) }
                    )
                    EditorNumberInput(
                        label = "Üst Boşluk",
                        value = draft.topY,
                        min = 0.0,
                        max = document.space.height,
                        step = 5.0,
                        onValueChange = { onDraftChange(draft.copy(topY = it)) }
                    )
                    EditorIntegerInput(
                        label = "Veri / Hane Sayısı",
                        value = draft.digits,
                        min = 1,
                        max = 16,
                        onValueChange = { onDraftChange(draft.copy(digits = it)) }
                    )
                    EditorNumberInput(
                        label = "Baloncuk Boyutu",
                        value = draft.bubbleRadius,
                        min = 6.0,
                        max = 25.0,
                        step = 0.5,
                        onValueChange = { onDraftChange(draft.copy(bubbleRadius = it)) }
                    )
                    EditorNumberInput(
                        label = "Hane Aralığı",
                        value = draft.columnGap,
                        min = 16.0,
                        max = 180.0,
                        step = 2.0,
                        onValueChange = { onDraftChange(draft.copy(columnGap = it)) }
                    )
                    EditorNumberInput(
                        label = "Değer Aralığı",
                        value = draft.rowGap,
                        min = 16.0,
                        max = 180.0,
                        step = 2.0,
                        onValueChange = { onDraftChange(draft.copy(rowGap = it)) }
                    )
                }
            }

            if (issue != null) {
                Text(
                    text = issue,
                    modifier = Modifier.padding(horizontal = 6.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            NumberAreaLivePreview(document = document, component = effectiveDraft)
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun ReadOnlyEditorField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ) {
            Text(
                text = value,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun EditorChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        FilledTonalButton(modifier = modifier, onClick = onClick) { Text("$label ✓") }
    } else {
        OutlinedButton(modifier = modifier, onClick = onClick) { Text(label) }
    }
}

@Composable
private fun NumberPatternPresetRows(
    selectedText: String,
    onSelected: (String) -> Unit
) {
    val presets = DesignerAreaCatalog.numberPatternPresets
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.take(3).forEach { preset ->
                EditorChoiceButton(
                    modifier = Modifier.weight(1f),
                    label = preset,
                    selected = selectedText == preset,
                    onClick = { onSelected(preset) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.drop(3).forEach { preset ->
                EditorChoiceButton(
                    modifier = Modifier.weight(1f),
                    label = preset,
                    selected = selectedText == preset,
                    onClick = { onSelected(preset) }
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun EditorNumberInput(
    label: String,
    value: Double,
    min: Double,
    max: Double,
    step: Double,
    onValueChange: (Double) -> Unit
) {
    var text by remember(value) { mutableStateOf(formatEditorNumber(value)) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = text,
            onValueChange = { input ->
                text = input
                input.replace(',', '.').toDoubleOrNull()?.let { parsed ->
                    if (parsed in min..max) onValueChange(parsed)
                }
            },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(14.dp)
        )
        OutlinedButton(
            enabled = value - step >= min,
            onClick = { onValueChange((value - step).coerceAtLeast(min)) }
        ) { Text("−") }
        OutlinedButton(
            enabled = value + step <= max,
            onClick = { onValueChange((value + step).coerceAtMost(max)) }
        ) { Text("+") }
    }
}

@Composable
private fun EditorIntegerInput(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = text,
            onValueChange = { input ->
                text = input
                input.toIntOrNull()?.let { parsed ->
                    if (parsed in min..max) onValueChange(parsed)
                }
            },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(14.dp)
        )
        OutlinedButton(enabled = value > min, onClick = { onValueChange(value - 1) }) { Text("−") }
        OutlinedButton(enabled = value < max, onClick = { onValueChange(value + 1) }) { Text("+") }
    }
}

@Composable
private fun NumberAreaLivePreview(
    document: DesignerDocument,
    component: NumericGridComponent
) {
    val previewDocument = remember(document.space, document.fiducials, component) {
        document.copy(components = listOf(component), visualElements = emptyList())
    }
    val template = remember(previewDocument) { DesignerTemplateCompiler.compile(previewDocument) }
    val safeArea = remember(document.space) { DesignerPageGeometry.safeArea(document.space) }
    val bubbleColor = Color(0xFFB54848)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text("Canlı Önizleme", style = MaterialTheme.typography.labelLarge)
            Text(
                "Önizleme okuyucuyla aynı canonical baloncuk geometrisini kullanır.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio((document.space.width / document.space.height).toFloat())
                    .background(Color.White, RoundedCornerShape(6.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val sx = size.width / document.space.width.toFloat()
                    val sy = size.height / document.space.height.toFloat()
                    fun x(value: Double): Float = value.toFloat() * sx
                    fun y(value: Double): Float = value.toFloat() * sy

                    drawRect(
                        color = Color(0xFFD7DCE6),
                        topLeft = Offset(x(safeArea.left), y(safeArea.top)),
                        size = Size(x(safeArea.width), y(safeArea.height)),
                        style = Stroke(
                            width = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                        )
                    )
                    document.fiducials.forEach { marker ->
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(x(marker.bounds.left), y(marker.bounds.top)),
                            size = Size(x(marker.bounds.width), y(marker.bounds.height))
                        )
                    }
                    drawNumberGrid(
                        component = component,
                        grid = template.markGrids.single(),
                        scaleX = sx,
                        scaleY = sy,
                        bubbleColor = bubbleColor
                    )
                    drawRect(color = Color(0xFFBFC5D0), style = Stroke(width = 1f))
                }
            }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                text = "Optik Form Bilgileri",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "* Zorunlu Alanlar",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = formName,
                onValueChange = onFormNameChange,
                placeholder = { Text("Ad *") },
                singleLine = true,
                shape = RoundedCornerShape(50.dp)
            )

            ReferenceDropdownField(
                label = "Sınav Türü",
                displayValue = if (formSpec.examMode == DesignerExamMode.UNSPECIFIED) {
                    "Seçiniz"
                } else {
                    formSpec.examMode.displayName
                },
                options = listOf(
                    DesignerExamMode.SINGLE_LESSON,
                    DesignerExamMode.MULTI_LESSON
                ),
                optionLabel = { it.displayName },
                onSelected = onExamModeChange
            )

            if (formSpec.examMode != DesignerExamMode.UNSPECIFIED) {
                ReferenceDropdownField(
                    label = "Deneme Türü",
                    displayValue = formSpec.examPreset.displayName,
                    options = listOf(
                        DesignerExamPreset.CUSTOM,
                        DesignerExamPreset.LGS,
                        DesignerExamPreset.TYT,
                        DesignerExamPreset.AYT,
                        DesignerExamPreset.YDT,
                        DesignerExamPreset.ALES,
                        DesignerExamPreset.DGS,
                        DesignerExamPreset.KPSS,
                        DesignerExamPreset.TUS,
                        DesignerExamPreset.SCHOLARSHIP
                    ),
                    optionLabel = { it.displayName },
                    onSelected = onExamPresetChange
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReferenceDropdownField(
                    label = "Kağıt",
                    displayValue = formSpec.paperSize.displayName,
                    options = listOf(
                        DesignerPaperSize.A3,
                        DesignerPaperSize.A4,
                        DesignerPaperSize.A5,
                        DesignerPaperSize.A6,
                        DesignerPaperSize.A7
                    ),
                    optionLabel = { it.displayName },
                    onSelected = onPaperSizeChange,
                    modifier = Modifier.weight(1f)
                )
                ReferenceDropdownField(
                    label = "Yön",
                    displayValue = formSpec.orientation.displayName,
                    options = listOf(
                        DesignerPageOrientation.PORTRAIT,
                        DesignerPageOrientation.LANDSCAPE
                    ),
                    optionLabel = { it.displayName },
                    onSelected = onOrientationChange,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun <T> ReferenceDropdownField(
    label: String,
    displayValue: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = label,
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                shape = RoundedCornerShape(50.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayValue,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    Text("⌄", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun OpticalFormAreaHeader(onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Optik Form Alanı",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onAdd),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("+", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpticalFormAreaPicker(
    onDismiss: () -> Unit,
    onSelected: (DesignerAreaKind) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Optik Form Alanı",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Forma eklemek istediğiniz alan türünü seçin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DesignerAreaCatalog.sections.forEach { section ->
                Spacer(Modifier.size(4.dp))
                Text(
                    text = section.title,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                section.kinds.forEach { kind ->
                    AreaPickerRow(
                        kind = kind,
                        onClick = { onSelected(kind) }
                    )
                }
            }
            Spacer(Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AreaPickerRow(
    kind: DesignerAreaKind,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = areaKindSymbol(kind),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = kind.displayName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    val physicalDimensions = DesignerPageGeometry.dimensions(document.formSpec.paperSize)
    val physicalLabel = if (physicalDimensions == null) {
        "Özel ölçü"
    } else {
        val width = if (document.formSpec.orientation == DesignerPageOrientation.PORTRAIT) {
            physicalDimensions.widthMm
        } else {
            physicalDimensions.heightMm
        }
        val height = if (document.formSpec.orientation == DesignerPageOrientation.PORTRAIT) {
            physicalDimensions.heightMm
        } else {
            physicalDimensions.widthMm
        }
        "${formatMillimetres(width)} × ${formatMillimetres(height)} mm"
    }
    val safeArea = remember(document.space) { DesignerPageGeometry.safeArea(document.space) }
    val compiled = remember(document) { DesignerTemplateCompiler.compile(document) }
    val aspect = document.space.aspectRatio.toFloat()
    val minorGridColor = Color(0xFFE8EBF2)
    val majorGridColor = Color(0xFFD4DAE6)
    val safeStroke = MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
    val safeFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.035f)
    val paperBorder = MaterialTheme.colorScheme.outlineVariant
    val numberBubbleColor = Color(0xFFB54848)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${document.formSpec.paperSize.displayName} · $physicalLabel",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = document.formSpec.orientation.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Canonical ${document.space.width.roundToInt()} × ${document.space.height.roundToInt()} · Grid 50 birim",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .background(Color.White, RoundedCornerShape(6.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val sx = size.width / document.space.width.toFloat()
                    val sy = size.height / document.space.height.toFloat()
                    fun x(value: Double): Float = value.toFloat() * sx
                    fun y(value: Double): Float = value.toFloat() * sy

                    var gridX = 50.0
                    var xIndex = 1
                    while (gridX < document.space.width) {
                        val major = xIndex % 2 == 0
                        drawLine(
                            color = if (major) majorGridColor else minorGridColor,
                            start = Offset(x(gridX), 0f),
                            end = Offset(x(gridX), size.height),
                            strokeWidth = if (major) 1.15f else 0.7f
                        )
                        gridX += 50.0
                        xIndex += 1
                    }

                    var gridY = 50.0
                    var yIndex = 1
                    while (gridY < document.space.height) {
                        val major = yIndex % 2 == 0
                        drawLine(
                            color = if (major) majorGridColor else minorGridColor,
                            start = Offset(0f, y(gridY)),
                            end = Offset(size.width, y(gridY)),
                            strokeWidth = if (major) 1.15f else 0.7f
                        )
                        gridY += 50.0
                        yIndex += 1
                    }

                    drawRect(
                        color = safeFill,
                        topLeft = Offset(x(safeArea.left), y(safeArea.top)),
                        size = Size(x(safeArea.width), y(safeArea.height))
                    )
                    drawRect(
                        color = safeStroke,
                        topLeft = Offset(x(safeArea.left), y(safeArea.top)),
                        size = Size(x(safeArea.width), y(safeArea.height)),
                        style = Stroke(
                            width = 1.3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))
                        )
                    )

                    document.fiducials.forEach { marker ->
                        val left = x(marker.bounds.left)
                        val top = y(marker.bounds.top)
                        val width = x(marker.bounds.width)
                        val height = y(marker.bounds.height)
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(left, top),
                            size = Size(width, height)
                        )
                        val whiteInsetX = width * 0.19f
                        val whiteInsetY = height * 0.19f
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(left + whiteInsetX, top + whiteInsetY),
                            size = Size(width - whiteInsetX * 2f, height - whiteInsetY * 2f)
                        )
                        val centerInsetX = width * 0.37f
                        val centerInsetY = height * 0.37f
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(left + centerInsetX, top + centerInsetY),
                            size = Size(width - centerInsetX * 2f, height - centerInsetY * 2f)
                        )
                    }

                    document.components.filterIsInstance<NumericGridComponent>().forEach { component ->
                        compiled.markGrids.firstOrNull { it.id == component.id }?.let { grid ->
                            drawNumberGrid(
                                component = component,
                                grid = grid,
                                scaleX = sx,
                                scaleY = sy,
                                bubbleColor = numberBubbleColor
                            )
                        }
                    }

                    drawRect(
                        color = paperBorder,
                        style = Stroke(width = 1.2f)
                    )
                }
            }

            Text(
                text = "Kesikli çerçeve güvenli yerleşim alanını, dört siyah işaret tarama referanslarını gösterir.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun DrawScope.drawNumberGrid(
    component: NumericGridComponent,
    grid: MarkGridSpec,
    scaleX: Float,
    scaleY: Float,
    bubbleColor: Color
) {
    val averageScale = (scaleX + scaleY) / 2f
    val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(65, 65, 65)
        textAlign = AndroidPaint.Align.CENTER
        textSize = (component.bubbleRadius.toFloat() * averageScale * 1.02f).coerceAtLeast(6f)
    }
    val labelPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(45, 45, 45)
        textAlign = AndroidPaint.Align.LEFT
        isFakeBoldText = true
        textSize = (component.bubbleRadius.toFloat() * averageScale * 1.35f).coerceAtLeast(7f)
    }

    grid.columns.forEach { column ->
        column.marks.forEach { mark ->
            val center = Offset(
                x = mark.center.x.toFloat() * scaleX,
                y = mark.center.y.toFloat() * scaleY
            )
            val radius = mark.radius.toFloat() * averageScale
            drawCircle(
                color = bubbleColor,
                radius = radius,
                center = center,
                style = Stroke(width = 1.15f)
            )
            drawIntoCanvas { canvas ->
                val metrics = textPaint.fontMetrics
                val baseline = center.y - (metrics.ascent + metrics.descent) / 2f
                canvas.nativeCanvas.drawText(mark.id, center.x, baseline, textPaint)
            }
        }
    }

    if (component.showLabel && component.label.isNotBlank()) {
        val labelX = (component.startX - component.bubbleRadius).toFloat() * scaleX
        val labelY = (component.topY - component.bubbleRadius * 2.2).toFloat() * scaleY
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(component.label, labelX, labelY, labelPaint)
        }
    }
}

private fun formatEditorNumber(value: Double): String =
    if (value == value.roundToInt().toDouble()) value.roundToInt().toString()
    else String.format(java.util.Locale.US, "%.1f", value)

private fun formatMillimetres(value: Double): String =
    if (value == value.roundToInt().toDouble()) {
        value.roundToInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", value)
    }
