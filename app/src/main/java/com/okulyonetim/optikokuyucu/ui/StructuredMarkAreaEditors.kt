package com.okulyonetim.optikokuyucu.ui

import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.DesignerAnswerAppearance
import com.okulyonetim.optikokuyucu.omr.designer.DesignerAreaCatalog
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerEditorLayout
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.NumericGridComponent
import com.okulyonetim.optikokuyucu.omr.designer.NumericGridOrientation
import com.okulyonetim.optikokuyucu.omr.designer.QuestionGroupComponent
import com.okulyonetim.optikokuyucu.omr.designer.QuestionGroupOrientation
import com.okulyonetim.optikokuyucu.omr.template.BubbleRowSpec
import com.okulyonetim.optikokuyucu.omr.template.MarkGridSpec
import kotlin.math.roundToInt

@Composable
internal fun NumberAreaEditorScreen(
    document: DesignerDocument,
    draft: NumericGridComponent,
    patternText: String,
    onDraftChange: (NumericGridComponent) -> Unit,
    onPatternTextChange: (String) -> Unit,
    onCancel: () -> Unit,
    onComplete: (NumericGridComponent) -> Unit
) {
    val normalized = draft.copy(bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS)
    val parsed = DesignerAreaCatalog.parseNumberPattern(patternText)
    val effective = parsed?.let { normalized.copy(values = it) } ?: normalized
    val patternIssue = if (parsed == null) "Desen en az 2 benzersiz değer içermelidir." else null
    val issue = patternIssue ?: DesignerAreaCatalog.numberAreaIssue(document, effective)
    val horizontalGap = if (normalized.orientation == NumericGridOrientation.DIGITS_HORIZONTAL) {
        normalized.columnGap
    } else {
        normalized.rowGap
    }
    val verticalGap = if (normalized.orientation == NumericGridOrientation.DIGITS_HORIZONTAL) {
        normalized.rowGap
    } else {
        normalized.columnGap
    }
    MarkScaffold(issue == null, onCancel, { onComplete(effective) }) {
        MarkCard {
            ReadOnlyField("Tür", "Numara")
            DirectionButtons(
                horizontal = normalized.orientation == NumericGridOrientation.DIGITS_HORIZONTAL,
                onHorizontal = { onDraftChange(normalized.copy(orientation = NumericGridOrientation.DIGITS_HORIZONTAL)) },
                onVertical = { onDraftChange(normalized.copy(orientation = NumericGridOrientation.DIGITS_VERTICAL)) }
            )
            LabelControls(normalized.label, normalized.showLabel, "Etiket", { onDraftChange(normalized.copy(showLabel = it)) }) {
                onDraftChange(normalized.copy(label = it))
            }
            Text("Etiket Hizası", style = MaterialTheme.typography.labelMedium)
            AlignmentButtons(normalized.labelAlignment) { onDraftChange(normalized.copy(labelAlignment = it)) }
            PatternField(patternText, patternIssue, DesignerAreaCatalog.numberPatternPresets, onPatternTextChange)
            IntInput("Veri / Hane Sayısı", normalized.digits, 1, 16) { onDraftChange(normalized.copy(digits = it)) }
            NumberInput("Yatay Baloncuk Aralığı", horizontalGap, 18.0, 120.0, 1.0) { gap ->
                onDraftChange(
                    if (normalized.orientation == NumericGridOrientation.DIGITS_HORIZONTAL) {
                        normalized.copy(columnGap = gap)
                    } else {
                        normalized.copy(rowGap = gap)
                    }
                )
            }
            NumberInput("Dikey Baloncuk Aralığı", verticalGap, 18.0, 120.0, 1.0) { gap ->
                onDraftChange(
                    if (normalized.orientation == NumericGridOrientation.DIGITS_HORIZONTAL) {
                        normalized.copy(rowGap = gap)
                    } else {
                        normalized.copy(columnGap = gap)
                    }
                )
            }
            Text("Baloncuk boyutu sabittir; yatay ve dikey aralıklar ayrı ayrı ayarlanabilir.", style = MaterialTheme.typography.bodySmall)
        }
        issue?.let { IssueText(it) }
        NumberPreview(document, effective)
    }
}

@Composable
internal fun AnswerAreaEditorScreen(
    document: DesignerDocument,
    draft: QuestionGroupComponent,
    patternText: String,
    onDraftChange: (QuestionGroupComponent) -> Unit,
    onPatternTextChange: (String) -> Unit,
    onCancel: () -> Unit,
    onComplete: (QuestionGroupComponent) -> Unit
) {
    val normalized = draft.copy(bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS)
    val parsed = DesignerAreaCatalog.parseAnswerPattern(patternText)
    val effective = parsed?.let { normalized.copy(choices = it) } ?: normalized
    val patternIssue = if (parsed == null) "Desen 2–8 benzersiz şık içermelidir." else null
    val issue = patternIssue ?: DesignerAreaCatalog.answerAreaIssue(document, effective)
    val perBlock = DesignerAreaCatalog.answerQuestionsPerBlock(effective)
    val horizontalGap = if (normalized.orientation == QuestionGroupOrientation.VERTICAL) {
        normalized.choiceGap
    } else {
        normalized.rowGap
    }
    val verticalGap = if (normalized.orientation == QuestionGroupOrientation.VERTICAL) {
        normalized.rowGap
    } else {
        normalized.choiceGap
    }
    MarkScaffold(issue == null, onCancel, { onComplete(effective) }) {
        MarkCard {
            ReadOnlyField("Tür", "Cevaplar")
            LabelControls(normalized.label, normalized.showLabel, "Ders Adı", { onDraftChange(normalized.copy(showLabel = it)) }) {
                onDraftChange(normalized.copy(label = it))
            }
            Text("Etiket Hizası", style = MaterialTheme.typography.labelMedium)
            AlignmentButtons(normalized.labelAlignment) { onDraftChange(normalized.copy(labelAlignment = it)) }
            PatternField(patternText, patternIssue, DesignerAreaCatalog.answerPatternPresets, onPatternTextChange)
            IntInput("İlk Soru Numarası", normalized.startQuestion, 1, 9999) { onDraftChange(normalized.copy(startQuestion = it)) }
            Text("Yön", style = MaterialTheme.typography.labelSmall)
            DirectionButtons(
                horizontal = normalized.orientation == QuestionGroupOrientation.HORIZONTAL,
                onHorizontal = { onDraftChange(normalized.copy(orientation = QuestionGroupOrientation.HORIZONTAL)) },
                onVertical = { onDraftChange(normalized.copy(orientation = QuestionGroupOrientation.VERTICAL)) }
            )
            IntInput("Sütun Sayısı", normalized.columns, 1, minOf(8, normalized.questionCount)) { columns ->
                val total = perBlock * columns
                onDraftChange(normalized.copy(columns = columns, questionCount = total))
            }
            IntInput("Sütundaki Soru Sayısı", perBlock, 1, maxOf(1, 250 / normalized.columns)) { count ->
                onDraftChange(normalized.copy(questionCount = count * normalized.columns))
            }
            NumberInput("Yatay Baloncuk Aralığı", horizontalGap, 18.0, 120.0, 1.0) { gap ->
                onDraftChange(
                    if (normalized.orientation == QuestionGroupOrientation.VERTICAL) {
                        normalized.copy(choiceGap = gap)
                    } else {
                        normalized.copy(rowGap = gap)
                    }
                )
            }
            NumberInput("Dikey Baloncuk Aralığı", verticalGap, 18.0, 120.0, 1.0) { gap ->
                onDraftChange(
                    if (normalized.orientation == QuestionGroupOrientation.VERTICAL) {
                        normalized.copy(rowGap = gap)
                    } else {
                        normalized.copy(choiceGap = gap)
                    }
                )
            }
            NumberInput("Sütunlar Arası Boşluk", normalized.columnGap, 20.0, 600.0, 5.0) { gap ->
                onDraftChange(normalized.copy(columnGap = gap))
            }
            Text("Toplam: ${effective.questionCount} soru · Baloncuk boyutu sabit, aralıklar ayarlanabilir.", style = MaterialTheme.typography.bodySmall)
        }
        issue?.let { IssueText(it) }
        AnswerPreview(document, effective)
    }
}

@Composable
private fun MarkScaffold(completeEnabled: Boolean, onCancel: () -> Unit, onComplete: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))) {
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary) {
            Row(modifier = Modifier.fillMaxWidth().padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("×") }
                Text("Optik Form Alanı", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimary)
                TextButton(enabled = completeEnabled, onClick = onComplete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("Tamam") }
            }
        }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content(); Spacer(Modifier.size(8.dp)) }
    }
}

@Composable
private fun MarkCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Optik Form Alanı Bilgileri", style = MaterialTheme.typography.labelLarge); content()
        }
    }
}

@Composable
private fun LabelControls(label: String, showLabel: Boolean, title: String, onShowLabelChange: (Boolean) -> Unit, onLabelChange: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Etiketi Gizle", modifier = Modifier.weight(1f))
        Switch(checked = !showLabel, onCheckedChange = { onShowLabelChange(!it) })
    }
    OutlinedTextField(value = label, onValueChange = { if ('\n' !in it && '\r' !in it && it.length <= 60) onLabelChange(it) }, modifier = Modifier.fillMaxWidth(), label = { Text(title) }, enabled = showLabel, singleLine = true)
}

@Composable
private fun PatternField(text: String, issue: String?, presets: List<String>, onChange: (String) -> Unit) {
    OutlinedTextField(value = text, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), label = { Text("Desen") }, isError = issue != null, singleLine = true)
    presets.chunked(3).forEach { group ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            group.forEach { preset -> ChoiceButton(Modifier.weight(1f), preset, text == preset) { onChange(preset) } }
            repeat(3 - group.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DirectionButtons(horizontal: Boolean, onHorizontal: () -> Unit, onVertical: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceButton(Modifier.weight(1f), "Yatay", horizontal, onHorizontal)
        ChoiceButton(Modifier.weight(1f), "Dikey", !horizontal, onVertical)
    }
}

@Composable
private fun ChoiceButton(modifier: Modifier, label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) FilledTonalButton(modifier = modifier, onClick = onClick) { Text("$label ✓") }
    else OutlinedButton(modifier = modifier, onClick = onClick) { Text(label) }
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    Column { Text(label, style = MaterialTheme.typography.labelSmall); Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Text(value, modifier = Modifier.padding(14.dp)) } }
}

@Composable
private fun NumberInput(label: String, value: Double, min: Double, max: Double, step: Double, onValueChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(formatNumber(value)) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = text, onValueChange = { input -> text = input; input.replace(',', '.').toDoubleOrNull()?.let { if (it in min..max) onValueChange(it) } }, modifier = Modifier.weight(1f), label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedButton(enabled = value - step >= min, onClick = { onValueChange(value - step) }) { Text("−") }
        OutlinedButton(enabled = value + step <= max, onClick = { onValueChange(value + step) }) { Text("+") }
    }
}

@Composable
private fun IntInput(label: String, value: Int, min: Int, max: Int, onValueChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = text, onValueChange = { input -> text = input; input.toIntOrNull()?.let { if (it in min..max) onValueChange(it) } }, modifier = Modifier.weight(1f), label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedButton(enabled = value > min, onClick = { onValueChange(value - 1) }) { Text("−") }
        OutlinedButton(enabled = value < max, onClick = { onValueChange(value + 1) }) { Text("+") }
    }
}

@Composable
private fun IssueText(message: String) { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

@Composable
private fun NumberPreview(document: DesignerDocument, component: NumericGridComponent) {
    val preview = remember(document.space, document.fiducials, component) { document.copy(components = listOf(component), visualElements = emptyList()) }
    val template = remember(preview) { DesignerTemplateCompiler.compile(preview) }
    PreviewCard(document) {
        val sx = size.width / document.space.width.toFloat(); val sy = size.height / document.space.height.toFloat()
        drawNumberGrid(component, template.markGrids.single(), sx, sy, Color(0xFFB54848)); drawComponentDecorations(preview, sx, sy)
    }
}

@Composable
private fun AnswerPreview(document: DesignerDocument, component: QuestionGroupComponent) {
    val preview = remember(document.space, document.fiducials, component) { document.copy(components = listOf(component), visualElements = emptyList()) }
    val template = remember(preview) { DesignerTemplateCompiler.compile(preview) }; val rows = remember(template) { template.bubbleRows.associateBy { it.id } }
    PreviewCard(document) {
        val sx = size.width / document.space.width.toFloat(); val sy = size.height / document.space.height.toFloat()
        drawAnswerGroup(component, rows, document.formSpec.answerAppearance, sx, sy, Color(0xFFB54848)); drawComponentDecorations(preview, sx, sy)
    }
}

@Composable
private fun PreviewCard(document: DesignerDocument, draw: DrawScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Box(modifier = Modifier.fillMaxWidth().padding(10.dp).aspectRatio((document.space.width / document.space.height).toFloat()).background(Color.White)) { Canvas(Modifier.fillMaxSize(), onDraw = draw) }
    }
}

internal fun DrawScope.drawNumberGrid(component: NumericGridComponent, grid: MarkGridSpec, scaleX: Float, scaleY: Float, bubbleColor: Color) {
    val averageScale = (scaleX + scaleY) / 2f
    val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textAlign = AndroidPaint.Align.CENTER; textSize = (component.bubbleRadius * 0.82).toFloat() * averageScale }
    grid.columns.forEach { column -> column.marks.forEach { mark ->
        val center = Offset(mark.center.x.toFloat() * scaleX, mark.center.y.toFloat() * scaleY)
        drawCircle(bubbleColor, mark.radius.toFloat() * averageScale, center, style = Stroke(1.05f))
        drawIntoCanvas { val m = textPaint.fontMetrics; it.nativeCanvas.drawText(mark.id, center.x, center.y - (m.ascent + m.descent) / 2f, textPaint) }
    } }
}

internal fun DrawScope.drawAnswerGroup(component: QuestionGroupComponent, rowsById: Map<String, BubbleRowSpec>, appearance: DesignerAnswerAppearance, scaleX: Float, scaleY: Float, bubbleColor: Color) {
    val averageScale = (scaleX + scaleY) / 2f
    val choicePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textAlign = AndroidPaint.Align.CENTER; textSize = (component.bubbleRadius * appearance.choiceLabelScale).toFloat() * averageScale }
    val numberPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textAlign = AndroidPaint.Align.RIGHT; textSize = (component.bubbleRadius * appearance.questionNumberScale).toFloat() * averageScale }
    repeat(component.questionCount) { index ->
        val number = component.startQuestion + index; val row = rowsById[DesignerTemplateCompiler.questionReadId(component, number)] ?: return@repeat; val first = row.bubbles.firstOrNull() ?: return@repeat
        val cy = first.center.y.toFloat() * scaleY
        drawIntoCanvas { val m = numberPaint.fontMetrics; it.nativeCanvas.drawText(number.toString(), (first.center.x - first.radius * appearance.questionNumberDistanceInRadii).toFloat() * scaleX, cy - (m.ascent + m.descent) / 2f, numberPaint) }
        row.bubbles.forEach { bubble -> val center = Offset(bubble.center.x.toFloat() * scaleX, bubble.center.y.toFloat() * scaleY); drawCircle(bubbleColor, bubble.radius.toFloat() * averageScale, center, style = Stroke(appearance.bubbleOutlineWidth.toFloat().coerceAtLeast(0.8f))); drawIntoCanvas { val m = choicePaint.fontMetrics; it.nativeCanvas.drawText(bubble.id, center.x, center.y - (m.ascent + m.descent) / 2f, choicePaint) } }
    }
}

private fun formatNumber(value: Double): String = if (value == value.roundToInt().toDouble()) value.roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", value)
