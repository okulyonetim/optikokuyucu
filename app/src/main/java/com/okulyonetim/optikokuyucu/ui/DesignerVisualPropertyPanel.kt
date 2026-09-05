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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.DesignerBoxElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerLineElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerVisualElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerVisualGeometry

@Composable
fun DesignerVisualPropertyPanel(
    document: DesignerDocument,
    selectedElementId: String?,
    onSelect: (String) -> Unit,
    onAddText: () -> Unit,
    onAddBox: () -> Unit,
    onAddLine: () -> Unit,
    onMove: (String, Double, Double) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onLockedChange: (String, Boolean) -> Unit,
    onTextChange: (String, String) -> Unit,
    onFontSizeChange: (String, Double) -> Unit,
    onStrokeWidthChange: (String, Double) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Görsel Katman", style = MaterialTheme.typography.titleSmall)
            Text(
                "Metin, kutu ve çizgiler PDF'de görünür; OMR geometrisinden ayrı düzenlenir.",
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onAddText) {
                    Text("Metin")
                }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onAddBox) {
                    Text("Kutu")
                }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onAddLine) {
                    Text("Çizgi")
                }
            }

            if (document.visualElements.isEmpty()) {
                Text("Henüz görsel öğe yok.", style = MaterialTheme.typography.bodySmall)
            } else {
                document.visualElements.forEach { element ->
                    if (element.id == selectedElementId) {
                        FilledTonalButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelect(element.id) }
                        ) {
                            Text("✓ ${visualLabel(element)}")
                        }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelect(element.id) }
                        ) {
                            Text(visualLabel(element))
                        }
                    }
                }
            }

            val selected = document.visualElements.firstOrNull { it.id == selectedElementId }
            if (selected != null) {
                val bounds = DesignerVisualGeometry.bounds(selected)
                Text(
                    "X ${bounds.left.toInt()} · Y ${bounds.top.toInt()} · " +
                        "W ${bounds.width.toInt()} · H ${bounds.height.toInt()}",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Kilitle", style = MaterialTheme.typography.labelMedium)
                        Text(
                            if (selected.locked) "Taşıma ve düzenleme kapalı" else "Düzenlenebilir",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = selected.locked,
                        onCheckedChange = { onLockedChange(selected.id, it) }
                    )
                }

                when (selected) {
                    is DesignerTextElement -> TextProperties(
                        element = selected,
                        onTextChange = onTextChange,
                        onFontSizeChange = onFontSizeChange
                    )
                    is DesignerBoxElement -> StrokeProperties(
                        id = selected.id,
                        strokeWidth = selected.strokeWidth,
                        locked = selected.locked,
                        onStrokeWidthChange = onStrokeWidthChange
                    )
                    is DesignerLineElement -> StrokeProperties(
                        id = selected.id,
                        strokeWidth = selected.strokeWidth,
                        locked = selected.locked,
                        onStrokeWidthChange = onStrokeWidthChange
                    )
                }

                Text("Konum · 5 canonical birim", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !selected.locked,
                        onClick = { onMove(selected.id, -5.0, 0.0) }
                    ) { Text("←") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !selected.locked,
                        onClick = { onMove(selected.id, 0.0, -5.0) }
                    ) { Text("↑") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !selected.locked,
                        onClick = { onMove(selected.id, 0.0, 5.0) }
                    ) { Text("↓") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !selected.locked,
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
                        enabled = !selected.locked,
                        onClick = { onDelete(selected.id) }
                    ) { Text("Sil") }
                }
            }
        }
    }
}

@Composable
private fun TextProperties(
    element: DesignerTextElement,
    onTextChange: (String, String) -> Unit,
    onFontSizeChange: (String, Double) -> Unit
) {
    var draft by remember(element.id, element.text) { mutableStateOf(element.text) }

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = draft,
        enabled = !element.locked,
        onValueChange = { next ->
            draft = next
            if (next.isNotEmpty()) onTextChange(element.id, next)
        },
        label = { Text("Metin") },
        supportingText = {
            if (draft.isEmpty()) Text("Metin boş bırakılamaz; yeni değer yazınca kaydedilir.")
        }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Yazı ${element.fontSize.toInt()}", modifier = Modifier.weight(1f))
        OutlinedButton(
            enabled = !element.locked,
            onClick = { onFontSizeChange(element.id, (element.fontSize - 2.0).coerceAtLeast(6.0)) }
        ) { Text("−") }
        OutlinedButton(
            enabled = !element.locked,
            onClick = { onFontSizeChange(element.id, (element.fontSize + 2.0).coerceAtMost(96.0)) }
        ) { Text("+") }
    }
}

@Composable
private fun StrokeProperties(
    id: String,
    strokeWidth: Double,
    locked: Boolean,
    onStrokeWidthChange: (String, Double) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Çizgi ${"%.1f".format(strokeWidth)}", modifier = Modifier.weight(1f))
        OutlinedButton(
            enabled = !locked,
            onClick = { onStrokeWidthChange(id, (strokeWidth - 0.5).coerceAtLeast(0.5)) }
        ) { Text("−") }
        OutlinedButton(
            enabled = !locked,
            onClick = { onStrokeWidthChange(id, (strokeWidth + 0.5).coerceAtMost(12.0)) }
        ) { Text("+") }
    }
}

private fun visualLabel(element: DesignerVisualElement): String {
    val type = when (element) {
        is DesignerTextElement -> "Metin · ${element.text.take(24)}"
        is DesignerBoxElement -> "Kutu"
        is DesignerLineElement -> "Çizgi"
    }
    return if (element.locked) "$type · kilitli" else type
}
