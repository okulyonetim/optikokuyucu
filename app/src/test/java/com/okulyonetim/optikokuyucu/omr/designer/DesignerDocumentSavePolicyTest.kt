package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Test

class DesignerDocumentSavePolicyTest {
    @Test
    fun `edited device form keeps the same persisted version`() {
        val stored = DesignerDocument(
            id = "form-sentetik",
            version = 1,
            name = "Sentetik Form"
        )
        val edited = stored.copy(name = "Sentetik Form Güncel")

        val resolved = DesignerDocumentSavePolicy.resolveForSave(
            document = edited,
            existing = listOf(stored)
        )

        assertEquals(edited, resolved)
        assertEquals(1, resolved.version)
    }

    @Test
    fun `editing immutable starter without a stored copy still derives safely`() {
        val baseline = DesignerStarterTemplates.questions20Abcd()
        val edited = baseline.copy(name = "Sentetik Düzenleme")

        val resolved = DesignerDocumentSavePolicy.resolveForSave(
            document = edited,
            existing = emptyList(),
            immutableBaselines = listOf(baseline)
        )

        assertEquals(2, resolved.version)
    }

    @Test
    fun `derived starter copy is updated in place after first save`() {
        val baseline = DesignerStarterTemplates.questions20Abcd()
        val stored = baseline.copy(version = 2, name = "Sentetik Kurum Formu")
        val edited = stored.copy(name = "Sentetik Kurum Formu Güncel")

        val resolved = DesignerDocumentSavePolicy.resolveForSave(
            document = edited,
            existing = listOf(stored),
            immutableBaselines = listOf(baseline)
        )

        assertEquals(edited, resolved)
        assertEquals(2, resolved.version)
    }
}
