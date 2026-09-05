package com.okulyonetim.optikokuyucu.omr.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class AnswerKeyXlsxExporterTest {
    @Test
    fun `export creates valid xlsx package with metadata and sorted answers`() {
        val key = StoredAnswerKey(
            answerKey = AnswerKey(
                templateId = "sample<&>",
                templateVersion = 2,
                answers = linkedMapOf(
                    "10" to "D",
                    "2" to "B",
                    "1" to "A"
                )
            ),
            variantGridId = "booklet",
            variantValue = "A",
            createdAtEpochMs = 0L,
            source = AnswerKeySource.GALLERY
        )

        val entries = unzip(AnswerKeyXlsxExporter.export(key))

        assertEquals(
            setOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels",
                "xl/styles.xml",
                "xl/worksheets/sheet1.xml"
            ),
            entries.keys
        )
        assertTrue(entries.getValue("[Content_Types].xml").contains("spreadsheetml.sheet.main+xml"))
        assertTrue(entries.getValue("xl/workbook.xml").contains("Cevap Anahtarı"))

        val sheet = entries.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("OPTİK OKUYUCU · CEVAP ANAHTARI"))
        assertTrue(sheet.contains("sample&lt;&amp;&gt;"))
        assertTrue(sheet.contains("Kitapçık"))
        assertTrue(sheet.contains(">A<"))
        assertTrue(sheet.contains("autoFilter ref=\"A9:B12\""))

        val q1 = sheet.indexOf(">1<")
        val q2 = sheet.indexOf(">2<", startIndex = q1 + 1)
        val q10 = sheet.indexOf(">10<", startIndex = q2 + 1)
        assertTrue(q1 >= 0 && q2 > q1 && q10 > q2)
    }

    @Test
    fun `general answer key is exported without a booklet variant`() {
        val key = StoredAnswerKey(
            answerKey = AnswerKey(
                templateId = "exam",
                templateVersion = 1,
                answers = mapOf("1" to "C")
            ),
            source = AnswerKeySource.SCAN_RECORD,
            sourceRecordId = "record-1"
        )

        val sheet = unzip(AnswerKeyXlsxExporter.export(key))
            .getValue("xl/worksheets/sheet1.xml")

        assertTrue(sheet.contains("Genel"))
        assertTrue(sheet.contains("Kamera kaydı"))
    }

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
