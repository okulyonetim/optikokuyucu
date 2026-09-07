package com.okulyonetim.optikokuyucu.omr.scoring

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import kotlin.math.max

/** Compact teacher-facing A4 answer-key sheets inspired by classic table answer keys. */
object AnswerKeyPdfExporter {
    const val A4_WIDTH_POINTS = 595
    const val A4_HEIGHT_POINTS = 842
    const val COLUMNS = 2
    const val ROWS = 3
    const val COPIES_PER_SHEET = COLUMNS * ROWS

    data class SheetEntry(
        val key: StoredAnswerKey,
        val title: String,
        val sections: List<ManualAnswerSection>
    )

    fun exportMulti(entries: List<SheetEntry>, output: OutputStream) {
        require(entries.isNotEmpty()) { "PDF için en az bir cevap anahtarı gerekir." }
        val ordered = entries.sortedWith(compareBy<SheetEntry> { it.key.variantValue ?: "" })
        val sequence = buildSheetSequence(ordered)
        val pdf = PdfDocument()
        try {
            sequence.chunked(COPIES_PER_SHEET).forEachIndexed { pageIndex, pageEntries ->
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_POINTS, A4_HEIGHT_POINTS, pageIndex + 1).create()
                val page = pdf.startPage(pageInfo)
                try {
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)
                    pageEntries.forEachIndexed { slotIndex, entry ->
                        drawEntry(canvas, entry, slotRect(slotIndex))
                    }
                    drawCutGuides(canvas)
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

    internal fun buildSheetSequence(entries: List<SheetEntry>): List<SheetEntry> {
        require(entries.isNotEmpty())
        return if (entries.size >= 2) {
            val pair = entries.take(2)
            buildList(COPIES_PER_SHEET) {
                repeat(ROWS) {
                    add(pair[0])
                    add(pair[1])
                }
            }
        } else {
            List(COPIES_PER_SHEET) { entries.single() }
        }
    }

    private fun slotRect(index: Int): RectF {
        val outer = 18f
        val gapX = 10f
        val gapY = 10f
        val usableWidth = A4_WIDTH_POINTS - outer * 2f - gapX
        val usableHeight = A4_HEIGHT_POINTS - outer * 2f - gapY * (ROWS - 1)
        val width = usableWidth / COLUMNS
        val height = usableHeight / ROWS
        val column = index % COLUMNS
        val row = index / COLUMNS
        val left = outer + column * (width + gapX)
        val top = outer + row * (height + gapY)
        return RectF(left, top, left + width, top + height)
    }

    private fun drawEntry(canvas: Canvas, entry: SheetEntry, rect: RectF) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 90, 90)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val headerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(92, 92, 92)
            style = Paint.Style.FILL
        }
        val sectionFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 226, 226)
            style = Paint.Style.FILL
        }
        val titlePaint = textPaint(7.8f, bold = true, color = Color.WHITE)
        val sectionPaint = textPaint(6.2f, bold = true)
        val cellPaint = textPaint(5.8f)
        val variant = entry.key.variantValue?.let { " ($it Kitapçığı)" }.orEmpty()
        val heading = "${entry.title}$variant"

        canvas.drawRect(rect, border)
        val headerHeight = 18f
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + headerHeight, headerFill)
        drawCenteredText(canvas, heading, rect.centerX(), rect.top + headerHeight / 2f, titlePaint)

        val blocks = entry.sections.flatMap { section ->
            section.questionIds.chunked(MAX_QUESTIONS_PER_ROW).mapIndexed { index, questionIds ->
                SectionBlock(
                    label = if (index == 0) section.label.uppercase() else "${section.label.uppercase()} (DEVAM)",
                    questionIds = questionIds
                )
            }
        }
        if (blocks.isEmpty()) return

        val available = rect.height() - headerHeight - 4f
        val blockHeight = (available / blocks.size).coerceIn(22f, 36f)
        var y = rect.top + headerHeight + 2f
        blocks.forEach { block ->
            if (y + blockHeight > rect.bottom + 0.1f) return@forEach
            drawSectionBlock(
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

    private fun drawSectionBlock(
        canvas: Canvas,
        entry: SheetEntry,
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
        val labelHeight = max(8f, height * 0.32f)
        val rowHeight = (height - labelHeight) / 2f
        canvas.drawRect(left, top, right, top + labelHeight, sectionFill)
        canvas.drawRect(left, top, right, top + height, border)
        canvas.drawLine(left, top + labelHeight, right, top + labelHeight, border)
        canvas.drawLine(left, top + labelHeight + rowHeight, right, top + labelHeight + rowHeight, border)
        drawLeftText(canvas, block.label, left + 2f, top + labelHeight / 2f, sectionPaint)

        val cellWidth = (right - left) / MAX_QUESTIONS_PER_ROW
        for (column in 0..MAX_QUESTIONS_PER_ROW) {
            val x = left + column * cellWidth
            canvas.drawLine(x, top + labelHeight, x, top + height, border)
        }
        block.questionIds.forEachIndexed { index, questionId ->
            val centerX = left + cellWidth * (index + 0.5f)
            drawCenteredText(canvas, (index + 1).toString(), centerX, top + labelHeight + rowHeight / 2f, cellPaint)
            drawCenteredText(
                canvas,
                entry.key.answerKey.answers[questionId].orEmpty(),
                centerX,
                top + labelHeight + rowHeight + rowHeight / 2f,
                cellPaint
            )
        }
    }

    private fun drawCutGuides(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(180, 180, 180)
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
        }
        val middleX = A4_WIDTH_POINTS / 2f
        canvas.drawLine(middleX, 8f, middleX, A4_HEIGHT_POINTS - 8f, paint)
        for (row in 1 until ROWS) {
            val y = A4_HEIGHT_POINTS * row / ROWS.toFloat()
            canvas.drawLine(8f, y, A4_WIDTH_POINTS - 8f, y, paint)
        }
    }

    private fun textPaint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
            textSize = size
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
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
        val questionIds: List<String>
    )

    private const val MAX_QUESTIONS_PER_ROW = 20
}
