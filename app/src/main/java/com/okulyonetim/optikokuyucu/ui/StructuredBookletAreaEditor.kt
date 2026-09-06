package com.okulyonetim.optikokuyucu.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.ChoiceAxis
import com.okulyonetim.optikokuyucu.omr.designer.DesignerAreaCatalog
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextAlignment
import com.okulyonetim.optikokuyucu.omr.designer.SingleChoiceComponent
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint

@Composable
internal fun BookletAreaEditorScreen(
    document: DesignerDocument,
    draft: SingleChoiceComponent,
    patternText: String,
    onDraftChange: (SingleChoiceComponent) -> Unit,
    onPatternTextChange: (String) -> Unit,
    onCancel: () -> Unit,
    onComplete: (SingleChoiceComponent) -> Unit
) {
    val parsed = DesignerAreaCatalog.parseBookletPattern(patternText)
    val effective = parsed?.let { draft.copy(choices = it) } ?: draft
    val issue = if (parsed == null) "Kitapçık deseni en az 2 benzersiz değer içermelidir." else DesignerAreaCatalog.bookletAreaIssue(document, effective)

    Column(Modifier.fillMaxSize().safeDrawingPadding().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))) {
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary) {
            Row(Modifier.fillMaxWidth().padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("×") }
                Text("Yeni Optik Form Alanı", Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimary)
                TextButton(enabled = issue == null, onClick = { onComplete(effective) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("Tamam") }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tür · Kitapçık Türü", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Etiketi Gizle", Modifier.weight(1f)); Switch(checked = !draft.showLabel, onCheckedChange = { onDraftChange(draft.copy(showLabel = !it)) })
                    }
                    OutlinedTextField(draft.label, { if ('\n' !in it && '\r' !in it && it.length <= 60) onDraftChange(draft.copy(label = it)) }, Modifier.fillMaxWidth(), enabled = draft.showLabel, label = { Text("Etiket") }, singleLine = true)
                    Text("Etiket Hizası", style = MaterialTheme.typography.labelMedium)
                    AlignmentButtons(draft.labelAlignment) { onDraftChange(draft.copy(labelAlignment = it)) }
                    ComponentLabelTypographyControls(
                        draft.labelFontSize,
                        draft.labelBold,
                        { onDraftChange(draft.copy(labelFontSize = it)) },
                        { onDraftChange(draft.copy(labelBold = it)) }
                    )
                    Text("Yön", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BookletChoice(Modifier.weight(1f), "Yatay", draft.axis == ChoiceAxis.HORIZONTAL) { onDraftChange(draft.copy(axis = ChoiceAxis.HORIZONTAL)) }
                        BookletChoice(Modifier.weight(1f), "Dikey", draft.axis == ChoiceAxis.VERTICAL) { onDraftChange(draft.copy(axis = ChoiceAxis.VERTICAL)) }
                    }
                    OutlinedTextField(patternText, onPatternTextChange, Modifier.fillMaxWidth(), label = { Text("Desen") }, supportingText = { Text("AB, ABC, ABCD veya A,B,C,D") }, isError = parsed == null, singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DesignerAreaCatalog.bookletPatternPresets.forEach { preset -> BookletChoice(Modifier.weight(1f), preset, patternText == preset) { onPatternTextChange(preset) } }
                    }
                    CoordinateButtons("Sol Boşluk", draft.start.x, 0.0, document.space.width) { onDraftChange(draft.copy(start = TemplatePoint(it, draft.start.y))) }
                    CoordinateButtons("Üst Boşluk", draft.start.y, 0.0, document.space.height) { onDraftChange(draft.copy(start = TemplatePoint(draft.start.x, it))) }
                    Text("Baloncuk boyutu diğer tüm işaretleme alanlarıyla aynıdır.", style = MaterialTheme.typography.bodySmall)
                }
            }
            issue?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            BookletPreview(document, effective)
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
internal fun AlignmentButtons(value: DesignerTextAlignment, onChange: (DesignerTextAlignment) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BookletChoice(Modifier.weight(1f), "Sol", value == DesignerTextAlignment.START) { onChange(DesignerTextAlignment.START) }
        BookletChoice(Modifier.weight(1f), "Orta", value == DesignerTextAlignment.CENTER) { onChange(DesignerTextAlignment.CENTER) }
        BookletChoice(Modifier.weight(1f), "Sağ", value == DesignerTextAlignment.END) { onChange(DesignerTextAlignment.END) }
    }
}

@Composable
private fun CoordinateButtons(label: String, value: Double, min: Double, max: Double, onChange: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$label: ${value.toInt()}", Modifier.weight(1f))
        OutlinedButton(enabled = value > min, onClick = { onChange((value - 5.0).coerceAtLeast(min)) }) { Text("−") }
        OutlinedButton(enabled = value < max, onClick = { onChange((value + 5.0).coerceAtMost(max)) }) { Text("+") }
    }
}

@Composable
private fun BookletChoice(modifier: Modifier, label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) FilledTonalButton(modifier = modifier, onClick = onClick) { Text("$label ✓") } else OutlinedButton(modifier = modifier, onClick = onClick) { Text(label) }
}

@Composable
private fun BookletPreview(document: DesignerDocument, component: SingleChoiceComponent) {
    val preview = document.copy(components = listOf(component), visualElements = emptyList())
    val compiled = DesignerTemplateCompiler.compile(preview)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Canlı Önizleme", style = MaterialTheme.typography.labelLarge)
            Box(Modifier.fillMaxWidth().aspectRatio(document.space.aspectRatio.toFloat()).background(Color.White)) {
                Canvas(Modifier.fillMaxSize()) {
                    val sx = size.width / document.space.width.toFloat(); val sy = size.height / document.space.height.toFloat()
                    compiled.markGrids.singleOrNull()?.let { drawSingleChoice(component, it, sx, sy, Color(0xFFB54848)) }
                    drawComponentDecorations(preview, sx, sy)
                }
            }
        }
    }
}
