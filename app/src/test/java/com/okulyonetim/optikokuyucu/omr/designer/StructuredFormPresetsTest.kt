package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredFormPresetsTest {
    @Test
    fun `all school presets compile into safe editable templates`() {
        val presets = StructuredFormPresets.all()

        assertEquals(5, presets.size)
        presets.forEach { preset ->
            val config = preset.instantiate(1234L)
            val result = StructuredFormDocumentFactory.build(config)
            val template = DesignerTemplateCompiler.compile(result.document)
            val readability = TemplateReadabilityAnalyzer.analyze(result.document, template)

            assertTrue("${preset.displayName} güvenli olmalı", readability.canSave)
            assertEquals(config.lessons.sumOf { it.questionCount }, template.bubbleRows.size)
            assertTrue(template.markGrids.any { it.id == "studentNumber" })
            assertTrue(template.markGrids.any { it.id == "booklet" })
        }
    }

    @Test
    fun `preset instances get independent document ids`() {
        val preset = StructuredFormPresets.standard20()
        val first = preset.instantiate(100L)
        val second = preset.instantiate(200L)

        assertNotEquals(first.id, second.id)
        assertEquals(first.lessons, second.lessons)
    }
}
