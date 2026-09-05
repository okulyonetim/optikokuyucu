package com.okulyonetim.optikokuyucu.omr.designer

import android.graphics.Bitmap
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
import kotlin.math.ceil
import kotlin.math.max

/**
 * Renders the same canonical geometry consumed by recognition into a physical PDF page profile.
 * Paper dimensions are output concerns only; the underlying OMR template is never rescaled/stored
 * differently for A4 vs A5.
 */
object DesignerPdfExporter {
    fun export(
        document: DesignerDocument,
        output: OutputStream,
        profile: PdfPageProfile = PdfPageProfile.A4
    ) {
        val template = DesignerTemplateCompiler.compile(document)
        val readability = TemplateReadabilityAnalyzer.analyze(template)
        require(readability.canSave) {
            "Template cannot be exported while readability errors exist."
        }

        val transform = DesignerPdfLayout.fit(template.space, profile)
        val pdf = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(
                profile.widthPoints,
                profile.heightPoints,
                1
            ).create()
            val page = pdf.startPage(pageInfo)
            try {
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                drawVisualLayer(canvas, document, transform)
                drawSemanticLabels(canvas, document, template, transform)
                drawOmrMarks(canvas, template, transform)
                drawFiducials(canvas, template, transform)
            } finally {
                pdf.finishPage(page)
            }
            pdf.writeTo(output)
            output.flush()
        } finally {
            pdf.close()
        }
    }

    private fun drawVisualLayer(
        canvas: Canvas,
        document: DesignerDocument,
        transform: CanonicalPageTransform
    ) {
        document.visualElements.forEach { element ->
            when (element) {
                is DesignerTextElement -> drawTextElement(canvas, element, transform)
                is DesignerBoxElement -> drawBoxElement(canvas, element, transform)
                is DesignerLineElement -> drawLineElement(canvas, element, transform)
            }
        }
    }

    private fun drawTextElement(
        canvas: Canvas,
        element: DesignerTextElement,
        transform: CanonicalPageTransform
    ) {
        val rect = transform.map(element.bounds)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            textSize = max(5.5f, transform.length(element.fontSize).toFloat())
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = when (element.alignment) {
                DesignerTextAlignment.START -> Paint.Align.LEFT
                DesignerTextAlignment.CENTER -> Paint.Align.CENTER
                DesignerTextAlignment.END -> Paint.Align.RIGHT
            }
        }
        val x = when (element.alignment) {
            DesignerTextAlignment.START -> rect.left.toFloat()
            DesignerTextAlignment.CENTER -> rect.center.x.toFloat()
            DesignerTextAlignment.END -> rect.right.toFloat()
        }
        val baseline = rect.top.toFloat() + paint.textSize
        canvas.drawText(element.text, x, baseline, paint)
    }

    private fun drawBoxElement(
        canvas: Canvas,
        element: DesignerBoxElement,
        transform: CanonicalPageTransform
    ) {
        val rect = transform.map(element.bounds)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = max(0.6f, transform.length(element.strokeWidth).toFloat())
        }
        canvas.drawRect(
            rect.left.toFloat(),
            rect.top.toFloat(),
            rect.right.toFloat(),
            rect.bottom.toFloat(),
            paint
        )
    }

    private fun drawLineElement(
        canvas: Canvas,
        element: DesignerLineElement,
        transform: CanonicalPageTransform
    ) {
        val start = transform.map(element.start)
        val end = transform.map(element.end)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = max(0.6f, transform.length(element.strokeWidth).toFloat())
        }
        canvas.drawLine(
            start.x.toFloat(),
            start.y.toFloat(),
            end.x.toFloat(),
            end.y.toFloat(),
            paint
        )
    }

    private fun drawSemanticLabels(
        canvas: Canvas,
        document: DesignerDocument,
        template: OmrTemplate,
        transform: CanonicalPageTransform
    ) {
        val byQuestionId = template.bubbleRows.associateBy { it.id }
        val byGridId = template.markGrids.associateBy { it.id }

        document.components.forEach { component ->
            when (component) {
                is QuestionGroupComponent -> {
                    repeat(component.questionCount) { index ->
                        val questionId = (component.startQuestion + index).toString()
                        val row = byQuestionId[questionId] ?: return@repeat
                        val first = row.bubbles.firstOrNull() ?: return@repeat
                        drawLabel(
                            canvas = canvas,
                            text = questionId,
                            canonicalX = first.center.x - first.radius * LABEL_DISTANCE,
                            canonicalY = first.center.y + first.radius * 0.42,
                            align = Paint.Align.RIGHT,
                            canonicalTextSize = first.radius * 1.08,
                            transform = transform
                        )
                        row.bubbles.forEach { bubble ->
                            drawLabel(
                                canvas = canvas,
                                text = bubble.id,
                                canonicalX = bubble.center.x,
                                canonicalY = bubble.center.y - bubble.radius * LABEL_DISTANCE,
                                align = Paint.Align.CENTER,
                                canonicalTextSize = bubble.radius * 0.92,
                                transform = transform
                            )
                        }
                    }
                }

                is NumericGridComponent -> {
                    val grid = byGridId[component.id] ?: return@forEach
                    val firstColumn = grid.columns.firstOrNull() ?: return@forEach
                    firstColumn.marks.forEach { mark ->
                        drawLabel(
                            canvas = canvas,
                            text = mark.id,
                            canonicalX = mark.center.x - mark.radius * LABEL_DISTANCE,
                            canonicalY = mark.center.y + mark.radius * 0.36,
                            align = Paint.Align.RIGHT,
                            canonicalTextSize = mark.radius * 0.92,
                            transform = transform
                        )
                    }
                    grid.columns.forEachIndexed { index, column ->
                        val firstMark = column.marks.firstOrNull() ?: return@forEachIndexed
                        drawLabel(
                            canvas = canvas,
                            text = (index + 1).toString(),
                            canonicalX = firstMark.center.x,
                            canonicalY = firstMark.center.y - firstMark.radius * LABEL_DISTANCE,
                            align = Paint.Align.CENTER,
                            canonicalTextSize = firstMark.radius * 0.82,
                            transform = transform
                        )
                    }
                }

                is SingleChoiceComponent -> {
                    val grid = byGridId[component.id] ?: return@forEach
                    val marks = grid.columns.firstOrNull()?.marks.orEmpty()
                    marks.forEach { mark ->
                        val (labelX, labelY, align) = if (component.axis == ChoiceAxis.HORIZONTAL) {
                            Triple(
                                mark.center.x,
                                mark.center.y - mark.radius * LABEL_DISTANCE,
                                Paint.Align.CENTER
                            )
                        } else {
                            Triple(
                                mark.center.x + mark.radius * LABEL_DISTANCE,
                                mark.center.y + mark.radius * 0.36,
                                Paint.Align.LEFT
                            )
                        }
                        drawLabel(
                            canvas = canvas,
                            text = mark.id,
                            canonicalX = labelX,
                            canonicalY = labelY,
                            align = align,
                            canonicalTextSize = mark.radius * 0.92,
                            transform = transform
                        )
                    }
                }
            }
        }
    }

    private fun drawLabel(
        canvas: Canvas,
        text: String,
        canonicalX: Double,
        canonicalY: Double,
        align: Paint.Align,
        canonicalTextSize: Double,
        transform: CanonicalPageTransform
    ) {
        val point = transform.map(
            com.okulyonetim.optikokuyucu.omr.template.TemplatePoint(
                canonicalX,
                canonicalY
            )
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            textAlign = align
            textSize = max(5.0f, transform.length(canonicalTextSize).toFloat())
        }
        canvas.drawText(text, point.x.toFloat(), point.y.toFloat(), paint)
    }

    private fun drawOmrMarks(
        canvas: Canvas,
        template: OmrTemplate,
        transform: CanonicalPageTransform
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = max(0.7f, (transform.scale * 1.45).toFloat())
        }

        template.bubbleRows.forEach { row ->
            row.bubbles.forEach { bubble ->
                val center = transform.map(bubble.center)
                canvas.drawCircle(
                    center.x.toFloat(),
                    center.y.toFloat(),
                    transform.length(bubble.radius).toFloat(),
                    paint
                )
            }
        }

        template.markGrids.forEach { grid ->
            grid.columns.forEach { column ->
                column.marks.forEach { mark ->
                    val center = transform.map(mark.center)
                    canvas.drawCircle(
                        center.x.toFloat(),
                        center.y.toFloat(),
                        transform.length(mark.radius).toFloat(),
                        paint
                    )
                }
            }
        }
    }

    private fun drawFiducials(
        canvas: Canvas,
        template: OmrTemplate,
        transform: CanonicalPageTransform
    ) {
        val dictionary = Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50)
        val markerPaint = Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
        }

        template.fiducials.forEach { fiducial ->
            val markerMat = Mat()
            var markerBitmap: Bitmap? = null
            try {
                dictionary.generateImageMarker(fiducial.markerId, MARKER_RASTER_SIZE, markerMat, 1)
                markerBitmap = Bitmap.createBitmap(
                    MARKER_RASTER_SIZE,
                    MARKER_RASTER_SIZE,
                    Bitmap.Config.ARGB_8888
                )
                Utils.matToBitmap(markerMat, markerBitmap)

                val rect = transform.map(fiducial.bounds)
                canvas.drawBitmap(
                    markerBitmap,
                    null,
                    RectF(
                        rect.left.toFloat(),
                        rect.top.toFloat(),
                        rect.right.toFloat(),
                        rect.bottom.toFloat()
                    ),
                    markerPaint
                )
            } finally {
                markerBitmap?.recycle()
                markerMat.release()
            }
        }
    }

    private const val LABEL_DISTANCE = 2.35
    private const val MARKER_RASTER_SIZE = 256
}
