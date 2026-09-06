package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `number patterns support approved presets and custom token lists`() {
        assertEquals(
            listOf("0123456789", "AB", "ABC", "ABCD", "ABCDE"),
            DesignerAreaCatalog.numberPatternPresets
        )
        assertEquals(
            listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"),
            DesignerAreaCatalog.parseNumberPattern("0123456789")
        )
        assertEquals(listOf("A", "B", "C", "D"), DesignerAreaCatalog.parseNumberPattern("ABCD"))
        assertEquals(listOf("01", "02", "03"), DesignerAreaCatalog.parseNumberPattern("01,02,03"))
        assertEquals("01,02,03", DesignerAreaCatalog.numberPatternText(listOf("01", "02", "03")))
        assertNull(DesignerAreaCatalog.parseNumberPattern("A"))
        assertNull(DesignerAreaCatalog.parseNumberPattern("AAB"))
    }

    @Test
    fun `default number area is valid canonical geometry inside safe page`() {
        val document = DesignerPageGeometry.apply(
            DesignerDocument(
                id = "new-form",
                version = 1,
                name = "Yeni Optik Form"
            )
        )

        val component = DesignerAreaCatalog.createNumberArea(document)

        assertEquals("number-1", component.id)
        assertEquals(6, component.digits)
        assertEquals(NumericGridOrientation.DIGITS_HORIZONTAL, component.orientation)
        assertEquals("Numara", component.label)
        assertTrue(component.showLabel)
        assertNull(DesignerAreaCatalog.numberAreaIssue(document, component))
    }
}
