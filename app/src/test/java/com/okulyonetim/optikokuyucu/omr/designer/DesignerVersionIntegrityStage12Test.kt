package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerVersionIntegrityStage12Test {
    @Test
    fun `editing immutable starter baseline creates next version`() {
        val baseline = DesignerStarterTemplates.questions20Abcd()
        val edited = baseline.copy(name = "Düzenlenmiş Başlangıç Formu")

        val resolved = DesignerTemplateVersioning.resolveForSave(
            document = edited,
            existing = emptyList(),
            immutableBaselines = listOf(baseline)
        )

        assertEquals(2, resolved.version)
        assertEquals("Düzenlenmiş Başlangıç Formu", resolved.name)
    }

    @Test
    fun `historical versions remain protected after a newer version exists`() {
        val v1 = DesignerStarterTemplates.questions20Abcd()
        val v2 = v1.copy(version = 2, name = "Sürüm 2")

        assertTrue(DesignerTemplateVersioning.isHistoricalVersion(v1.id, 1, listOf(v1, v2)))
        assertFalse(DesignerTemplateVersioning.isHistoricalVersion(v1.id, 2, listOf(v1, v2)))
    }

    @Test
    fun `stale requested version advances beyond latest known version`() {
        val v1 = DesignerStarterTemplates.questions20Abcd()
        val v3 = v1.copy(version = 3, name = "Sürüm 3")
        val stale = v1.copy(version = 2, name = "Yeni değişiklik")

        val resolved = DesignerTemplateVersioning.resolveForSave(
            document = stale,
            existing = listOf(v3)
        )

        assertEquals(4, resolved.version)
    }
}
