package com.okulyonetim.optikokuyucu.exam

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamReportXlsxExporterTest {
    @Test
    fun `xlsx contains lesson score net rankings note and exam rows`() {
        val report = ExamReport(
            examId = "exam-1",
            examName = "Deneme",
            schoolName = "Koruk Ortaokulu",
            generatedAtEpochMs = 1L,
            rows = listOf(
                ExamReportRow(
                    ordinal = 1,
                    scanRecordId = "scan&1",
                    studentName = "Ali <İmran>",
                    className = "8-A",
                    studentNumber = "123",
                    bookletCode = "A",
                    capturedAtEpochMs = 1_700_000_000_000L,
                    correct = 12,
                    wrong = 8,
                    blank = 3,
                    doubleMark = 0,
                    suspicious = 1,
                    noKey = 0,
                    points = 410.5,
                    maximumPoints = 500.0,
                    status = ExamReportRowStatus.REVIEW_REQUIRED,
                    net = 10.0,
                    lessons = listOf(
                        ExamLessonScore(
                            lessonId = "turkce",
                            correct = 12,
                            wrong = 8,
                            blank = 0,
                            doubleMark = 0,
                            suspicious = 0,
                            noKey = 0,
                            net = 9.333333,
                            weight = 4.0,
                            standardScore = 55.25,
                            weightedStandardScore = 221.0
                        )
                    ),
                    overallRank = 3,
                    classRank = 2,
                    scoreNote = "Yerel <grup> & MEB yöntemi"
                ),
                ExamReportRow(
                    ordinal = 2,
                    scanRecordId = "missing",
                    studentName = "Eksik Kayıt",
                    className = "",
                    studentNumber = "",
                    bookletCode = "",
                    capturedAtEpochMs = null,
                    correct = null,
                    wrong = null,
                    blank = null,
                    doubleMark = null,
                    suspicious = null,
                    noKey = null,
                    points = null,
                    maximumPoints = null,
                    status = ExamReportRowStatus.SCAN_MISSING
                )
            ),
            scoringType = ExamScoringType.LGS
        )

        val bytes = ExamReportXlsxExporter.export(report)
        val entries = unzipTextEntries(bytes)

        assertEquals(ExamReportXlsxExporter.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        assertTrue(bytes.size > 500)
        assertTrue(entries.keys.containsAll(
            listOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels",
                "xl/styles.xml",
                "xl/worksheets/sheet1.xml"
            )
        ))

        val workbook = requireNotNull(entries["xl/workbook.xml"])
        val sheet = requireNotNull(entries["xl/worksheets/sheet1.xml"])

        assertTrue(workbook.contains("sheet name=\"Sonuçlar\""))
        assertTrue(sheet.contains("<autoFilter ref=\"A1:U3\"/>"))
        assertTrue(sheet.contains("Ders Sonuçları"))
        assertTrue(sheet.contains("Ali &lt;İmran&gt;"))
        assertTrue(sheet.contains("scan&amp;1"))
        assertTrue(sheet.contains("<c r=\"M2\"><v>10</v></c>"))
        assertTrue(sheet.contains("<c r=\"N2\"><v>410.5</v></c>"))
        assertTrue(sheet.contains("<c r=\"O2\"><v>500</v></c>"))
        assertTrue(sheet.contains("<c r=\"P2\"><v>3</v></c>"))
        assertTrue(sheet.contains("<c r=\"Q2\"><v>2</v></c>"))
        assertTrue(sheet.contains("turkce: D 12 Y 8 B 0 Net 9,33 SP 55,25 ASP 221,00"))
        assertTrue(sheet.contains("Yerel &lt;grup&gt; &amp; MEB yöntemi"))
        assertTrue(sheet.contains("KONTROL GEREKLİ"))
        assertTrue(sheet.contains("TARAMA YOK"))
    }

    @Test
    fun `empty report still creates header-only workbook`() {
        val report = ExamReport(
            examId = "exam-empty",
            examName = "Boş",
            schoolName = "Okul",
            generatedAtEpochMs = 1L,
            rows = emptyList()
        )

        val sheet = requireNotNull(
            unzipTextEntries(ExamReportXlsxExporter.export(report))["xl/worksheets/sheet1.xml"]
        )

        assertTrue(sheet.contains("<row r=\"1\">"))
        assertTrue(sheet.contains("Sıra"))
        assertTrue(sheet.contains("Ders Sonuçları"))
        assertTrue(sheet.contains("Genel Sıra"))
        assertTrue(!sheet.contains("<autoFilter"))
    }

    private fun unzipTextEntries(bytes: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        return result
    }
}
