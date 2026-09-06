package com.okulyonetim.optikokuyucu.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.DesignerComponentGeometry
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocumentEditor
import com.okulyonetim.optikokuyucu.omr.designer.DesignerEditorLayout
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPageGeometry
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPageOrientation
import com.okulyonetim.optikokuyucu.omr.designer.DesignerResizeHandleGeometry
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.DesignerVisualGeometry
import com.okulyonetim.optikokuyucu.omr.designer.DesignerVisualTransform
import com.okulyonetim.optikokuyucu.omr.designer.NumericGridComponent
import com.okulyonetim.optikokuyucu.omr.designer.QuestionGroupComponent
import com.okulyonetim.optikokuyucu.omr.designer.SingleChoiceComponent
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
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

    fun toCanonical(offset: Offset, width: Int, height: Int): TemplatePoint = TemplatePoint(
        offset.x / width.toDouble() * currentDocument.space.width,
        offset.y / height.toDouble() * currentDocument.space.height
    )

    fun hitTest(point: TemplatePoint): StructuredPaperSelection? {
        val visual = DesignerVisualGeometry.hitTest(currentDocument, point)
        if (visual != null) return StructuredPaperSelection(StructuredSelectionKind.VISUAL, visual)
        return DesignerComponentGeometry.hitTest(currentDocument, point)?.let { StructuredPaperSelection(StructuredSelectionKind.COMPONENT, it) }
    }

    fun insideSafe(candidate: DesignerDocument, target: StructuredPaperSelection): Boolean {
        val bounds = when (target.kind) {
            StructuredSelectionKind.COMPONENT -> candidate.components.firstOrNull { it.id == target.id }?.let(DesignerComponentGeometry::bounds)
            StructuredSelectionKind.VISUAL -> candidate.visualElements.firstOrNull { it.id == target.id }?.let(DesignerVisualGeometry::bounds)
        } ?: return false
        val area = DesignerPageGeometry.safeArea(candidate.space)
        return bounds.left >= area.left && bounds.top >= area.top && bounds.right <= area.right && bounds.bottom <= area.bottom
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${document.formSpec.paperSize.displayName} · $physical", style = MaterialTheme.typography.labelMedium)
                Text(document.formSpec.orientation.displayName, style = MaterialTheme.typography.labelSmall)
            }
            Text("Canonical ${document.space.width.roundToInt()} × ${document.space.height.roundToInt()} · Grid 2,5 mm · Snap 1 mm", style = MaterialTheme.typography.labelSmall)
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(document.space.aspectRatio.toFloat())
                    .background(Color.White, RoundedCornerShape(6.dp))
                    .pointerInput(document.space, document.components.size, document.visualElements.size) {
                        detectTapGestures { offset ->
                            if (size.width > 0 && size.height > 0) currentOnSelectionChange(hitTest(toCanonical(offset, size.width, size.height)))
                        }
                    }
                    .pointerInput(document.space, document.components, document.visualElements, selection) {
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
                                baseline = currentDocument; totalX = 0.0; totalY = 0.0; currentOnSelectionChange(target)
                            },
                            onDragEnd = { target = null; baseline = null; resizeVisual = false },
                            onDragCancel = { target = null; baseline = null; resizeVisual = false }
                        ) { change, dragAmount ->
                            val selected = target ?: return@detectDragGestures
                            val base = baseline ?: return@detectDragGestures
                            change.consume()
                            totalX += dragAmount.x / size.width.toDouble() * base.space.width
                            totalY += dragAmount.y / size.height.toDouble() * base.space.height
                            val snap = DesignerEditorLayout.canonicalForMillimeters(base, DesignerEditorLayout.DRAG_SNAP_MM)
                            val candidate = when {
                                resizeVisual -> DesignerVisualTransform.resize(base, selected.id, totalX, totalY, snapStep = snap, minSize = snap * 6.0)
                                selected.kind == StructuredSelectionKind.COMPONENT -> DesignerDocumentEditor.moveComponent(base, selected.id, totalX, totalY, snap)
                                else -> DesignerDocumentEditor.moveVisualElement(base, selected.id, totalX, totalY, snap)
                            }
                            if (insideSafe(candidate, selected)) currentOnDocumentChange(candidate)
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val sx = size.width / document.space.width.toFloat()
                    val sy = size.height / document.space.height.toFloat()
                    val minorStep = DesignerEditorLayout.canonicalForMillimeters(document, DesignerEditorLayout.GRID_MINOR_MM)
                    val majorStep = DesignerEditorLayout.canonicalForMillimeters(document, DesignerEditorLayout.GRID_MAJOR_MM)
                    val minorColor = Color(0xFFE9ECF3); val majorColor = Color(0xFFD2D8E4)
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
                    val selectedBounds = selection?.let { selected -> when (selected.kind) {
                        StructuredSelectionKind.COMPONENT -> document.components.firstOrNull { it.id == selected.id }?.let(DesignerComponentGeometry::bounds)
                        StructuredSelectionKind.VISUAL -> document.visualElements.firstOrNull { it.id == selected.id }?.let(DesignerVisualGeometry::bounds)
                    } }
                    selectedBounds?.let { drawRect(selectionColor, Offset(it.left.toFloat() * sx, it.top.toFloat() * sy), Size(it.width.toFloat() * sx, it.height.toFloat() * sy), style = Stroke(2.3f)) }
                    selection?.takeIf { it.kind == StructuredSelectionKind.VISUAL }
                        ?.let { selected -> document.visualElements.firstOrNull { it.id == selected.id } }
                        ?.let(DesignerResizeHandleGeometry::handlePoint)
                        ?.let { handle -> val center = Offset(handle.x.toFloat() * sx, handle.y.toFloat() * sy); drawCircle(Color.White, 8f, center); drawCircle(selectionColor, 5.5f, center) }
                    drawRect(Color(0xFFBFC5D0), style = Stroke(1.1f))
                }
            }
            Text("Öğeyi seçip doğrudan sürükleyin. Görsel öğelerde sağ-alt tutamaç boyutlandırır.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatWorkspaceMillimetres(value: Double): String = if (value == value.roundToInt().toDouble()) value.roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", value)
