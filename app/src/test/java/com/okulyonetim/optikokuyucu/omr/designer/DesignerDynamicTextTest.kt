package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerDynamicTextTest {
    @Test
    fun `student name label is rendered before personalized value when enabled`() {
        val element = DesignerTextElement(
            id = "student-name-1",
            bounds = TemplateRect(100.0, 100.0, 400.0, 60.0),
            text = "Öğrenci Adı Soyadı",
            fontSize = 18.0,
            label = "Ad Soyad:",
            showLabel = true
        )

        assertEquals("Ad Soyad: Örnek Öğrenci", DesignerDynamicText.render(element, "Örnek Öğrenci"))
        assertEquals("Örnek Öğrenci", DesignerDynamicText.render(element.copy(showLabel = false), "Örnek Öğrenci"))
    }

    @Test
    fun `designer codec preserves editable label settings`() {
        val element = DesignerTextElement(
            id = "school-name-1",
            bounds = TemplateRect(100.0, 100.0, 500.0, 60.0),
            text = "Okul Adı",
            fontSize = 18.0,
            label = "Kurum:",
            showLabel = true
        )
        val document = DesignerDocument(
            id = "label-form",
            version = 1,
            name = "Etiketli Form",
            visualElements = listOf(element)
        )

        val decoded = DesignerDocumentCodec.decode(DesignerDocumentCodec.encode(document))
        val decodedElement = decoded.visualElements.single() as DesignerTextElement

        assertEquals("Kurum:", decodedElement.label)
        assertTrue(decodedElement.showLabel)
        assertFalse(DesignerDynamicText.defaultLabel("ordinary-text").isNotEmpty())
    }
}
