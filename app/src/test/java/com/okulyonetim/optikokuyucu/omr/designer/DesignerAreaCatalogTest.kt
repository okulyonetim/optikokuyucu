package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerAreaCatalogTest {
    @Test
    fun `add area catalog exposes number answers booklet and approved information fields`() {
        assertEquals(listOf("İşaretleme Alanı", "Bilgilendirme Alanı"), DesignerAreaCatalog.sections.map { it.title })
        assertEquals(
            listOf(DesignerAreaKind.NUMBER, DesignerAreaKind.ANSWERS, DesignerAreaKind.BOOKLET),
            DesignerAreaCatalog.sections[0].kinds
        )
        assertEquals(listOf(DesignerAreaKind.DESCRIPTION, DesignerAreaKind.IMAGE), DesignerAreaCatalog.sections[1].kinds)
        assertEquals(DesignerAreaKind.entries.toSet(), DesignerAreaCatalog.allKinds.toSet())
    }

    @Test
    fun `student photo is never offered`() {
        val labels = DesignerAreaCatalog.allKinds.map { it.displayName }
        assertFalse(labels.any { it.contains("Öğrenci Fotoğraf", ignoreCase = true) })
        assertTrue(labels.containsAll(listOf("Numara", "Cevaplar", "Kitapçık Türü", "Açıklama", "Resim")))
    }

    @Test
    fun `new marking fields share one bubble radius`() {
        val document = DesignerPageGeometry.apply(DesignerDocument("same-bubbles", 1, "Form"))
        val number = DesignerAreaCatalog.createNumberArea(document)
        val answer = DesignerAreaCatalog.createAnswerArea(document)
        val booklet = DesignerAreaCatalog.createBookletArea(document)
        assertEquals(DesignerEditorLayout.STANDARD_BUBBLE_RADIUS, number.bubbleRadius, 0.0)
        assertEquals(number.bubbleRadius, answer.bubbleRadius, 0.0)
        assertEquals(number.bubbleRadius, booklet.bubbleRadius, 0.0)
        assertNull(DesignerAreaCatalog.numberAreaIssue(document, number))
        assertNull(DesignerAreaCatalog.answerAreaIssue(document, answer))
        assertNull(DesignerAreaCatalog.bookletAreaIssue(document, booklet))
    }

    @Test
    fun `six compact lesson fields fit side by side on portrait A4`() {
        var document = DesignerPageGeometry.apply(
            DesignerDocument(
                id = "six-lessons",
                version = 1,
                name = "A4",
                formSpec = DesignerFormSpec(
                    paperSize = DesignerPaperSize.A4,
                    orientation = DesignerPageOrientation.PORTRAIT,
                    examMode = DesignerExamMode.MULTI_LESSON
                )
            )
        )
        val bounds = mutableListOf<com.okulyonetim.optikokuyucu.omr.template.TemplateRect>()
        repeat(6) {
            val answer = DesignerAreaCatalog.createAnswerArea(document)
            assertEquals(1, answer.columns)
            assertEquals(20, answer.questionCount)
            assertEquals(DesignerEditorLayout.STANDARD_BUBBLE_RADIUS, answer.bubbleRadius, 0.0)
            assertNull(DesignerAreaCatalog.answerAreaIssue(document, answer))
            bounds += DesignerComponentGeometry.bounds(answer)
            document = document.copy(components = document.components + answer)
        }
        for (index in 1 until bounds.size) {
            assertTrue("lesson fields must progress left to right", bounds[index].left > bounds[index - 1].right)
        }
    }

    @Test
    fun `number header boxes follow numeric orientation`() {
        val document = DesignerPageGeometry.apply(DesignerDocument("number-boxes", 1, "Form"))
        val horizontal = DesignerAreaCatalog.createNumberArea(document)
        val hBoxes = DesignerEditorLayout.numericHeaderBoxes(horizontal)
        assertEquals(horizontal.digits, hBoxes.size)
        assertTrue(hBoxes.zipWithNext().all { (a, b) -> b.left > a.left && kotlin.math.abs(b.top - a.top) < 0.001 })

        val vertical = horizontal.copy(orientation = NumericGridOrientation.DIGITS_VERTICAL)
        val vBoxes = DesignerEditorLayout.numericHeaderBoxes(vertical)
        assertTrue(vBoxes.zipWithNext().all { (a, b) -> b.top > a.top && kotlin.math.abs(b.left - a.left) < 0.001 })
    }

    @Test
    fun `patterns keep presets and custom token support`() {
        assertEquals(listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"), DesignerAreaCatalog.parseNumberPattern("0123456789"))
        assertEquals(listOf("01", "02", "03"), DesignerAreaCatalog.parseNumberPattern("01,02,03"))
        assertEquals(listOf("A", "B", "C", "D"), DesignerAreaCatalog.parseAnswerPattern("ABCD"))
        assertEquals(listOf("A", "B"), DesignerAreaCatalog.parseBookletPattern("AB"))
        assertNull(DesignerAreaCatalog.parseAnswerPattern("A"))
    }
}
