package com.okulyonetim.optikokuyucu.omr.template

import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveOmrTemplateTest {
    @Test
    fun `selection codec round trips source id and version`() {
        val selection = ActiveTemplateSelection(
            source = ActiveTemplateSource.DESIGNER_DOCUMENT,
            templateId = "my-template",
            templateVersion = 7
        )

        assertEquals(
            selection,
            ActiveTemplateSelectionCodec.decode(ActiveTemplateSelectionCodec.encode(selection))
        )
    }

    @Test
    fun `default selection preserves production scanner template`() {
        val resolved = ActiveOmrTemplateResolver.resolveOrDefault(
            selection = ActiveOmrTemplateDefaults.selection,
            savedDocuments = emptyList(),
            starterDocuments = emptyList()
        )

        assertFalse(resolved.fellBackToDefault)
        assertEquals(StandardOmrTemplate.SAMPLE_20_ABCD_STUDENT_6_BOOKLET_AB.id, resolved.template.id)
        assertEquals(StandardOmrTemplate.SAMPLE_20_ABCD_STUDENT_6_BOOKLET_AB.version, resolved.template.version)
        assertEquals(20, resolved.template.bubbleRows.size)
        assertTrue(resolved.template.markGrids.any { it.id == "studentNumber" })
        assertTrue(resolved.template.markGrids.any { it.id == "booklet" })
    }

    @Test
    fun `starter designer document resolves to compiled reader template`() {
        val document = DesignerStarterTemplates.questions40Abcd()
        val selection = ActiveTemplateSelection(
            source = ActiveTemplateSource.DESIGNER_DOCUMENT,
            templateId = document.id,
            templateVersion = document.version
        )

        val resolved = ActiveOmrTemplateResolver.resolveOrDefault(
            selection = selection,
            savedDocuments = emptyList(),
            starterDocuments = listOf(document)
        )

        assertFalse(resolved.fellBackToDefault)
        assertEquals(document.id, resolved.template.id)
        assertEquals(document.version, resolved.template.version)
        assertEquals(document.name, resolved.name)
        assertEquals(40, resolved.template.bubbleRows.size)
    }

    @Test
    fun `missing selected designer version safely falls back to production default`() {
        val resolved = ActiveOmrTemplateResolver.resolveOrDefault(
            selection = ActiveTemplateSelection(
                source = ActiveTemplateSource.DESIGNER_DOCUMENT,
                templateId = "deleted-template",
                templateVersion = 99
            ),
            savedDocuments = emptyList(),
            starterDocuments = emptyList()
        )

        assertTrue(resolved.fellBackToDefault)
        assertEquals(ActiveOmrTemplateDefaults.selection, resolved.selection)
        assertEquals(ActiveOmrTemplateDefaults.template.id, resolved.template.id)
    }
}
