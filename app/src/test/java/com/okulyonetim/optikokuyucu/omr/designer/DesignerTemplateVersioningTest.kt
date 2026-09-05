package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Test

class DesignerTemplateVersioningTest {
    @Test
    fun `first save keeps requested version`() {
        val document = DesignerStarterTemplates.questions20Abcd()

        val resolved = DesignerTemplateVersioning.resolveForSave(document, emptyList())

        assertEquals(document, resolved)
    }

    @Test
    fun `saving identical existing version is idempotent`() {
        val document = DesignerStarterTemplates.questions20Abcd()

        val resolved = DesignerTemplateVersioning.resolveForSave(document, listOf(document))

        assertEquals(document, resolved)
    }

    @Test
    fun `changed content on an existing version receives next version`() {
        val v1 = DesignerStarterTemplates.questions20Abcd()
        val editedV1 = v1.copy(name = "Düzenlenmiş Form")

        val resolved = DesignerTemplateVersioning.resolveForSave(editedV1, listOf(v1))

        assertEquals(2, resolved.version)
        assertEquals("Düzenlenmiş Form", resolved.name)
    }

    @Test
    fun `next version skips versions already used by same template id`() {
        val v1 = DesignerStarterTemplates.questions20Abcd()
        val v2 = v1.copy(version = 2, name = "Sürüm 2")
        val editedV1 = v1.copy(name = "Yeni değişiklik")

        val resolved = DesignerTemplateVersioning.resolveForSave(
            editedV1,
            listOf(v1, v2)
        )

        assertEquals(3, resolved.version)
    }
}
