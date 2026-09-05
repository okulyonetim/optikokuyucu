package com.okulyonetim.optikokuyucu.omr.diagnostics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.objdetect.Objdetect
import kotlin.math.roundToInt

/**
 * Renders any logical OMR template into a deterministic synthetic bitmap.
 *
 * The renderer deliberately uses the same template coordinates as recognition. It is useful for
 * regression benchmarks today and can later power the form designer's "Test Et" workflow.
 */
object SyntheticOmrRenderer {
    fun render(
        template: OmrTemplate,
        markedChoicesByRow: Map<String, Set<String>>,
        markGray: Int = 0,
        markedGridChoices: Map<String, Map<String, Set<String>>> = emptyMap()
    ): Bitmap {
        require(markGray in 0..255)
        val width = template.space.width.roundToInt()
        val height = template.space.height.roundToInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        drawFiducials(canvas, template)

        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(markGray, markGray, markGray)
            style = Paint.Style.FILL
        }

        template.bubbleRows.forEach { row ->
            val marked = markedChoicesByRow[row.id].orEmpty()
            row.bubbles.forEach { bubble ->
                drawBubble(canvas, bubble.center.x, bubble.center.y, bubble.radius, bubble.id in marked, outlinePaint, fillPaint)
            }
        }

        template.markGrids.forEach { grid ->
            val markedByColumn = markedGridChoices[grid.id].orEmpty()
            grid.columns.forEach { column ->
                val marked = markedByColumn[column.id].orEmpty()
                column.marks.forEach { mark ->
                    drawBubble(canvas, mark.center.x, mark.center.y, mark.radius, mark.id in marked, outlinePaint, fillPaint)
                }
            }
        }

        return bitmap
    }

    private fun drawBubble(
        canvas: Canvas,
        x: Double,
        y: Double,
        radius: Double,
        filled: Boolean,
        outlinePaint: Paint,
        fillPaint: Paint
    ) {
        canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), outlinePaint)
        if (filled) {
            canvas.drawCircle(
                x.toFloat(),
                y.toFloat(),
                (radius * 0.78).toFloat(),
                fillPaint
            )
        }
    }

    private fun drawFiducials(canvas: Canvas, template: OmrTemplate) {
        val dictionary = Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50)
        template.fiducials.forEach { spec ->
            val size = spec.bounds.width.roundToInt()
            val markerMat = Mat()
            try {
                dictionary.generateImageMarker(spec.markerId, size, markerMat, 1)
                val markerBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                try {
                    Utils.matToBitmap(markerMat, markerBitmap)
                    canvas.drawBitmap(
                        markerBitmap,
                        spec.bounds.left.toFloat(),
                        spec.bounds.top.toFloat(),
                        null
                    )
                } finally {
                    markerBitmap.recycle()
                }
            } finally {
                markerMat.release()
            }
        }
    }
}
