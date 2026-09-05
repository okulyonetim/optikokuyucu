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
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerOmrComponent
import com.okulyonetim.optikokuyucu.omr.designer.NumericGridComponent
import com.okulyonetim.optikokuyucu.omr.designer.QuestionGroupComponent
import com.okulyonetim.optikokuyucu.omr.designer.SingleChoiceComponent

@Composable
fun DesignerPropertyPanel(
    document: DesignerDocument,
    selectedComponentId: String?,
    onSelect: (String) -> Unit,
    onMove: (String, Double, Double) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Bileşenler", style = MaterialTheme.typography.titleSmall)

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

            DesignerPdfExportCard(document = document)
        }
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
            "${component.digits} hane"
    is SingleChoiceComponent ->
        "X ${component.start.x.toInt()} · Y ${component.start.y.toInt()} · " +
            component.choices.joinToString("/")
}
