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
        val pageWidth = if (config.orientation == ReportPageOrientation.LANDSCAPE) 842 else 595
        val pageHeight = if (config.orientation == ReportPageOrientation.LANDSCAPE) 595 else 842
        val left = 24f
        val right = pageWidth - 24f
        val top = 96f
        val bottom = pageHeight - 34f
        val headerHeight = 24f
        val rowHeight = if (ReportColumn.LESSONS in config.columns) 31f else 24f
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
                        boundaries = boundaries
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
        boundaries: FloatArray
    ) {
        canvas.drawColor(Color.WHITE)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 8.8f
        }
        val title = buildString {
            append(config.report.examName)
            if (config.titleSuffix.isNotBlank()) append(" · ${config.titleSuffix}")
        }
        canvas.drawText(fitted(title, titlePaint, pageWidth - 48f), 24f, 40f, titlePaint)
        canvas.drawText(
            fitted(
                "${config.report.schoolName.ifBlank { "Okul bilgisi yok" }} · ${config.rows.size} kayıt",
                subtitlePaint,
                pageWidth - 48f
            ),
            24f,
            57f,
            subtitlePaint
        )
        canvas.drawText(
            "Sayfa ${pageIndex + 1} / $totalPages · ${if (config.orientation == ReportPageOrientation.LANDSCAPE) "Yatay" else "Dikey"}",
            24f,
            72f,
            subtitlePaint
        )

        val headerFill = Paint().apply { color = Color.rgb(235, 239, 237); style = Paint.Style.FILL }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(185, 190, 188)
            strokeWidth = 0.6f
            style = Paint.Style.STROKE
        }
        val headerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 7.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cellText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = if (ReportColumn.LESSONS in config.columns) 6.6f else 7.2f
        }

        canvas.drawRect(boundaries.first(), top, boundaries.last(), top + headerHeight, headerFill)
        canvas.drawRect(boundaries.first(), top, boundaries.last(), top + headerHeight, border)
        boundaries.forEach { x -> canvas.drawLine(x, top, x, top + headerHeight, border) }
        config.columns.forEachIndexed { index, column ->
            drawCell(canvas, column.label, boundaries[index], boundaries[index + 1], top + 16f, headerText)
        }

        rows.forEachIndexed { rowIndex, row ->
            val rowTop = top + headerHeight + rowIndex * rowHeight
            val rowBottom = rowTop + rowHeight
            canvas.drawRect(boundaries.first(), rowTop, boundaries.last(), rowBottom, border)
            boundaries.forEach { x -> canvas.drawLine(x, rowTop, x, rowBottom, border) }
            config.columns.forEachIndexed { index, column ->
                drawCell(
                    canvas,
                    columnValue(row, column, config.selectedLessonIds),
                    boundaries[index],
                    boundaries[index + 1],
                    rowTop + rowHeight * 0.64f,
                    cellText
                )
            }
        }

        canvas.drawText(
            "Optik Okuyucu",
            pageWidth - 78f,
            pageHeight - 17f,
            subtitlePaint
        )
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

    private fun drawCell(canvas: Canvas, value: String, left: Float, right: Float, baseline: Float, paint: Paint) {
        val inset = 2.5f
        canvas.drawText(fitted(value, paint, (right - left - inset * 2).coerceAtLeast(1f)), left + inset, baseline, paint)
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
            .append(if (config.orientation == ReportPageOrientation.LANDSCAPE) "landscape" else "portrait")
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
