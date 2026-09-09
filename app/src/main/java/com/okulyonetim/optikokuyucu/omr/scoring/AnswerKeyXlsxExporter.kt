package com.okulyonetim.optikokuyucu.omr.scoring

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Dependency-free Office Open XML exporter for answer keys. */
object AnswerKeyXlsxExporter {
    /** Legacy single-key layout retained for the template-library export flow. */
    fun export(key: StoredAnswerKey): ByteArray {
        val lastRow = HEADER_ROW + key.answerKey.answers.size
        return workbookBytes(buildLegacySheetXml(key, lastRow))
    }

    /**
     * Exam-oriented workbook: human-readable subject + local question order followed by
     * one answer column for every available booklet.
     */
    fun exportStructured(
        keys: List<StoredAnswerKey>,
        sections: List<ManualAnswerSection>,
        subjectName: String? = null
    ): ByteArray {
        require(keys.isNotEmpty()) { "Dışa aktarılacak cevap anahtarı bulunamadı." }
        val first = keys.first()
        require(keys.all {
            it.templateId == first.templateId &&
                it.templateVersion == first.templateVersion &&
                it.examId == first.examId
        }) { "Aynı Excel dosyasına yalnız aynı sınav ve form sürümünün cevap anahtarları yazılabilir." }

        val tr = Locale("tr", "TR")
        val orderedKeys = keys
            .distinctBy { it.variantValue.orEmpty() }
            .sortedWith(compareBy { it.variantValue.orEmpty().lowercase(tr) })
        val rows = structuredRows(first, sections, subjectName)
        return workbookBytes(buildStructuredSheetXml(orderedKeys, rows, HEADER_ROW + rows.size))
    }

    private fun workbookBytes(sheetXml: String): ByteArray {
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

    private fun buildLegacySheetXml(key: StoredAnswerKey, lastRow: Int): String {
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
            worksheetStart("B", lastRow, this)
            appendColumns(listOf(24.0, 34.0), this)
            append("<sheetData>")
            appendTitleRow(this)
            appendMetadata(metadata, this)
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

    private fun buildStructuredSheetXml(
        keys: List<StoredAnswerKey>,
        rows: List<StructuredQuestionRow>,
        lastRow: Int
    ): String {
        val first = keys.first()
        val lastColumn = columnName(2 + keys.size)
        val variants = keys.joinToString(", ") { it.variantValue ?: "Genel" }
        val metadata = listOf(
            "Şablon" to first.templateId,
            "Sürüm" to first.templateVersion.toString(),
            (if (keys.size > 1) "Kitapçıklar" else "Kitapçık") to variants,
            "Oluşturma" to formatDate(keys.maxOf { it.createdAtEpochMs }),
            "Kaynak" to keys.map { sourceLabel(it.source) }.distinct().joinToString(", ")
        )

        return buildString {
            worksheetStart(lastColumn, lastRow, this)
            append("<cols>")
            append("<col min=\"1\" max=\"1\" width=\"28\" customWidth=\"1\"/>")
            append("<col min=\"2\" max=\"2\" width=\"12\" customWidth=\"1\"/>")
            append("<col min=\"3\" max=\"${2 + keys.size}\" width=\"18\" customWidth=\"1\"/>")
            append("</cols>")
            append("<sheetData>")
            appendTitleRow(this)
            appendMetadata(metadata, this)

            append("<row r=\"9\" ht=\"22\" customHeight=\"1\">")
            append(inlineCell("A9", "Ders", HEADER_STYLE))
            append(inlineCell("B9", "Soru", HEADER_STYLE))
            keys.forEachIndexed { index, key ->
                val column = columnName(3 + index)
                val header = key.variantValue?.let { "Kitapçık $it" } ?: "Doğru Cevap"
                append(inlineCell("${column}9", header, HEADER_STYLE))
            }
            append("</row>")

            rows.forEachIndexed { index, item ->
                val row = HEADER_ROW + index + 1
                append("<row r=\"$row\">")
                append(inlineCell("A$row", item.subject, BODY_LEFT_STYLE))
                append(inlineCell("B$row", item.questionOrder.toString(), BODY_STYLE))
                keys.forEachIndexed { keyIndex, key ->
                    val column = columnName(3 + keyIndex)
                    append(inlineCell("$column$row", key.answerKey.answers[item.questionId].orEmpty(), ANSWER_STYLE))
                }
                append("</row>")
            }

            append("</sheetData>")
            append("<autoFilter ref=\"A9:$lastColumn$lastRow\"/>")
            append("<mergeCells count=\"1\"><mergeCell ref=\"A1:${lastColumn}1\"/></mergeCells>")
            append("</worksheet>")
        }
    }

    private fun structuredRows(
        primaryKey: StoredAnswerKey,
        sections: List<ManualAnswerSection>,
        subjectName: String?
    ): List<StructuredQuestionRow> {
        val forcedSubject = subjectName?.trim()?.takeIf(String::isNotBlank)
        if (sections.isNotEmpty()) {
            return sections.flatMap { section ->
                section.questionIds.mapIndexed { index, questionId ->
                    StructuredQuestionRow(
                        subject = forcedSubject ?: section.label,
                        questionOrder = index + 1,
                        questionId = questionId
                    )
                }
            }
        }
        return primaryKey.answerKey.answers.keys
            .sortedWith { left, right -> compareQuestionIds(left, right) }
            .mapIndexed { index, questionId ->
                StructuredQuestionRow(
                    subject = forcedSubject ?: "Tüm Sorular",
                    questionOrder = localQuestionNumber(questionId) ?: index + 1,
                    questionId = questionId
                )
            }
    }

    private fun worksheetStart(lastColumn: String, lastRow: Int, target: StringBuilder) {
        target.append(XML_DECLARATION)
        target.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        target.append("<dimension ref=\"A1:$lastColumn$lastRow\"/>")
        target.append("<sheetViews><sheetView workbookViewId=\"0\">")
        target.append("<pane ySplit=\"9\" topLeftCell=\"A10\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        target.append("</sheetView></sheetViews>")
    }

    private fun appendColumns(widths: List<Double>, target: StringBuilder) {
        target.append("<cols>")
        widths.forEachIndexed { index, width ->
            val column = index + 1
            target.append("<col min=\"$column\" max=\"$column\" width=\"$width\" customWidth=\"1\"/>")
        }
        target.append("</cols>")
    }

    private fun appendTitleRow(target: StringBuilder) {
        target.append("<row r=\"1\" ht=\"28\" customHeight=\"1\">")
        target.append(inlineCell("A1", "OPTİK OKUYUCU · CEVAP ANAHTARI", TITLE_STYLE))
        target.append("</row>")
    }

    private fun appendMetadata(metadata: List<Pair<String, String>>, target: StringBuilder) {
        metadata.forEachIndexed { index, (label, value) ->
            val row = index + 3
            target.append("<row r=\"$row\">")
            target.append(inlineCell("A$row", label, LABEL_STYLE))
            target.append(inlineCell("B$row", value, VALUE_STYLE))
            target.append("</row>")
        }
    }

    private fun localQuestionNumber(questionId: String): Int? =
        questionId.substringAfterLast(':', missingDelimiterValue = questionId).toIntOrNull()

    private fun columnName(oneBasedIndex: Int): String {
        require(oneBasedIndex > 0)
        var value = oneBasedIndex
        return buildString {
            while (value > 0) {
                val remainder = (value - 1) % 26
                append(('A'.code + remainder).toChar())
                value = (value - 1) / 26
            }
        }.reversed()
    }

    private fun inlineCell(reference: String, value: String, style: Int): String =
        "<c r=\"$reference\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" +
            escapeXml(value) + "</t></is></c>"

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
        "<cellXfs count=\"7\">" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"><alignment vertical=\"center\"/></xf>" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFill=\"1\" applyBorder=\"1\"><alignment vertical=\"center\"/></xf>" +
        "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\"><alignment horizontal=\"left\" vertical=\"center\"/></xf>" +
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
        AnswerKeySource.CAMERA -> "Kamera"
        AnswerKeySource.MANUAL -> "Manuel"
        AnswerKeySource.SPREADSHEET -> "Excel"
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

    private data class StructuredQuestionRow(
        val subject: String,
        val questionOrder: Int,
        val questionId: String
    )

    private const val XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
    private const val HEADER_ROW = 9
    private const val TITLE_STYLE = 1
    private const val LABEL_STYLE = 2
    private const val HEADER_STYLE = 3
    private const val BODY_STYLE = 4
    private const val ANSWER_STYLE = 5
    private const val BODY_LEFT_STYLE = 6
    private const val VALUE_STYLE = 0
}
