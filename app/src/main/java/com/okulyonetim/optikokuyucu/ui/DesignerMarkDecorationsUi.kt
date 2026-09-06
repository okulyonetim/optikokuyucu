package com.okulyonetim.optikokuyucu.ui

import android.graphics.Paint as AndroidPaint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerEditorLayout
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextAlignment
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTypography
import com.okulyonetim.optikokuyucu.omr.designer.NumericGridComponent
import com.okulyonetim.optikokuyucu.omr.designer.SingleChoiceComponent
import com.okulyonetim.optikokuyucu.omr.template.MarkGridSpec

internal fun DrawScope.drawSingleChoice(
    component: SingleChoiceComponent,
    grid: MarkGridSpec,
    scaleX: Float,
    scaleY: Float,
    bubbleColor: Color
) {
    val averageScale = (scaleX + scaleY) / 2f
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.DKGRAY
        textAlign = AndroidPaint.Align.CENTER
        textSize = (component.bubbleRadius * 0.82).toFloat() * averageScale
        typeface = DesignerTypography.typeface()
    }
    grid.columns.firstOrNull()?.marks.orEmpty().forEach { mark ->
        val center = Offset(mark.center.x.toFloat() * scaleX, mark.center.y.toFloat() * scaleY)
        drawCircle(bubbleColor, mark.radius.toFloat() * averageScale, center, style = Stroke(1.1f))
        drawIntoCanvas { canvas ->
            val metrics = paint.fontMetrics
            canvas.nativeCanvas.drawText(mark.id, center.x, center.y - (metrics.ascent + metrics.descent) / 2f, paint)
        }
    }
}

internal fun DrawScope.drawComponentDecorations(
    document: DesignerDocument,
    scaleX: Float,
    scaleY: Float
) {
    val averageScale = (scaleX + scaleY) / 2f
    document.components.forEach { component ->
        if (DesignerEditorLayout.componentShowsLabel(component)) {
            val text = DesignerEditorLayout.componentLabel(component)
            if (text.isNotBlank()) {
                val anchor = DesignerEditorLayout.labelAnchor(component)
                val alignment = DesignerEditorLayout.componentLabelAlignment(component)
                val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(40, 40, 40)
                    typeface = DesignerTypography.typeface(bold = true)
                    textSize = (DesignerEditorLayout.componentBubbleRadius(component) * 1.15).toFloat() * averageScale
                    textAlign = when (alignment) {
                        DesignerTextAlignment.START -> AndroidPaint.Align.LEFT
                        DesignerTextAlignment.CENTER -> AndroidPaint.Align.CENTER
                        DesignerTextAlignment.END -> AndroidPaint.Align.RIGHT
                    }
                }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(text, anchor.x.toFloat() * scaleX, anchor.y.toFloat() * scaleY, paint)
                }
            }
        }
        if (component is NumericGridComponent) {
            DesignerEditorLayout.numericHeaderBoxes(component).forEach { box ->
                drawRect(
                    color = Color(0xFF555555),
                    topLeft = Offset(box.left.toFloat() * scaleX, box.top.toFloat() * scaleY),
                    size = Size(box.width.toFloat() * scaleX, box.height.toFloat() * scaleY),
                    style = Stroke(width = 1.0f)
                )
            }
        }
    }
}
