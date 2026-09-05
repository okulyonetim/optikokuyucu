package com.okulyonetim.optikokuyucu.ui

import android.graphics.Paint as AndroidPaint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.DesignerBoxElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerComponentGeometry
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocumentEditor
import com.okulyonetim.optikokuyucu.omr.designer.DesignerHistory
import com.okulyonetim.optikokuyucu.omr.designer.DesignerLineElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerResizeHandleGeometry
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextAlignment
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerVisualGeometry
import com.okulyonetim.optikokuyucu.omr.designer.DesignerVisualTransform
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.designer.NumericGridComponent
import com.okulyonetim.optikokuyucu.omr.designer.SingleChoiceComponent
import com.okulyonetim.optikokuyucu.omr.designer.TemplateReadabilityAnalyzer
import com.okulyonetim.optikokuyucu.omr.diagnostics.OmrSelfTestResult
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect

private sealed interface DesignerCanvasSelection {
    val id: String

    data class Component(override val id: String) : DesignerCanvasSelection
    data class Visual(override val id: String) : DesignerCanvasSelection
    data class VisualResize(override val id: String) : DesignerCanvasSelection
}

@Composable
fun OmrDesignerScreen(
    openCvReady: Boolean,
    selfTest: OmrSelfTestResult,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) {
        FileDesignerDocumentRepository(context.applicationContext)
    }
    val starters = remember { DesignerStarterTemplates.all() }
    val initialDocument = remember { starters.first() }
    val history = remember { DesignerHistory(initialDocument) }
    var document by remember { mutableStateOf(initialDocument) }
    var selection by remember { mutableStateOf<DesignerCanvasSelection?>(null) }
    var showOmrRegions by remember { mutableStateOf(true) }
    var savedDocuments by remember { mutableStateOf(repository.list()) }
    var saveStatus by remember { mutableStateOf<String?>(null) }
    var testCamera by remember { mutableStateOf(false) }

    val compiled = remember(document) { DesignerTemplateCompiler.compile(document) }
    val readability = remember(document, compiled) {
        TemplateReadabilityAnalyzer.analyze(document, compiled)
    }

    if (testCamera) {
        BackHandler { testCamera = false }
        Box(modifier = Modifier.fillMaxSize()) {
            OmrCameraScreen(
                openCvReady = openCvReady,
                selfTest = selfTest,
                template = compiled
            )
            TextButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 16.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                        RoundedCornerShape(12.dp)
                    ),
                onClick = { testCamera = false }
            ) {
                Text("← Tasarımcıya dön")
            }
        }
        return
    }

    fun commit(next: DesignerDocument) {
        document = history.commit(next)
        saveStatus = null
    }

    fun openDocument(next: DesignerDocument) {
        history.reset(next)
        document = next
        selection = null
        saveStatus = null
    }

    fun nextComponentDuplicateId(sourceId: String): String {
        var suffix = 1
        var candidate = "$sourceId-copy$suffix"
        while (document.components.any { it.id == candidate }) {
            suffix += 1
            candidate = "$sourceId-copy$suffix"
        }
        return candidate
    }

    fun nextVisualId(prefix: String): String {
        var suffix = 1
        var candidate = "$prefix-$suffix"
        while (document.visualElements.any { it.id == candidate }) {
            suffix += 1
            candidate = "$prefix-$suffix"
        }
        return candidate
    }

    fun nextVisualDuplicateId(sourceId: String): String {
        var suffix = 1
        var candidate = "$sourceId-copy$suffix"
        while (document.visualElements.any { it.id == candidate }) {
            suffix += 1
            candidate = "$sourceId-copy$suffix"
        }
        return candidate
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
                    if (starter.id == document.id && starter.version == document.version) {
                        FilledTonalButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { }
                        ) {
                            Text("✓ ${starter.name}")
                        }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { openDocument(starter) }
                        ) {
                            Text(starter.name)
                        }
                    }
                }
            }
        }

        if (savedDocuments.isNotEmpty()) {
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
                    Text("Cihazdaki şablonlar", style = MaterialTheme.typography.titleSmall)
                    savedDocuments.forEach { saved ->
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { openDocument(saved) }
                        ) {
                            Text("${saved.name} · v${saved.version}")
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
                                "${compiled.markGrids.size} işaret alanı · " +
                                "${document.visualElements.size} görsel öğe · v${document.version}",
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
                    document = document,
                    template = compiled,
                    selection = selection,
                    showOmrRegions = showOmrRegions,
                    onSelect = { selection = it },
                    onDragStart = {
                        history.beginTransaction()
                        saveStatus = null
                    },
                    onDragUpdate = { target, dx, dy ->
                        val next = when (target) {
                            is DesignerCanvasSelection.Component ->
                                DesignerDocumentEditor.moveComponent(document, target.id, dx, dy)
                            is DesignerCanvasSelection.Visual ->
                                DesignerDocumentEditor.moveVisualElement(document, target.id, dx, dy)
                            is DesignerCanvasSelection.VisualResize ->
                                DesignerVisualTransform.resize(document, target.id, dx, dy)
                        }
                        document = history.updateTransaction(next)
                        saveStatus = null
                    },
                    onDragEnd = {
                        document = history.endTransaction()
                        saveStatus = null
                    },
                    onDragCancel = {
                        document = history.cancelTransaction()
                        saveStatus = null
                    }
                )
            }
        }

        DesignerPropertyPanel(
            document = document,
            selectedComponentId = (selection as? DesignerCanvasSelection.Component)?.id,
            onSelect = { selection = DesignerCanvasSelection.Component(it) },
            onMove = { id, dx, dy ->
                commit(DesignerDocumentEditor.moveComponent(document, id, dx, dy))
            },
            onDuplicate = { id ->
                val newId = nextComponentDuplicateId(id)
                commit(
                    DesignerDocumentEditor.duplicateComponent(
                        document = document,
                        componentId = id,
                        newId = newId
                    )
                )
                selection = DesignerCanvasSelection.Component(newId)
            },
            onDelete = { id ->
                commit(DesignerDocumentEditor.deleteComponent(document, id))
                selection = null
            }
        )

        DesignerVisualPropertyPanel(
            document = document,
            selectedElementId = (selection as? DesignerCanvasSelection.Visual)?.id,
            onSelect = { selection = DesignerCanvasSelection.Visual(it) },
            onAddText = {
                val id = nextVisualId("text")
                commit(
                    document.copy(
                        visualElements = document.visualElements + DesignerTextElement(
                            id = id,
                            bounds = TemplateRect(250.0, 120.0, 500.0, 55.0),
                            text = "Sınav Adı",
                            fontSize = 28.0,
                            alignment = DesignerTextAlignment.CENTER
                        )
                    )
                )
                selection = DesignerCanvasSelection.Visual(id)
            },
            onAddBox = {
                val id = nextVisualId("box")
                commit(
                    document.copy(
                        visualElements = document.visualElements + DesignerBoxElement(
                            id = id,
                            bounds = TemplateRect(240.0, 105.0, 520.0, 80.0),
                            strokeWidth = 2.0
                        )
                    )
                )
                selection = DesignerCanvasSelection.Visual(id)
            },
            onAddLine = {
                val id = nextVisualId("line")
                commit(
                    document.copy(
                        visualElements = document.visualElements + DesignerLineElement(
                            id = id,
                            start = TemplatePoint(250.0, 210.0),
                            end = TemplatePoint(750.0, 210.0),
                            strokeWidth = 2.0
                        )
                    )
                )
                selection = DesignerCanvasSelection.Visual(id)
            },
            onMove = { id, dx, dy ->
                commit(DesignerDocumentEditor.moveVisualElement(document, id, dx, dy))
            },
            onResize = { id, deltaWidth, deltaHeight ->
                commit(
                    DesignerVisualTransform.resize(
                        document = document,
                        elementId = id,
                        deltaWidth = deltaWidth,
                        deltaHeight = deltaHeight
                    )
                )
            },
            onAlignHorizontal = { id, alignment ->
                commit(DesignerVisualTransform.alignHorizontal(document, id, alignment))
            },
            onAlignVertical = { id, alignment ->
                commit(DesignerVisualTransform.alignVertical(document, id, alignment))
            },
            onDuplicate = { id ->
                val newId = nextVisualDuplicateId(id)
                commit(
                    DesignerDocumentEditor.duplicateVisualElement(
                        document = document,
                        elementId = id,
                        newId = newId
                    )
                )
                selection = DesignerCanvasSelection.Visual(newId)
            },
            onDelete = { id ->
                commit(DesignerDocumentEditor.deleteVisualElement(document, id))
                selection = null
            },
            onLockedChange = { id, locked ->
                commit(DesignerDocumentEditor.setVisualElementLocked(document, id, locked))
            },
            onTextChange = { id, text ->
                commit(DesignerDocumentEditor.setVisualText(document, id, text))
            },
            onFontSizeChange = { id, size ->
                commit(DesignerDocumentEditor.setVisualFontSize(document, id, size))
            },
            onStrokeWidthChange = { id, width ->
                commit(DesignerDocumentEditor.setVisualStrokeWidth(document, id, width))
            }
        )

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
                readability.issues.take(5).forEach { issue ->
                    Text("• ${issue.message}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = readability.canSave && openCvReady,
            onClick = { testCamera = true }
        ) {
            Text("Kamerayla Test Et")
        }

        if (!openCvReady) {
            Text(
                "Kamera testi için OpenCV hazır değil.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = history.canUndo(),
                onClick = {
                    document = history.undo()
                    selection = null
                    saveStatus = null
                }
            ) {
                Text("Geri Al")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = history.canRedo(),
                onClick = {
                    document = history.redo()
                    selection = null
                    saveStatus = null
                }
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
                    selection = DesignerCanvasSelection.Component("studentNumber")
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
                    selection = DesignerCanvasSelection.Component("booklet")
                }
            ) {
                Text("Kitapçık A/B Ekle")
            }
        }

        DesignerPdfExportCard(document = document)

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = readability.canSave,
            onClick = {
                saveStatus = runCatching {
                    val stored = repository.save(document)
                    if (stored != document) {
                        history.reset(stored)
                        document = stored
                        selection = null
                    }
                    savedDocuments = repository.list()
                    "Şablon cihazda kaydedildi · v${stored.version} ✓"
                }.getOrElse { error ->
                    "Kaydetme hatası: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        ) {
            Text("Şablonu Kaydet")
        }

        saveStatus?.let { status ->
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CanonicalTemplatePreview(
    document: DesignerDocument,
    template: OmrTemplate,
    selection: DesignerCanvasSelection?,
    showOmrRegions: Boolean,
    onSelect: (DesignerCanvasSelection?) -> Unit,
    onDragStart: (DesignerCanvasSelection) -> Unit,
    onDragUpdate: (DesignerCanvasSelection, Double, Double) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val aspect = (template.space.width / template.space.height).toFloat()
    val paperColor = Color.White
    val omrColor = MaterialTheme.colorScheme.primary
    val selectionColor = MaterialTheme.colorScheme.tertiary
    val markerColor = Color.Black
    val currentDocument by rememberUpdatedState(document)
    val currentSelection by rememberUpdatedState(selection)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragUpdate by rememberUpdatedState(onDragUpdate)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    fun hitTest(point: TemplatePoint): DesignerCanvasSelection? {
        val visualId = DesignerVisualGeometry.hitTest(currentDocument, point)
        if (visualId != null) return DesignerCanvasSelection.Visual(visualId)
        val componentId = DesignerComponentGeometry.hitTest(currentDocument, point)
        return componentId?.let { DesignerCanvasSelection.Component(it) }
    }

    fun resizeTarget(point: TemplatePoint): DesignerCanvasSelection.VisualResize? {
        val selectedId = (currentSelection as? DesignerCanvasSelection.Visual)?.id ?: return null
        val element = currentDocument.visualElements.firstOrNull { it.id == selectedId } ?: return null
        return if (DesignerResizeHandleGeometry.hitTest(element, point)) {
            DesignerCanvasSelection.VisualResize(selectedId)
        } else {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .background(paperColor, RoundedCornerShape(6.dp))
            .pointerInput(template.space) {
                detectTapGestures { offset ->
                    if (size.width <= 0 || size.height <= 0) return@detectTapGestures
                    val point = TemplatePoint(
                        x = offset.x / size.width.toDouble() * template.space.width,
                        y = offset.y / size.height.toDouble() * template.space.height
                    )
                    currentOnSelect(hitTest(point))
                }
            }
            .pointerInput(template.space) {
                var dragging: DesignerCanvasSelection? = null
                detectDragGestures(
                    onDragStart = { offset ->
                        if (size.width <= 0 || size.height <= 0) return@detectDragGestures
                        val point = TemplatePoint(
                            x = offset.x / size.width.toDouble() * template.space.width,
                            y = offset.y / size.height.toDouble() * template.space.height
                        )
                        val resize = resizeTarget(point)
                        val target = resize ?: hitTest(point)
                        dragging = target
                        currentOnSelect(
                            if (target is DesignerCanvasSelection.VisualResize) {
                                DesignerCanvasSelection.Visual(target.id)
                            } else {
                                target
                            }
                        )
                        if (target != null) currentOnDragStart(target)
                    },
                    onDragEnd = {
                        if (dragging != null) currentOnDragEnd()
                        dragging = null
                    },
                    onDragCancel = {
                        if (dragging != null) currentOnDragCancel()
                        dragging = null
                    }
                ) { change, dragAmount ->
                    val target = dragging ?: return@detectDragGestures
                    change.consume()
                    if (size.width <= 0 || size.height <= 0) return@detectDragGestures
                    val dx = dragAmount.x / size.width.toDouble() * template.space.width
                    val dy = dragAmount.y / size.height.toDouble() * template.space.height
                    currentOnDragUpdate(target, dx, dy)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaleX = size.width / template.space.width.toFloat()
            val scaleY = size.height / template.space.height.toFloat()
            val averageScale = (scaleX + scaleY) / 2f

            fun x(value: Double): Float = value.toFloat() * scaleX
            fun y(value: Double): Float = value.toFloat() * scaleY
            fun radius(value: Double): Float = value.toFloat() * averageScale

            document.visualElements.forEach { element ->
                when (element) {
                    is DesignerTextElement -> {
                        drawIntoCanvas { canvas ->
                            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.BLACK
                                textSize = (element.fontSize.toFloat() * averageScale).coerceAtLeast(7f)
                            }
                            val left = x(element.bounds.left)
                            val right = x(element.bounds.right)
                            val textWidth = paint.measureText(element.text)
                            val textX = when (element.alignment) {
                                DesignerTextAlignment.START -> left
                                DesignerTextAlignment.CENTER -> left + (right - left - textWidth) / 2f
                                DesignerTextAlignment.END -> right - textWidth
                            }
                            val maxHeight = y(element.bounds.height)
                            val baseline = y(element.bounds.top) + minOf(paint.textSize, maxHeight * 0.82f)
                            canvas.nativeCanvas.drawText(element.text, textX, baseline, paint)
                        }
                    }
                    is DesignerBoxElement -> {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(x(element.bounds.left), y(element.bounds.top)),
                            size = Size(x(element.bounds.width), y(element.bounds.height)),
                            style = Stroke(width = (element.strokeWidth.toFloat() * averageScale).coerceAtLeast(1f))
                        )
                    }
                    is DesignerLineElement -> {
                        drawLine(
                            color = Color.Black,
                            start = Offset(x(element.start.x), y(element.start.y)),
                            end = Offset(x(element.end.x), y(element.end.y)),
                            strokeWidth = (element.strokeWidth.toFloat() * averageScale).coerceAtLeast(1f)
                        )
                    }
                }
            }

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

            val selectedBounds = when (selection) {
                is DesignerCanvasSelection.Component -> document.components
                    .firstOrNull { it.id == selection.id }
                    ?.let { DesignerComponentGeometry.bounds(it) }
                is DesignerCanvasSelection.Visual,
                is DesignerCanvasSelection.VisualResize -> document.visualElements
                    .firstOrNull { it.id == selection.id }
                    ?.let { DesignerVisualGeometry.bounds(it) }
                null -> null
            }
            if (selectedBounds != null) {
                drawRect(
                    color = selectionColor,
                    topLeft = Offset(x(selectedBounds.left), y(selectedBounds.top)),
                    size = Size(x(selectedBounds.width), y(selectedBounds.height)),
                    style = Stroke(width = 3f)
                )
            }

            val selectedVisual = (selection as? DesignerCanvasSelection.Visual)?.id
                ?.let { id -> document.visualElements.firstOrNull { it.id == id } }
            val handle = selectedVisual?.let { DesignerResizeHandleGeometry.handlePoint(it) }
            if (handle != null) {
                val center = Offset(x(handle.x), y(handle.y))
                drawCircle(color = Color.White, radius = 10f, center = center)
                drawCircle(color = selectionColor, radius = 7f, center = center)
            }
        }
    }
}
