package com.okulyonetim.optikokuyucu.exam

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Dependency-free Excel/OpenXML exporter for one exam report.
 *
 * The workbook uses inline strings, so no shared-string table or third-party XLSX library is
 * required. This keeps the Android package small and allows reports to be produced fully offline.
 */
object ExamReportXlsxExporter {
    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun export(report: ExamReport): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putText("[Content_Types].xml", contentTypesXml())
            zip.putText("_rels/.rels", packageRelationshipsXml())
            zip.putText("xl/workbook.xml", workbookXml())
            zip.putText("xl/_rels/workbook.xml.rels", workbookRelationshipsXml())
            zip.putText("xl/styles.xml", stylesXml())
            zip.putText("xl/worksheets/sheet1.xml", worksheetXml(report))
        }
        return output.toByteArray()
    }

    private fun worksheetXml(report: ExamReport): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        append("<sheetViews><sheetView workbookViewId=\"0\">")
        append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        append("</sheetView></sheetViews>")
        append("<sheetFormatPr defaultRowHeight=\"15\"/>")
        append("<cols>")
        append("<col min=\"1\" max=\"1\" width=\"7\" customWidth=\"1\"/>")
        append("<col min=\"2\" max=\"2\" width=\"26\" customWidth=\"1\"/>")
        append("<col min=\"3\" max=\"5\" width=\"14\" customWidth=\"1\"/>")
        append("<col min=\"6\" max=\"6\" width=\"20\" customWidth=\"1\"/>")
        append("<col min=\"7\" max=\"14\" width=\"13\" customWidth=\"1\"/>")
        append("<col min=\"15\" max=\"15\" width=\"18\" customWidth=\"1\"/>")
        append("<col min=\"16\" max=\"16\" width=\"28\" customWidth=\"1\"/>")
        append("</cols>")
        append("<sheetData>")

        val headers = listOf(
            "Sıra",
            "Öğrenci",
            "Sınıf",
            "Numara",
            "Kitapçık",
            "Tarama Tarihi",
            "Doğru",
            "Yanlış",
            "Boş",
            "Çift İşaret",
            "Şüpheli",
            "Anahtarsız",
            "Net / Puan",
            "Maksimum",
            "Durum",
            "Kayıt ID"
        )
        append("<row r=\"1\">")
        headers.forEachIndexed { index, header ->
            append(inlineStringCell(index + 1, 1, header, style = 1))
        }
        append("</row>")

        report.rows.forEachIndexed { rowIndex, row ->
            val excelRow = rowIndex + 2
            append("<row r=\"")
            append(excelRow)
            append("\">")
            append(numberCell(1, excelRow, row.ordinal.toDouble()))
            append(inlineStringCell(2, excelRow, row.studentName))
            append(inlineStringCell(3, excelRow, row.className))
            append(inlineStringCell(4, excelRow, row.studentNumber))
            append(inlineStringCell(5, excelRow, row.bookletCode))
            append(inlineStringCell(6, excelRow, row.capturedAtEpochMs?.let(::formatDate).orEmpty()))
            append(optionalNumberCell(7, excelRow, row.correct?.toDouble()))
            append(optionalNumberCell(8, excelRow, row.wrong?.toDouble()))
            append(optionalNumberCell(9, excelRow, row.blank?.toDouble()))
            append(optionalNumberCell(10, excelRow, row.doubleMark?.toDouble()))
            append(optionalNumberCell(11, excelRow, row.suspicious?.toDouble()))
            append(optionalNumberCell(12, excelRow, row.noKey?.toDouble()))
            append(optionalNumberCell(13, excelRow, row.points))
            append(optionalNumberCell(14, excelRow, row.maximumPoints))
            append(inlineStringCell(15, excelRow, statusLabel(row.status)))
            append(inlineStringCell(16, excelRow, row.scanRecordId))
            append("</row>")
        }

        append("</sheetData>")
        if (report.rows.isNotEmpty()) {
            append("<autoFilter ref=\"A1:P")
            append(report.rows.size + 1)
            append("\"/>")
        }
        append("</worksheet>")
    }

    private fun inlineStringCell(column: Int, row: Int, value: String, style: Int = 0): String {
        val reference = "${columnName(column)}$row"
        val styleAttribute = if (style == 0) "" else " s=\"$style\""
        return "<c r=\"$reference\" t=\"inlineStr\"$styleAttribute><is><t xml:space=\"preserve\">" +
            escapeXml(value) +
            "</t></is></c>"
    }

    private fun optionalNumberCell(column: Int, row: Int, value: Double?): String =
        value?.let { numberCell(column, row, it) }
            ?: inlineStringCell(column, row, "")

    private fun numberCell(column: Int, row: Int, value: Double): String {
        require(value.isFinite()) { "Excel hücresine sonlu olmayan sayı yazılamaz." }
        val reference = "${columnName(column)}$row"
        val normalized = if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.10f", value).trimEnd('0').trimEnd('.')
        }
        return "<c r=\"$reference\"><v>$normalized</v></c>"
    }

    private fun columnName(index: Int): String {
        require(index > 0)
        var current = index
        val result = StringBuilder()
        while (current > 0) {
            current -= 1
            result.append(('A'.code + (current % 26)).toChar())
            current /= 26
        }
        return result.reverse().toString()
    }

    private fun formatDate(epochMs: Long): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("tr", "TR")).format(Date(epochMs))

    private fun statusLabel(status: ExamReportRowStatus): String = when (status) {
        ExamReportRowStatus.SCORED -> "PUANLANDI"
        ExamReportRowStatus.REVIEW_REQUIRED -> "KONTROL GEREKLİ"
        ExamReportRowStatus.NO_ANSWER_KEY -> "ANAHTAR YOK"
        ExamReportRowStatus.SCAN_MISSING -> "TARAMA YOK"
    }

    private fun escapeXml(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '\"' -> append("&quot;")
                '\'' -> append("&apos;")
                '\t', '\n', '\r' -> append(char)
                else -> if (char.code >= 0x20) append(char)
            }
        }
    }

    private fun contentTypesXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""".trimIndent()

    private fun packageRelationshipsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".trimIndent()

    private fun workbookXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="Sonuçlar" sheetId="1" r:id="rId1"/></sheets>
</workbook>""".trimIndent()

    private fun workbookRelationshipsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".trimIndent()

    private fun stylesXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/><family val="2"/></font>
    <font><b/><sz val="11"/><name val="Calibri"/><family val="2"/></font>
  </fonts>
  <fills count="2">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
  </fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
  </cellXfs>
  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>""".trimIndent()

    private fun ZipOutputStream.putText(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
