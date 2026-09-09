package com.okulyonetim.optikokuyucu.omr.scoring

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import kotlin.math.max

object MiniAnswerKeyPdfExporter {
    enum class Orientation { PORTRAIT, LANDSCAPE }

    fun export(
        entries: List<AnswerKeyPdfExporter.SheetEntry>,
        copiesPerPage: Int,
        orientation: Orientation,
        output: OutputStream
    ) {
        require(entries.isNotEmpty()) { "PDF için en az bir cevap anahtarı gerekir." }
        require(copiesPerPage in setOf(2, 4, 6, 8)) { "Sayfa başına kopya 2, 4, 6 veya 8 olmalıdır." }
        val pageWidth = if (orientation == Orientation.LANDSCAPE) 842 else 595
        val pageHeight = if (orientation == Orientation.LANDSCAPE) 595 else 842
        val (columns, rows) = grid(copiesPerPage, orientation)
        val sequence = List(copiesPerPage) { index -> entries[index % entries.size] }
        val pdf = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdf.startPage(pageInfo)
            try {
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                sequence.forEachIndexed { index, entry ->
                    drawEntry(canvas, entry, slotRect(index, columns, rows, pageWidth, pageHeight))
                }
                drawCutGuides(canvas, columns, rows, pageWidth, pageHeight)
            } finally {
                pdf.finishPage(page)
            }
            pdf.writeTo(output)
            output.flush()
        } finally {
            pdf.close()
        }
    }

    private fun grid(copies: Int, orientation: Orientation): Pair<Int, Int> = when (copies) {
        2 -> if (orientation == Orientation.LANDSCAPE) 2 to 1 else 1 to 2
        4 -> 2 to 2
        6 -> if (orientation == Orientation.LANDSCAPE) 3 to 2 else 2 to 3
        else -> if (orientation == Orientation.LANDSCAPE) 4 to 2 else 2 to 4
    }

    private fun slotRect(index: Int, columns: Int, rows: Int, pageWidth: Int, pageHeight: Int): RectF {
        val outer = 16f
        val gap = 8f
        val usableWidth = pageWidth - outer * 2f - gap * (columns - 1)
        val usableHeight = pageHeight - outer * 2f - gap * (rows - 1)
        val width = usableWidth / columns
        val height = usableHeight / rows
        val column = index % columns
        val row = index / columns
        val left = outer + column * (width + gap)
        val top = outer + row * (height + gap)
        return RectF(left, top, left + width, top + height)
    }

    private fun drawEntry(canvas: Canvas, entry: AnswerKeyPdfExporter.SheetEntry, rect: RectF) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 90, 90)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val headerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(70, 86, 80)
            style = Paint.Style.FILL
        }
        val sectionFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(232, 237, 234)
            style = Paint.Style.FILL
        }
        val titlePaint = textPaint((rect.width() / 40f).coerceIn(6.8f, 9f), bold = true, color = Color.WHITE)
        val sectionPaint = textPaint((rect.width() / 48f).coerceIn(5.5f, 7f), bold = true)
        val cellPaint = textPaint((rect.width() / 52f).coerceIn(5.2f, 6.5f))
        val variant = entry.key.variantValue?.let { " ($it Kitapçığı)" }.orEmpty()
        val heading = "${entry.title}$variant"

        canvas.drawRect(rect, border)
        val headerHeight = (rect.height() * 0.10f).coerceIn(16f, 23f)
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + headerHeight, headerFill)
        drawCenteredText(canvas, fitted(heading, titlePaint, rect.width() - 8f), rect.centerX(), rect.top + headerHeight / 2f, titlePaint)

        val maxQuestions = when {
            rect.width() >= 360f -> 25
            rect.width() >= 260f -> 20
            else -> 15
        }
        val blocks = entry.sections.flatMap { section ->
            section.questionIds.chunked(maxQuestions).mapIndexed { index, questionIds ->
                SectionBlock(
                    label = if (index == 0) section.label.uppercase() else "${section.label.uppercase()} (DEVAM)",
                    questionIds = questionIds,
                    startNumber = index * maxQuestions + 1,
                    maxColumns = maxQuestions
                )
            }
        }
        if (blocks.isEmpty()) return
        val available = rect.height() - headerHeight - 4f
        val blockHeight = (available / blocks.size).coerceAtLeast(18f)
        var y = rect.top + headerHeight + 2f
        blocks.forEach { block ->
            if (y + blockHeight > rect.bottom + 0.1f) return@forEach
            drawBlock(
                canvas = canvas,
                entry = entry,
                block = block,
                left = rect.left + 2f,
                top = y,
                right = rect.right - 2f,
                height = blockHeight,
                border = border,
                sectionFill = sectionFill,
                sectionPaint = sectionPaint,
                cellPaint = cellPaint
            )
            y += blockHeight
        }
    }

    private fun drawBlock(
        canvas: Canvas,
        entry: AnswerKeyPdfExporter.SheetEntry,
        block: SectionBlock,
        left: Float,
        top: Float,
        right: Float,
        height: Float,
        border: Paint,
        sectionFill: Paint,
        sectionPaint: Paint,
        cellPaint: Paint
    ) {
        val labelHeight = max(7f, height * 0.30f)
        val rowHeight = (height - labelHeight) / 2f
        canvas.drawRect(left, top, right, top + labelHeight, sectionFill)
        canvas.drawRect(left, top, right, top + height, border)
        canvas.drawLine(left, top + labelHeight, right, top + labelHeight, border)
        canvas.drawLine(left, top + labelHeight + rowHeight, right, top + labelHeight + rowHeight, border)
        drawLeftText(canvas, fitted(block.label, sectionPaint, right - left - 4f), left + 2f, top + labelHeight / 2f, sectionPaint)

        val cellWidth = (right - left) / block.maxColumns
        for (column in 0..block.maxColumns) {
            val x = left + column * cellWidth
            canvas.drawLine(x, top + labelHeight, x, top + height, border)
        }
        block.questionIds.forEachIndexed { index, questionId ->
            val centerX = left + cellWidth * (index + 0.5f)
            drawCenteredText(canvas, (block.startNumber + index).toString(), centerX, top + labelHeight + rowHeight / 2f, cellPaint)
            drawCenteredText(canvas, entry.key.answerKey.answers[questionId].orEmpty(), centerX, top + labelHeight + rowHeight + rowHeight / 2f, cellPaint)
        }
    }

    private fun drawCutGuides(canvas: Canvas, columns: Int, rows: Int, pageWidth: Int, pageHeight: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(180, 180, 180)
            style = Paint.Style.STROKE
            strokeWidth = 0.55f
        }
        for (column in 1 until columns) {
            val x = pageWidth * column / columns.toFloat()
            canvas.drawLine(x, 7f, x, pageHeight - 7f, paint)
        }
        for (row in 1 until rows) {
            val y = pageHeight * row / rows.toFloat()
            canvas.drawLine(7f, y, pageWidth - 7f, y, paint)
        }
    }

    private fun textPaint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
        textSize = size
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun fitted(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText(ellipsis) > maxWidth) end--
        return if (end <= 0) ellipsis else text.substring(0, end).trimEnd() + ellipsis
    }

    private fun drawCenteredText(canvas: Canvas, text: String, x: Float, centerY: Float, paint: Paint) {
        val metrics = paint.fontMetrics
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, x - paint.measureText(text) / 2f, baseline, paint)
    }

    private fun drawLeftText(canvas: Canvas, text: String, x: Float, centerY: Float, paint: Paint) {
        val metrics = paint.fontMetrics
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, x, baseline, paint)
    }

    private data class SectionBlock(
        val label: String,
        val questionIds: List<String>,
        val startNumber: Int,
        val maxColumns: Int
    )
}
