package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerAreaCatalogTest {
    @Test
    fun `add area catalog matches approved stage four choices`() {
        assertEquals(
            listOf("İşaretleme Alanı", "Bilgilendirme Alanı"),
            DesignerAreaCatalog.sections.map { it.title }
        )
        assertEquals(
            listOf(DesignerAreaKind.NUMBER, DesignerAreaKind.ANSWERS),
            DesignerAreaCatalog.sections[0].kinds
        )
        assertEquals(
            listOf(DesignerAreaKind.DESCRIPTION, DesignerAreaKind.IMAGE),
            DesignerAreaCatalog.sections[1].kinds
        )
        assertEquals(DesignerAreaKind.entries.toSet(), DesignerAreaCatalog.allKinds.toSet())
    }

    @Test
    fun `student photo is not offered`() {
        val labels = DesignerAreaCatalog.allKinds.map { it.displayName }

        assertFalse(labels.any { it.contains("Öğrenci Fotoğraf", ignoreCase = true) })
        assertTrue(labels.containsAll(listOf("Numara", "Cevaplar", "Açıklama", "Resim")))
    }
}
