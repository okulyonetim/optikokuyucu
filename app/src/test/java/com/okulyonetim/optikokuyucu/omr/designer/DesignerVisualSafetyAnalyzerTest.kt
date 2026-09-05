package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerVisualSafetyAnalyzerTest {
    @Test
    fun `text over bubble blocks save`() {
        val base = DesignerStarterTemplates.questions20Abcd()
        val document = base.copy(
            visualElements = listOf(
                DesignerTextElement(
                    id = "title",
                    bounds = TemplateRect(405.0, 280.0, 70.0, 40.0),
                    text = "Başlık",
                    fontSize = 20.0
                )
            )
        )
        val template = DesignerTemplateCompiler.compile(document)

        val report = TemplateReadabilityAnalyzer.analyze(document, template)

        assertFalse(report.canSave)
        assertTrue(report.issues.any { it.type == ReadabilityIssueType.VISUAL_MARK_CLEARANCE })
    }

    @Test
    fun `box may surround bubble when its strokes stay clear`() {
        val base = DesignerStarterTemplates.questions20Abcd()
        val document = base.copy(
            visualElements = listOf(
                DesignerBoxElement(
                    id = "safe-box",
                    bounds = TemplateRect(375.0, 245.0, 110.0, 110.0),
                    strokeWidth = 2.0
                )
            )
        )
        val template = DesignerTemplateCompiler.compile(document)

        val issues = DesignerVisualSafetyAnalyzer.analyze(document, template)

        assertTrue(issues.none { it.type == ReadabilityIssueType.VISUAL_MARK_CLEARANCE })
    }

    @Test
    fun `box stroke through bubble is rejected`() {
        val base = DesignerStarterTemplates.questions20Abcd()
        val document = base.copy(
            visualElements = listOf(
                DesignerBoxElement(
                    id = "unsafe-box",
                    bounds = TemplateRect(430.0, 250.0, 100.0, 100.0),
                    strokeWidth = 2.0
                )
            )
        )
        val template = DesignerTemplateCompiler.compile(document)

        val issues = DesignerVisualSafetyAnalyzer.analyze(document, template)

        assertTrue(issues.any { it.type == ReadabilityIssueType.VISUAL_MARK_CLEARANCE })
    }

    @Test
    fun `line through fiducial safety area is rejected`() {
        val base = DesignerStarterTemplates.questions20Abcd()
        val document = base.copy(
            visualElements = listOf(
                DesignerLineElement(
                    id = "marker-line",
                    start = TemplatePoint(20.0, 65.0),
                    end = TemplatePoint(130.0, 65.0),
                    strokeWidth = 2.0
                )
            )
        )
        val template = DesignerTemplateCompiler.compile(document)

        val issues = DesignerVisualSafetyAnalyzer.analyze(document, template)

        assertTrue(issues.any { it.type == ReadabilityIssueType.VISUAL_FIDUCIAL_CLEARANCE })
    }

    @Test
    fun `visual element outside canonical page is rejected`() {
        val base = DesignerStarterTemplates.questions20Abcd()
        val document = base.copy(
            visualElements = listOf(
                DesignerTextElement(
                    id = "outside",
                    bounds = TemplateRect(-10.0, 150.0, 80.0, 30.0),
                    text = "Taşan",
                    fontSize = 18.0
                )
            )
        )
        val template = DesignerTemplateCompiler.compile(document)

        val issues = DesignerVisualSafetyAnalyzer.analyze(document, template)

        assertTrue(issues.any { it.type == ReadabilityIssueType.VISUAL_EDGE_CLEARANCE })
    }
}
