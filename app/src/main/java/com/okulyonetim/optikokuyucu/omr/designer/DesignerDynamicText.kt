package com.okulyonetim.optikokuyucu.omr.designer

/**
 * Semantic rules for the text areas that are replaced with exam/student values while creating
 * personalized forms. The existing DesignerTextElement stays the canonical persisted model: its
 * text is the editor preview text and, when changed from the canonical placeholder, also acts as
 * an optional printable label.
 */
object DesignerDynamicText {
    data class Descriptor(
        val prefix: String,
        val placeholder: String,
        val suggestedLabel: String
    )

    private val descriptors = listOf(
        Descriptor("student-name-", "Öğrenci Adı Soyadı", "Ad Soyad"),
        Descriptor("student-class-", "Sınıfı", "Sınıf"),
        Descriptor("student-number-text-", "Öğrenci Numarası", "Öğrenci No"),
        Descriptor("exam-name-", "Sınav Adı", "Sınav"),
        Descriptor("school-name-", "Okul Adı", "Okul")
    )

    fun descriptor(elementId: String): Descriptor? =
        descriptors.firstOrNull { elementId.startsWith(it.prefix) }

    fun isDynamic(elementId: String): Boolean = descriptor(elementId) != null

    fun labelEnabled(element: DesignerTextElement): Boolean {
        val descriptor = descriptor(element.id) ?: return false
        return element.text.trim() != descriptor.placeholder
    }

    fun withLabelEnabled(element: DesignerTextElement, enabled: Boolean): DesignerTextElement {
        val descriptor = descriptor(element.id) ?: return element
        return element.copy(
            text = if (enabled) descriptor.suggestedLabel else descriptor.placeholder
        )
    }

    fun renderPersonalized(element: DesignerTextElement, value: String): String {
        val descriptor = descriptor(element.id) ?: return value
        val rawLabel = element.text.trim()
        if (rawLabel == descriptor.placeholder) return value
        val label = rawLabel.trimEnd().trimEnd(':').trimEnd()
        return if (label.isBlank()) value else "$label: $value"
    }
}
