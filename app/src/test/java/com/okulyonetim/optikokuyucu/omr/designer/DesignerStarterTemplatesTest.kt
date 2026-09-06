package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerStarterTemplatesTest {
    @Test
    fun `starter pack keeps expected question and choice geometry`() {
        val expected = mapOf(
            "starter-20-abcd" to (20 to 4),
            "starter-40-abcd" to (40 to 4),
            "starter-50-abcde" to (50 to 5),
            "starter-80-abcd" to (80 to 4),
            "starter-100-abcd" to (100 to 4),
            DesignerPhysicalTestPack.TEMPLATE_ID to (20 to 4)
        )
        val documents = DesignerStarterTemplates.all()

        assertEquals(expected.keys, documents.map { it.id }.toSet())
        assertEquals(documents.size, documents.map { it.id }.distinct().size)

        documents.forEach { document ->
            val template = DesignerTemplateCompiler.compile(document)
            val (questionCount, choiceCount) = requireNotNull(expected[document.id])

            assertEquals(questionCount, template.bubbleRows.size)
            assertTrue(template.bubbleRows.all { it.bubbles.size == choiceCount })
            assertEquals(4, template.fiducials.map { it.markerId }.toSet().size)
        }
    }

    @Test
    fun `every starter template passes complete designer safety gate`() {
        DesignerStarterTemplates.all().forEach { document ->
            val template = DesignerTemplateCompiler.compile(document)
            val report = TemplateReadabilityAnalyzer.analyze(document, template)
            assertTrue("${document.name}: ${report.issues}", report.canSave)
        }
    }
}
