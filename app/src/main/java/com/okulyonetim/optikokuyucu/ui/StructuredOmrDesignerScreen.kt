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
import com.okulyonetim.optikokuyucu.omr.designer.DesignerAnswerAppearance
import com.okulyonetim.optikokuyucu.omr.designer.DesignerAreaCatalog
import com.okulyonetim.optikokuyucu.omr.designer.DesignerAreaKind
import com.okulyonetim.optikokuyucu.omr.designer.DesignerComponentGeometry
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
import com.okulyonetim.optikokuyucu.omr.designer.QuestionGroupComponent
import com.okulyonetim.optikokuyucu.omr.designer.QuestionGroupOrientation
import com.okulyonetim.optikokuyucu.omr.template.BubbleRowSpec
import com.okulyonetim.optikokuyucu.omr.template.MarkGridSpec
import kotlin.math.roundToInt

/**
 * Friendly optical-form editor backed directly by DesignerDocument.
 * There is no editor-only OMR geometry: live previews compile the same components used by reading.
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
    var answerDraft by remember { mutableStateOf<QuestionGroupComponent?>(null) }
    var answerPatternText by remember { mutableStateOf("") }

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

    numberDraft?.let { draft ->
        NumberAreaEditor(
            document = document,
            draft = draft,
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

    answerDraft?.let { draft ->
        AnswerAreaEditor(
            document = document,
            draft = draft,
            patternText = answerPatternText,
            onDraftChange = { answerDraft = it },
            onPatternTextChange = { text ->
                answerPatternText = text
                DesignerAreaCatalog.parseAnswerPattern(text)?.let { choices ->
                    answerDraft = answerDraft?.copy(choices = choices)
                }
            },
            onCancel = {
                answerDraft = null
                answerPatternText = ""
            },
            onComplete = { completed ->
                document = document.copy(components = document.components + completed)
                answerDraft = null
                answerPatternText = ""
                status = "Cevaplar alanı eklendi."
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
        ReferenceEditorTopBar(onBack = onBack, onSave = ::saveDocument)

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
                onExamModeChange = { selected -> updateFormSpec { it.copy(examMode = selected) } },
                onExamPresetChange = { selected -> updateFormSpec { it.copy(examPreset = selected) } },
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
                    DesignerAreaKind.ANSWERS -> {
                        val draft = DesignerAreaCatalog.createAnswerArea(document)
                        answerDraft = draft
                        answerPatternText = DesignerAreaCatalog.answerPatternText(draft.choices)
                    }
                    DesignerAreaKind.DESCRIPTION,
                    DesignerAreaKind.IMAGE -> {
                        status = "${selected.displayName} alanı bilgi alanları aşamasında etkinleştirilecek."
                    }
                }
            }
        )
    }
}

@Composable
private fun ReferenceEditorTopBar(onBack: () -> Unit, onSave: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
            ) { Text("×", style = MaterialTheme.typography.titleLarge) }
            Text(
                text = "Yeni Optik Form",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            TextButton(
                onClick = onSave,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
            ) { Text("Kaydet", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun AreaEditorTopBar(
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
            ) { Text("×", style = MaterialTheme.typography.titleLarge) }
            Text(
                text = "Yeni Optik Form Alanı",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            TextButton(
                enabled = completeEnabled,
                onClick = onComplete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
            ) { Text("Tamam", style = MaterialTheme.typography.labelLarge) }
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
    val effectiveDraft = parsedPattern?.let { draft.copy(values = it) } ?: draft
    val patternIssue = if (parsedPattern == null) {
        "Desen en az 2 benzersiz değer içermelidir. Virgülle özel değer listesi girebilirsiniz."
    } else null
    val issue = patternIssue ?: DesignerAreaCatalog.numberAreaIssue(document, effectiveDraft)

    AreaEditorScaffold(
        completeEnabled = issue == null,
        onCancel = onCancel,
        onComplete = { onComplete(effectiveDraft) }
    ) {
        EditorInformationCard {
            ReadOnlyEditorField("Tür", "Numara")
            DirectionSelector(
                horizontal = draft.orientation == NumericGridOrientation.DIGITS_HORIZONTAL,
                onHorizontal = { onDraftChange(draft.copy(orientation = NumericGridOrientation.DIGITS_HORIZONTAL)) },
                onVertical = { onDraftChange(draft.copy(orientation = NumericGridOrientation.DIGITS_VERTICAL)) }
            )
            LabelControls(
                label = draft.label,
                showLabel = draft.showLabel,
                labelTitle = "Etiket",
                onShowLabelChange = { onDraftChange(draft.copy(showLabel = it)) },
                onLabelChange = { onDraftChange(draft.copy(label = it)) }
            )
            PatternEditor(
                patternText = patternText,
                patternIssue = patternIssue,
                presets = DesignerAreaCatalog.numberPatternPresets,
                supportingText = "Örn. 0123456789, ABCD veya 01,02,03",
                onPatternTextChange = onPatternTextChange
            )
            EditorNumberInput("Sol Boşluk", draft.startX, 0.0, document.space.width, 5.0) {
                onDraftChange(draft.copy(startX = it))
            }
            EditorNumberInput("Üst Boşluk", draft.topY, 0.0, document.space.height, 5.0) {
                onDraftChange(draft.copy(topY = it))
            }
            EditorIntegerInput("Veri / Hane Sayısı", draft.digits, 1, 16) {
                onDraftChange(draft.copy(digits = it))
            }
            EditorNumberInput("Baloncuk Boyutu", draft.bubbleRadius, 6.0, 25.0, 0.5) {
                onDraftChange(draft.copy(bubbleRadius = it))
            }
            EditorNumberInput("Hane Aralığı", draft.columnGap, 16.0, 180.0, 2.0) {
                onDraftChange(draft.copy(columnGap = it))
            }
            EditorNumberInput("Değer Aralığı", draft.rowGap, 16.0, 180.0, 2.0) {
                onDraftChange(draft.copy(rowGap = it))
            }
        }
        EditorIssue(issue)
        NumberAreaLivePreview(document, effectiveDraft)
    }
}

@Composable
private fun AnswerAreaEditor(
    document: DesignerDocument,
    draft: QuestionGroupComponent,
    patternText: String,
    onDraftChange: (QuestionGroupComponent) -> Unit,
    onPatternTextChange: (String) -> Unit,
    onCancel: () -> Unit,
    onComplete: (QuestionGroupComponent) -> Unit
) {
    val parsedPattern = DesignerAreaCatalog.parseAnswerPattern(patternText)
    val effectiveDraft = parsedPattern?.let { draft.copy(choices = it) } ?: draft
    val patternIssue = if (parsedPattern == null) {
        "Desen 2–8 benzersiz şık içermelidir. Virgülle özel şık listesi girebilirsiniz."
    } else null
    val issue = patternIssue ?: DesignerAreaCatalog.answerAreaIssue(document, effectiveDraft)
    val questionsPerBlock = DesignerAreaCatalog.answerQuestionsPerBlock(effectiveDraft)

    AreaEditorScaffold(
        completeEnabled = issue == null,
        onCancel = onCancel,
        onComplete = { onComplete(effectiveDraft) }
    ) {
        EditorInformationCard {
            ReadOnlyEditorField("Tür", "Cevaplar")
            LabelControls(
                label = draft.label,
                showLabel = draft.showLabel,
                labelTitle = "Ders Adı",
                onShowLabelChange = { onDraftChange(draft.copy(showLabel = it)) },
                onLabelChange = { onDraftChange(draft.copy(label = it)) }
            )
            PatternEditor(
                patternText = patternText,
                patternIssue = patternIssue,
                presets = DesignerAreaCatalog.answerPatternPresets,
                supportingText = "Örn. ABCD, ABCDE veya A,B,C,D",
                onPatternTextChange = onPatternTextChange
            )
            EditorIntegerInput("İlk Soru Numarası", draft.startQuestion, 1, 9999) {
                onDraftChange(draft.copy(startQuestion = it))
            }
            EditorIntegerInput("Toplam Soru Sayısı", draft.questionCount, 1, 250) { count ->
                onDraftChange(draft.copy(questionCount = count, columns = minOf(draft.columns, count)))
            }

            Text("Yön", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DirectionSelector(
                horizontal = draft.orientation == QuestionGroupOrientation.HORIZONTAL,
                onHorizontal = { onDraftChange(draft.copy(orientation = QuestionGroupOrientation.HORIZONTAL)) },
                onVertical = { onDraftChange(draft.copy(orientation = QuestionGroupOrientation.VERTICAL)) }
            )

            Text("Sütun Düzeni", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorChoiceButton(
                    modifier = Modifier.weight(1f),
                    label = "Tek Sütun",
                    selected = draft.columns == 1,
                    onClick = { onDraftChange(draft.copy(columns = 1)) }
                )
                EditorChoiceButton(
                    modifier = Modifier.weight(1f),
                    label = "Çok Sütun",
                    selected = draft.columns > 1,
                    onClick = {
                        if (draft.questionCount > 1) {
                            onDraftChange(draft.copy(columns = maxOf(2, draft.columns).coerceAtMost(draft.questionCount)))
                        }
                    }
                )
            }

            EditorIntegerInput("Blok Sayısı", draft.columns, 1, minOf(8, draft.questionCount)) {
                onDraftChange(draft.copy(columns = it))
            }
            EditorIntegerInput(
                label = "Blok Başına Soru",
                value = questionsPerBlock,
                min = 1,
                max = maxOf(1, 250 / draft.columns)
            ) { perBlock ->
                onDraftChange(draft.copy(questionCount = perBlock * draft.columns))
            }
            EditorNumberInput("Bloklar Arası Boşluk", draft.columnGap, 40.0, 900.0, 10.0) {
                onDraftChange(draft.copy(columnGap = it))
            }
            EditorNumberInput("Sol Boşluk", draft.firstChoiceX, 0.0, document.space.width, 5.0) {
                onDraftChange(draft.copy(firstChoiceX = it))
            }
            EditorNumberInput("Üst Boşluk", draft.topY, 0.0, document.space.height, 5.0) {
                onDraftChange(draft.copy(topY = it))
            }
        }
        EditorIssue(issue)
        AnswerAreaLivePreview(document, effectiveDraft)
    }
}

@Composable
private fun AreaEditorScaffold(
    completeEnabled: Boolean,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
    ) {
        AreaEditorTopBar(completeEnabled, onCancel, onComplete)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun EditorInformationCard(content: @Composable () -> Unit) {
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
                "Optik Form Alanı Bilgileri",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

@Composable
private fun LabelControls(
    label: String,
    showLabel: Boolean,
    labelTitle: String,
    onShowLabelChange: (Boolean) -> Unit,
    onLabelChange: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Etiketi Gizle", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Kapalıyken etiket form üzerinde görünür.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = !showLabel,
            onCheckedChange = { hidden -> onShowLabelChange(!hidden) }
        )
    }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = label,
        onValueChange = { text ->
            if ('\n' !in text && '\r' !in text && text.length <= 60) onLabelChange(text)
        },
        label = { Text(labelTitle) },
        enabled = showLabel,
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun PatternEditor(
    patternText: String,
    patternIssue: String?,
    presets: List<String>,
    supportingText: String,
    onPatternTextChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = patternText,
        onValueChange = onPatternTextChange,
        label = { Text("Desen") },
        supportingText = { Text(supportingText) },
        isError = patternIssue != null,
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
    Text("Hazır desenler", style = MaterialTheme.typography.labelMedium)
    PatternPresetRows(presets, patternText, onPatternTextChange)
}

@Composable
private fun PatternPresetRows(
    presets: List<String>,
    selectedText: String,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        presets.chunked(3).forEach { rowPresets ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowPresets.forEach { preset ->
                    EditorChoiceButton(
                        modifier = Modifier.weight(1f),
                        label = preset,
                        selected = selectedText == preset,
                        onClick = { onSelected(preset) }
                    )
                }
                repeat(3 - rowPresets.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DirectionSelector(
    horizontal: Boolean,
    onHorizontal: () -> Unit,
    onVertical: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorChoiceButton(
            modifier = Modifier.weight(1f),
            label = "Yatay",
            selected = horizontal,
            onClick = onHorizontal
        )
        EditorChoiceButton(
            modifier = Modifier.weight(1f),
            label = "Dikey",
            selected = !horizontal,
            onClick = onVertical
        )
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
            Text(value, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
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
        OutlinedButton(enabled = value - step >= min, onClick = { onValueChange((value - step).coerceAtLeast(min)) }) { Text("−") }
        OutlinedButton(enabled = value + step <= max, onClick = { onValueChange((value + step).coerceAtMost(max)) }) { Text("+") }
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
                input.toIntOrNull()?.let { parsed -> if (parsed in min..max) onValueChange(parsed) }
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
private fun EditorIssue(issue: String?) {
    if (issue != null) {
        Text(
            issue,
            modifier = Modifier.padding(horizontal = 6.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun NumberAreaLivePreview(document: DesignerDocument, component: NumericGridComponent) {
    val previewDocument = remember(document.space, document.fiducials, component) {
        document.copy(components = listOf(component), visualElements = emptyList())
    }
    val template = remember(previewDocument) { DesignerTemplateCompiler.compile(previewDocument) }
    PreviewCard(document, "Önizleme okuyucuyla aynı canonical baloncuk geometrisini kullanır.") {
        val sx = size.width / document.space.width.toFloat()
        val sy = size.height / document.space.height.toFloat()
        drawPreviewFrame(document, sx, sy)
        drawNumberGrid(component, template.markGrids.single(), sx, sy, Color(0xFFB54848))
    }
}

@Composable
private fun AnswerAreaLivePreview(document: DesignerDocument, component: QuestionGroupComponent) {
    val previewDocument = remember(document.space, document.fiducials, component) {
        document.copy(components = listOf(component), visualElements = emptyList())
    }
    val template = remember(previewDocument) { DesignerTemplateCompiler.compile(previewDocument) }
    val rows = remember(template) { template.bubbleRows.associateBy { it.id } }
    PreviewCard(document, "Soru numarası, şık balonları ve okuma koordinatları aynı compiler çıktısından çizilir.") {
        val sx = size.width / document.space.width.toFloat()
        val sy = size.height / document.space.height.toFloat()
        drawPreviewFrame(document, sx, sy)
        drawAnswerGroup(component, rows, document.formSpec.answerAppearance, sx, sy, Color(0xFFB54848))
    }
}

@Composable
private fun PreviewCard(
    document: DesignerDocument,
    description: String,
    drawContent: DrawScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Canlı Önizleme", style = MaterialTheme.typography.labelLarge)
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio((document.space.width / document.space.height).toFloat())
                    .background(Color.White, RoundedCornerShape(6.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize(), onDraw = drawContent)
            }
        }
    }
}

private fun DrawScope.drawPreviewFrame(document: DesignerDocument, scaleX: Float, scaleY: Float) {
    val safe = DesignerPageGeometry.safeArea(document.space)
    fun x(value: Double) = value.toFloat() * scaleX
    fun y(value: Double) = value.toFloat() * scaleY
    drawRect(
        color = Color(0xFFD7DCE6),
        topLeft = Offset(x(safe.left), y(safe.top)),
        size = Size(x(safe.width), y(safe.height)),
        style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
    )
    document.fiducials.forEach { marker ->
        drawRect(
            color = Color.Black,
            topLeft = Offset(x(marker.bounds.left), y(marker.bounds.top)),
            size = Size(x(marker.bounds.width), y(marker.bounds.height))
        )
    }
    drawRect(color = Color(0xFFBFC5D0), style = Stroke(width = 1f))
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
            Text("Optik Form Bilgileri", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("* Zorunlu Alanlar", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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
                displayValue = if (formSpec.examMode == DesignerExamMode.UNSPECIFIED) "Seçiniz" else formSpec.examMode.displayName,
                options = listOf(DesignerExamMode.SINGLE_LESSON, DesignerExamMode.MULTI_LESSON),
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReferenceDropdownField(
                    label = "Kağıt",
                    displayValue = formSpec.paperSize.displayName,
                    options = listOf(DesignerPaperSize.A3, DesignerPaperSize.A4, DesignerPaperSize.A5, DesignerPaperSize.A6, DesignerPaperSize.A7),
                    optionLabel = { it.displayName },
                    onSelected = onPaperSizeChange,
                    modifier = Modifier.weight(1f)
                )
                ReferenceDropdownField(
                    label = "Yön",
                    displayValue = formSpec.orientation.displayName,
                    options = listOf(DesignerPageOrientation.PORTRAIT, DesignerPageOrientation.LANDSCAPE),
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
            Text(label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                shape = RoundedCornerShape(50.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(displayValue, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("⌄", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Optik Form Alanı", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            modifier = Modifier.size(28.dp).clickable(onClick = onAdd),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) { Box(contentAlignment = Alignment.Center) { Text("+", fontWeight = FontWeight.Bold) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpticalFormAreaPicker(onDismiss: () -> Unit, onSelected: (DesignerAreaKind) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Optik Form Alanı", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Forma eklemek istediğiniz alan türünü seçin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DesignerAreaCatalog.sections.forEach { section ->
                Spacer(Modifier.size(4.dp))
                Text(section.title, modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                section.kinds.forEach { kind -> AreaPickerRow(kind) { onSelected(kind) } }
            }
            Spacer(Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AreaPickerRow(kind: DesignerAreaKind, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(modifier = Modifier.size(38.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(contentAlignment = Alignment.Center) { Text(areaKindSymbol(kind), fontWeight = FontWeight.Bold) }
            }
            Text(kind.displayName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        val width = if (document.formSpec.orientation == DesignerPageOrientation.PORTRAIT) physicalDimensions.widthMm else physicalDimensions.heightMm
        val height = if (document.formSpec.orientation == DesignerPageOrientation.PORTRAIT) physicalDimensions.heightMm else physicalDimensions.widthMm
        "${formatMillimetres(width)} × ${formatMillimetres(height)} mm"
    }
    val safeArea = remember(document.space) { DesignerPageGeometry.safeArea(document.space) }
    val compiled = remember(document) { DesignerTemplateCompiler.compile(document) }
    val questionRows = remember(compiled) { compiled.bubbleRows.associateBy { it.id } }
    val aspect = document.space.aspectRatio.toFloat()
    val minorGridColor = Color(0xFFE8EBF2)
    val majorGridColor = Color(0xFFD4DAE6)
    val safeStroke = MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
    val safeFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.035f)
    val paperBorder = MaterialTheme.colorScheme.outlineVariant
    val omrColor = Color(0xFFB54848)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${document.formSpec.paperSize.displayName} · $physicalLabel", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(document.formSpec.orientation.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Canonical ${document.space.width.roundToInt()} × ${document.space.height.roundToInt()} · Grid 50 birim", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(aspect).background(Color.White, RoundedCornerShape(6.dp))) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val sx = size.width / document.space.width.toFloat()
                    val sy = size.height / document.space.height.toFloat()
                    fun x(value: Double) = value.toFloat() * sx
                    fun y(value: Double) = value.toFloat() * sy

                    var gx = 50.0
                    var xi = 1
                    while (gx < document.space.width) {
                        val major = xi % 2 == 0
                        drawLine(if (major) majorGridColor else minorGridColor, Offset(x(gx), 0f), Offset(x(gx), size.height), if (major) 1.15f else 0.7f)
                        gx += 50.0
                        xi += 1
                    }
                    var gy = 50.0
                    var yi = 1
                    while (gy < document.space.height) {
                        val major = yi % 2 == 0
                        drawLine(if (major) majorGridColor else minorGridColor, Offset(0f, y(gy)), Offset(size.width, y(gy)), if (major) 1.15f else 0.7f)
                        gy += 50.0
                        yi += 1
                    }

                    drawRect(safeFill, Offset(x(safeArea.left), y(safeArea.top)), Size(x(safeArea.width), y(safeArea.height)))
                    drawRect(
                        safeStroke,
                        Offset(x(safeArea.left), y(safeArea.top)),
                        Size(x(safeArea.width), y(safeArea.height)),
                        style = Stroke(1.3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f)))
                    )
                    document.fiducials.forEach { marker ->
                        drawRect(Color.Black, Offset(x(marker.bounds.left), y(marker.bounds.top)), Size(x(marker.bounds.width), y(marker.bounds.height)))
                    }
                    document.components.filterIsInstance<QuestionGroupComponent>().forEach { component ->
                        drawAnswerGroup(component, questionRows, document.formSpec.answerAppearance, sx, sy, omrColor)
                    }
                    document.components.filterIsInstance<NumericGridComponent>().forEach { component ->
                        compiled.markGrids.firstOrNull { it.id == component.id }?.let { grid ->
                            drawNumberGrid(component, grid, sx, sy, omrColor)
                        }
                    }
                    drawRect(paperBorder, style = Stroke(width = 1.2f))
                }
            }
            Text("Kesikli çerçeve güvenli yerleşim alanını, dört siyah işaret tarama referanslarını gösterir.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            val center = Offset(mark.center.x.toFloat() * scaleX, mark.center.y.toFloat() * scaleY)
            val radius = mark.radius.toFloat() * averageScale
            drawCircle(bubbleColor, radius, center, style = Stroke(width = 1.15f))
            drawIntoCanvas { canvas ->
                val metrics = textPaint.fontMetrics
                canvas.nativeCanvas.drawText(mark.id, center.x, center.y - (metrics.ascent + metrics.descent) / 2f, textPaint)
            }
        }
    }
    if (component.showLabel && component.label.isNotBlank()) {
        val labelX = (component.startX - component.bubbleRadius).toFloat() * scaleX
        val labelY = (component.topY - component.bubbleRadius * 2.2).toFloat() * scaleY
        drawIntoCanvas { it.nativeCanvas.drawText(component.label, labelX, labelY, labelPaint) }
    }
}

private fun DrawScope.drawAnswerGroup(
    component: QuestionGroupComponent,
    rowsById: Map<String, BubbleRowSpec>,
    appearance: DesignerAnswerAppearance,
    scaleX: Float,
    scaleY: Float,
    bubbleColor: Color
) {
    val averageScale = (scaleX + scaleY) / 2f
    val choicePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(65, 65, 65)
        textAlign = AndroidPaint.Align.CENTER
        textSize = (component.bubbleRadius * appearance.choiceLabelScale).toFloat() * averageScale
    }
    val numberPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(45, 45, 45)
        textAlign = AndroidPaint.Align.RIGHT
        textSize = (component.bubbleRadius * appearance.questionNumberScale).toFloat() * averageScale
    }
    val labelPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(35, 35, 35)
        textAlign = AndroidPaint.Align.LEFT
        isFakeBoldText = true
        textSize = (component.bubbleRadius * 1.35).toFloat() * averageScale
    }

    repeat(component.questionCount) { index ->
        val questionNumber = component.startQuestion + index
        val row = rowsById[DesignerTemplateCompiler.questionReadId(component, questionNumber)] ?: return@repeat
        val first = row.bubbles.firstOrNull() ?: return@repeat
        val firstCenter = Offset(first.center.x.toFloat() * scaleX, first.center.y.toFloat() * scaleY)
        val numberX = (first.center.x - first.radius * appearance.questionNumberDistanceInRadii).toFloat() * scaleX
        drawIntoCanvas { canvas ->
            val metrics = numberPaint.fontMetrics
            val baseline = firstCenter.y - (metrics.ascent + metrics.descent) / 2f
            canvas.nativeCanvas.drawText(questionNumber.toString(), numberX, baseline, numberPaint)
        }
        row.bubbles.forEach { bubble ->
            val center = Offset(bubble.center.x.toFloat() * scaleX, bubble.center.y.toFloat() * scaleY)
            val radius = bubble.radius.toFloat() * averageScale
            drawCircle(
                bubbleColor,
                radius,
                center,
                style = Stroke(width = appearance.bubbleOutlineWidth.toFloat().coerceAtLeast(0.8f))
            )
            drawIntoCanvas { canvas ->
                val metrics = choicePaint.fontMetrics
                canvas.nativeCanvas.drawText(bubble.id, center.x, center.y - (metrics.ascent + metrics.descent) / 2f, choicePaint)
            }
        }
    }

    if (component.showLabel && component.label.isNotBlank()) {
        val bounds = DesignerComponentGeometry.bounds(component)
        val labelX = bounds.left.toFloat() * scaleX
        val labelY = (bounds.top - component.bubbleRadius * 2.2).toFloat() * scaleY
        drawIntoCanvas { it.nativeCanvas.drawText(component.label, labelX, labelY, labelPaint) }
    }
}

private fun formatEditorNumber(value: Double): String =
    if (value == value.roundToInt().toDouble()) value.roundToInt().toString()
    else String.format(java.util.Locale.US, "%.1f", value)

private fun formatMillimetres(value: Double): String =
    if (value == value.roundToInt().toDouble()) value.roundToInt().toString()
    else String.format(java.util.Locale.US, "%.1f", value)
