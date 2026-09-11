package com.okulyonetim.optikokuyucu.exam

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale

/** One-page, parent-friendly student result report inspired by classic optical-reader reports. */
object StudentResultPdfExporter {
    const val MIME_TYPE = "application/pdf"

    fun exportBytes(
        presentation: StudentResultPresentation,
        typeface: Typeface? = null
    ): ByteArray {
        val output = ByteArrayOutputStream()
        export(presentation, output, typeface)
        return output.toByteArray()
    }

    fun export(
        presentation: StudentResultPresentation,
        output: OutputStream,
        typeface: Typeface? = null
    ) {
        val pageWidth = 595
        val pageHeight = 842
        val pdf = PdfDocument()
        val normal = typeface ?: Typeface.create("sans-serif", Typeface.NORMAL)
        val bold = Typeface.create(normal, Typeface.BOLD)
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
            try {
                drawPage(page.canvas, presentation, pageWidth, pageHeight, normal, bold)
            } finally {
                pdf.finishPage(page)
            }
            pdf.writeTo(output)
            output.flush()
        } finally {
            pdf.close()
        }
    }

    private fun drawPage(
        canvas: Canvas,
        p: StudentResultPresentation,
        pageWidth: Int,
        pageHeight: Int,
        normal: Typeface,
        bold: Typeface
    ) {
        canvas.drawColor(Color.WHITE)
        val brand = Color.rgb(70, 58, 166)
        val brandSoft = Color.rgb(240, 238, 252)
        val ink = Color.rgb(28, 32, 40)
        val muted = Color.rgb(92, 99, 113)
        val line = Color.rgb(211, 214, 223)
        val green = Color.rgb(61, 155, 86)
        val red = Color.rgb(211, 72, 72)

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 11f
            typeface = bold
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 8.2f
            typeface = normal
        }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 7f
            typeface = normal
        }
        val boldSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 7.4f
            typeface = bold
        }

        val left = 48f
        val right = pageWidth - 48f
        canvas.drawText(fitted(p.examName, title, 260f), left, 53f, title)
        val rightTitle = "Öğrenci Sonuçları"
        canvas.drawText(rightTitle, right - title.measureText(rightTitle), 53f, title)
        canvas.drawText(fitted(p.schoolName, small, 310f), left, 68f, small)

        val studentName = p.studentName.ifBlank { "İsimsiz Öğrenci" }
        drawLabeled(canvas, "Ad Soyad", studentName, left, 95f, body, small, bold)
        drawLabeled(canvas, "Sınıf", p.className.ifBlank { "—" }, 355f, 95f, body, small, bold)
        drawLabeled(canvas, "Numara", p.studentNumber.ifBlank { "—" }, 455f, 95f, body, small, bold)

        val metricY = 132f
        val metricWidth = (right - left - 12f) / 4f
        val metricLabels = listOf("Toplam Net", "Genel Sıra", p.scoreLabel, "Sınıf Sırası")
        val metricValues = listOf(
            p.net?.let(::number) ?: "—",
            p.overallRank?.displayText ?: "—",
            p.score?.let(::scoreNumber) ?: "—",
            p.classRank?.displayText ?: "—"
        )
        repeat(4) { index ->
            val x = left + index * (metricWidth + 4f)
            canvas.drawRoundRect(x, metricY, x + metricWidth, metricY + 42f, 6f, 6f, Paint().apply { color = brandSoft })
            canvas.drawText(metricLabels[index], x + 8f, metricY + 14f, small)
            val valuePaint = Paint(body).apply { typeface = bold; textSize = 13f; color = brand }
            canvas.drawText(metricValues[index], x + 8f, metricY + 32f, valuePaint)
        }

        val tableTop = 196f
        val tableLeft = left
        val tableRight = right
        val rowHeight = 23f
        val widths = floatArrayOf(0.31f, 0.10f, 0.10f, 0.10f, 0.17f, 0.22f)
        val xs = FloatArray(widths.size + 1)
        xs[0] = tableLeft
        widths.forEachIndexed { index, ratio -> xs[index + 1] = xs[index] + (tableRight - tableLeft) * ratio }
        val headerPaint = Paint().apply { color = brand }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = line; style = Paint.Style.STROKE; strokeWidth = 0.7f }
        val headerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 7.2f; typeface = bold; textAlign = Paint.Align.CENTER
        }
        val cell = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; textSize = 7.3f; typeface = normal; textAlign = Paint.Align.CENTER
        }
        val headers = listOf("Ders", "D", "Y", "B", "Net", "Sıralama")
        headers.indices.forEach { index ->
            canvas.drawRect(xs[index], tableTop, xs[index + 1], tableTop + rowHeight, headerPaint)
            canvas.drawRect(xs[index], tableTop, xs[index + 1], tableTop + rowHeight, border)
            drawCentered(canvas, headers[index], xs[index], xs[index + 1], tableTop, rowHeight, headerText)
        }

        p.lessons.forEachIndexed { rowIndex, lesson ->
            val y = tableTop + rowHeight * (rowIndex + 1)
            val values = listOf(
                lesson.lessonName,
                lesson.correct.toString(),
                lesson.wrong.toString(),
                lesson.blank.toString(),
                number(lesson.net),
                lesson.rank?.displayText ?: "—"
            )
            values.indices.forEach { column ->
                if (rowIndex % 2 == 0) {
                    canvas.drawRect(xs[column], y, xs[column + 1], y + rowHeight, Paint().apply { color = Color.rgb(249, 249, 252) })
                }
                canvas.drawRect(xs[column], y, xs[column + 1], y + rowHeight, border)
                if (column == 0) {
                    val leftCell = Paint(cell).apply { textAlign = Paint.Align.LEFT; typeface = bold }
                    canvas.drawText(fitted(values[column], leftCell, xs[column + 1] - xs[column] - 8f), xs[column] + 4f, baseline(y, rowHeight, leftCell), leftCell)
                } else {
                    drawCentered(canvas, values[column], xs[column], xs[column + 1], y, rowHeight, cell)
                }
            }
        }

        val totalY = tableTop + rowHeight * (p.lessons.size + 1)
        val totalValues = listOf(
            "Toplam",
            p.correct?.toString() ?: "—",
            p.wrong?.toString() ?: "—",
            p.blank?.toString() ?: "—",
            p.net?.let(::number) ?: "—",
            p.overallRank?.displayText ?: "—"
        )
        totalValues.indices.forEach { column ->
            canvas.drawRect(xs[column], totalY, xs[column + 1], totalY + rowHeight, Paint().apply { color = brandSoft })
            canvas.drawRect(xs[column], totalY, xs[column + 1], totalY + rowHeight, border)
            val totalPaint = Paint(cell).apply { typeface = bold }
            if (column == 0) {
                totalPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(totalValues[column], xs[column] + 4f, baseline(totalY, rowHeight, totalPaint), totalPaint)
            } else {
                drawCentered(canvas, totalValues[column], xs[column], xs[column + 1], totalY, rowHeight, totalPaint)
            }
        }

        val chartTop = totalY + 48f
        canvas.drawText("Ders Başarı Grafiği", chartLeft(p, left), chartTop - 13f, boldSmall)
        drawLegend(canvas, right - 122f, chartTop - 17f, "Doğru", green, small)
        drawLegend(canvas, right - 62f, chartTop - 17f, "Yanlış", red, small)
        drawChart(canvas, p, left, right, chartTop, green, red, line, muted, small, boldSmall)

        val totalsTop = chartTop + 192f
        canvas.drawText("Toplam Doğru: ${p.correct ?: 0}", left, totalsTop, boldSmall)
        canvas.drawText("Toplam Yanlış: ${p.wrong ?: 0}", left + 125f, totalsTop, boldSmall)
        canvas.drawText("Toplam Net: ${p.net?.let(::number) ?: "—"}", left + 260f, totalsTop, boldSmall)
        canvas.drawText("${p.scoreLabel}: ${p.score?.let(::scoreNumber) ?: "—"}", left + 380f, totalsTop, boldSmall)

        if (p.scoreNote.isNotBlank()) {
            val note = fitted(p.scoreNote, small, right - left)
            canvas.drawText(note, left, pageHeight - 48f, small)
        }
        val footer = "Optik Okuyucu · Öğrenci Sonuç Raporu"
        canvas.drawText(footer, left, pageHeight - 26f, small)
    }

    private fun drawChart(
        canvas: Canvas,
        p: StudentResultPresentation,
        left: Float,
        right: Float,
        top: Float,
        green: Int,
        red: Int,
        line: Int,
        muted: Int,
        small: Paint,
        boldSmall: Paint
    ) {
        val axisLeft = left + 32f
        val chartRight = right
        val chartHeight = 118f
        val bottom = top + chartHeight
        val grid = Paint().apply { color = line; strokeWidth = 0.6f }
        val axisText = Paint(small).apply { color = muted; textAlign = Paint.Align.RIGHT; textSize = 6.3f }
        for (step in 0..5) {
            val percent = 100 - step * 20
            val y = top + chartHeight * step / 5f
            canvas.drawLine(axisLeft, y, chartRight, y, grid)
            canvas.drawText("% $percent", axisLeft - 5f, y + 2f, axisText)
        }
        if (p.lessons.isEmpty()) return
        val groupWidth = (chartRight - axisLeft) / p.lessons.size
        val barWidth = (groupWidth * 0.23f).coerceAtMost(15f)
        p.lessons.forEachIndexed { index, lesson ->
            val center = axisLeft + groupWidth * (index + 0.5f)
            val correctHeight = chartHeight * (lesson.correctPercent / 100.0).toFloat().coerceIn(0f, 1f)
            val wrongHeight = chartHeight * (lesson.wrongPercent / 100.0).toFloat().coerceIn(0f, 1f)
            canvas.drawRect(center - barWidth - 1f, bottom - correctHeight, center - 1f, bottom, Paint().apply { color = green })
            canvas.drawRect(center + 1f, bottom - wrongHeight, center + barWidth + 1f, bottom, Paint().apply { color = red })
            val label = shortLessonName(lesson.lessonName)
            val labelPaint = Paint(boldSmall).apply { textAlign = Paint.Align.CENTER; textSize = 6.3f }
            canvas.drawText(fitted(label, labelPaint, groupWidth - 4f), center, bottom + 14f, labelPaint)
        }
    }

    private fun drawLegend(canvas: Canvas, x: Float, y: Float, label: String, color: Int, textPaint: Paint) {
        canvas.drawRect(x, y - 7f, x + 8f, y + 1f, Paint().apply { this.color = color })
        canvas.drawText(label, x + 12f, y, textPaint)
    }

    private fun drawLabeled(
        canvas: Canvas,
        label: String,
        value: String,
        x: Float,
        y: Float,
        body: Paint,
        small: Paint,
        bold: Typeface
    ) {
        canvas.drawText(label, x, y, small)
        canvas.drawText(fitted(value, Paint(body).apply { typeface = bold }, 235f), x, y + 13f, Paint(body).apply { typeface = bold })
    }

    private fun drawCentered(canvas: Canvas, text: String, left: Float, right: Float, top: Float, height: Float, paint: Paint) {
        val value = fitted(text, paint, right - left - 6f)
        canvas.drawText(value, (left + right) / 2f, baseline(top, height, paint), paint)
    }

    private fun baseline(top: Float, height: Float, paint: Paint): Float =
        top + (height - paint.descent() - paint.ascent()) / 2f

    private fun fitted(text: String, paint: Paint, maxWidth: Float): String {
        if (text.isBlank() || paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText(ellipsis) > maxWidth) end--
        return if (end <= 0) ellipsis else text.substring(0, end).trimEnd() + ellipsis
    }

    private fun chartLeft(p: StudentResultPresentation, left: Float): Float = left

    private fun number(value: Double): String = String.format(Locale.forLanguageTag("tr-TR"), "%.1f", value)
    private fun scoreNumber(value: Double): String = String.format(Locale.forLanguageTag("tr-TR"), "%.1f", value)

    private fun shortLessonName(name: String): String = when {
        name.startsWith("Türkçe", ignoreCase = true) -> "Tür"
        name.startsWith("İnkılap", ignoreCase = true) -> "İnk"
        name.startsWith("Din", ignoreCase = true) -> "Din"
        name.startsWith("Yabancı", ignoreCase = true) -> "Yab"
        name.startsWith("Matematik", ignoreCase = true) -> "Mat"
        name.startsWith("Fen", ignoreCase = true) -> "Fen"
        name.startsWith("Sosyal", ignoreCase = true) -> "Sos"
        else -> name.take(5)
    }
}
