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
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextAlignment
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerVisualElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerVisualGeometry
import com.okulyonetim.optikokuyucu.omr.designer.VisualHorizontalAlignment
import com.okulyonetim.optikokuyucu.omr.designer.VisualVerticalAlignment
import com.okulyonetim.optikokuyucu.omr.designer.VisualZOrderAction

@Composable
fun DesignerVisualPropertyPanel(
    document: DesignerDocument,
    selectedElementId: String?,
    onSelect: (String) -> Unit,
    onAddText: () -> Unit,
    onAddBox: () -> Unit,
    onAddLine: () -> Unit,
    onMove: (String, Double, Double) -> Unit,
    onResize: (String, Double, Double) -> Unit,
    onAlignHorizontal: (String, VisualHorizontalAlignment) -> Unit,
    onAlignVertical: (String, VisualVerticalAlignment) -> Unit,
    onZOrder: (String, VisualZOrderAction) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onLockedChange: (String, Boolean) -> Unit,
    onTextChange: (String, String) -> Unit,
    onFontSizeChange: (String, Double) -> Unit,
    onTextAlignmentChange: (String, DesignerTextAlignment) -> Unit,
    onBoldChange: (String, Boolean) -> Unit,
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
                        onFontSizeChange = onFontSizeChange,
                        onTextAlignmentChange = onTextAlignmentChange,
                        onBoldChange = onBoldChange
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

                when (selected) {
                    is DesignerTextElement,
                    is DesignerBoxElement -> {
                        Text("Boyut · 10 canonical birim", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = !selected.locked,
                                onClick = { onResize(selected.id, -10.0, 0.0) }
                            ) { Text("W−") }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = !selected.locked,
                                onClick = { onResize(selected.id, 10.0, 0.0) }
                            ) { Text("W+") }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = !selected.locked,
                                onClick = { onResize(selected.id, 0.0, -10.0) }
                            ) { Text("H−") }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = !selected.locked,
                                onClick = { onResize(selected.id, 0.0, 10.0) }
                            ) { Text("H+") }
                        }
                    }
                    is DesignerLineElement -> {
                        Text("Çizgi uç noktası · 10 canonical birim", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = !selected.locked,
                                onClick = { onResize(selected.id, -10.0, 0.0) }
                            ) { Text("X−") }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = !selected.locked,
                                onClick = { onResize(selected.id, 10.0, 0.0) }
                            ) { Text("X+") }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = !selected.locked,
                                onClick = { onResize(selected.id, 0.0, -10.0) }
                            ) { Text("Y−") }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = !selected.locked,
                                onClick = { onResize(selected.id, 0.0, 10.0) }
                            ) { Text("Y+") }
                        }
                    }
                }

                Text("Sayfaya hizala", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !selected.locked,
                        onClick = {
                            onAlignHorizontal(selected.id, VisualHorizontalAlignment.LEFT)
                        }
                    ) { Text("Sol") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !selected.locked,
                        onClick = {
                            onAlignHorizontal(selected.id, VisualHorizontalAlignment.CENTER)
                        }
                    ) { Text("Orta") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !selected.locked,
                        onClick = {
                            onAlignHorizontal(selected.id, VisualHorizontalAlignment.RIGHT)
                        }
                    ) { Text("Sağ") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !selected.locked,
                        onClick = {
                            onAlignVertical(selected.id, VisualVerticalAlignment.TOP)
                        }
                    ) { Text("Üst") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !selected.locked,
                        onClick = {
                            onAlignVertical(selected.id, VisualVerticalAlignment.CENTER)
                        }
                    ) { Text("Orta") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !selected.locked,
                        onClick = {
                            onAlignVertical(selected.id, VisualVerticalAlignment.BOTTOM)
                        }
                    ) { Text("Alt") }
                }

                Text("Katman sırası", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !selected.locked,
                        onClick = { onZOrder(selected.id, VisualZOrderAction.SEND_TO_BACK) }
                    ) { Text("En alt") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !selected.locked,
                        onClick = { onZOrder(selected.id, VisualZOrderAction.SEND_BACKWARD) }
                    ) { Text("Geri") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !selected.locked,
                        onClick = { onZOrder(selected.id, VisualZOrderAction.BRING_FORWARD) }
                    ) { Text("İleri") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !selected.locked,
                        onClick = { onZOrder(selected.id, VisualZOrderAction.BRING_TO_FRONT) }
                    ) { Text("En üst") }
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
    onFontSizeChange: (String, Double) -> Unit,
    onTextAlignmentChange: (String, DesignerTextAlignment) -> Unit,
    onBoldChange: (String, Boolean) -> Unit
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Kalın", style = MaterialTheme.typography.labelMedium)
            Text(if (element.bold) "Açık" else "Kapalı", style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = element.bold,
            enabled = !element.locked,
            onCheckedChange = { onBoldChange(element.id, it) }
        )
    }

    Text("Metin hizası", style = MaterialTheme.typography.labelMedium)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TextAlignmentButton(
            modifier = Modifier.weight(1f),
            label = "Sol",
            selected = element.alignment == DesignerTextAlignment.START,
            enabled = !element.locked,
            onClick = { onTextAlignmentChange(element.id, DesignerTextAlignment.START) }
        )
        TextAlignmentButton(
            modifier = Modifier.weight(1f),
            label = "Orta",
            selected = element.alignment == DesignerTextAlignment.CENTER,
            enabled = !element.locked,
            onClick = { onTextAlignmentChange(element.id, DesignerTextAlignment.CENTER) }
        )
        TextAlignmentButton(
            modifier = Modifier.weight(1f),
            label = "Sağ",
            selected = element.alignment == DesignerTextAlignment.END,
            enabled = !element.locked,
            onClick = { onTextAlignmentChange(element.id, DesignerTextAlignment.END) }
        )
    }
}

@Composable
private fun TextAlignmentButton(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        FilledTonalButton(modifier = modifier, enabled = enabled, onClick = onClick) {
            Text(label)
        }
    } else {
        OutlinedButton(modifier = modifier, enabled = enabled, onClick = onClick) {
            Text(label)
        }
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
