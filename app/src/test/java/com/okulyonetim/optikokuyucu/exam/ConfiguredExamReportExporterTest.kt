package com.okulyonetim.optikokuyucu.exam

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfiguredExamReportExporterTest {
    @Test
    fun `xlsx keeps requested base order and expands lessons into grouped four-column blocks plus total`() {
        val row = ExamReportRow(
            ordinal = 1,
            scanRecordId = "scan-1",
            studentName = "Ali İmran",
            className = "8-A",
            studentNumber = "16",
            bookletCode = "A",
            capturedAtEpochMs = 1L,
            correct = 27,
            wrong = 5,
            blank = 8,
            doubleMark = 0,
            suspicious = 0,
            noKey = 0,
            points = 410.25,
            maximumPoints = 500.0,
            status = ExamReportRowStatus.SCORED,
            net = 25.33,
            lessons = listOf(
                ExamLessonScore("turkce", 15, 2, 3, 0, 0, 0, 14.33),
                ExamLessonScore("matematik", 12, 3, 5, 0, 0, 0, 11.0)
            )
        )
        val report = ExamReport(
            examId = "exam-1",
            examName = "Deneme",
            schoolName = "Okul",
            generatedAtEpochMs = 1L,
            rows = listOf(row)
        )
        val config = ConfiguredExamReport(
            report = report,
            rows = listOf(row),
            columns = listOf(
                ReportColumn.STUDENT,
                ReportColumn.CLASS,
                ReportColumn.NUMBER,
                ReportColumn.SCORE,
                ReportColumn.LESSONS
            ),
            orientation = ReportPageOrientation.LANDSCAPE
        )

        val sheet = unzip(ConfiguredExamReportExporter.exportXlsx(config))["xl/worksheets/sheet1.xml"].orEmpty()

        val student = sheet.indexOf("Ad Soyad")
        val clazz = sheet.indexOf("Sınıf")
        val number = sheet.indexOf(">No<")
        val score = sheet.indexOf("Puan")
        val turkish = sheet.indexOf("Türkçe")
        val math = sheet.indexOf("Matematik")
        val total = sheet.indexOf("Toplam")

        assertTrue(student >= 0)
        assertTrue(student < clazz && clazz < number && number < score)
        assertTrue(score < turkish && turkish < math && math < total)
        assertTrue(sheet.contains(">D<"))
        assertTrue(sheet.contains(">Y<"))
        assertTrue(sheet.contains(">B<"))
        assertTrue(sheet.contains(">Net<"))
        assertTrue(sheet.contains("Ali İmran"))
        assertTrue(sheet.contains("<mergeCells"))
        assertTrue(sheet.contains("orientation=\"landscape\""))
    }

    @Test
    fun `lgs xlsx labels configured score as LGS score`() {
        val row = ExamReportRow(
            ordinal = 1,
            scanRecordId = "scan-lgs",
            studentName = "Muhammet Emin",
            className = "8-A",
            studentNumber = "136",
            bookletCode = "A",
            capturedAtEpochMs = 1L,
            correct = 70,
            wrong = 15,
            blank = 5,
            doubleMark = 0,
            suspicious = 0,
            noKey = 0,
            points = 384.7,
            maximumPoints = 500.0,
            status = ExamReportRowStatus.SCORED,
            net = 65.0,
            overallRank = 1
        )
        val report = ExamReport(
            examId = "lgs",
            examName = "LGS Deneme",
            schoolName = "Koruk Ortaokulu",
            generatedAtEpochMs = 1L,
            rows = listOf(row),
            scoringType = ExamScoringType.LGS
        )
        val config = ConfiguredExamReport(
            report = report,
            rows = listOf(row),
            columns = listOf(
                ReportColumn.OVERALL_RANK,
                ReportColumn.NUMBER,
                ReportColumn.STUDENT,
                ReportColumn.CLASS,
                ReportColumn.CORRECT,
                ReportColumn.WRONG,
                ReportColumn.NET,
                ReportColumn.SCORE
            )
        )

        val sheet = unzip(ConfiguredExamReportExporter.exportXlsx(config))["xl/worksheets/sheet1.xml"].orEmpty()

        assertTrue(sheet.contains("LGS Puanı"))
        assertTrue(sheet.contains("384.70") || sheet.contains("384,70"))
    }

    private fun unzip(bytes: ByteArray): Map<String, String> {
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
