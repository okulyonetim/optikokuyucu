package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerAreaCatalogTest {
    @Test
    fun `add area catalog matches approved choices without student photo`() {
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
        assertNull(DesignerAreaCatalog.parseNumberPattern("A"))
        assertNull(DesignerAreaCatalog.parseNumberPattern("AAB"))
    }

    @Test
    fun `answer patterns and default two block answer field match stage six contract`() {
        val document = pageDocument("answer-form")
        val component = DesignerAreaCatalog.createAnswerArea(document)

        assertEquals("answers-1", component.id)
        assertEquals("answers-1", component.questionIdPrefix)
        assertEquals(20, component.questionCount)
        assertEquals(2, component.columns)
        assertEquals(10, DesignerAreaCatalog.answerQuestionsPerBlock(component))
        assertEquals(listOf("A", "B", "C", "D"), component.choices)
        assertEquals(QuestionGroupOrientation.VERTICAL, component.orientation)
        assertNull(DesignerAreaCatalog.answerAreaIssue(document, component))
    }

    @Test
    fun `default number area is valid canonical geometry inside safe page`() {
        val document = pageDocument("number-form")
        val component = DesignerAreaCatalog.createNumberArea(document)

        assertEquals("number-1", component.id)
        assertEquals(6, component.digits)
        assertEquals(NumericGridOrientation.DIGITS_HORIZONTAL, component.orientation)
        assertNull(DesignerAreaCatalog.numberAreaIssue(document, component))
    }

    @Test
    fun `stage seven description and image defaults are valid canonical visual elements`() {
        val document = pageDocument("info-form")
        val description = DesignerAreaCatalog.createDescriptionArea(document)
        val imageData = DesignerImageData(
            mimeType = "image/jpeg",
            pixelWidth = 400,
            pixelHeight = 200,
            bytes = byteArrayOf(1, 2, 3)
        )
        val image = DesignerAreaCatalog.createImageArea(
            document.copy(visualElements = listOf(description)),
            imageData
        )

        assertEquals("description-1", description.id)
        assertEquals("Açıklama", description.text)
        assertNull(DesignerAreaCatalog.descriptionAreaIssue(document, description))
        assertEquals("image-1", image.id)
        assertEquals(2.0, image.bounds.width / image.bounds.height, 0.001)
        assertNull(
            DesignerAreaCatalog.imageAreaIssue(
                document.copy(visualElements = listOf(description)),
                image
            )
        )
    }

    private fun pageDocument(id: String): DesignerDocument = DesignerPageGeometry.apply(
        DesignerDocument(id = id, version = 1, name = "Yeni Optik Form")
    )
}
