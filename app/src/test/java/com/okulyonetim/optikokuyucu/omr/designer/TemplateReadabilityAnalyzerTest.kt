package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.BubbleRowSpec
import com.okulyonetim.optikokuyucu.omr.template.BubbleSpec
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateReadabilityAnalyzerTest {
    @Test
    fun `standard dense templates are saveable`() {
        val report100 = TemplateReadabilityAnalyzer.analyze(StandardOmrTemplate.SAMPLE_100_ABCD)
        val reportCombined = TemplateReadabilityAnalyzer.analyze(
            StandardOmrTemplate.SAMPLE_20_ABCD_STUDENT_6_BOOKLET_AB
        )

        assertTrue(report100.canSave)
        assertTrue(reportCombined.canSave)
        assertEquals(100, report100.score)
    }

    @Test
    fun `tiny overlapping marks block save`() {
        val invalid = StandardOmrTemplate.DEFAULT.copy(
            id = "invalid-readability-test",
            bubbleRows = listOf(
                BubbleRowSpec(
                    id = "1",
                    bubbles = listOf(
                        BubbleSpec("A", TemplatePoint(200.0, 200.0), 2.0),
                        BubbleSpec("B", TemplatePoint(202.0, 200.0), 2.0)
                    )
                )
            )
        )

        val report = TemplateReadabilityAnalyzer.analyze(invalid)

        assertFalse(report.canSave)
        assertTrue(report.errorCount >= 2)
        assertTrue(report.issues.any { it.type == ReadabilityIssueType.MARK_TOO_SMALL })
        assertTrue(report.issues.any { it.type == ReadabilityIssueType.MARK_OVERLAP })
    }
}
