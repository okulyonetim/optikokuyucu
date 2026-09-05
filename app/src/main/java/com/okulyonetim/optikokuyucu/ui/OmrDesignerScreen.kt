package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerHistory
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.NumericGridComponent
import com.okulyonetim.optikokuyucu.omr.designer.SingleChoiceComponent
import com.okulyonetim.optikokuyucu.omr.designer.TemplateReadabilityAnalyzer
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint

@Composable
fun OmrDesignerScreen(onBack: () -> Unit) {
    val starters = remember { DesignerStarterTemplates.all() }
    val initialDocument = remember { starters.first() }
    val history = remember { DesignerHistory(initialDocument) }
    var document by remember { mutableStateOf(initialDocument) }
    var showOmrRegions by remember { mutableStateOf(true) }

    val compiled = remember(document) { DesignerTemplateCompiler.compile(document) }
    val readability = remember(compiled) { TemplateReadabilityAnalyzer.analyze(compiled) }

    fun commit(next: DesignerDocument) {
        document = history.commit(next)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Geri") }
            Text("Optik Form Tasarımcısı", style = MaterialTheme.typography.titleLarge)
        }

        Text(
            "Editör ve okuyucu aynı canonical form koordinatlarını kullanır. " +
                "Kağıt boyutu yalnız baskı/dışa aktarma katmanında belirlenir.",
            style = MaterialTheme.typography.bodySmall
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Başlangıç şablonları", style = MaterialTheme.typography.titleSmall)
                starters.forEach { starter ->
                    if (starter.id == document.id) {
                        FilledTonalButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { }
                        ) {
                            Text("✓ ${starter.name}")
                        }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                history.reset(starter)
                                document = starter
                            }
                        ) {
                            Text(starter.name)
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(document.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${compiled.bubbleRows.size} soru · " +
                                "${compiled.markGrids.size} işaret alanı",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("OMR bölgeleri", style = MaterialTheme.typography.labelMedium)
                        Switch(
                            checked = showOmrRegions,
                            onCheckedChange = { showOmrRegions = it }
                        )
                    }
                }

                CanonicalTemplatePreview(
                    template = compiled,
                    showOmrRegions = showOmrRegions
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (readability.canSave) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Okunabilirlik ${readability.score}/100",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    if (readability.canSave) {
                        "Kaydetme kapısı açık · ${readability.warningCount} uyarı"
                    } else {
                        "Kaydetme engellendi · ${readability.errorCount} hata"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                readability.issues.take(4).forEach { issue ->
                    Text("• ${issue.message}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = history.canUndo(),
                onClick = { document = history.undo() }
            ) {
                Text("Geri Al")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = history.canRedo(),
                onClick = { document = history.redo() }
            ) {
                Text("Yinele")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = document.components.none { it.id == "studentNumber" },
                onClick = {
                    commit(
                        document.copy(
                            components = document.components + NumericGridComponent(
                                id = "studentNumber",
                                digits = 6,
                                startX = 120.0,
                                topY = 830.0,
                                bubbleRadius = 10.0,
                                columnGap = 45.0,
                                rowGap = 34.0
                            )
                        )
                    )
                }
            ) {
                Text("Öğrenci No Ekle")
            }

            Button(
                modifier = Modifier.weight(1f),
                enabled = document.components.none { it.id == "booklet" },
                onClick = {
                    commit(
                        document.copy(
                            components = document.components + SingleChoiceComponent(
                                id = "booklet",
                                choices = listOf("A", "B"),
                                start = TemplatePoint(155.0, 1210.0),
                                bubbleRadius = 12.0,
                                gap = 60.0
                            )
                        )
                    )
                }
            ) {
                Text("Kitapçık A/B Ekle")
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = readability.canSave,
            onClick = { }
        ) {
            Text("Şablonu Kaydet · sonraki paket")
        }
    }
}

@Composable
private fun CanonicalTemplatePreview(
    template: OmrTemplate,
    showOmrRegions: Boolean
) {
    val aspect = (template.space.width / template.space.height).toFloat()
    val paperColor = Color.White
    val omrColor = MaterialTheme.colorScheme.primary
    val markerColor = Color.Black

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .background(paperColor, RoundedCornerShape(6.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaleX = size.width / template.space.width.toFloat()
            val scaleY = size.height / template.space.height.toFloat()

            fun x(value: Double): Float = value.toFloat() * scaleX
            fun y(value: Double): Float = value.toFloat() * scaleY
            fun radius(value: Double): Float = value.toFloat() * ((scaleX + scaleY) / 2f)

            template.fiducials.forEach { fiducial ->
                drawRect(
                    color = markerColor,
                    topLeft = Offset(x(fiducial.bounds.left), y(fiducial.bounds.top)),
                    size = Size(x(fiducial.bounds.width), y(fiducial.bounds.height))
                )
            }

            if (showOmrRegions) {
                template.bubbleRows.forEach { row ->
                    row.bubbles.forEach { bubble ->
                        drawCircle(
                            color = omrColor,
                            radius = radius(bubble.radius),
                            center = Offset(x(bubble.center.x), y(bubble.center.y)),
                            style = Stroke(width = 1.4f)
                        )
                    }
                }

                template.markGrids.forEach { grid ->
                    grid.columns.forEach { column ->
                        column.marks.forEach { mark ->
                            drawCircle(
                                color = omrColor,
                                radius = radius(mark.radius),
                                center = Offset(x(mark.center.x), y(mark.center.y)),
                                style = Stroke(width = 1.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}
