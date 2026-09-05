package com.okulyonetim.optikokuyucu.omr.scoring

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Dependency-free Office Open XML exporter for a single stored answer key.
 * Produces a real .xlsx workbook that Excel, LibreOffice and Google Sheets can open.
 */
object AnswerKeyXlsxExporter {
    fun export(key: StoredAnswerKey): ByteArray {
        val lastRow = HEADER_ROW + key.answerKey.answers.size
        val sheetXml = buildSheetXml(key, lastRow)

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            writeEntry(zip, "[Content_Types].xml", contentTypesXml())
            writeEntry(zip, "_rels/.rels", rootRelationshipsXml())
            writeEntry(zip, "xl/workbook.xml", workbookXml())
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelationshipsXml())
            writeEntry(zip, "xl/styles.xml", stylesXml())
            writeEntry(zip, "xl/worksheets/sheet1.xml", sheetXml)
        }
        return output.toByteArray()
    }

    private fun buildSheetXml(key: StoredAnswerKey, lastRow: Int): String {
        val answers = key.answerKey.answers.entries.sortedWith { left, right ->
            compareQuestionIds(left.key, right.key)
        }
        val metadata = listOf(
            "Şablon" to key.templateId,
            "Sürüm" to key.templateVersion.toString(),
            "Kitapçık" to (key.variantValue ?: "Genel"),
            "Oluşturma" to formatDate(key.createdAtEpochMs),
            "Kaynak" to sourceLabel(key.source)
        )

        return buildString {
            append(XML_DECLARATION)
            append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            append("<dimension ref=\"A1:B$lastRow\"/>")
            append("<sheetViews><sheetView workbookViewId=\"0\">")
            append("<pane ySplit=\"9\" topLeftCell=\"A10\" activePane=\"bottomLeft\" state=\"frozen\"/>")
            append("</sheetView></sheetViews>")
            append("<cols><col min=\"1\" max=\"1\" width=\"24\" customWidth=\"1\"/>")
            append("<col min=\"2\" max=\"2\" width=\"34\" customWidth=\"1\"/></cols>")
            append("<sheetData>")

            append("<row r=\"1\" ht=\"28\" customHeight=\"1\">")
            append(inlineCell("A1", "OPTİK OKUYUCU · CEVAP ANAHTARI", TITLE_STYLE))
            append("</row>")

            metadata.forEachIndexed { index, (label, value) ->
                val row = index + 3
                append("<row r=\"$row\">")
                append(inlineCell("A$row", label, LABEL_STYLE))
                append(inlineCell("B$row", value, VALUE_STYLE))
                append("</row>")
            }

            append("<row r=\"9\" ht=\"22\" customHeight=\"1\">")
            append(inlineCell("A9", "Soru", HEADER_STYLE))
            append(inlineCell("B9", "Doğru Cevap", HEADER_STYLE))
            append("</row>")

            answers.forEachIndexed { index, entry ->
                val row = HEADER_ROW + index + 1
                append("<row r=\"$row\">")
                append(inlineCell("A$row", entry.key, BODY_STYLE))
                append(inlineCell("B$row", entry.value, ANSWER_STYLE))
                append("</row>")
            }

            append("</sheetData>")
            append("<autoFilter ref=\"A9:B$lastRow\"/>")
            append("<mergeCells count=\"1\"><mergeCell ref=\"A1:B1\"/></mergeCells>")
            append("</worksheet>")
        }
    }

    private fun inlineCell(reference: String, value: String, style: Int): String =
        "<c r=\"$reference\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" +
            escapeXml(value) +
            "</t></is></c>"

    private fun contentTypesXml(): String = XML_DECLARATION +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
        "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
        "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
        "</Types>"

    private fun rootRelationshipsXml(): String = XML_DECLARATION +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    private fun workbookXml(): String = XML_DECLARATION +
        "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
        "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
        "<sheets><sheet name=\"Cevap Anahtarı\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
        "</workbook>"

    private fun workbookRelationshipsXml(): String = XML_DECLARATION +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
        "</Relationships>"

    private fun stylesXml(): String = XML_DECLARATION +
        "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
        "<fonts count=\"3\">" +
        "<font><sz val=\"11\"/><name val=\"Calibri\"/><family val=\"2\"/></font>" +
        "<font><b/><sz val=\"16\"/><color rgb=\"FF1F1F1F\"/><name val=\"Calibri\"/></font>" +
        "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>" +
        "</fonts>" +
        "<fills count=\"4\">" +
        "<fill><patternFill patternType=\"none\"/></fill>" +
        "<fill><patternFill patternType=\"gray125\"/></fill>" +
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE8EEF7\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF2F5597\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
        "</fills>" +
        "<borders count=\"2\">" +
        "<border><left/><right/><top/><bottom/><diagonal/></border>" +
        "<border><left style=\"thin\"><color rgb=\"FFD9D9D9\"/></left><right style=\"thin\"><color rgb=\"FFD9D9D9\"/></right><top style=\"thin\"><color rgb=\"FFD9D9D9\"/></top><bottom style=\"thin\"><color rgb=\"FFD9D9D9\"/></bottom><diagonal/></border>" +
        "</borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"6\">" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"><alignment vertical=\"center\"/></xf>" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFill=\"1\" applyBorder=\"1\"><alignment vertical=\"center\"/></xf>" +
        "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>" +
        "</cellXfs>" +
        "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
        "</styleSheet>"

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun compareQuestionIds(left: String, right: String): Int {
        val leftNumber = left.toIntOrNull()
        val rightNumber = right.toIntOrNull()
        return when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> left.compareTo(right, ignoreCase = true)
        }
    }

    private fun formatDate(epochMs: Long): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("tr", "TR")).format(Date(epochMs))

    private fun sourceLabel(source: AnswerKeySource): String = when (source) {
        AnswerKeySource.GALLERY -> "Galeri"
        AnswerKeySource.SCAN_RECORD -> "Kamera kaydı"
    }

    private fun escapeXml(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (char.code >= 0x20 || char == '\n' || char == '\r' || char == '\t') append(char)
            }
        }
    }

    private const val XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
    private const val HEADER_ROW = 9
    private const val TITLE_STYLE = 1
    private const val LABEL_STYLE = 2
    private const val HEADER_STYLE = 3
    private const val BODY_STYLE = 4
    private const val ANSWER_STYLE = 5
    private const val VALUE_STYLE = 0
}
