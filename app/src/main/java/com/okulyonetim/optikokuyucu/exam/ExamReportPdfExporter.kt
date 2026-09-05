package com.okulyonetim.optikokuyucu.exam

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A4, dependency-free PDF exporter for one exam result report. */
object ExamReportPdfExporter {
    const val MIME_TYPE = "application/pdf"

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val LEFT = 32f
    private const val RIGHT = 563f
    private const val TABLE_TOP = 118f
    private const val HEADER_HEIGHT = 24f
    private const val ROW_HEIGHT = 24f

    private val columns = floatArrayOf(
        32f, 54f, 190f, 230f, 280f, 316f, 344f, 372f, 400f, 460f, 563f
    )

    fun export(report: ExamReport, output: OutputStream) {
        val pdf = PdfDocument()
        try {
            val slices = ExamReportPdfLayout.pageSlices(report.rows.size)
            slices.forEach { slice ->
                val pageInfo = PdfDocument.PageInfo.Builder(
                    PAGE_WIDTH,
                    PAGE_HEIGHT,
                    slice.pageNumber
                ).create()
                val page = pdf.startPage(pageInfo)
                try {
                    drawPage(
                        canvas = page.canvas,
                        report = report,
                        slice = slice,
                        totalPages = slices.size
                    )
                } finally {
                    pdf.finishPage(page)
                }
            }
            pdf.writeTo(output)
            output.flush()
        } finally {
            pdf.close()
        }
    }

    private fun drawPage(
        canvas: Canvas,
        report: ExamReport,
        slice: ExamReportPdfPageSlice,
        totalPages: Int
    ) {
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val summaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        canvas.drawText(fittedText(report.examName, titlePaint, RIGHT - LEFT), LEFT, 50f, titlePaint)
        canvas.drawText(
            fittedText(report.schoolName.ifBlank { "Okul bilgisi yok" }, bodyPaint, RIGHT - LEFT),
            LEFT,
            69f,
            bodyPaint
        )
        canvas.drawText(
            "Kağıt ${report.paperCount}   •   Puanlandı ${report.scoredCount}   •   " +
                "Kontrol ${report.reviewRequiredCount}   •   Anahtar yok ${report.noAnswerKeyCount}   •   " +
                "Kayıt yok ${report.missingScanCount}",
            LEFT,
            92f,
            summaryPaint
        )
        canvas.drawText(
            "Oluşturma: ${formatDate(report.generatedAtEpochMs)}",
            LEFT,
            108f,
            bodyPaint
        )

        drawTableHeader(canvas)

        if (slice.rowCount == 0) {
            val emptyPaint = Paint(bodyPaint).apply { textSize = 11f }
            canvas.drawText("Henüz raporlanacak öğrenci kağıdı yok.", LEFT + 6f, TABLE_TOP + 62f, emptyPaint)
        } else {
            report.rows.subList(slice.fromIndex, slice.toIndexExclusive).forEachIndexed { index, row ->
                drawRow(canvas, row, index)
            }
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = 8.5f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(
            "Sayfa ${slice.pageNumber} / $totalPages",
            RIGHT,
            PAGE_HEIGHT - 24f,
            footerPaint
        )
    }

    private fun drawTableHeader(canvas: Canvas) {
        val background = Paint().apply {
            color = Color.rgb(238, 238, 238)
            style = Paint.Style.FILL
        }
        canvas.drawRect(LEFT, TABLE_TOP, RIGHT, TABLE_TOP + HEADER_HEIGHT, background)

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(180, 180, 180)
            strokeWidth = 0.7f
            style = Paint.Style.STROKE
        }
        canvas.drawRect(LEFT, TABLE_TOP, RIGHT, TABLE_TOP + HEADER_HEIGHT, border)
        columns.forEach { x ->
            canvas.drawLine(x, TABLE_TOP, x, TABLE_TOP + HEADER_HEIGHT, border)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headers = listOf("#", "Öğrenci", "Sınıf", "Numara", "Kit.", "D", "Y", "B", "Puan", "Durum")
        headers.forEachIndexed { index, text ->
            drawCellText(
                canvas = canvas,
                text = text,
                left = columns[index],
                right = columns[index + 1],
                baseline = TABLE_TOP + 16f,
                paint = paint
            )
        }
    }

    private fun drawRow(canvas: Canvas, row: ExamReportRow, localIndex: Int) {
        val top = TABLE_TOP + HEADER_HEIGHT + localIndex * ROW_HEIGHT
        val bottom = top + ROW_HEIGHT
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(210, 210, 210)
            strokeWidth = 0.55f
            style = Paint.Style.STROKE
        }
        canvas.drawRect(LEFT, top, RIGHT, bottom, border)
        columns.forEach { x -> canvas.drawLine(x, top, x, bottom, border) }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 7.8f
        }
        val baseline = top + 15.5f
        val name = row.studentName.ifBlank {
            row.studentNumber.takeIf { it.isNotBlank() }?.let { "Öğrenci $it" } ?: "İsimsiz"
        }
        val values = listOf(
            row.ordinal.toString(),
            name,
            row.className,
            row.studentNumber,
            row.bookletCode,
            row.correct?.toString().orEmpty(),
            row.wrong?.toString().orEmpty(),
            row.blank?.toString().orEmpty(),
            row.points?.let(::formatNumber).orEmpty(),
            statusLabel(row.status)
        )
        values.forEachIndexed { index, text ->
            drawCellText(
                canvas = canvas,
                text = text,
                left = columns[index],
                right = columns[index + 1],
                baseline = baseline,
                paint = paint
            )
        }
    }

    private fun drawCellText(
        canvas: Canvas,
        text: String,
        left: Float,
        right: Float,
        baseline: Float,
        paint: Paint
    ) {
        val inset = 3f
        val maxWidth = (right - left - inset * 2).coerceAtLeast(1f)
        canvas.drawText(
            fittedText(text, paint, maxWidth),
            left + inset,
            baseline,
            paint
        )
    }

    private fun fittedText(text: String, paint: Paint, maxWidth: Float): String {
        if (text.isEmpty() || paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val ellipsisWidth = paint.measureText(ellipsis)
        if (ellipsisWidth >= maxWidth) return ""

        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + ellipsisWidth > maxWidth) {
            end -= 1
        }
        return if (end == 0) ellipsis else text.substring(0, end).trimEnd() + ellipsis
    }

    private fun statusLabel(status: ExamReportRowStatus): String = when (status) {
        ExamReportRowStatus.SCORED -> "Puanlandı"
        ExamReportRowStatus.REVIEW_REQUIRED -> "Kontrol"
        ExamReportRowStatus.NO_ANSWER_KEY -> "Anahtar yok"
        ExamReportRowStatus.SCAN_MISSING -> "Kayıt yok"
    }

    private fun formatNumber(value: Double): String =
        String.format(Locale("tr", "TR"), "%.2f", value)

    private fun formatDate(epochMs: Long): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR")).format(Date(epochMs))
}
