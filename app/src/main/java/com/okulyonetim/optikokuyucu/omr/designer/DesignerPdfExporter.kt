package com.okulyonetim.optikokuyucu.omr.designer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.objdetect.Objdetect
import java.io.OutputStream
import kotlin.math.max

/** Renders the same canonical source document into a physical PDF page. */
object DesignerPdfExporter {
    fun export(document: DesignerDocument, output: OutputStream, profile: PdfPageProfile = PdfPageProfile.A4) {
        val template = DesignerTemplateCompiler.compile(document)
        val readability = TemplateReadabilityAnalyzer.analyze(document, template)
        require(readability.canSave) { "Template cannot be exported while readability errors exist." }
        val transform = DesignerPdfLayout.fit(template.space, profile)
        val pdf = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(profile.widthPoints, profile.heightPoints, 1).create()
            val page = pdf.startPage(pageInfo)
            try {
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                drawVisualLayer(canvas, document, transform)
                drawSemanticLabels(canvas, document, template, transform)
                drawComponentDecorations(canvas, document, transform)
                drawOmrMarks(canvas, template, transform)
                drawFiducials(canvas, template, transform)
            } finally { pdf.finishPage(page) }
            pdf.writeTo(output); output.flush()
        } finally { pdf.close() }
    }

    private fun drawVisualLayer(canvas: Canvas, document: DesignerDocument, transform: CanonicalPageTransform) {
        document.visualElements.forEach { element ->
            when (element) {
                is DesignerTextElement -> drawTextElement(canvas, element, transform)
                is DesignerImageElement -> drawImageElement(canvas, element, transform)
                is DesignerBoxElement -> drawBoxElement(canvas, element, transform)
                is DesignerLineElement -> drawLineElement(canvas, element, transform)
            }
        }
    }

    private fun drawTextElement(canvas: Canvas, element: DesignerTextElement, transform: CanonicalPageTransform) {
        val rect = transform.map(element.bounds)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; style = Paint.Style.FILL; textSize = max(5.5f, transform.length(element.fontSize).toFloat())
            typeface = Typeface.create(Typeface.DEFAULT, if (element.bold) Typeface.BOLD else Typeface.NORMAL)
            textAlign = when (element.alignment) { DesignerTextAlignment.START -> Paint.Align.LEFT; DesignerTextAlignment.CENTER -> Paint.Align.CENTER; DesignerTextAlignment.END -> Paint.Align.RIGHT }
        }
        val x = when (element.alignment) { DesignerTextAlignment.START -> rect.left.toFloat(); DesignerTextAlignment.CENTER -> rect.center.x.toFloat(); DesignerTextAlignment.END -> rect.right.toFloat() }
        val lineHeight = paint.textSize * 1.22f; var baseline = rect.top.toFloat() + paint.textSize
        canvas.save(); canvas.clipRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat())
        element.text.split('\n').forEach { line -> if (baseline <= rect.bottom.toFloat() + paint.textSize * 0.2f) { canvas.drawText(line, x, baseline, paint); baseline += lineHeight } }
        canvas.restore()
    }

    private fun drawImageElement(canvas: Canvas, element: DesignerImageElement, transform: CanonicalPageTransform) {
        val bytes = element.image.copyBytes()
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Embedded designer image could not be decoded." }
        try {
            val rect = transform.map(element.bounds)
            canvas.drawBitmap(bitmap, null, RectF(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat()), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } finally { bitmap.recycle() }
    }

    private fun drawBoxElement(canvas: Canvas, element: DesignerBoxElement, transform: CanonicalPageTransform) {
        val rect = transform.map(element.bounds)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = max(0.6f, transform.length(element.strokeWidth).toFloat()) }
        canvas.drawRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), paint)
    }

    private fun drawLineElement(canvas: Canvas, element: DesignerLineElement, transform: CanonicalPageTransform) {
        val start = transform.map(element.start); val end = transform.map(element.end)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = max(0.6f, transform.length(element.strokeWidth).toFloat()) }
        canvas.drawLine(start.x.toFloat(), start.y.toFloat(), end.x.toFloat(), end.y.toFloat(), paint)
    }

    private fun drawSemanticLabels(canvas: Canvas, document: DesignerDocument, template: OmrTemplate, transform: CanonicalPageTransform) {
        val byQuestionId = template.bubbleRows.associateBy { it.id }; val byGridId = template.markGrids.associateBy { it.id }
        document.components.forEach { component ->
            when (component) {
                is QuestionGroupComponent -> repeat(component.questionCount) { index ->
                    val number = component.startQuestion + index
                    val row = byQuestionId[DesignerTemplateCompiler.questionReadId(component, number)] ?: return@repeat
                    val first = row.bubbles.firstOrNull() ?: return@repeat
                    drawLabel(canvas, number.toString(), first.center.x - first.radius * document.formSpec.answerAppearance.questionNumberDistanceInRadii, first.center.y + first.radius * 0.40, Paint.Align.RIGHT, first.radius * document.formSpec.answerAppearance.questionNumberScale, transform)
                    row.bubbles.forEach { drawBubbleLabel(canvas, it.id, it.center.x, it.center.y, it.radius, transform) }
                }
                is NumericGridComponent -> byGridId[component.id]?.columns.orEmpty().forEach { column -> column.marks.forEach { drawBubbleLabel(canvas, it.id, it.center.x, it.center.y, it.radius, transform) } }
                is SingleChoiceComponent -> byGridId[component.id]?.columns?.firstOrNull()?.marks.orEmpty().forEach { drawBubbleLabel(canvas, it.id, it.center.x, it.center.y, it.radius, transform) }
            }
        }
    }

    private fun drawComponentDecorations(canvas: Canvas, document: DesignerDocument, transform: CanonicalPageTransform) {
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = max(0.65f, transform.length(1.0).toFloat()) }
        document.components.forEach { component ->
            if (DesignerEditorLayout.componentShowsLabel(component)) {
                val label = DesignerEditorLayout.componentLabel(component)
                if (label.isNotBlank()) {
                    val anchor = DesignerEditorLayout.labelAnchor(component)
                    val align = when (DesignerEditorLayout.componentLabelAlignment(component)) {
                        DesignerTextAlignment.START -> Paint.Align.LEFT
                        DesignerTextAlignment.CENTER -> Paint.Align.CENTER
                        DesignerTextAlignment.END -> Paint.Align.RIGHT
                    }
                    drawLabel(canvas, label, anchor.x, anchor.y, align, DesignerEditorLayout.componentBubbleRadius(component) * 1.15, transform, bold = true)
                }
            }
            if (component is NumericGridComponent) {
                DesignerEditorLayout.numericHeaderBoxes(component).forEach { box ->
                    val rect = transform.map(box)
                    canvas.drawRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), boxPaint)
                }
            }
        }
    }

    private fun drawBubbleLabel(canvas: Canvas, text: String, x: Double, y: Double, radius: Double, transform: CanonicalPageTransform) =
        drawLabel(canvas, text, x, y + radius * 0.34, Paint.Align.CENTER, radius * 0.78, transform)

    private fun drawLabel(
        canvas: Canvas,
        text: String,
        canonicalX: Double,
        canonicalY: Double,
        align: Paint.Align,
        canonicalTextSize: Double,
        transform: CanonicalPageTransform,
        bold: Boolean = false
    ) {
        val point = transform.map(com.okulyonetim.optikokuyucu.omr.template.TemplatePoint(canonicalX, canonicalY))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; style = Paint.Style.FILL; textAlign = align
            textSize = max(4.2f, transform.length(canonicalTextSize).toFloat())
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        canvas.drawText(text, point.x.toFloat(), point.y.toFloat(), paint)
    }

    private fun drawOmrMarks(canvas: Canvas, template: OmrTemplate, transform: CanonicalPageTransform) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = max(0.7f, (transform.scale * 1.2).toFloat()) }
        template.bubbleRows.forEach { row -> row.bubbles.forEach { bubble -> val center = transform.map(bubble.center); canvas.drawCircle(center.x.toFloat(), center.y.toFloat(), transform.length(bubble.radius).toFloat(), paint) } }
        template.markGrids.forEach { grid -> grid.columns.forEach { column -> column.marks.forEach { mark -> val center = transform.map(mark.center); canvas.drawCircle(center.x.toFloat(), center.y.toFloat(), transform.length(mark.radius).toFloat(), paint) } } }
    }

    private fun drawFiducials(canvas: Canvas, template: OmrTemplate, transform: CanonicalPageTransform) {
        val dictionary = Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50)
        val markerPaint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
        template.fiducials.forEach { fiducial ->
            val markerMat = Mat(); var markerBitmap: Bitmap? = null
            try {
                dictionary.generateImageMarker(fiducial.markerId, MARKER_RASTER_SIZE, markerMat, 1)
                markerBitmap = Bitmap.createBitmap(MARKER_RASTER_SIZE, MARKER_RASTER_SIZE, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(markerMat, markerBitmap)
                val rect = transform.map(fiducial.bounds)
                canvas.drawBitmap(markerBitmap, null, RectF(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat()), markerPaint)
            } finally { markerBitmap?.recycle(); markerMat.release() }
        }
    }

    private const val MARKER_RASTER_SIZE = 256
}
