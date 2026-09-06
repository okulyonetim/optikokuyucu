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
import androidx.compose.material3.CardDefaults
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
import com.okulyonetim.optikokuyucu.omr.designer.DesignerComponentGeometry
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
    val parsedPattern = DesignerAreaCatalog.parseNumberPattern(patternText)
    val effective = parsedPattern?.let { draft.copy(values = it) } ?: draft
    val patternIssue = if (parsedPattern == null) {
        "Desen en az 2 benzersiz değer içermelidir. Virgülle özel değer listesi girebilirsiniz."
    } else null
    val issue = patternIssue ?: DesignerAreaCatalog.numberAreaIssue(document, effective)
    MarkAreaScaffold(issue == null, onCancel, { onComplete(effective) }) {
        MarkEditorCard {
            MarkReadOnlyField("Tür", "Numara")
            MarkDirectionSelector(
                horizontal = draft.orientation == NumericGridOrientation.DIGITS_HORIZONTAL,
                onHorizontal = { onDraftChange(draft.copy(orientation = NumericGridOrientation.DIGITS_HORIZONTAL)) },
                onVertical = { onDraftChange(draft.copy(orientation = NumericGridOrientation.DIGITS_VERTICAL)) }
            )
            MarkLabelControls(
                label = draft.label,
                showLabel = draft.showLabel,
                labelTitle = "Etiket",
                onShowLabelChange = { onDraftChange(draft.copy(showLabel = it)) },
                onLabelChange = { onDraftChange(draft.copy(label = it)) }
            )
            MarkPatternEditor(
                patternText, patternIssue, DesignerAreaCatalog.numberPatternPresets,
                "Örn. 0123456789, ABCD veya 01,02,03", onPatternTextChange
            )
            MarkNumberInput("Sol Boşluk", draft.startX, 0.0, document.space.width, 5.0) {
                onDraftChange(draft.copy(startX = it))
            }
            MarkNumberInput("Üst Boşluk", draft.topY, 0.0, document.space.height, 5.0) {
                onDraftChange(draft.copy(topY = it))
            }
            MarkIntegerInput("Veri / Hane Sayısı", draft.digits, 1, 16) {
                onDraftChange(draft.copy(digits = it))
            }
            MarkNumberInput("Baloncuk Boyutu", draft.bubbleRadius, 6.0, 25.0, 0.5) {
                onDraftChange(draft.copy(bubbleRadius = it))
            }
            MarkNumberInput("Hane Aralığı", draft.columnGap, 16.0, 180.0, 2.0) {
                onDraftChange(draft.copy(columnGap = it))
            }
            MarkNumberInput("Değer Aralığı", draft.rowGap, 16.0, 180.0, 2.0) {
                onDraftChange(draft.copy(rowGap = it))
            }
        }
        MarkIssue(issue)
        NumberAreaPreview(document, effective)
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
    val parsedPattern = DesignerAreaCatalog.parseAnswerPattern(patternText)
    val effective = parsedPattern?.let { draft.copy(choices = it) } ?: draft
    val patternIssue = if (parsedPattern == null) {
        "Desen 2–8 benzersiz şık içermelidir. Virgülle özel şık listesi girebilirsiniz."
    } else null
    val issue = patternIssue ?: DesignerAreaCatalog.answerAreaIssue(document, effective)
    val perBlock = DesignerAreaCatalog.answerQuestionsPerBlock(effective)

    MarkAreaScaffold(issue == null, onCancel, { onComplete(effective) }) {
        MarkEditorCard {
            MarkReadOnlyField("Tür", "Cevaplar")
            MarkLabelControls(
                label = draft.label,
                showLabel = draft.showLabel,
                labelTitle = "Ders Adı",
                onShowLabelChange = { onDraftChange(draft.copy(showLabel = it)) },
                onLabelChange = { onDraftChange(draft.copy(label = it)) }
            )
            MarkPatternEditor(
                patternText, patternIssue, DesignerAreaCatalog.answerPatternPresets,
                "Örn. ABCD, ABCDE veya A,B,C,D", onPatternTextChange
            )
            MarkIntegerInput("İlk Soru Numarası", draft.startQuestion, 1, 9999) {
                onDraftChange(draft.copy(startQuestion = it))
            }
            MarkIntegerInput("Toplam Soru Sayısı", draft.questionCount, 1, 250) { count ->
                onDraftChange(draft.copy(questionCount = count, columns = minOf(draft.columns, count)))
            }
            Text("Yön", style = MaterialTheme.typography.labelSmall)
            MarkDirectionSelector(
                horizontal = draft.orientation == QuestionGroupOrientation.HORIZONTAL,
                onHorizontal = { onDraftChange(draft.copy(orientation = QuestionGroupOrientation.HORIZONTAL)) },
                onVertical = { onDraftChange(draft.copy(orientation = QuestionGroupOrientation.VERTICAL)) }
            )
            Text("Sütun Düzeni", style = MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarkChoiceButton(Modifier.weight(1f), "Tek Sütun", draft.columns == 1) {
                    onDraftChange(draft.copy(columns = 1))
                }
                MarkChoiceButton(Modifier.weight(1f), "Çok Sütun", draft.columns > 1) {
                    if (draft.questionCount > 1) {
                        onDraftChange(draft.copy(columns = maxOf(2, draft.columns).coerceAtMost(draft.questionCount)))
                    }
                }
            }
            MarkIntegerInput("Blok Sayısı", draft.columns, 1, minOf(8, draft.questionCount)) {
                onDraftChange(draft.copy(columns = it))
            }
            MarkIntegerInput("Blok Başına Soru", perBlock, 1, maxOf(1, 250 / draft.columns)) {
                onDraftChange(draft.copy(questionCount = it * draft.columns))
            }
            MarkNumberInput("Bloklar Arası Boşluk", draft.columnGap, 40.0, 900.0, 10.0) {
                onDraftChange(draft.copy(columnGap = it))
            }
            MarkNumberInput("Sol Boşluk", draft.firstChoiceX, 0.0, document.space.width, 5.0) {
                onDraftChange(draft.copy(firstChoiceX = it))
            }
            MarkNumberInput("Üst Boşluk", draft.topY, 0.0, document.space.height, 5.0) {
                onDraftChange(draft.copy(topY = it))
            }
        }
        MarkIssue(issue)
        AnswerAreaPreview(document, effective)
    }
}

@Composable
private fun MarkAreaScaffold(
    completeEnabled: Boolean,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
    ) {
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary) {
            Row(Modifier.fillMaxWidth().padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) {
                    Text("×", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    "Yeni Optik Form Alanı", Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleSmall
                )
                TextButton(
                    enabled = completeEnabled,
                    onClick = onComplete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                ) { Text("Tamam") }
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun MarkEditorCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Optik Form Alanı Bilgileri", style = MaterialTheme.typography.labelLarge)
            content()
        }
    }
}

@Composable
private fun MarkLabelControls(
    label: String,
    showLabel: Boolean,
    labelTitle: String,
    onShowLabelChange: (Boolean) -> Unit,
    onLabelChange: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Etiketi Gizle")
            Text("Kapalıyken etiket form üzerinde görünür.", style = MaterialTheme.typography.labelSmall)
        }
        Switch(checked = !showLabel, onCheckedChange = { onShowLabelChange(!it) })
    }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = label,
        onValueChange = { text -> if ('\n' !in text && '\r' !in text && text.length <= 60) onLabelChange(text) },
        label = { Text(labelTitle) },
        enabled = showLabel,
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun MarkPatternEditor(
    text: String,
    issue: String?,
    presets: List<String>,
    supporting: String,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        Modifier.fillMaxWidth(), text, onChange,
        label = { Text("Desen") },
        supportingText = { Text(supporting) },
        isError = issue != null,
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
    Text("Hazır desenler", style = MaterialTheme.typography.labelMedium)
    presets.chunked(3).forEach { group ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            group.forEach { preset ->
                MarkChoiceButton(Modifier.weight(1f), preset, text == preset) { onChange(preset) }
            }
            repeat(3 - group.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun MarkDirectionSelector(horizontal: Boolean, onHorizontal: () -> Unit, onVertical: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MarkChoiceButton(Modifier.weight(1f), "Yatay", horizontal, onHorizontal)
        MarkChoiceButton(Modifier.weight(1f), "Dikey", !horizontal, onVertical)
    }
}

@Composable
private fun MarkReadOnlyField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelSmall)
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) { Text(value, Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) }
    }
}

@Composable
private fun MarkChoiceButton(modifier: Modifier, label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) FilledTonalButton(modifier, onClick = onClick) { Text("$label ✓") }
    else OutlinedButton(modifier, onClick = onClick) { Text(label) }
}

@Composable
private fun MarkNumberInput(
    label: String,
    value: Double,
    min: Double,
    max: Double,
    step: Double,
    onValueChange: (Double) -> Unit
) {
    var text by remember(value) { mutableStateOf(formatMarkNumber(value)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            Modifier.weight(1f), text,
            onValueChange = { input ->
                text = input
                input.replace(',', '.').toDoubleOrNull()?.let { if (it in min..max) onValueChange(it) }
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
private fun MarkIntegerInput(label: String, value: Int, min: Int, max: Int, onValueChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            Modifier.weight(1f), text,
            onValueChange = { input ->
                text = input
                input.toIntOrNull()?.let { if (it in min..max) onValueChange(it) }
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
private fun MarkIssue(issue: String?) {
    if (issue != null) Text(issue, Modifier.padding(horizontal = 6.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun NumberAreaPreview(document: DesignerDocument, component: NumericGridComponent) {
    val preview = remember(document.space, document.fiducials, component) {
        document.copy(components = listOf(component), visualElements = emptyList())
    }
    val template = remember(preview) { DesignerTemplateCompiler.compile(preview) }
    MarkPreviewCard(document) {
        val sx = size.width / document.space.width.toFloat()
        val sy = size.height / document.space.height.toFloat()
        drawMarkPreviewFrame(document, sx, sy)
        drawNumberGrid(component, template.markGrids.single(), sx, sy, Color(0xFFB54848))
    }
}

@Composable
private fun AnswerAreaPreview(document: DesignerDocument, component: QuestionGroupComponent) {
    val preview = remember(document.space, document.fiducials, component) {
        document.copy(components = listOf(component), visualElements = emptyList())
    }
    val template = remember(preview) { DesignerTemplateCompiler.compile(preview) }
    val rows = remember(template) { template.bubbleRows.associateBy { it.id } }
    MarkPreviewCard(document) {
        val sx = size.width / document.space.width.toFloat()
        val sy = size.height / document.space.height.toFloat()
        drawMarkPreviewFrame(document, sx, sy)
        drawAnswerGroup(component, rows, document.formSpec.answerAppearance, sx, sy, Color(0xFFB54848))
    }
}

@Composable
private fun MarkPreviewCard(document: DesignerDocument, draw: DrawScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Canlı Önizleme", style = MaterialTheme.typography.labelLarge)
            Text("Önizleme okuyucuyla aynı canonical geometriyi kullanır.", style = MaterialTheme.typography.labelSmall)
            Box(Modifier.fillMaxWidth().aspectRatio((document.space.width / document.space.height).toFloat()).background(Color.White)) {
                Canvas(Modifier.fillMaxSize(), onDraw = draw)
            }
        }
    }
}

private fun DrawScope.drawMarkPreviewFrame(document: DesignerDocument, sx: Float, sy: Float) {
    val safe = DesignerPageGeometry.safeArea(document.space)
    drawRect(
        Color(0xFFD7DCE6), Offset(safe.left.toFloat() * sx, safe.top.toFloat() * sy),
        Size(safe.width.toFloat() * sx, safe.height.toFloat() * sy),
        style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
    )
    document.fiducials.forEach { marker ->
        drawRect(Color.Black, Offset(marker.bounds.left.toFloat() * sx, marker.bounds.top.toFloat() * sy), Size(marker.bounds.width.toFloat() * sx, marker.bounds.height.toFloat() * sy))
    }
}

internal fun DrawScope.drawNumberGrid(
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
    grid.columns.forEach { column -> column.marks.forEach { mark ->
        val center = Offset(mark.center.x.toFloat() * scaleX, mark.center.y.toFloat() * scaleY)
        val radius = mark.radius.toFloat() * averageScale
        drawCircle(bubbleColor, radius, center, style = Stroke(1.15f))
        drawIntoCanvas { canvas ->
            val metrics = textPaint.fontMetrics
            canvas.nativeCanvas.drawText(mark.id, center.x, center.y - (metrics.ascent + metrics.descent) / 2f, textPaint)
        }
    } }
    if (component.showLabel && component.label.isNotBlank()) {
        drawIntoCanvas {
            it.nativeCanvas.drawText(
                component.label,
                (component.startX - component.bubbleRadius).toFloat() * scaleX,
                (component.topY - component.bubbleRadius * 2.2).toFloat() * scaleY,
                labelPaint
            )
        }
    }
}

internal fun DrawScope.drawAnswerGroup(
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
        val number = component.startQuestion + index
        val row = rowsById[DesignerTemplateCompiler.questionReadId(component, number)] ?: return@repeat
        val first = row.bubbles.firstOrNull() ?: return@repeat
        val firstCenter = Offset(first.center.x.toFloat() * scaleX, first.center.y.toFloat() * scaleY)
        val numberX = (first.center.x - first.radius * appearance.questionNumberDistanceInRadii).toFloat() * scaleX
        drawIntoCanvas { canvas ->
            val m = numberPaint.fontMetrics
            canvas.nativeCanvas.drawText(number.toString(), numberX, firstCenter.y - (m.ascent + m.descent) / 2f, numberPaint)
        }
        row.bubbles.forEach { bubble ->
            val center = Offset(bubble.center.x.toFloat() * scaleX, bubble.center.y.toFloat() * scaleY)
            val radius = bubble.radius.toFloat() * averageScale
            drawCircle(bubbleColor, radius, center, style = Stroke(appearance.bubbleOutlineWidth.toFloat().coerceAtLeast(0.8f)))
            drawIntoCanvas { canvas ->
                val m = choicePaint.fontMetrics
                canvas.nativeCanvas.drawText(bubble.id, center.x, center.y - (m.ascent + m.descent) / 2f, choicePaint)
            }
        }
    }
    if (component.showLabel && component.label.isNotBlank()) {
        val bounds = DesignerComponentGeometry.bounds(component)
        drawIntoCanvas {
            it.nativeCanvas.drawText(
                component.label,
                bounds.left.toFloat() * scaleX,
                (bounds.top - component.bubbleRadius * 2.2).toFloat() * scaleY,
                labelPaint
            )
        }
    }
}

private fun formatMarkNumber(value: Double): String =
    if (value == value.roundToInt().toDouble()) value.roundToInt().toString()
    else String.format(java.util.Locale.US, "%.1f", value)
