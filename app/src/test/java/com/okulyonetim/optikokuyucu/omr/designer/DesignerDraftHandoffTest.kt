package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesignerDraftHandoffTest {
    @Test
    fun `draft handoff is consumed once`() {
        val document = DesignerDocument(
            id = "handoff-test",
            version = 1,
            name = "Handoff Test"
        )

        DesignerDraftHandoff.offer(document)

        assertEquals(document, DesignerDraftHandoff.consume())
        assertNull(DesignerDraftHandoff.consume())
    }
}
