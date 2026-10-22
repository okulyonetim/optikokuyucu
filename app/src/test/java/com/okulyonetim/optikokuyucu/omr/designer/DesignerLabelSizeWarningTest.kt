package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerLabelSizeWarningTest {
    private fun withLabelScale(scale: Double): DesignerDocument {
        val base = DesignerStarterTemplates.questions20Abcd()
        return base.copy(
            formSpec = base.formSpec.copy(
                answerAppearance = base.formSpec.answerAppearance.copy(choiceLabelScale = scale)
            )
        )
    }

    private fun labelIssues(scale: Double) = TemplateReadabilityAnalyzer
        .analyze(withLabelScale(scale))
        .issues
        .filter { it.type == ReadabilityIssueType.LABEL_TOO_LARGE }

    @Test
    fun `default label size raises no warning`() {
        assertTrue(labelIssues(0.82).isEmpty())
        assertTrue(labelIssues(1.10).isEmpty())
    }

    @Test
    fun `oversized choice letters warn but never block saving`() {
        val report = TemplateReadabilityAnalyzer.analyze(withLabelScale(1.35))
        val issues = report.issues.filter { it.type == ReadabilityIssueType.LABEL_TOO_LARGE }

        assertEquals(1, issues.size)
        assertEquals(ReadabilitySeverity.WARNING, issues.single().severity)
        assertTrue(report.canSave)
    }
}
