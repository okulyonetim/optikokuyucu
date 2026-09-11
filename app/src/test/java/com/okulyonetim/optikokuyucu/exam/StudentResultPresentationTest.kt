package com.okulyonetim.optikokuyucu.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudentResultPresentationTest {
    @Test
    fun `presentation adds general class and lesson rank denominators`() {
        val rows = listOf(
            row("a", "Ali", "8-A", 430.0, 1, 1, 15.0),
            row("b", "Buse", "8-A", 410.0, 2, 2, 12.0),
            row("c", "Cem", "8-A", 390.0, 3, 3, 12.0),
            row("d", "Deniz", "8-B", 360.0, 4, 1, 8.0)
        )
        val report = ExamReport(
            examId = "exam",
            examName = "LGS Deneme",
            schoolName = "Okul",
            generatedAtEpochMs = 1L,
            rows = rows,
            scoringType = ExamScoringType.LGS
        )

        val result = requireNotNull(StudentResultPresentationBuilder.build(report, "b"))

        assertEquals("LGS Puanı", result.scoreLabel)
        assertEquals("2/4", result.overallRank?.displayText)
        assertEquals("2/3", result.classRank?.displayText)
        assertEquals("2/4", result.lessons.single().rank?.displayText)
        assertEquals(50.0, result.lessons.single().correctPercent, 0.0001)
        assertEquals(25.0, result.lessons.single().wrongPercent, 0.0001)
    }

    @Test
    fun `lesson ranking uses competition ranking for equal displayed nets`() {
        val rows = listOf(
            row("a", "A", "8-A", 450.0, 1, 1, 18.0),
            row("b", "B", "8-A", 430.0, 2, 2, 12.004),
            row("c", "C", "8-A", 420.0, 3, 3, 12.003),
            row("d", "D", "8-A", 400.0, 4, 4, 7.0)
        )
        val report = ExamReport("exam", "Deneme", "Okul", 1L, rows, ExamScoringType.LGS)

        val b = requireNotNull(StudentResultPresentationBuilder.build(report, "b"))
        val c = requireNotNull(StudentResultPresentationBuilder.build(report, "c"))
        val d = requireNotNull(StudentResultPresentationBuilder.build(report, "d"))

        assertEquals("2/4", b.lessons.single().rank?.displayText)
        assertEquals("2/4", c.lessons.single().rank?.displayText)
        assertEquals("4/4", d.lessons.single().rank?.displayText)
    }

    @Test
    fun `unscored rows do not inflate score rank denominator`() {
        val scored = row("a", "A", "8-A", 420.0, 1, 1, 10.0)
        val unavailable = row("b", "B", "8-A", null, null, null, 8.0)
            .copy(status = ExamReportRowStatus.REVIEW_REQUIRED)
        val report = ExamReport(
            examId = "exam",
            examName = "Deneme",
            schoolName = "Okul",
            generatedAtEpochMs = 1L,
            rows = listOf(scored, unavailable),
            scoringType = ExamScoringType.LGS
        )

        val first = requireNotNull(StudentResultPresentationBuilder.build(report, "a"))
        val second = requireNotNull(StudentResultPresentationBuilder.build(report, "b"))

        assertEquals("1/1", first.overallRank?.displayText)
        assertNull(second.overallRank)
        assertNull(second.lessons.single().rank)
    }

    private fun row(
        id: String,
        name: String,
        clazz: String,
        points: Double?,
        overallRank: Int?,
        classRank: Int?,
        lessonNet: Double
    ): ExamReportRow = ExamReportRow(
        ordinal = id.first().code,
        scanRecordId = id,
        studentName = name,
        className = clazz,
        studentNumber = id,
        bookletCode = "A",
        capturedAtEpochMs = 1L,
        correct = 10,
        wrong = 5,
        blank = 5,
        doubleMark = 0,
        suspicious = 0,
        noKey = 0,
        points = points,
        maximumPoints = 500.0,
        status = ExamReportRowStatus.SCORED,
        net = lessonNet,
        overallRank = overallRank,
        classRank = classRank,
        lessons = listOf(
            ExamLessonScore(
                lessonId = "turkce",
                correct = 10,
                wrong = 5,
                blank = 5,
                doubleMark = 0,
                suspicious = 0,
                noKey = 0,
                net = lessonNet
            )
        )
    )
}
