package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DesignerDraftCodecTest {
    private fun sampleDocument() = DesignerDocument(
        id = "form-1",
        version = 2,
        name = "Taslak Form",
        formSpec = DesignerFormSpec()
    )

    @Test
    fun `draft round trip keeps owner name document and timestamp`() {
        val draft = DesignerDraft(
            ownerKey = "uid-42",
            formName = "Yazılı 1",
            document = sampleDocument(),
            savedAtEpochMs = 1_700_000_000_000L
        )

        val decoded = DesignerDraftCodec.decode(DesignerDraftCodec.encode(draft))

        assertEquals(draft, decoded)
    }

    @Test
    fun `very long names are truncated instead of failing the encode`() {
        val draft = DesignerDraft(
            ownerKey = "",
            formName = "ş".repeat(5_000),
            document = sampleDocument(),
            savedAtEpochMs = 1L
        )

        val decoded = DesignerDraftCodec.decode(DesignerDraftCodec.encode(draft))

        assertEquals(200, decoded.formName.length)
    }

    @Test
    fun `corrupt bytes are rejected`() {
        assertThrows(Exception::class.java) { DesignerDraftCodec.decode(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)) }
    }
}
