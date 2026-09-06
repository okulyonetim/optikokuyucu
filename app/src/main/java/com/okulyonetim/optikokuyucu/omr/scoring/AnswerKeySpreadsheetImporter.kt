package com.okulyonetim.optikokuyucu.omr.scoring

import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import jxl.Workbook
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class ImportedAnswerKey(
    val answerKey: AnswerKey,
    val variantValue: String?
)

/** Offline .xls/.xlsx answer-key importer tied to the selected OMR template. */
object AnswerKeySpreadsheetImporter {
    fun import(
        input: InputStream,
        fileName: String,
        template: OmrTemplate,
        fallbackVariant: String? = null
    ): ImportedAnswerKey {
        val bytes = input.use { it.readBytes() }
        require(bytes.isNotEmpty()) { "Excel dosyası boş." }
        val rows = when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "xlsx" -> readXlsx(bytes)
            "xls" -> readXls(bytes)
            else -> error("Yalnız .xls ve .xlsx cevap anahtarı dosyaları desteklenir.")
        }
        return parseRows(rows, template, fallbackVariant)
    }

    internal fun parseRows(
        rows: List<List<String>>,
        template: OmrTemplate,
        fallbackVariant: String? = null
    ): ImportedAnswerKey {
        require(rows.isNotEmpty()) { "Excel sayfasında veri bulunamadı." }
        val tr = Locale("tr", "TR")
        val metadata = linkedMapOf<String, String>()
        rows.forEach { row ->
            val key = row.getOrNull(0).orEmpty().trim().lowercase(tr)
            val value = row.getOrNull(1).orEmpty().trim()
            if (key.isNotBlank() && value.isNotBlank()) metadata[key] = value
        }

        metadata["şablon"]?.let { require(it == template.id) { "Excel farklı bir optik forma ait: $it" } }
        metadata["sürüm"]?.toIntOrNull()?.let {
            require(it == template.version) { "Excel farklı bir optik form sürümüne ait: v$it" }
        }

        val headerIndex = rows.indexOfFirst { row ->
            val first = row.getOrNull(0).orEmpty().trim().lowercase(tr)
            val second = row.getOrNull(1).orEmpty().trim().lowercase(tr)
            (first == "soru" || first == "soru no" || first == "soru numarası") &&
                ("cevap" in second || "doğru" in second)
        }
        val candidates = if (headerIndex >= 0) rows.drop(headerIndex + 1) else rows
        val validRows = template.bubbleRows.associateBy { it.id }
        val answers = linkedMapOf<String, String>()

        candidates.forEach { row ->
            val rawQuestion = row.getOrNull(0).orEmpty().trim()
            val rawAnswer = row.getOrNull(1).orEmpty().trim()
            if (rawQuestion.isBlank() || rawAnswer.isBlank()) return@forEach
            val questionId = resolveQuestionId(rawQuestion, validRows.keys) ?: return@forEach
            val allowed = validRows.getValue(questionId).bubbles.map { it.id }
            require(rawAnswer in allowed) {
                "$rawQuestion sorusu için '$rawAnswer' geçerli bir seçenek değil (${allowed.joinToString("/")})."
            }
            require(questionId !in answers) { "$rawQuestion sorusu Excel'de birden fazla kez bulunuyor." }
            answers[questionId] = rawAnswer
        }

        require(answers.isNotEmpty()) { "Excel'de kullanılabilir Soru / Doğru Cevap satırı bulunamadı." }
        val missing = validRows.keys - answers.keys
        require(missing.isEmpty()) { "Cevap anahtarı eksik. ${missing.size} soru için cevap bulunamadı." }

        val variantFromFile = metadata["kitapçık"]
            ?.takeUnless { it.equals("genel", ignoreCase = true) || it == "-" }
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return ImportedAnswerKey(
            answerKey = AnswerKey(template.id, template.version, answers),
            variantValue = variantFromFile ?: fallbackVariant?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private fun resolveQuestionId(raw: String, validIds: Set<String>): String? {
        if (raw in validIds) return raw
        val matches = validIds.filter { it.substringAfterLast(':') == raw }
        return when (matches.size) {
            0 -> null
            1 -> matches.single()
            else -> error("'$raw' soru numarası birden fazla derste bulundu. Excel'de tam soru kimliğini kullanın.")
        }
    }

    private fun readXls(bytes: ByteArray): List<List<String>> {
        val workbook = Workbook.getWorkbook(ByteArrayInputStream(bytes))
        try {
            require(workbook.numberOfSheets > 0) { "XLS çalışma sayfası bulunamadı." }
            val sheet = workbook.getSheet(0)
            return (0 until sheet.rows).map { row ->
                (0 until sheet.columns.coerceAtLeast(2)).map { column ->
                    sheet.getCell(column, row).contents.orEmpty()
                }
            }
        } finally {
            workbook.close()
        }
    }

    private fun readXlsx(bytes: ByteArray): List<List<String>> {
        var sharedStrings: List<String> = emptyList()
        var sheetXml: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val payload = zip.readBytes()
                when {
                    entry.name == "xl/sharedStrings.xml" -> sharedStrings = parseSharedStrings(payload)
                    entry.name == "xl/worksheets/sheet1.xml" -> sheetXml = payload
                }
                zip.closeEntry()
            }
        }
        val xml = requireNotNull(sheetXml) { "XLSX içinde ilk çalışma sayfası bulunamadı." }
        return parseSheet(xml, sharedStrings)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val doc = document(bytes)
        val items = doc.getElementsByTagName("si")
        return (0 until items.length).map { index ->
            val item = items.item(index) as Element
            val texts = item.getElementsByTagName("t")
            buildString {
                for (i in 0 until texts.length) append(texts.item(i).textContent.orEmpty())
            }
        }
    }

    private fun parseSheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val doc = document(bytes)
        val cells = doc.getElementsByTagName("c")
        val byRow = sortedMapOf<Int, MutableMap<Int, String>>()
        for (i in 0 until cells.length) {
            val cell = cells.item(i) as Element
            val ref = cell.getAttribute("r")
            val row = ref.dropWhile { it.isLetter() }.toIntOrNull() ?: continue
            val column = columnIndex(ref.takeWhile { it.isLetter() })
            val type = cell.getAttribute("t")
            val value = when (type) {
                "inlineStr" -> cell.getElementsByTagName("t").item(0)?.textContent.orEmpty()
                "s" -> {
                    val index = cell.getElementsByTagName("v").item(0)?.textContent?.toIntOrNull()
                    index?.let(sharedStrings::getOrNull).orEmpty()
                }
                else -> cell.getElementsByTagName("v").item(0)?.textContent.orEmpty()
            }
            byRow.getOrPut(row) { mutableMapOf() }[column] = value
        }
        return byRow.values.map { columns ->
            val max = (columns.keys.maxOrNull() ?: 1).coerceAtLeast(1)
            (0..max).map { columns[it].orEmpty() }
        }
    }

    private fun columnIndex(letters: String): Int {
        var value = 0
        letters.uppercase(Locale.ROOT).forEach { char ->
            if (char in 'A'..'Z') value = value * 26 + (char - 'A' + 1)
        }
        return (value - 1).coerceAtLeast(0)
    }

    private fun document(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
}
