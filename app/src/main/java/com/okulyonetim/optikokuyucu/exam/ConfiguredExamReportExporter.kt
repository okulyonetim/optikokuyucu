package com.okulyonetim.optikokuyucu.exam

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ReportPageOrientation { PORTRAIT, LANDSCAPE }

enum class ReportColumn(val label: String, val weight: Float) {
    STUDENT("Öğrenci", 2.4f),
    NUMBER("No", 0.9f),
    CLASS("Sınıf", 0.9f),
    BOOKLET("Kit.", 0.7f),
    SCORE("Puan", 1.0f),
    NET("Net", 0.9f),
    CORRECT("D", 0.55f),
    WRONG("Y", 0.55f),
    BLANK("B", 0.55f),
    OVERALL_RANK("Genel Sıra", 0.9f),
    CLASS_RANK("Sınıf Sıra", 0.9f),
    LESSONS("Ders Sonuçları", 3.2f)
}

data class ConfiguredExamReport(
    val report: ExamReport,
    val rows: List<ExamReportRow>,
    val columns: List<ReportColumn>,
    val selectedLessonIds: Set<String> = emptySet(),
    val orientation: ReportPageOrientation = ReportPageOrientation.PORTRAIT,
    val titleSuffix: String = ""
)

object ConfiguredExamReportExporter {
    const val PDF_MIME_TYPE = "application/pdf"
    const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun exportPdf(config: ConfiguredExamReport, output: OutputStream) {
        require(config.columns.isNotEmpty()) { "Rapor için en az bir alan seçilmelidir." }
        val orientation = effectiveOrientation(config)
        val pageWidth = if (orientation == ReportPageOrientation.LANDSCAPE) 842 else 595
        val pageHeight = if (orientation == ReportPageOrientation.LANDSCAPE) 595 else 842
        val left = 24f
        val right = pageWidth - 24f
        val top = 132f
        val bottom = pageHeight - 34f
        val headerHeight = 27f
        val rowHeight = if (ReportColumn.LESSONS in config.columns) 38f else 28f
        val rowsPerPage = ((bottom - top - headerHeight) / rowHeight).toInt().coerceAtLeast(1)
        val pages = config.rows.chunked(rowsPerPage).ifEmpty { listOf(emptyList()) }
        val boundaries = columnBoundaries(config.columns, left, right)

        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { pageIndex, rows ->
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = pdf.startPage(info)
                try {
                    drawPdfPage(
                        canvas = page.canvas,
                        config = config,
                        rows = rows,
                        pageIndex = pageIndex,
                        totalPages = pages.size,
                        pageWidth = pageWidth,
                        pageHeight = pageHeight,
                        top = top,
                        headerHeight = headerHeight,
                        rowHeight = rowHeight,
                        boundaries = boundaries,
                        orientation = orientation
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

    fun exportXlsx(config: ConfiguredExamReport): ByteArray {
        require(config.columns.isNotEmpty()) { "Rapor için en az bir alan seçilmelidir." }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putText("[Content_Types].xml", contentTypesXml())
            zip.putText("_rels/.rels", packageRelationshipsXml())
            zip.putText("xl/workbook.xml", workbookXml())
            zip.putText("xl/_rels/workbook.xml.rels", workbookRelationshipsXml())
            zip.putText("xl/styles.xml", stylesXml())
            zip.putText("xl/worksheets/sheet1.xml", worksheetXml(config))
        }
        return output.toByteArray()
    }

    private fun drawPdfPage(
        canvas: Canvas,
        config: ConfiguredExamReport,
        rows: List<ExamReportRow>,
        pageIndex: Int,
        totalPages: Int,
        pageWidth: Int,
        pageHeight: Int,
        top: Float,
        headerHeight: Float,
        rowHeight: Float,
        boundaries: FloatArray,
        orientation: ReportPageOrientation
    ) {
        canvas.drawColor(Color.WHITE)

        val brand = Color.rgb(22, 90, 70)
        val brandSoft = Color.rgb(232, 244, 239)
        val ink = Color.rgb(31, 42, 38)
        val muted = Color.rgb(92, 105, 100)
        val line = Color.rgb(205, 216, 211)
        val alternate = Color.rgb(247, 250, 249)

        val brandPaint = Paint().apply { color = brand; style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 78f, brandPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(221, 240, 233)
            textSize = 9f
        }
        val title = buildString {
            append(config.report.examName)
            if (config.titleSuffix.isNotBlank()) append(" · ${config.titleSuffix}")
        }
        canvas.drawText(fitted(title, titlePaint, pageWidth - 48f), 24f, 34f, titlePaint)
        canvas.drawText(
            fitted(config.report.schoolName.ifBlank { "Okul bilgisi yok" }, subtitlePaint, pageWidth - 48f),
            24f,
            55f,
            subtitlePaint
        )

        val scored = config.rows.count { it.status == ExamReportRowStatus.SCORED }
        val averageScore = config.rows.mapNotNull { it.points }.takeIf { it.isNotEmpty() }?.average()
        val averageNet = config.rows.mapNotNull { it.net }.takeIf { it.isNotEmpty() }?.average()
        val summaryLabels = listOf("KAYIT", "PUAN ORT.", "NET ORT.")
        val summaryValues = listOf(
            config.rows.size.toString(),
            averageScore?.let(::number) ?: "—",
            averageNet?.let(::number) ?: "—"
        )
        val summaryGap = 8f
        val summaryTop = 88f
        val summaryHeight = 32f
        val summaryWidth = (pageWidth - 48f - summaryGap * 2f) / 3f
        val summaryFill = Paint().apply { color = brandSoft; style = Paint.Style.FILL }
        val summaryLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 6.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val summaryValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brand
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        repeat(3) { index ->
            val x = 24f + index * (summaryWidth + summaryGap)
            canvas.drawRoundRect(x, summaryTop, x + summaryWidth, summaryTop + summaryHeight, 6f, 6f, summaryFill)
            canvas.drawText(summaryLabels[index], x + 8f, summaryTop + 11f, summaryLabelPaint)
            canvas.drawText(summaryValues[index], x + 8f, summaryTop + 26f, summaryValuePaint)
        }

        val headerFill = Paint().apply { color = brand; style = Paint.Style.FILL }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = line
            strokeWidth = 0.65f
            style = Paint.Style.STROKE
        }
        val headerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (config.columns.size >= 9) 7f else 7.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cellText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = when {
                config.columns.size >= 10 -> 6.9f
                config.columns.size >= 8 -> 7.3f
                else -> 8f
            }
        }
        val alternateFill = Paint().apply { color = alternate; style = Paint.Style.FILL }

        canvas.drawRect(boundaries.first(), top, boundaries.last(), top + headerHeight, headerFill)
        config.columns.forEachIndexed { index, column ->
            drawCell(
                canvas = canvas,
                value = column.label,
                left = boundaries[index],
                right = boundaries[index + 1],
                top = top,
                height = headerHeight,
                paint = headerText,
                maxLines = 1
            )
        }

        rows.forEachIndexed { rowIndex, row ->
            val rowTop = top + headerHeight + rowIndex * rowHeight
            val rowBottom = rowTop + rowHeight
            if (rowIndex % 2 == 1) {
                canvas.drawRect(boundaries.first(), rowTop, boundaries.last(), rowBottom, alternateFill)
            }
            canvas.drawRect(boundaries.first(), rowTop, boundaries.last(), rowBottom, border)
            boundaries.forEach { x -> canvas.drawLine(x, rowTop, x, rowBottom, border) }
            config.columns.forEachIndexed { index, column ->
                drawCell(
                    canvas = canvas,
                    value = columnValue(row, column, config.selectedLessonIds),
                    left = boundaries[index],
                    right = boundaries[index + 1],
                    top = rowTop,
                    height = rowHeight,
                    paint = cellText,
                    maxLines = if (column == ReportColumn.LESSONS) 2 else 1
                )
            }
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 7f
        }
        canvas.drawText(
            "${pageIndex + 1} / $totalPages · ${if (orientation == ReportPageOrientation.LANDSCAPE) "Yatay" else "Dikey"} · $scored puanlanan",
            24f,
            pageHeight - 17f,
            footerPaint
        )
        val brandFooter = "Optik Okuyucu"
        canvas.drawText(
            brandFooter,
            pageWidth - 24f - footerPaint.measureText(brandFooter),
            pageHeight - 17f,
            footerPaint
        )
    }

    private fun effectiveOrientation(config: ConfiguredExamReport): ReportPageOrientation =
        if (config.columns.size >= 8 || ReportColumn.LESSONS in config.columns) {
            ReportPageOrientation.LANDSCAPE
        } else {
            config.orientation
        }

    private fun columnBoundaries(columns: List<ReportColumn>, left: Float, right: Float): FloatArray {
        val total = columns.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)
        val width = right - left
        val result = FloatArray(columns.size + 1)
        result[0] = left
        var current = left
        columns.forEachIndexed { index, column ->
            current += width * (column.weight / total)
            result[index + 1] = current
        }
        result[result.lastIndex] = right
        return result
    }

    private fun drawCell(
        canvas: Canvas,
        value: String,
        left: Float,
        right: Float,
        top: Float,
        height: Float,
        paint: Paint,
        maxLines: Int
    ) {
        if (value.isBlank()) return
        val inset = 3f
        val maxWidth = (right - left - inset * 2).coerceAtLeast(1f)
        val lineHeight = paint.textSize + 2f
        val lines = if (maxLines <= 1) {
            listOf(fitted(value, paint, maxWidth))
        } else {
            splitForCell(value, paint, maxWidth, maxLines)
        }
        val contentHeight = lines.size * lineHeight
        var baseline = top + (height - contentHeight) / 2f + paint.textSize
        lines.forEach { lineText ->
            canvas.drawText(lineText, left + inset, baseline, paint)
            baseline += lineHeight
        }
    }

    private fun splitForCell(value: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        if (paint.measureText(value) <= maxWidth) return listOf(value)
        val chunks = value.split(" | ")
        val lines = mutableListOf<String>()
        var current = ""
        chunks.forEach { chunk ->
            val candidate = if (current.isBlank()) chunk else "$current | $chunk"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotBlank()) lines += current
                current = chunk
            }
        }
        if (current.isNotBlank()) lines += current
        if (lines.size <= maxLines) return lines
        val kept = lines.take(maxLines).toMutableList()
        kept[kept.lastIndex] = fitted(
            (kept.last() + " | " + lines.drop(maxLines).joinToString(" | ")).trim(),
            paint,
            maxWidth
        )
        return kept
    }

    private fun fitted(text: String, paint: Paint, maxWidth: Float): String {
        if (text.isBlank() || paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText(ellipsis) > maxWidth) end--
        return if (end <= 0) ellipsis else text.substring(0, end).trimEnd() + ellipsis
    }

    private fun columnValue(row: ExamReportRow, column: ReportColumn, selectedLessonIds: Set<String>): String = when (column) {
        ReportColumn.STUDENT -> row.studentName.ifBlank { row.studentNumber.takeIf(String::isNotBlank)?.let { "Öğrenci $it" } ?: "İsimsiz" }
        ReportColumn.NUMBER -> row.studentNumber
        ReportColumn.CLASS -> row.className
        ReportColumn.BOOKLET -> row.bookletCode
        ReportColumn.SCORE -> row.points?.let(::number).orEmpty()
        ReportColumn.NET -> row.net?.let(::number).orEmpty()
        ReportColumn.CORRECT -> row.correct?.toString().orEmpty()
        ReportColumn.WRONG -> row.wrong?.toString().orEmpty()
        ReportColumn.BLANK -> row.blank?.toString().orEmpty()
        ReportColumn.OVERALL_RANK -> row.overallRank?.toString().orEmpty()
        ReportColumn.CLASS_RANK -> row.classRank?.toString().orEmpty()
        ReportColumn.LESSONS -> row.lessons
            .filter { selectedLessonIds.isEmpty() || it.lessonId in selectedLessonIds }
            .joinToString(" | ") { lesson ->
                "${examLessonDisplayName(lesson.lessonId)} D${lesson.correct} Y${lesson.wrong} B${lesson.blank} N${number(lesson.net)}"
            }
    }

    private fun worksheetXml(config: ConfiguredExamReport): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        append("<sheetFormatPr defaultRowHeight=\"15\"/>")
        append("<cols>")
        config.columns.forEachIndexed { index, column ->
            val width = when (column) {
                ReportColumn.STUDENT -> 28
                ReportColumn.LESSONS -> 70
                ReportColumn.OVERALL_RANK, ReportColumn.CLASS_RANK -> 14
                else -> 12
            }
            append("<col min=\"").append(index + 1).append("\" max=\"").append(index + 1)
                .append("\" width=\"").append(width).append("\" customWidth=\"1\"/>")
        }
        append("</cols><sheetData>")
        append("<row r=\"1\">")
        config.columns.forEachIndexed { index, column -> append(inlineStringCell(index + 1, 1, column.label, 1)) }
        append("</row>")
        config.rows.forEachIndexed { rowIndex, row ->
            val excelRow = rowIndex + 2
            append("<row r=\"").append(excelRow).append("\">")
            config.columns.forEachIndexed { columnIndex, column ->
                append(inlineStringCell(columnIndex + 1, excelRow, columnValue(row, column, config.selectedLessonIds)))
            }
            append("</row>")
        }
        append("</sheetData>")
        if (config.rows.isNotEmpty()) {
            append("<autoFilter ref=\"A1:").append(columnName(config.columns.size)).append(config.rows.size + 1).append("\"/>")
        }
        append("<pageSetup orientation=\"")
            .append(if (effectiveOrientation(config) == ReportPageOrientation.LANDSCAPE) "landscape" else "portrait")
            .append("\" paperSize=\"9\"/>")
        append("</worksheet>")
    }

    private fun inlineStringCell(column: Int, row: Int, value: String, style: Int = 0): String {
        val ref = "${columnName(column)}$row"
        val styleAttr = if (style == 0) "" else " s=\"$style\""
        return "<c r=\"$ref\" t=\"inlineStr\"$styleAttr><is><t xml:space=\"preserve\">${escapeXml(value)}</t></is></c>"
    }

    private fun columnName(index: Int): String {
        require(index > 0)
        var current = index
        val result = StringBuilder()
        while (current > 0) {
            current--
            result.append(('A'.code + current % 26).toChar())
            current /= 26
        }
        return result.reverse().toString()
    }

    private fun number(value: Double): String = String.format(Locale("tr", "TR"), "%.2f", value)

    private fun escapeXml(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                '\t', '\n', '\r' -> append(char)
                else -> if (char.code >= 0x20) append(char)
            }
        }
    }

    private fun contentTypesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""".trimIndent()

    private fun packageRelationshipsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".trimIndent()

    private fun workbookXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="Rapor" sheetId="1" r:id="rId1"/></sheets>
</workbook>""".trimIndent()

    private fun workbookRelationshipsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".trimIndent()

    private fun stylesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
  <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>
  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>""".trimIndent()

    private fun ZipOutputStream.putText(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
