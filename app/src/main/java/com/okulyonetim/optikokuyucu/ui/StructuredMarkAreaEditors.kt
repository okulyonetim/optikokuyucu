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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.DesignerAnswerAppearance
import com.okulyonetim.optikokuyucu.omr.designer.DesignerAreaCatalog
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPageGeometry
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
    val parsed = DesignerAreaCatalog.parseNumberPattern(patternText)
    val effective = parsed?.let { draft.copy(values = it) } ?: draft
    val patternIssue = if (parsed == null) "Desen en az 2 benzersiz değer içermelidir." else null
    val issue = patternIssue ?: DesignerAreaCatalog.numberAreaIssue(document, effective)
    MarkScaffold(issue == null, onCancel, { onComplete(effective) }) {
        MarkCard {
            ReadOnlyField("Tür", "Numara")
            DirectionButtons(
                horizontal = draft.orientation == NumericGridOrientation.DIGITS_HORIZONTAL,
                onHorizontal = { onDraftChange(draft.copy(orientation = NumericGridOrientation.DIGITS_HORIZONTAL)) },
                onVertical = { onDraftChange(draft.copy(orientation = NumericGridOrientation.DIGITS_VERTICAL)) }
            )
            LabelControls(draft.label, draft.showLabel, "Etiket", { onDraftChange(draft.copy(showLabel = it)) }) {
                onDraftChange(draft.copy(label = it))
            }
            PatternField(patternText, patternIssue, DesignerAreaCatalog.numberPatternPresets, onPatternTextChange)
            NumberInput("Sol Boşluk", draft.startX, 0.0, document.space.width, 5.0) { onDraftChange(draft.copy(startX = it)) }
            NumberInput("Üst Boşluk", draft.topY, 0.0, document.space.height, 5.0) { onDraftChange(draft.copy(topY = it)) }
            IntInput("Veri / Hane Sayısı", draft.digits, 1, 16) { onDraftChange(draft.copy(digits = it)) }
            NumberInput("Baloncuk Boyutu", draft.bubbleRadius, 6.0, 25.0, 0.5) { onDraftChange(draft.copy(bubbleRadius = it)) }
            NumberInput("Hane Aralığı", draft.columnGap, 16.0, 180.0, 2.0) { onDraftChange(draft.copy(columnGap = it)) }
            NumberInput("Değer Aralığı", draft.rowGap, 16.0, 180.0, 2.0) { onDraftChange(draft.copy(rowGap = it)) }
        }
        if (issue != null) IssueText(issue)
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
    val parsed = DesignerAreaCatalog.parseAnswerPattern(patternText)
    val effective = parsed?.let { draft.copy(choices = it) } ?: draft
    val patternIssue = if (parsed == null) "Desen 2–8 benzersiz şık içermelidir." else null
    val issue = patternIssue ?: DesignerAreaCatalog.answerAreaIssue(document, effective)
    val perBlock = DesignerAreaCatalog.answerQuestionsPerBlock(effective)
    MarkScaffold(issue == null, onCancel, { onComplete(effective) }) {
        MarkCard {
            ReadOnlyField("Tür", "Cevaplar")
            LabelControls(draft.label, draft.showLabel, "Ders Adı", { onDraftChange(draft.copy(showLabel = it)) }) {
                onDraftChange(draft.copy(label = it))
            }
            PatternField(patternText, patternIssue, DesignerAreaCatalog.answerPatternPresets, onPatternTextChange)
            IntInput("İlk Soru Numarası", draft.startQuestion, 1, 9999) { onDraftChange(draft.copy(startQuestion = it)) }
            IntInput("Toplam Soru Sayısı", draft.questionCount, 1, 250) {
                onDraftChange(draft.copy(questionCount = it, columns = minOf(draft.columns, it)))
            }
            Text("Yön", style = MaterialTheme.typography.labelSmall)
            DirectionButtons(
                horizontal = draft.orientation == QuestionGroupOrientation.HORIZONTAL,
                onHorizontal = { onDraftChange(draft.copy(orientation = QuestionGroupOrientation.HORIZONTAL)) },
                onVertical = { onDraftChange(draft.copy(orientation = QuestionGroupOrientation.VERTICAL)) }
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton(Modifier.weight(1f), "Tek Sütun", draft.columns == 1) { onDraftChange(draft.copy(columns = 1)) }
                ChoiceButton(Modifier.weight(1f), "Çok Sütun", draft.columns > 1) {
                    if (draft.questionCount > 1) onDraftChange(draft.copy(columns = maxOf(2, draft.columns).coerceAtMost(draft.questionCount)))
                }
            }
            IntInput("Blok Sayısı", draft.columns, 1, minOf(8, draft.questionCount)) { onDraftChange(draft.copy(columns = it)) }
            IntInput("Blok Başına Soru", perBlock, 1, maxOf(1, 250 / draft.columns)) { onDraftChange(draft.copy(questionCount = it * draft.columns)) }
            NumberInput("Bloklar Arası Boşluk", draft.columnGap, 40.0, 900.0, 10.0) { onDraftChange(draft.copy(columnGap = it)) }
            NumberInput("Sol Boşluk", draft.firstChoiceX, 0.0, document.space.width, 5.0) { onDraftChange(draft.copy(firstChoiceX = it)) }
            NumberInput("Üst Boşluk", draft.topY, 0.0, document.space.height, 5.0) { onDraftChange(draft.copy(topY = it)) }
        }
        if (issue != null) IssueText(issue)
        AnswerPreview(document, effective)
    }
}

@Composable
private fun MarkScaffold(
    completeEnabled: Boolean,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
    ) {
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary) {
            Row(modifier = Modifier.fillMaxWidth().padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) {
                    Text("×", style = MaterialTheme.typography.titleLarge)
                }
                Text("Yeni Optik Form Alanı", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimary)
                TextButton(enabled = completeEnabled, onClick = onComplete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("Tamam") }
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) { content(); Spacer(Modifier.size(8.dp)) }
    }
}

@Composable
private fun MarkCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Optik Form Alanı Bilgileri", style = MaterialTheme.typography.labelLarge)
            content()
        }
    }
}

@Composable
private fun LabelControls(
    label: String,
    showLabel: Boolean,
    title: String,
    onShowLabelChange: (Boolean) -> Unit,
    onLabelChange: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Etiketi Gizle")
            Text("Kapalıyken etiket form üzerinde görünür.", style = MaterialTheme.typography.labelSmall)
        }
        Switch(checked = !showLabel, onCheckedChange = { onShowLabelChange(!it) })
    }
    OutlinedTextField(
        value = label,
        onValueChange = { text -> if ('\n' !in text && '\r' !in text && text.length <= 60) onLabelChange(text) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(title) },
        enabled = showLabel,
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun PatternField(text: String, issue: String?, presets: List<String>, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Desen") },
        supportingText = { Text("Hazır desen seçin veya virgülle özel değer girin.") },
        isError = issue != null,
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
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
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelSmall)
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Text(value, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
        }
    }
}

@Composable
private fun NumberInput(label: String, value: Double, min: Double, max: Double, step: Double, onValueChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(formatNumber(value)) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                input.replace(',', '.').toDoubleOrNull()?.let { parsed -> if (parsed in min..max) onValueChange(parsed) }
            },
            modifier = Modifier.weight(1f),
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
private fun IntInput(label: String, value: Int, min: Int, max: Int, onValueChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                input.toIntOrNull()?.let { parsed -> if (parsed in min..max) onValueChange(parsed) }
            },
            modifier = Modifier.weight(1f),
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
private fun IssueText(message: String) {
    Text(message, modifier = Modifier.padding(horizontal = 6.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun NumberPreview(document: DesignerDocument, component: NumericGridComponent) {
    val preview = remember(document.space, document.fiducials, component) { document.copy(components = listOf(component), visualElements = emptyList()) }
    val template = remember(preview) { DesignerTemplateCompiler.compile(preview) }
    PreviewCard(document) {
        val sx = size.width / document.space.width.toFloat(); val sy = size.height / document.space.height.toFloat()
        drawPreviewFrame(document, sx, sy)
        drawNumberGrid(component, template.markGrids.single(), sx, sy, Color(0xFFB54848))
    }
}

@Composable
private fun AnswerPreview(document: DesignerDocument, component: QuestionGroupComponent) {
    val preview = remember(document.space, document.fiducials, component) { document.copy(components = listOf(component), visualElements = emptyList()) }
    val template = remember(preview) { DesignerTemplateCompiler.compile(preview) }
    val rows = remember(template) { template.bubbleRows.associateBy { it.id } }
    PreviewCard(document) {
        val sx = size.width / document.space.width.toFloat(); val sy = size.height / document.space.height.toFloat()
        drawPreviewFrame(document, sx, sy)
        drawAnswerGroup(component, rows, document.formSpec.answerAppearance, sx, sy, Color(0xFFB54848))
    }
}

@Composable
private fun PreviewCard(document: DesignerDocument, draw: DrawScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Canlı Önizleme", style = MaterialTheme.typography.labelLarge)
            Box(modifier = Modifier.fillMaxWidth().aspectRatio((document.space.width / document.space.height).toFloat()).background(Color.White)) {
                Canvas(modifier = Modifier.fillMaxSize(), onDraw = draw)
            }
        }
    }
}

private fun DrawScope.drawPreviewFrame(document: DesignerDocument, sx: Float, sy: Float) {
    val safe = DesignerPageGeometry.safeArea(document.space)
    drawRect(Color(0xFFD7DCE6), Offset(safe.left.toFloat() * sx, safe.top.toFloat() * sy), Size(safe.width.toFloat() * sx, safe.height.toFloat() * sy), style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))))
    document.fiducials.forEach { marker -> drawRect(Color.Black, Offset(marker.bounds.left.toFloat() * sx, marker.bounds.top.toFloat() * sy), Size(marker.bounds.width.toFloat() * sx, marker.bounds.height.toFloat() * sy)) }
}

internal fun DrawScope.drawNumberGrid(component: NumericGridComponent, grid: MarkGridSpec, scaleX: Float, scaleY: Float, bubbleColor: Color) {
    val averageScale = (scaleX + scaleY) / 2f
    val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textAlign = AndroidPaint.Align.CENTER; textSize = (component.bubbleRadius.toFloat() * averageScale).coerceAtLeast(6f) }
    grid.columns.forEach { column -> column.marks.forEach { mark ->
        val center = Offset(mark.center.x.toFloat() * scaleX, mark.center.y.toFloat() * scaleY)
        drawCircle(bubbleColor, mark.radius.toFloat() * averageScale, center, style = Stroke(1.15f))
        drawIntoCanvas { val m = textPaint.fontMetrics; it.nativeCanvas.drawText(mark.id, center.x, center.y - (m.ascent + m.descent) / 2f, textPaint) }
    } }
}

internal fun DrawScope.drawAnswerGroup(component: QuestionGroupComponent, rowsById: Map<String, BubbleRowSpec>, appearance: DesignerAnswerAppearance, scaleX: Float, scaleY: Float, bubbleColor: Color) {
    val averageScale = (scaleX + scaleY) / 2f
    val choicePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textAlign = AndroidPaint.Align.CENTER; textSize = (component.bubbleRadius * appearance.choiceLabelScale).toFloat() * averageScale }
    val numberPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textAlign = AndroidPaint.Align.RIGHT; textSize = (component.bubbleRadius * appearance.questionNumberScale).toFloat() * averageScale }
    repeat(component.questionCount) { index ->
        val number = component.startQuestion + index
        val row = rowsById[DesignerTemplateCompiler.questionReadId(component, number)] ?: return@repeat
        val first = row.bubbles.firstOrNull() ?: return@repeat
        val firstCenter = Offset(first.center.x.toFloat() * scaleX, first.center.y.toFloat() * scaleY)
        drawIntoCanvas { val m = numberPaint.fontMetrics; it.nativeCanvas.drawText(number.toString(), (first.center.x - first.radius * appearance.questionNumberDistanceInRadii).toFloat() * scaleX, firstCenter.y - (m.ascent + m.descent) / 2f, numberPaint) }
        row.bubbles.forEach { bubble ->
            val center = Offset(bubble.center.x.toFloat() * scaleX, bubble.center.y.toFloat() * scaleY)
            drawCircle(bubbleColor, bubble.radius.toFloat() * averageScale, center, style = Stroke(appearance.bubbleOutlineWidth.toFloat().coerceAtLeast(0.8f)))
            drawIntoCanvas { val m = choicePaint.fontMetrics; it.nativeCanvas.drawText(bubble.id, center.x, center.y - (m.ascent + m.descent) / 2f, choicePaint) }
        }
    }
}

private fun formatNumber(value: Double): String = if (value == value.roundToInt().toDouble()) value.roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", value)
