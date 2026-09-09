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
    STUDENT("Ad Soyad", 2.5f),
    NUMBER("No", 0.9f),
    CLASS("Sınıf", 0.9f),
    BOOKLET("Kitapçık", 0.8f),
    SCORE("Puan", 1.0f),
    NET("Net", 0.9f),
    CORRECT("Doğru", 0.75f),
    WRONG("Yanlış", 0.75f),
    BLANK("Boş", 0.75f),
    OVERALL_RANK("Genel Sıra", 1.0f),
    CLASS_RANK("Sınıf Sıra", 1.0f),
    LESSONS("Dersler · D/Y/B/Net", 3.2f)
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

    private enum class Metric { CORRECT, WRONG, BLANK, NET }

    private data class LeafColumn(
        val label: String,
        val weight: Float,
        val staticColumn: ReportColumn? = null,
        val lessonId: String? = null,
        val metric: Metric? = null,
        val total: Boolean = false,
        val leftAligned: Boolean = false
    )

    private data class TableBlock(
        val title: String?,
        val leaves: List<LeafColumn>,
        val sticky: Boolean = false
    )

    fun exportPdfBytes(config: ConfiguredExamReport, typeface: Typeface? = null): ByteArray {
        val output = ByteArrayOutputStream()
        exportPdf(config, output, typeface)
        return output.toByteArray()
    }

    fun exportPdf(config: ConfiguredExamReport, output: OutputStream, typeface: Typeface? = null) {
        require(config.columns.isNotEmpty()) { "Rapor için en az bir alan seçilmelidir." }
        val blocks = buildBlocks(config)
        require(blocks.isNotEmpty()) { "Rapor için görüntülenecek sütun bulunamadı." }

        val orientation = effectiveOrientation(config, blocks)
        val pageWidth = if (orientation == ReportPageOrientation.LANDSCAPE) 842 else 595
        val pageHeight = if (orientation == ReportPageOrientation.LANDSCAPE) 595 else 842
        val left = 22f
        val right = pageWidth - 22f
        val top = 126f
        val bottom = pageHeight - 32f
        val headerHeight = 38f
        val rowHeight = 25f
        val rowsPerPage = ((bottom - top - headerHeight) / rowHeight).toInt().coerceAtLeast(1)
        val rowPages = config.rows.chunked(rowsPerPage).ifEmpty { listOf(emptyList()) }
        val horizontalSegments = horizontalSegments(blocks)
        val totalPages = rowPages.size * horizontalSegments.size

        val normalTypeface = typeface ?: Typeface.create("sans-serif", Typeface.NORMAL)
        val boldTypeface = Typeface.create(normalTypeface, Typeface.BOLD)

        val pdf = PdfDocument()
        try {
            var pageNumber = 1
            rowPages.forEachIndexed { rowPageIndex, rows ->
                horizontalSegments.forEachIndexed { segmentIndex, segmentBlocks ->
                    val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    val page = pdf.startPage(info)
                    try {
                        drawPdfPage(
                            canvas = page.canvas,
                            config = config,
                            rows = rows,
                            pageNumber = pageNumber,
                            totalPages = totalPages,
                            rowPageIndex = rowPageIndex,
                            rowPageCount = rowPages.size,
                            segmentIndex = segmentIndex,
                            segmentCount = horizontalSegments.size,
                            pageWidth = pageWidth,
                            pageHeight = pageHeight,
                            top = top,
                            headerHeight = headerHeight,
                            rowHeight = rowHeight,
                            blocks = segmentBlocks,
                            left = left,
                            right = right,
                            orientation = orientation,
                            normalTypeface = normalTypeface,
                            boldTypeface = boldTypeface
                        )
                    } finally {
                        pdf.finishPage(page)
                    }
                    pageNumber++
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

    private fun buildBlocks(config: ConfiguredExamReport): List<TableBlock> {
        val lessonOrder = config.rows
            .flatMap { row -> row.lessons.map { it.lessonId } }
            .distinct()
            .filter { config.selectedLessonIds.isEmpty() || it in config.selectedLessonIds }

        val blocks = mutableListOf<TableBlock>()
        config.columns.distinct().forEach { column ->
            if (column == ReportColumn.LESSONS) {
                lessonOrder.forEach { lessonId ->
                    blocks += metricBlock(examLessonDisplayName(lessonId), lessonId = lessonId)
                }
            } else {
                blocks += staticBlock(column)
            }
        }
        if (ReportColumn.LESSONS in config.columns) {
            blocks += metricBlock("Toplam", total = true)
        }
        return blocks
    }

    private fun staticBlock(column: ReportColumn): TableBlock = TableBlock(
        title = null,
        leaves = listOf(
            LeafColumn(
                label = column.label,
                weight = column.weight,
                staticColumn = column,
                leftAligned = column == ReportColumn.STUDENT
            )
        ),
        sticky = column == ReportColumn.STUDENT || column == ReportColumn.CLASS || column == ReportColumn.NUMBER
    )

    private fun metricBlock(title: String, lessonId: String? = null, total: Boolean = false): TableBlock = TableBlock(
        title = title,
        leaves = listOf(
            LeafColumn("D", 0.62f, lessonId = lessonId, metric = Metric.CORRECT, total = total),
            LeafColumn("Y", 0.62f, lessonId = lessonId, metric = Metric.WRONG, total = total),
            LeafColumn("B", 0.62f, lessonId = lessonId, metric = Metric.BLANK, total = total),
            LeafColumn("Net", 0.82f, lessonId = lessonId, metric = Metric.NET, total = total)
        )
    )

    private fun horizontalSegments(blocks: List<TableBlock>): List<List<TableBlock>> {
        val leafCount = blocks.sumOf { it.leaves.size }
        if (leafCount <= MAX_PDF_LEAVES) return listOf(blocks)

        val sticky = blocks.filter { it.sticky }
        val remaining = blocks.filterNot { it.sticky }
        val capacity = (MAX_PDF_LEAVES - sticky.sumOf { it.leaves.size }).coerceAtLeast(4)
        val packed = mutableListOf<List<TableBlock>>()
        var current = mutableListOf<TableBlock>()
        var currentLeaves = 0

        remaining.forEach { block ->
            val size = block.leaves.size
            if (current.isNotEmpty() && currentLeaves + size > capacity) {
                packed.add(current.toList())
                current = mutableListOf()
                currentLeaves = 0
            }
            current += block
            currentLeaves += size
        }
        if (current.isNotEmpty()) packed.add(current.toList())
        if (packed.isEmpty()) packed.add(emptyList())
        return packed.map { part -> sticky + part }
    }

    private fun drawPdfPage(
        canvas: Canvas,
        config: ConfiguredExamReport,
        rows: List<ExamReportRow>,
        pageNumber: Int,
        totalPages: Int,
        rowPageIndex: Int,
        rowPageCount: Int,
        segmentIndex: Int,
        segmentCount: Int,
        pageWidth: Int,
        pageHeight: Int,
        top: Float,
        headerHeight: Float,
        rowHeight: Float,
        blocks: List<TableBlock>,
        left: Float,
        right: Float,
        orientation: ReportPageOrientation,
        normalTypeface: Typeface,
        boldTypeface: Typeface
    ) {
        canvas.drawColor(Color.WHITE)

        val brand = Color.rgb(20, 91, 70)
        val brandDark = Color.rgb(13, 68, 53)
        val brandSoft = Color.rgb(232, 244, 239)
        val ink = Color.rgb(27, 37, 34)
        val muted = Color.rgb(91, 104, 99)
        val line = Color.rgb(199, 212, 206)
        val alternate = Color.rgb(247, 250, 249)

        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 72f, Paint().apply { color = brand })

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 17f
            typeface = boldTypeface
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(222, 241, 234)
            textSize = 9f
            typeface = normalTypeface
        }
        val title = buildString {
            append(config.report.examName)
            if (config.titleSuffix.isNotBlank()) append(" · ${config.titleSuffix}")
        }
        canvas.drawText(fitted(title, titlePaint, pageWidth - 44f), 22f, 31f, titlePaint)
        canvas.drawText(
            fitted(config.report.schoolName.ifBlank { "Okul bilgisi yok" }, subtitlePaint, pageWidth - 44f),
            22f,
            51f,
            subtitlePaint
        )

        val averageScore = config.rows.mapNotNull { it.points }.takeIf { it.isNotEmpty() }?.average()
        val averageNet = config.rows.mapNotNull { it.net }.takeIf { it.isNotEmpty() }?.average()
        val summaryLabels = listOf("KAYIT", "PUAN ORT.", "NET ORT.")
        val summaryValues = listOf(
            config.rows.size.toString(),
            averageScore?.let(::number) ?: "—",
            averageNet?.let(::number) ?: "—"
        )
        val summaryGap = 7f
        val summaryTop = 82f
        val summaryHeight = 30f
        val summaryWidth = (pageWidth - 44f - summaryGap * 2f) / 3f
        val summaryFill = Paint().apply { color = brandSoft }
        val summaryLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 6.7f
            typeface = boldTypeface
        }
        val summaryValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brand
            textSize = 11.5f
            typeface = boldTypeface
        }
        repeat(3) { index ->
            val x = 22f + index * (summaryWidth + summaryGap)
            canvas.drawRoundRect(x, summaryTop, x + summaryWidth, summaryTop + summaryHeight, 5f, 5f, summaryFill)
            canvas.drawText(summaryLabels[index], x + 7f, summaryTop + 10f, summaryLabelPaint)
            canvas.drawText(summaryValues[index], x + 7f, summaryTop + 24f, summaryValuePaint)
        }

        val leaves = blocks.flatMap { it.leaves }
        val boundaries = leafBoundaries(leaves, left, right)
        val headerTopHeight = headerHeight * 0.48f
        val headerFill = Paint().apply { color = brand }
        val subHeaderFill = Paint().apply { color = brandDark }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = line
            strokeWidth = 0.65f
            style = Paint.Style.STROKE
        }
        val headerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (leaves.size >= 13) 6.5f else 7.3f
            typeface = boldTypeface
            textAlign = Paint.Align.CENTER
        }
        val cellText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = when {
                leaves.size >= 14 -> 6.7f
                leaves.size >= 11 -> 7.0f
                else -> 7.7f
            }
            typeface = normalTypeface
        }

        var leafIndex = 0
        blocks.forEach { block ->
            val blockLeft = boundaries[leafIndex]
            val blockRight = boundaries[leafIndex + block.leaves.size]
            if (block.title == null) {
                canvas.drawRect(blockLeft, top, blockRight, top + headerHeight, headerFill)
                canvas.drawRect(blockLeft, top, blockRight, top + headerHeight, border)
                drawCenteredText(canvas, block.leaves.single().label, blockLeft, blockRight, top, headerHeight, headerText)
            } else {
                canvas.drawRect(blockLeft, top, blockRight, top + headerTopHeight, headerFill)
                canvas.drawRect(blockLeft, top, blockRight, top + headerTopHeight, border)
                drawCenteredText(canvas, block.title, blockLeft, blockRight, top, headerTopHeight, headerText)
                block.leaves.forEachIndexed { subIndex, leaf ->
                    val subLeft = boundaries[leafIndex + subIndex]
                    val subRight = boundaries[leafIndex + subIndex + 1]
                    canvas.drawRect(subLeft, top + headerTopHeight, subRight, top + headerHeight, subHeaderFill)
                    canvas.drawRect(subLeft, top + headerTopHeight, subRight, top + headerHeight, border)
                    drawCenteredText(
                        canvas,
                        leaf.label,
                        subLeft,
                        subRight,
                        top + headerTopHeight,
                        headerHeight - headerTopHeight,
                        headerText
                    )
                }
            }
            leafIndex += block.leaves.size
        }

        val alternateFill = Paint().apply { color = alternate }
        rows.forEachIndexed { rowIndex, row ->
            val rowTop = top + headerHeight + rowIndex * rowHeight
            val rowBottom = rowTop + rowHeight
            if (rowIndex % 2 == 1) canvas.drawRect(left, rowTop, right, rowBottom, alternateFill)
            leaves.forEachIndexed { index, leaf ->
                val cellLeft = boundaries[index]
                val cellRight = boundaries[index + 1]
                canvas.drawRect(cellLeft, rowTop, cellRight, rowBottom, border)
                drawBodyText(
                    canvas = canvas,
                    text = leafValue(row, leaf),
                    left = cellLeft,
                    right = cellRight,
                    top = rowTop,
                    height = rowHeight,
                    paint = cellText,
                    leftAligned = leaf.leftAligned
                )
            }
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 6.8f
            typeface = normalTypeface
        }
        val segmentText = if (segmentCount > 1) " · Bölüm ${segmentIndex + 1}/$segmentCount" else ""
        val rowPageText = if (rowPageCount > 1) " · Satır sayfası ${rowPageIndex + 1}/$rowPageCount" else ""
        canvas.drawText(
            "$pageNumber / $totalPages$segmentText$rowPageText · ${if (orientation == ReportPageOrientation.LANDSCAPE) "Yatay" else "Dikey"}",
            22f,
            pageHeight - 16f,
            footerPaint
        )
        val brandFooter = "Optik Okuyucu"
        canvas.drawText(
            brandFooter,
            pageWidth - 22f - footerPaint.measureText(brandFooter),
            pageHeight - 16f,
            footerPaint
        )
    }

    private fun leafBoundaries(leaves: List<LeafColumn>, left: Float, right: Float): FloatArray {
        val total = leaves.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)
        val width = right - left
        val result = FloatArray(leaves.size + 1)
        result[0] = left
        var current = left
        leaves.forEachIndexed { index, leaf ->
            current += width * (leaf.weight / total)
            result[index + 1] = current
        }
        result[result.lastIndex] = right
        return result
    }

    private fun drawCenteredText(
        canvas: Canvas,
        text: String,
        left: Float,
        right: Float,
        top: Float,
        height: Float,
        paint: Paint
    ) {
        val maxWidth = (right - left - 5f).coerceAtLeast(1f)
        val value = fitted(text, paint, maxWidth)
        val baseline = top + (height - (paint.descent() + paint.ascent())) / 2f
        canvas.drawText(value, (left + right) / 2f, baseline, paint)
    }

    private fun drawBodyText(
        canvas: Canvas,
        text: String,
        left: Float,
        right: Float,
        top: Float,
        height: Float,
        paint: Paint,
        leftAligned: Boolean
    ) {
        if (text.isBlank()) return
        val inset = 3f
        val maxWidth = (right - left - inset * 2).coerceAtLeast(1f)
        val oldAlign = paint.textAlign
        paint.textAlign = if (leftAligned) Paint.Align.LEFT else Paint.Align.CENTER
        val x = if (leftAligned) left + inset else (left + right) / 2f
        val baseline = top + (height - (paint.descent() + paint.ascent())) / 2f
        canvas.drawText(fitted(text, paint, maxWidth), x, baseline, paint)
        paint.textAlign = oldAlign
    }

    private fun fitted(text: String, paint: Paint, maxWidth: Float): String {
        if (text.isBlank() || paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText(ellipsis) > maxWidth) end--
        return if (end <= 0) ellipsis else text.substring(0, end).trimEnd() + ellipsis
    }

    private fun leafValue(row: ExamReportRow, leaf: LeafColumn): String {
        leaf.staticColumn?.let { column -> return staticColumnValue(row, column) }
        val metric = leaf.metric ?: return ""
        if (leaf.total) {
            return when (metric) {
                Metric.CORRECT -> row.correct?.toString().orEmpty()
                Metric.WRONG -> row.wrong?.toString().orEmpty()
                Metric.BLANK -> row.blank?.toString().orEmpty()
                Metric.NET -> row.net?.let(::number).orEmpty()
            }
        }
        val lesson = row.lessons.firstOrNull { it.lessonId == leaf.lessonId } ?: return ""
        return when (metric) {
            Metric.CORRECT -> lesson.correct.toString()
            Metric.WRONG -> lesson.wrong.toString()
            Metric.BLANK -> lesson.blank.toString()
            Metric.NET -> number(lesson.net)
        }
    }

    private fun staticColumnValue(row: ExamReportRow, column: ReportColumn): String = when (column) {
        ReportColumn.STUDENT -> row.studentName.ifBlank {
            row.studentNumber.takeIf(String::isNotBlank)?.let { "Öğrenci $it" } ?: "İsimsiz"
        }
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
        ReportColumn.LESSONS -> ""
    }

    private fun effectiveOrientation(config: ConfiguredExamReport, blocks: List<TableBlock>): ReportPageOrientation =
        if (blocks.sumOf { it.leaves.size } >= 8 || ReportColumn.LESSONS in config.columns) {
            ReportPageOrientation.LANDSCAPE
        } else {
            config.orientation
        }

    private fun worksheetXml(config: ConfiguredExamReport): String {
        val blocks = buildBlocks(config)
        val leaves = blocks.flatMap { it.leaves }
        val mergedRanges = mutableListOf<String>()
        var columnCursor = 1

        val xml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"2\" topLeftCell=\"A3\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
            append("<sheetFormatPr defaultRowHeight=\"17\"/>")
            append("<cols>")
            leaves.forEachIndexed { index, leaf ->
                val width = when {
                    leaf.staticColumn == ReportColumn.STUDENT -> 28
                    leaf.staticColumn == ReportColumn.OVERALL_RANK || leaf.staticColumn == ReportColumn.CLASS_RANK -> 14
                    leaf.staticColumn != null -> 11
                    leaf.metric == Metric.NET -> 10
                    else -> 8
                }
                append("<col min=\"").append(index + 1).append("\" max=\"").append(index + 1)
                    .append("\" width=\"").append(width).append("\" customWidth=\"1\"/>")
            }
            append("</cols><sheetData>")

            append("<row r=\"1\" ht=\"22\" customHeight=\"1\">")
            columnCursor = 1
            blocks.forEach { block ->
                if (block.title == null) {
                    append(inlineStringCell(columnCursor, 1, block.leaves.single().label, STYLE_HEADER_CENTER))
                    mergedRanges += "${columnName(columnCursor)}1:${columnName(columnCursor)}2"
                } else {
                    append(inlineStringCell(columnCursor, 1, block.title, STYLE_HEADER_CENTER))
                    val end = columnCursor + block.leaves.size - 1
                    mergedRanges += "${columnName(columnCursor)}1:${columnName(end)}1"
                }
                columnCursor += block.leaves.size
            }
            append("</row>")

            append("<row r=\"2\" ht=\"20\" customHeight=\"1\">")
            columnCursor = 1
            blocks.forEach { block ->
                if (block.title == null) {
                    append(inlineStringCell(columnCursor, 2, "", STYLE_HEADER_CENTER))
                } else {
                    block.leaves.forEachIndexed { index, leaf ->
                        append(inlineStringCell(columnCursor + index, 2, leaf.label, STYLE_HEADER_CENTER))
                    }
                }
                columnCursor += block.leaves.size
            }
            append("</row>")

            config.rows.forEachIndexed { rowIndex, row ->
                val excelRow = rowIndex + 3
                append("<row r=\"").append(excelRow).append("\">")
                leaves.forEachIndexed { columnIndex, leaf ->
                    val style = if (leaf.leftAligned) STYLE_BODY_LEFT else STYLE_BODY_CENTER
                    append(inlineStringCell(columnIndex + 1, excelRow, leafValue(row, leaf), style))
                }
                append("</row>")
            }
            append("</sheetData>")

            if (mergedRanges.isNotEmpty()) {
                append("<mergeCells count=\"").append(mergedRanges.size).append("\">")
                mergedRanges.forEach { range -> append("<mergeCell ref=\"").append(range).append("\"/>") }
                append("</mergeCells>")
            }
            append("<pageSetup orientation=\"")
                .append(if (effectiveOrientation(config, blocks) == ReportPageOrientation.LANDSCAPE) "landscape" else "portrait")
                .append("\" paperSize=\"9\" fitToWidth=\"1\" fitToHeight=\"0\"/>")
            append("</worksheet>")
        }
        return xml
    }

    private fun inlineStringCell(column: Int, row: Int, value: String, style: Int): String {
        val ref = "${columnName(column)}$row"
        return "<c r=\"$ref\" t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">${escapeXml(value)}</t></is></c>"
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

    private fun number(value: Double): String = String.format(Locale.forLanguageTag("tr-TR"), "%.2f", value)

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
  <fonts count="2">
    <font><sz val="10"/><name val="Noto Sans"/></font>
    <font><b/><sz val="10"/><name val="Noto Sans"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF145B46"/><bgColor indexed="64"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border><left style="thin"><color rgb="FFD6E0DC"/></left><right style="thin"><color rgb="FFD6E0DC"/></right><top style="thin"><color rgb="FFD6E0DC"/></top><bottom style="thin"><color rgb="FFD6E0DC"/></bottom><diagonal/></border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
  </cellXfs>
  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>""".trimIndent()

    private fun ZipOutputStream.putText(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private const val MAX_PDF_LEAVES = 15
    private const val STYLE_HEADER_CENTER = 1
    private const val STYLE_BODY_CENTER = 2
    private const val STYLE_BODY_LEFT = 3
}
