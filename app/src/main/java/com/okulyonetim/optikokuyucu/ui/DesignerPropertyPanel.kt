package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.ChoiceAxis
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerOmrComponent
import com.okulyonetim.optikokuyucu.omr.designer.NumericGridComponent
import com.okulyonetim.optikokuyucu.omr.designer.QuestionGroupComponent
import com.okulyonetim.optikokuyucu.omr.designer.SingleChoiceComponent
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun DesignerPropertyPanel(
    document: DesignerDocument,
    selectedComponentId: String?,
    onSelect: (String) -> Unit,
    onMove: (String, Double, Double) -> Unit,
    onComponentChange: (DesignerOmrComponent) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("OMR Bileşenleri", style = MaterialTheme.typography.titleSmall)

            document.components.forEach { component ->
                if (component.id == selectedComponentId) {
                    FilledTonalButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelect(component.id) }
                    ) {
                        Text("✓ ${componentLabel(component)}")
                    }
                } else {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelect(component.id) }
                    ) {
                        Text(componentLabel(component))
                    }
                }
            }

            val selected = document.components.firstOrNull { it.id == selectedComponentId }
            if (selected != null) {
                Text(componentDetails(selected), style = MaterialTheme.typography.bodySmall)

                when (selected) {
                    is QuestionGroupComponent -> QuestionGroupControls(selected, onComponentChange)
                    is NumericGridComponent -> NumericGridControls(selected, onComponentChange)
                    is SingleChoiceComponent -> SingleChoiceControls(selected, onComponentChange)
                }

                Text("Konum · 5 canonical birim", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onMove(selected.id, -5.0, 0.0) }
                    ) { Text("←") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onMove(selected.id, 0.0, -5.0) }
                    ) { Text("↑") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onMove(selected.id, 0.0, 5.0) }
                    ) { Text("↓") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onMove(selected.id, 5.0, 0.0) }
                    ) { Text("→") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onDuplicate(selected.id) }
                    ) { Text("Çoğalt") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onDelete(selected.id) }
                    ) { Text("Sil") }
                }
            }
        }
    }
}

@Composable
private fun QuestionGroupControls(
    component: QuestionGroupComponent,
    onChange: (DesignerOmrComponent) -> Unit
) {
    Text("Soru grubu", style = MaterialTheme.typography.labelLarge)

    ValueStepper(
        label = "Soru sayısı",
        value = component.questionCount.toString(),
        canDecrease = component.questionCount > 1,
        onDecrease = {
            val count = max(1, component.questionCount - 5)
            onChange(component.copy(questionCount = count, columns = min(component.columns, count)))
        },
        onIncrease = {
            val count = min(250, component.questionCount + 5)
            onChange(component.copy(questionCount = count))
        }
    )
    ValueStepper(
        label = "İlk soru",
        value = component.startQuestion.toString(),
        canDecrease = component.startQuestion > 1,
        onDecrease = { onChange(component.copy(startQuestion = component.startQuestion - 1)) },
        onIncrease = { onChange(component.copy(startQuestion = component.startQuestion + 1)) }
    )
    ValueStepper(
        label = "Sütun",
        value = component.columns.toString(),
        canDecrease = component.columns > 1,
        canIncrease = component.columns < min(8, component.questionCount),
        onDecrease = { onChange(component.copy(columns = component.columns - 1)) },
        onIncrease = { onChange(component.copy(columns = component.columns + 1)) }
    )

    Text("Şıklar", style = MaterialTheme.typography.labelMedium)
    ChoicePresetRow(
        selected = component.choices,
        onSelected = { choices -> onChange(component.copy(choices = choices)) }
    )

    ValueStepper(
        label = "Balon yarıçapı",
        value = oneDecimal(component.bubbleRadius),
        canDecrease = component.bubbleRadius > 6.0,
        canIncrease = component.bubbleRadius < 25.0,
        onDecrease = { onChange(component.copy(bubbleRadius = max(6.0, component.bubbleRadius - 0.5))) },
        onIncrease = { onChange(component.copy(bubbleRadius = min(25.0, component.bubbleRadius + 0.5))) }
    )
    ValueStepper(
        label = "Şık aralığı",
        value = oneDecimal(component.choiceGap),
        canDecrease = component.choiceGap > 16.0,
        onDecrease = { onChange(component.copy(choiceGap = max(16.0, component.choiceGap - 2.0))) },
        onIncrease = { onChange(component.copy(choiceGap = min(180.0, component.choiceGap + 2.0))) }
    )
    ValueStepper(
        label = "Satır aralığı",
        value = oneDecimal(component.rowGap),
        canDecrease = component.rowGap > 16.0,
        onDecrease = { onChange(component.copy(rowGap = max(16.0, component.rowGap - 2.0))) },
        onIncrease = { onChange(component.copy(rowGap = min(180.0, component.rowGap + 2.0))) }
    )
    ValueStepper(
        label = "Sütun aralığı",
        value = oneDecimal(component.columnGap),
        canDecrease = component.columnGap > 40.0,
        onDecrease = { onChange(component.copy(columnGap = max(40.0, component.columnGap - 5.0))) },
        onIncrease = { onChange(component.copy(columnGap = min(900.0, component.columnGap + 5.0))) }
    )
}

@Composable
private fun NumericGridControls(
    component: NumericGridComponent,
    onChange: (DesignerOmrComponent) -> Unit
) {
    Text("Sayısal işaret alanı", style = MaterialTheme.typography.labelLarge)
    ValueStepper(
        label = "Hane sayısı",
        value = component.digits.toString(),
        canDecrease = component.digits > 1,
        canIncrease = component.digits < 16,
        onDecrease = { onChange(component.copy(digits = component.digits - 1)) },
        onIncrease = { onChange(component.copy(digits = component.digits + 1)) }
    )
    ValueStepper(
        label = "Balon yarıçapı",
        value = oneDecimal(component.bubbleRadius),
        canDecrease = component.bubbleRadius > 6.0,
        canIncrease = component.bubbleRadius < 25.0,
        onDecrease = { onChange(component.copy(bubbleRadius = max(6.0, component.bubbleRadius - 0.5))) },
        onIncrease = { onChange(component.copy(bubbleRadius = min(25.0, component.bubbleRadius + 0.5))) }
    )
    ValueStepper(
        label = "Hane aralığı",
        value = oneDecimal(component.columnGap),
        canDecrease = component.columnGap > 16.0,
        onDecrease = { onChange(component.copy(columnGap = max(16.0, component.columnGap - 2.0))) },
        onIncrease = { onChange(component.copy(columnGap = min(160.0, component.columnGap + 2.0))) }
    )
    ValueStepper(
        label = "Rakam aralığı",
        value = oneDecimal(component.rowGap),
        canDecrease = component.rowGap > 16.0,
        onDecrease = { onChange(component.copy(rowGap = max(16.0, component.rowGap - 2.0))) },
        onIncrease = { onChange(component.copy(rowGap = min(120.0, component.rowGap + 2.0))) }
    )
}

@Composable
private fun SingleChoiceControls(
    component: SingleChoiceComponent,
    onChange: (DesignerOmrComponent) -> Unit
) {
    Text("Tek seçim alanı", style = MaterialTheme.typography.labelLarge)
    Text("Seçenekler", style = MaterialTheme.typography.labelMedium)
    ChoicePresetRow(
        selected = component.choices,
        onSelected = { choices -> onChange(component.copy(choices = choices)) }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val horizontal = component.axis == ChoiceAxis.HORIZONTAL
        if (horizontal) {
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                onClick = { }
            ) { Text("Yatay ✓") }
        } else {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { onChange(component.copy(axis = ChoiceAxis.HORIZONTAL)) }
            ) { Text("Yatay") }
        }
        if (!horizontal) {
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                onClick = { }
            ) { Text("Dikey ✓") }
        } else {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { onChange(component.copy(axis = ChoiceAxis.VERTICAL)) }
            ) { Text("Dikey") }
        }
    }

    ValueStepper(
        label = "Balon yarıçapı",
        value = oneDecimal(component.bubbleRadius),
        canDecrease = component.bubbleRadius > 6.0,
        canIncrease = component.bubbleRadius < 25.0,
        onDecrease = { onChange(component.copy(bubbleRadius = max(6.0, component.bubbleRadius - 0.5))) },
        onIncrease = { onChange(component.copy(bubbleRadius = min(25.0, component.bubbleRadius + 0.5))) }
    )
    ValueStepper(
        label = "Seçenek aralığı",
        value = oneDecimal(component.gap),
        canDecrease = component.gap > 16.0,
        onDecrease = { onChange(component.copy(gap = max(16.0, component.gap - 2.0))) },
        onIncrease = { onChange(component.copy(gap = min(180.0, component.gap + 2.0))) }
    )
}

@Composable
private fun ChoicePresetRow(
    selected: List<String>,
    onSelected: (List<String>) -> Unit
) {
    val presets = listOf(
        listOf("A", "B"),
        listOf("A", "B", "C"),
        listOf("A", "B", "C", "D"),
        listOf("A", "B", "C", "D", "E")
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        presets.forEach { preset ->
            val label = preset.joinToString("")
            if (selected == preset) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = { }
                ) { Text("$label ✓") }
            } else {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onSelected(preset) }
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun ValueStepper(
    label: String,
    value: String,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(enabled = canDecrease, onClick = onDecrease) { Text("−") }
        OutlinedButton(enabled = canIncrease, onClick = onIncrease) { Text("+") }
    }
}

private fun componentLabel(component: DesignerOmrComponent): String = when (component) {
    is QuestionGroupComponent -> "${component.id} · ${component.questionCount} soru"
    is NumericGridComponent -> "${component.id} · ${component.digits} haneli sayı"
    is SingleChoiceComponent -> "${component.id} · ${component.choices.joinToString("/")}"
}

private fun componentDetails(component: DesignerOmrComponent): String = when (component) {
    is QuestionGroupComponent ->
        "X ${component.firstChoiceX.toInt()} · Y ${component.topY.toInt()} · " +
            "${component.columns} sütun · ${component.choices.joinToString("")}"
    is NumericGridComponent ->
        "X ${component.startX.toInt()} · Y ${component.topY.toInt()} · " +
            "${component.digits} hane · ${component.values.joinToString("")}"
    is SingleChoiceComponent ->
        "X ${component.start.x.toInt()} · Y ${component.start.y.toInt()} · " +
            "${component.choices.joinToString("/")} · " +
            if (component.axis == ChoiceAxis.HORIZONTAL) "yatay" else "dikey"
}

private fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)
