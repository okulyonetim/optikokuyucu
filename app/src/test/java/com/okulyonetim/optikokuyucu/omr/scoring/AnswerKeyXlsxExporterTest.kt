package com.okulyonetim.optikokuyucu.omr.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `structured export shows subjects local question order and all booklet columns`() {
        val sections = listOf(
            ManualAnswerSection(
                id = "answers-1",
                label = "Türkçe",
                questionIds = listOf("answers-1:1", "answers-1:2"),
                allowedChoices = setOf("A", "B", "C", "D")
            ),
            ManualAnswerSection(
                id = "answers-2",
                label = "Matematik",
                questionIds = listOf("answers-2:1"),
                allowedChoices = setOf("A", "B", "C", "D")
            )
        )
        val keyA = StoredAnswerKey(
            answerKey = AnswerKey(
                templateId = "deneme",
                templateVersion = 3,
                answers = linkedMapOf(
                    "answers-1:1" to "B",
                    "answers-1:2" to "D",
                    "answers-2:1" to "A"
                )
            ),
            variantGridId = "booklet",
            variantValue = "A",
            source = AnswerKeySource.MANUAL,
            examId = "exam-1"
        )
        val keyB = StoredAnswerKey(
            answerKey = AnswerKey(
                templateId = "deneme",
                templateVersion = 3,
                answers = linkedMapOf(
                    "answers-1:1" to "C",
                    "answers-1:2" to "A",
                    "answers-2:1" to "D"
                )
            ),
            variantGridId = "booklet",
            variantValue = "B",
            source = AnswerKeySource.MANUAL,
            examId = "exam-1"
        )

        val sheet = unzip(
            AnswerKeyXlsxExporter.exportStructured(
                keys = listOf(keyB, keyA),
                sections = sections
            )
        ).getValue("xl/worksheets/sheet1.xml")

        assertTrue(sheet.contains(">Ders<"))
        assertTrue(sheet.contains(">Soru<"))
        assertTrue(sheet.contains(">Kitapçık A<"))
        assertTrue(sheet.contains(">Kitapçık B<"))
        assertTrue(sheet.contains(">Türkçe<"))
        assertTrue(sheet.contains(">Matematik<"))
        assertTrue(sheet.contains("autoFilter ref=\"A9:D12\""))
        assertFalse(sheet.contains("answers-1:1"))
        assertFalse(sheet.contains("answers-2:1"))

        val turkish = sheet.indexOf(">Türkçe<")
        val math = sheet.indexOf(">Matematik<")
        assertTrue(turkish >= 0 && math > turkish)
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
