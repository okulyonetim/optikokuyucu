package com.okulyonetim.optikokuyucu.ui

import com.okulyonetim.optikokuyucu.omr.designer.DesignerPhysicalTestPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesignerLibraryOpenHandoffTest {
    @Test
    fun `preview request carries exact designer document once`() {
        val document = DesignerPhysicalTestPack.document()

        DesignerLibraryOpenHandoff.offer(document, DesignerLibraryOpenMode.PREVIEW)
        val request = DesignerLibraryOpenHandoff.consume()

        assertEquals(document, request?.document)
        assertEquals(DesignerLibraryOpenMode.PREVIEW, request?.mode)
        assertNull(DesignerLibraryOpenHandoff.consume())
    }

    @Test
    fun `edit request preserves template id and version`() {
        val document = DesignerPhysicalTestPack.document()

        DesignerLibraryOpenHandoff.offer(document, DesignerLibraryOpenMode.EDIT)
        val request = DesignerLibraryOpenHandoff.consume()

        assertEquals(document.id, request?.document?.id)
        assertEquals(document.version, request?.document?.version)
        assertEquals(DesignerLibraryOpenMode.EDIT, request?.mode)
    }
}
