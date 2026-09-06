package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.DesignerAlignmentGuideMatch
import com.okulyonetim.optikokuyucu.omr.designer.DesignerComponentGeometry
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocumentEditor
import com.okulyonetim.optikokuyucu.omr.designer.DesignerEditorLayout
import com.okulyonetim.optikokuyucu.omr.designer.DesignerMobilePrecision
import com.okulyonetim.optikokuyucu.omr.designer.DesignerMobileViewport
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPageGeometry
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPageOrientation
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPrecisionFrame
import com.okulyonetim.optikokuyucu.omr.designer.DesignerResizeHandleGeometry
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.DesignerVisualGeometry
import com.okulyonetim.optikokuyucu.omr.designer.DesignerVisualTransform
import com.okulyonetim.optikokuyucu.omr.designer.NumericGridComponent
import com.okulyonetim.optikokuyucu.omr.designer.QuestionGroupComponent
import com.okulyonetim.optikokuyucu.omr.designer.SingleChoiceComponent
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class StructuredSelectionKind { COMPONENT, VISUAL }
internal data class StructuredPaperSelection(val kind: StructuredSelectionKind, val id: String)

@Composable
internal fun InteractivePaperWorkspace(
    document: DesignerDocument,
    selection: StructuredPaperSelection?,
    onSelectionChange: (StructuredPaperSelection?) -> Unit,
    onDocumentChange: (DesignerDocument) -> Unit
) {
    val dimensions = DesignerPageGeometry.dimensions(document.formSpec.paperSize)
    val physicalWidthMm = dimensions?.let {
        if (document.formSpec.orientation == DesignerPageOrientation.PORTRAIT) it.widthMm else it.heightMm
    }
    val physical = dimensions?.let {
        val w = if (document.formSpec.orientation == DesignerPageOrientation.PORTRAIT) it.widthMm else it.heightMm
        val h = if (document.formSpec.orientation == DesignerPageOrientation.PORTRAIT) it.heightMm else it.widthMm
        "${formatWorkspaceMillimetres(w)} × ${formatWorkspaceMillimetres(h)} mm"
    } ?: "Özel ölçü"
    val safe = remember(document.space) { DesignerPageGeometry.safeArea(document.space) }
    val compiled = remember(document) { DesignerTemplateCompiler.compile(document) }
    val rows = remember(compiled) { compiled.bubbleRows.associateBy { it.id } }
    val images = rememberDesignerImageBitmaps(document.visualElements)
    val currentDocument by rememberUpdatedState(document)
    val currentSelection by rememberUpdatedState(selection)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val currentOnDocumentChange by rememberUpdatedState(onDocumentChange)
    val omrColor = Color(0xFFB54848)
    val selectionColor = MaterialTheme.colorScheme.tertiary
    val safeFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.035f)
    val safeStroke = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    val guideColor = MaterialTheme.colorScheme.secondary
    val density = LocalDensity.current

    var zoom by remember(document.id) { mutableStateOf(DesignerMobileViewport.FIT_ZOOM.toFloat()) }
    var pan by remember(document.id) { mutableStateOf(Offset.Zero) }
    var panMode by remember(document.id) { mutableStateOf(false) }
    var viewportSize by remember(document.id) { mutableStateOf(IntSize.Zero) }
    var activeGuides by remember(document.id) { mutableStateOf(DesignerAlignmentGuideMatch()) }
    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(pan)

    fun clampPanFor(newZoom: Float, proposed: Offset): Offset {
        val clamped = DesignerMobileViewport.clampPan(
            viewportSize.width.toDouble(),
            viewportSize.height.toDouble(),
            newZoom.toDouble(),
            proposed.x.toDouble(),
            proposed.y.toDouble()
        )
        return Offset(clamped.x.toFloat(), clamped.y.toFloat())
    }

    fun setZoom(newValue: Double) {
        val value = DesignerMobileViewport.clampZoom(newValue).toFloat()
        zoom = value
        pan = clampPanFor(value, pan)
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextZoom = DesignerMobileViewport.clampZoom(zoom.toDouble() * zoomChange.toDouble()).toFloat()
        zoom = nextZoom
        pan = clampPanFor(nextZoom, pan + panChange)
    }

    fun toCanonical(offset: Offset, width: Int, height: Int): TemplatePoint {
        val z = currentZoom.coerceAtLeast(0.001f)
        val centerX = width / 2.0
        val centerY = height / 2.0
        val baseX = centerX + (offset.x - centerX - currentPan.x) / z
        val baseY = centerY + (offset.y - centerY - currentPan.y) / z
        return TemplatePoint(
            baseX / width.toDouble() * currentDocument.space.width,
            baseY / height.toDouble() * currentDocument.space.height
        )
    }

    fun hitTest(point: TemplatePoint): StructuredPaperSelection? {
        val visual = DesignerVisualGeometry.hitTest(currentDocument, point)
        if (visual != null) return StructuredPaperSelection(StructuredSelectionKind.VISUAL, visual)
        return DesignerComponentGeometry.hitTest(currentDocument, point)?.let { StructuredPaperSelection(StructuredSelectionKind.COMPONENT, it) }
    }

    fun boundsFor(candidate: DesignerDocument, target: StructuredPaperSelection): TemplateRect? = when (target.kind) {
        StructuredSelectionKind.COMPONENT -> candidate.components.firstOrNull { it.id == target.id }?.let(DesignerComponentGeometry::bounds)
        StructuredSelectionKind.VISUAL -> candidate.visualElements.firstOrNull { it.id == target.id }?.let(DesignerVisualGeometry::bounds)
    }

    fun insideSafe(candidate: DesignerDocument, target: StructuredPaperSelection): Boolean {
        val bounds = boundsFor(candidate, target) ?: return false
        val area = DesignerPageGeometry.safeArea(candidate.space)
        return bounds.left >= area.left && bounds.top >= area.top && bounds.right <= area.right && bounds.bottom <= area.bottom
    }

    fun stationaryBounds(candidate: DesignerDocument, target: StructuredPaperSelection): List<TemplateRect> = buildList {
        candidate.components.forEach { component ->
            if (target.kind != StructuredSelectionKind.COMPONENT || component.id != target.id) add(DesignerComponentGeometry.bounds(component))
        }
        candidate.visualElements.forEach { element ->
            if (target.kind != StructuredSelectionKind.VISUAL || element.id != target.id) add(DesignerVisualGeometry.bounds(element))
        }
    }

    val viewportWidthDp = with(density) { viewportSize.width.toDp().value.toDouble() }
    val hundredZoom = if (physicalWidthMm != null && viewportWidthDp > 0.0) {
        DesignerMobileViewport.oneHundredPercentZoom(viewportWidthDp, physicalWidthMm)
    } else null
    val zoomPercent = hundredZoom?.let { (zoom / it * 100.0).roundToInt() } ?: (zoom * 100f).roundToInt()

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${document.formSpec.paperSize.displayName} · $physical", style = MaterialTheme.typography.labelMedium)
                Text(document.formSpec.orientation.displayName, style = MaterialTheme.typography.labelSmall)
            }
            Text("Canonical ${document.space.width.roundToInt()} × ${document.space.height.roundToInt()} · Grid 2,5 mm · Snap 1 mm", style = MaterialTheme.typography.labelSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { zoom = DesignerMobileViewport.FIT_ZOOM.toFloat(); pan = Offset.Zero }
                ) { Text("Sığdır") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = hundredZoom != null,
                    onClick = { hundredZoom?.let(::setZoom); pan = clampPanFor(zoom, Offset.Zero) }
                ) { Text("100%") }
                if (panMode) {
                    FilledTonalButton(modifier = Modifier.weight(1f), onClick = { panMode = false }) { Text("Gezdir ✓") }
                } else {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { panMode = true; activeGuides = DesignerAlignmentGuideMatch() }) { Text("Gezdir") }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { setZoom(zoom / 1.25) }) { Text("−") }
                Text("Yakınlaştırma $zoomPercent%", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { setZoom(zoom * 1.25) }) { Text("+") }
            }
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(document.space.aspectRatio.toFloat())
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .onSizeChanged { viewportSize = it; pan = clampPanFor(zoom, pan) }
                    .transformable(state = transformableState, enabled = panMode)
                    .pointerInput(document.space, document.components.size, document.visualElements.size, panMode) {
                        if (!panMode) {
                            detectTapGestures { offset ->
                                if (size.width > 0 && size.height > 0) currentOnSelectionChange(hitTest(toCanonical(offset, size.width, size.height)))
                            }
                        }
                    }
                    .pointerInput(document.space, document.components, document.visualElements, selection, panMode) {
                        if (!panMode) {
                            var target: StructuredPaperSelection? = null
                            var resizeVisual = false
                            var baseline: DesignerDocument? = null
                            var totalX = 0.0
                            var totalY = 0.0
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (size.width <= 0 || size.height <= 0) return@detectDragGestures
                                    val point = toCanonical(offset, size.width, size.height)
                                    val selectedVisual = currentSelection?.takeIf { it.kind == StructuredSelectionKind.VISUAL }
                                        ?.let { selected -> currentDocument.visualElements.firstOrNull { it.id == selected.id } }
                                    resizeVisual = selectedVisual?.let { DesignerResizeHandleGeometry.hitTest(it, point) } == true
                                    target = if (resizeVisual && selectedVisual != null) StructuredPaperSelection(StructuredSelectionKind.VISUAL, selectedVisual.id) else hitTest(point)
                                    baseline = currentDocument
                                    totalX = 0.0
                                    totalY = 0.0
                                    activeGuides = DesignerAlignmentGuideMatch()
                                    currentOnSelectionChange(target)
                                },
                                onDragEnd = { target = null; baseline = null; resizeVisual = false; activeGuides = DesignerAlignmentGuideMatch() },
                                onDragCancel = { target = null; baseline = null; resizeVisual = false; activeGuides = DesignerAlignmentGuideMatch() }
                            ) { change, dragAmount ->
                                val selected = target ?: return@detectDragGestures
                                val base = baseline ?: return@detectDragGestures
                                change.consume()
                                val z = currentZoom.coerceAtLeast(0.001f)
                                totalX += dragAmount.x / size.width.toDouble() * base.space.width / z
                                totalY += dragAmount.y / size.height.toDouble() * base.space.height / z
                                val snap = DesignerEditorLayout.canonicalForMillimeters(base, DesignerEditorLayout.DRAG_SNAP_MM)
                                val rawCandidate = when {
                                    resizeVisual -> DesignerVisualTransform.resize(base, selected.id, totalX, totalY, snapStep = snap, minSize = snap * 6.0)
                                    selected.kind == StructuredSelectionKind.COMPONENT -> DesignerDocumentEditor.moveComponent(base, selected.id, totalX, totalY, snap)
                                    else -> DesignerDocumentEditor.moveVisualElement(base, selected.id, totalX, totalY, snap)
                                }
                                if (!insideSafe(rawCandidate, selected)) return@detectDragGestures
                                if (resizeVisual) {
                                    activeGuides = DesignerAlignmentGuideMatch()
                                    currentOnDocumentChange(rawCandidate)
                                    return@detectDragGestures
                                }
                                val moving = boundsFor(rawCandidate, selected) ?: return@detectDragGestures
                                val guideMatch = DesignerMobilePrecision.resolveAlignmentGuides(
                                    moving = moving,
                                    stationary = stationaryBounds(rawCandidate, selected),
                                    safeArea = DesignerPageGeometry.safeArea(rawCandidate.space),
                                    tolerance = DesignerEditorLayout.canonicalForMillimeters(base, DesignerMobilePrecision.ALIGNMENT_GUIDE_MM)
                                )
                                val aligned = when (selected.kind) {
                                    StructuredSelectionKind.COMPONENT -> DesignerMobilePrecision.translateComponentExact(rawCandidate, selected.id, guideMatch.deltaX, guideMatch.deltaY)
                                    StructuredSelectionKind.VISUAL -> DesignerMobilePrecision.translateVisualExact(rawCandidate, selected.id, guideMatch.deltaX, guideMatch.deltaY)
                                }
                                if (insideSafe(aligned, selected)) {
                                    activeGuides = guideMatch
                                    currentOnDocumentChange(aligned)
                                } else {
                                    activeGuides = DesignerAlignmentGuideMatch()
                                    currentOnDocumentChange(rawCandidate)
                                }
                            }
                        }
                    }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        }
                        .background(Color.White)
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val sx = size.width / document.space.width.toFloat()
                        val sy = size.height / document.space.height.toFloat()
                        val minorStep = DesignerEditorLayout.canonicalForMillimeters(document, DesignerEditorLayout.GRID_MINOR_MM)
                        val majorStep = DesignerEditorLayout.canonicalForMillimeters(document, DesignerEditorLayout.GRID_MAJOR_MM)
                        val minorColor = Color(0xFFE9ECF3)
                        val majorColor = Color(0xFFD2D8E4)
                        var gx = minorStep
                        while (gx < document.space.width) {
                            val major = abs((gx / majorStep) - (gx / majorStep).roundToInt()) < 0.02
                            drawLine(if (major) majorColor else minorColor, Offset(gx.toFloat() * sx, 0f), Offset(gx.toFloat() * sx, size.height), if (major) 1.05f else 0.55f)
                            gx += minorStep
                        }
                        var gy = minorStep
                        while (gy < document.space.height) {
                            val major = abs((gy / majorStep) - (gy / majorStep).roundToInt()) < 0.02
                            drawLine(if (major) majorColor else minorColor, Offset(0f, gy.toFloat() * sy), Offset(size.width, gy.toFloat() * sy), if (major) 1.05f else 0.55f)
                            gy += minorStep
                        }
                        drawRect(safeFill, Offset(safe.left.toFloat() * sx, safe.top.toFloat() * sy), Size(safe.width.toFloat() * sx, safe.height.toFloat() * sy))
                        drawRect(safeStroke, Offset(safe.left.toFloat() * sx, safe.top.toFloat() * sy), Size(safe.width.toFloat() * sx, safe.height.toFloat() * sy), style = Stroke(1.1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))))
                        drawDesignerVisualElements(document.visualElements, images, sx, sy)
                        document.components.filterIsInstance<QuestionGroupComponent>().forEach { drawAnswerGroup(it, rows, document.formSpec.answerAppearance, sx, sy, omrColor) }
                        document.components.filterIsInstance<NumericGridComponent>().forEach { component -> compiled.markGrids.firstOrNull { it.id == component.id }?.let { drawNumberGrid(component, it, sx, sy, omrColor) } }
                        document.components.filterIsInstance<SingleChoiceComponent>().forEach { component -> compiled.markGrids.firstOrNull { it.id == component.id }?.let { drawSingleChoice(component, it, sx, sy, omrColor) } }
                        drawComponentDecorations(document, sx, sy)
                        document.fiducials.forEach { marker -> drawRect(Color.Black, Offset(marker.bounds.left.toFloat() * sx, marker.bounds.top.toFloat() * sy), Size(marker.bounds.width.toFloat() * sx, marker.bounds.height.toFloat() * sy)) }
                        val selectedBounds = selection?.let { selected -> boundsFor(document, selected) }
                        selectedBounds?.let { drawRect(selectionColor, Offset(it.left.toFloat() * sx, it.top.toFloat() * sy), Size(it.width.toFloat() * sx, it.height.toFloat() * sy), style = Stroke(2.3f)) }
                        selection?.takeIf { it.kind == StructuredSelectionKind.VISUAL }
                            ?.let { selected -> document.visualElements.firstOrNull { it.id == selected.id } }
                            ?.let(DesignerResizeHandleGeometry::handlePoint)
                            ?.let { handle ->
                                val center = Offset(handle.x.toFloat() * sx, handle.y.toFloat() * sy)
                                drawCircle(Color.White, 8f, center)
                                drawCircle(selectionColor, 5.5f, center)
                            }
                        activeGuides.verticalGuideX?.let { x ->
                            drawLine(guideColor, Offset(x.toFloat() * sx, safe.top.toFloat() * sy), Offset(x.toFloat() * sx, safe.bottom.toFloat() * sy), 1.5f)
                        }
                        activeGuides.horizontalGuideY?.let { y ->
                            drawLine(guideColor, Offset(safe.left.toFloat() * sx, y.toFloat() * sy), Offset(safe.right.toFloat() * sx, y.toFloat() * sy), 1.5f)
                        }
                        drawRect(Color(0xFFBFC5D0), style = Stroke(1.1f))
                    }
                }
            }
            Text(
                if (panMode) "Gezdir modu: tek parmakla kaydırın, iki parmakla pinch zoom yapın. Düzenlemeye dönmek için Gezdir düğmesine dokunun."
                else "Düzenle modu: öğeyi seçip sürükleyin. Grid snap 1 mm; yakın hizalamalarda kılavuz otomatik devreye girer.",
                style = MaterialTheme.typography.labelSmall
            )
            selection?.let { PrecisionGeometryCard(document, it, currentOnDocumentChange) }
        }
    }
}

@Composable
private fun PrecisionGeometryCard(
    document: DesignerDocument,
    selection: StructuredPaperSelection,
    onDocumentChange: (DesignerDocument) -> Unit
) {
    val component = selection.takeIf { it.kind == StructuredSelectionKind.COMPONENT }
        ?.let { selected -> document.components.firstOrNull { it.id == selected.id } }
    val visual = selection.takeIf { it.kind == StructuredSelectionKind.VISUAL }
        ?.let { selected -> document.visualElements.firstOrNull { it.id == selected.id } }
    val frame: DesignerPrecisionFrame = when (selection.kind) {
        StructuredSelectionKind.COMPONENT -> DesignerMobilePrecision.componentFrameMm(document, selection.id)
        StructuredSelectionKind.VISUAL -> DesignerMobilePrecision.visualFrameMm(document, selection.id)
    } ?: return
    val locked = visual?.locked == true
    val canResizeWidth = component?.let(DesignerMobilePrecision::componentCanResizeWidth) ?: true
    val canResizeHeight = component?.let(DesignerMobilePrecision::componentCanResizeHeight) ?: true

    var xText by remember(selection.kind, selection.id, frame.xMm) { mutableStateOf(formatPrecisionMillimetres(frame.xMm)) }
    var yText by remember(selection.kind, selection.id, frame.yMm) { mutableStateOf(formatPrecisionMillimetres(frame.yMm)) }
    var widthText by remember(selection.kind, selection.id, frame.widthMm) { mutableStateOf(formatPrecisionMillimetres(frame.widthMm)) }
    var heightText by remember(selection.kind, selection.id, frame.heightMm) { mutableStateOf(formatPrecisionMillimetres(frame.heightMm)) }

    val x = parsePrecisionMillimetres(xText)
    val y = parsePrecisionMillimetres(yText)
    val width = parsePrecisionMillimetres(widthText)
    val height = parsePrecisionMillimetres(heightText)
    val valid = !locked && x != null && y != null && width != null && height != null && x >= 0.0 && y >= 0.0 && width > 0.0 && height > 0.0

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Hassas Konum ve Boyut · mm", style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                PrecisionField("X", xText, { xText = it }, !locked, Modifier.weight(1f))
                PrecisionField("Y", yText, { yText = it }, !locked, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                PrecisionField("Genişlik", widthText, { widthText = it }, !locked && canResizeWidth, Modifier.weight(1f))
                PrecisionField("Yükseklik", heightText, { heightText = it }, !locked && canResizeHeight, Modifier.weight(1f))
            }
            if (component != null && (!canResizeWidth || !canResizeHeight)) {
                Text("OMR alanında eksen dışı boyut standart baloncuk geometrisinden türetilir.", style = MaterialTheme.typography.labelSmall)
            }
            if (locked) Text("Kilitli görsel öğe hassas düzenlenemez.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = valid,
                onClick = {
                    val updated = when (selection.kind) {
                        StructuredSelectionKind.COMPONENT -> DesignerMobilePrecision.setComponentFrameMm(document, selection.id, x!!, y!!, width!!, height!!)
                        StructuredSelectionKind.VISUAL -> DesignerMobilePrecision.setVisualFrameMm(document, selection.id, x!!, y!!, width!!, height!!)
                    }
                    onDocumentChange(updated)
                }
            ) { Text("Hassas Değerleri Uygula") }
        }
    }
}

@Composable
private fun PrecisionField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> if (input.length <= 10 && input.all { it.isDigit() || it == '.' || it == ',' || it == '-' }) onValueChange(input) },
        modifier = modifier,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

private fun parsePrecisionMillimetres(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

private fun formatPrecisionMillimetres(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)

private fun formatWorkspaceMillimetres(value: Double): String = if (value == value.roundToInt().toDouble()) value.roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", value)
