package com.okulyonetim.optikokuyucu.omr.designer

/** Metadata and rendering rules for text areas whose value can be filled from an exam/student. */
data class DesignerDynamicTextField(
    val displayName: String,
    val defaultLabel: String
)

object DesignerDynamicText {
    fun fieldForId(id: String): DesignerDynamicTextField? = when {
        id.startsWith("student-name-") -> DesignerDynamicTextField("Öğrenci Adı Soyadı", "Ad Soyad:")
        id.startsWith("student-class-") -> DesignerDynamicTextField("Sınıfı", "Sınıf:")
        id.startsWith("student-number-text-") -> DesignerDynamicTextField("Öğrenci Numarası", "No:")
        id.startsWith("exam-name-") -> DesignerDynamicTextField("Sınav Adı", "Sınav:")
        id.startsWith("school-name-") -> DesignerDynamicTextField("Okul Adı", "Okul:")
        else -> null
    }

    fun defaultLabel(id: String): String = fieldForId(id)?.defaultLabel.orEmpty()

    fun render(element: DesignerTextElement, valueOverride: String? = null): String {
        val value = valueOverride ?: element.text
        if (fieldForId(element.id) == null || !element.showLabel) return value
        val label = element.label.trim()
        return if (label.isBlank()) value else "$label $value"
    }
}
