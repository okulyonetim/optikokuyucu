package com.okulyonetim.optikokuyucu.omr.template

import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HistoricalTemplateResolutionStage12Test {
    @Test
    fun `old exam selection resolves exact historical designer version`() {
        val v1 = DesignerStarterTemplates.questions20Abcd().copy(
            id = "stage12-form",
            name = "Sürüm 1"
        )
        val v2 = v1.copy(version = 2, name = "Sürüm 2")
        val selection = ActiveTemplateSelection(
            source = ActiveTemplateSource.DESIGNER_DOCUMENT,
            templateId = v1.id,
            templateVersion = 1
        )

        val resolved = ActiveOmrTemplateResolver.resolve(
            selection = selection,
            savedDocuments = listOf(v2, v1),
            starterDocuments = emptyList()
        )

        assertNotNull(resolved)
        assertEquals(1, resolved!!.template.version)
        assertEquals("Sürüm 1", resolved.name)
        assertEquals(selection, resolved.selection)
    }
}
