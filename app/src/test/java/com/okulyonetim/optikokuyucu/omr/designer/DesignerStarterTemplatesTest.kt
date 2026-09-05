package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerStarterTemplatesTest {
    @Test
    fun `starter pack compiles with expected question counts`() {
        val documents = DesignerStarterTemplates.all()
        val expectedCounts = listOf(20, 40, 50, 80, 100)

        assertEquals(expectedCounts.size, documents.size)
        documents.zip(expectedCounts).forEach { (document, expectedCount) ->
            val template = DesignerTemplateCompiler.compile(document)
            assertEquals(expectedCount, template.bubbleRows.size)
        }
    }

    @Test
    fun `every starter template passes save-readability gate`() {
        DesignerStarterTemplates.all().forEach { document ->
            val template = DesignerTemplateCompiler.compile(document)
            val report = TemplateReadabilityAnalyzer.analyze(template)
            assertTrue("${document.name}: ${report.issues}", report.canSave)
        }
    }
}
