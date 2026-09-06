package com.okulyonetim.optikokuyucu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.DesignerAreaCatalog
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerImageElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPageGeometry
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPersonalizedTextBinding
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextAlignment
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextElement

@Composable
internal fun DescriptionAreaEditorScreen(
    document: DesignerDocument,
    draft: DesignerTextElement,
    onDraftChange: (DesignerTextElement) -> Unit,
    onCancel: () -> Unit,
    onComplete: (DesignerTextElement) -> Unit
) {
    val personalizedField = DesignerPersonalizedTextBinding.fieldForId(draft.id)
    val issue = DesignerAreaCatalog.descriptionAreaIssue(document, draft)
    InfoAreaScaffold(
        completeEnabled = issue == null,
        onCancel = onCancel,
        onComplete = { onComplete(draft) }
    ) {
        InfoEditorCard {
            InfoReadOnlyField("Tür", personalizedField?.displayName ?: "Açıklama")
            if (personalizedField != null) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Etiketi Göster", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (draft.showPersonalizedLabel) {
                                "Kişisel formda etiket ve değer birlikte yazılır."
                            } else {
                                "Kişisel formda yalnızca otomatik değer yazılır."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = draft.showPersonalizedLabel,
                        onCheckedChange = { onDraftChange(draft.copy(showPersonalizedLabel = it)) }
                    )
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.text,
                onValueChange = {
                    if (it.isNotEmpty() && it.length <= 2_000) onDraftChange(draft.copy(text = it))
                },
                label = { Text(if (personalizedField == null) "Metin / Açıklama" else "Etiket Metni") },
                supportingText = {
                    if (personalizedField == null) {
                        Text("${draft.text.length}/2000")
                    } else {
                        Text(
                            if (draft.showPersonalizedLabel) {
                                "Örnek: ${DesignerPersonalizedTextBinding.render(draft, "ÖRNEK DEĞER")}" 
                            } else {
                                "Örnek: ÖRNEK DEĞER"
                            }
                        )
                    }
                },
                minLines = if (personalizedField == null) 4 else 1,
                maxLines = if (personalizedField == null) 8 else 2,
                shape = RoundedCornerShape(14.dp)
            )
            InfoNumberStepper("Yazı Boyutu", draft.fontSize.toInt(), 8, 72) {
                onDraftChange(draft.copy(fontSize = it.toDouble()))
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Kalın", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (draft.bold) "Açık" else "Kapalı",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = draft.bold, onCheckedChange = { onDraftChange(draft.copy(bold = it)) })
            }
            Text("Metin Hizası", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoChoiceButton(
                    Modifier.weight(1f), "Sol", draft.alignment == DesignerTextAlignment.START
                ) { onDraftChange(draft.copy(alignment = DesignerTextAlignment.START)) }
                InfoChoiceButton(
                    Modifier.weight(1f), "Orta", draft.alignment == DesignerTextAlignment.CENTER
                ) { onDraftChange(draft.copy(alignment = DesignerTextAlignment.CENTER)) }
                InfoChoiceButton(
                    Modifier.weight(1f), "Sağ", draft.alignment == DesignerTextAlignment.END
                ) { onDraftChange(draft.copy(alignment = DesignerTextAlignment.END)) }
            }
        }
        if (issue != null) InfoIssue(issue)
        InfoVisualPreview(document, listOf(draft))
    }
}

@Composable
internal fun ImageAreaEditorScreen(
    document: DesignerDocument,
    draft: DesignerImageElement?,
    onDraftChange: (DesignerImageElement) -> Unit,
    onCancel: () -> Unit,
    onComplete: (DesignerImageElement) -> Unit
) {
    val context = LocalContext.current
    var importError by remember { mutableStateOf<String?>(null) }
    val issue = draft?.let { DesignerAreaCatalog.imageAreaIssue(document, it) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importDesignerImage(context, uri).onSuccess { data ->
                importError = null
                val next = if (draft == null) {
                    DesignerAreaCatalog.createImageArea(document, data)
                } else {
                    draft.copy(image = data)
                }
                onDraftChange(next)
            }.onFailure { error ->
                importError = error.message ?: "Resim yüklenemedi."
            }
        }
    }

    InfoAreaScaffold(
        completeEnabled = draft != null && issue == null && importError == null,
        onCancel = onCancel,
        onComplete = { draft?.let(onComplete) }
    ) {
        InfoEditorCard {
            InfoReadOnlyField("Tür", "Resim")
            Text(
                "Galeriden bir resim seçin. Form, dış bağlantı yerine çevrimdışı kullanılabilen sıkıştırılmış bir kopya saklar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { launcher.launch("image/*") }
            ) {
                Text(if (draft == null) "Resim Seç" else "Resmi Değiştir")
            }
            if (draft != null) {
                Text(
                    "${draft.image.pixelWidth} × ${draft.image.pixelHeight} px · " +
                        "${(draft.image.byteSize / 1024.0).toInt()} KB · JPEG",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        importError?.let { InfoIssue(it) }
        if (issue != null) InfoIssue(issue)
        if (draft != null) InfoVisualPreview(document, listOf(draft))
    }
}

@Composable
private fun InfoAreaScaffold(
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
                    "Yeni Optik Form Alanı",
                    modifier = Modifier.weight(1f),
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
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun InfoEditorCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
private fun InfoReadOnlyField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelSmall)
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
private fun InfoNumberStepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label: $value", modifier = Modifier.weight(1f))
        OutlinedButton(enabled = value > min, onClick = { onChange(value - 1) }) { Text("−") }
        OutlinedButton(enabled = value < max, onClick = { onChange(value + 1) }) { Text("+") }
    }
}

@Composable
private fun InfoChoiceButton(modifier: Modifier, label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) FilledTonalButton(modifier = modifier, onClick = onClick) { Text("$label ✓") }
    else OutlinedButton(modifier = modifier, onClick = onClick) { Text(label) }
}

@Composable
private fun InfoIssue(message: String) {
    Text(
        message,
        modifier = Modifier.padding(horizontal = 6.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun InfoVisualPreview(document: DesignerDocument, elements: List<com.okulyonetim.optikokuyucu.omr.designer.DesignerVisualElement>) {
    val bitmaps = rememberDesignerImageBitmaps(elements)
    val safe = remember(document.space) { DesignerPageGeometry.safeArea(document.space) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Canlı Önizleme", style = MaterialTheme.typography.labelLarge)
            Text(
                "Bilgilendirme alanı belgeyle aynı canonical sayfa koordinatlarında çizilir.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio((document.space.width / document.space.height).toFloat())
                    .background(Color.White, RoundedCornerShape(6.dp))
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val sx = size.width / document.space.width.toFloat()
                    val sy = size.height / document.space.height.toFloat()
                    drawRect(
                        color = Color(0xFFD7DCE6),
                        topLeft = androidx.compose.ui.geometry.Offset(safe.left.toFloat() * sx, safe.top.toFloat() * sy),
                        size = androidx.compose.ui.geometry.Size(safe.width.toFloat() * sx, safe.height.toFloat() * sy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                    )
                    drawDesignerVisualElements(elements, bitmaps, sx, sy)
                    document.fiducials.forEach { marker ->
                        drawRect(
                            Color.Black,
                            androidx.compose.ui.geometry.Offset(marker.bounds.left.toFloat() * sx, marker.bounds.top.toFloat() * sy),
                            androidx.compose.ui.geometry.Size(marker.bounds.width.toFloat() * sx, marker.bounds.height.toFloat() * sy)
                        )
                    }
                }
            }
        }
    }
}
