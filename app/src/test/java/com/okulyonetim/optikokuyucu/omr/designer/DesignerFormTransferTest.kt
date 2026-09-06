package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Test

class DesignerFormTransferTest {
    @Test
    fun exportedFormCanBeImportedForEditing() {
        var document = DesignerDocument(id = "portable-form", version = 1, name = "Taşınabilir Form")
        document = document.copy(
            components = document.components + DesignerAreaCatalog.createAnswerArea(document),
            visualElements = document.visualElements + DesignerAreaCatalog.createStudentNameArea(document)
        )

        val bytes = DesignerFormTransfer.export(document)
        val restored = DesignerFormTransfer.import(bytes)

        assertEquals(document, restored)
        assertEquals("Taşınabilir-Form-v1.omrd", DesignerFormTransfer.fileName(document))
    }
}
