package com.okulyonetim.optikokuyucu.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerExamMode
import com.okulyonetim.optikokuyucu.omr.designer.DesignerExamPreset
import com.okulyonetim.optikokuyucu.omr.designer.DesignerFormSpec
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPageOrientation
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPaperSize
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import kotlin.math.sqrt

/**
 * Friendly optical-form entry screen.
 *
 * DesignerDocument is intentionally the single source of truth for form metadata. The legacy
 * structured config can still build old presets, but this screen no longer mirrors paper/exam
 * state into a second editor model. Later field-editing stages will extend this same document.
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
        mutableStateOf(
            DesignerDocument(
                id = "form-${System.currentTimeMillis()}",
                version = 1,
                name = "Yeni Optik Form",
                components = emptyList(),
                visualElements = emptyList(),
                formSpec = DesignerFormSpec()
            )
        )
    }
    var formName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
    ) {
        ReferenceEditorTopBar(
            onBack = onBack,
            onSave = ::saveDocument
        )

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
                onExamModeChange = { selected ->
                    updateFormSpec { it.copy(examMode = selected) }
                },
                onExamPresetChange = { selected ->
                    updateFormSpec { it.copy(examPreset = selected) }
                },
                onPaperSizeChange = { selected ->
                    updateFormSpec { it.copy(paperSize = selected) }
                },
                onOrientationChange = { selected ->
                    updateFormSpec { it.copy(orientation = selected) }
                }
            )

            OpticalFormAreaHeader(
                onAdd = {
                    status = "Alan ekleme sistemi bir sonraki alan-editörü aşamasında etkinleştirilecek."
                }
            )

            PaperWorkspace(document.formSpec)

            if (status.isNotBlank()) {
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    color = if (status.startsWith("Kaydedildi")) {
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
}

@Composable
private fun ReferenceEditorTopBar(
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text("×", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "Yeni Optik Form",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            TextButton(
                onClick = onSave,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text("Kaydet", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
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
            Text(
                text = "Optik Form Bilgileri",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "* Zorunlu Alanlar",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )

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
                displayValue = if (formSpec.examMode == DesignerExamMode.UNSPECIFIED) {
                    "Seçiniz"
                } else {
                    formSpec.examMode.displayName
                },
                options = listOf(
                    DesignerExamMode.SINGLE_LESSON,
                    DesignerExamMode.MULTI_LESSON
                ),
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReferenceDropdownField(
                    label = "Kağıt",
                    displayValue = formSpec.paperSize.displayName,
                    options = listOf(
                        DesignerPaperSize.A3,
                        DesignerPaperSize.A4,
                        DesignerPaperSize.A5,
                        DesignerPaperSize.A6,
                        DesignerPaperSize.A7
                    ),
                    optionLabel = { it.displayName },
                    onSelected = onPaperSizeChange,
                    modifier = Modifier.weight(1f)
                )
                ReferenceDropdownField(
                    label = "Yön",
                    displayValue = formSpec.orientation.displayName,
                    options = listOf(
                        DesignerPageOrientation.PORTRAIT,
                        DesignerPageOrientation.LANDSCAPE
                    ),
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
            Text(
                text = label,
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                shape = RoundedCornerShape(50.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayValue,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    Text("⌄", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Optik Form Alanı",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onAdd),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("+", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PaperWorkspace(formSpec: DesignerFormSpec) {
    val isoAspect = sqrt(2.0).toFloat()
    val widthToHeight = if (formSpec.orientation == DesignerPageOrientation.PORTRAIT) {
        1f / isoAspect
    } else {
        isoAspect
    }
    val gridColor = Color(0xFFE5E8EF)
    val paperBorder = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .aspectRatio(widthToHeight)
                .background(Color.White, RoundedCornerShape(8.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 18.dp.toPx()
                var x = step
                while (x < size.width) {
                    drawLine(
                        color = gridColor,
                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(
                        color = gridColor,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += step
                }
                drawRect(
                    color = paperBorder,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                )
            }
        }
    }
}
