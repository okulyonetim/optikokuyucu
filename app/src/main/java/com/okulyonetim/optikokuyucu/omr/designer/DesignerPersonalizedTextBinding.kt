package com.okulyonetim.optikokuyucu.omr.designer

enum class DesignerPersonalizedField(
    val idPrefix: String,
    val displayName: String,
    val defaultLabel: String,
    val legacyLabels: Set<String>
) {
    STUDENT_NAME(
        idPrefix = "student-name-",
        displayName = "Ad Soyad",
        defaultLabel = "Ad Soyad:",
        legacyLabels = setOf("Öğrenci Adı Soyadı", "Ad Soyad")
    ),
    STUDENT_CLASS(
        idPrefix = "student-class-",
        displayName = "Sınıf",
        defaultLabel = "Sınıf:",
        legacyLabels = setOf("Sınıfı", "Sınıf")
    ),
    STUDENT_NUMBER(
        idPrefix = "student-number-text-",
        displayName = "Öğrenci No",
        defaultLabel = "Öğrenci No:",
        legacyLabels = setOf("Öğrenci Numarası", "Öğrenci No")
    ),
    EXAM_NAME(
        idPrefix = "exam-name-",
        displayName = "Sınav",
        defaultLabel = "Sınav:",
        legacyLabels = setOf("Sınav Adı", "Sınav")
    ),
    SCHOOL_NAME(
        idPrefix = "school-name-",
        displayName = "Okul",
        defaultLabel = "Okul:",
        legacyLabels = setOf("Okul Adı", "Okul")
    )
}

/** Stable semantic binding for text fields that receive exam/student values during personalization. */
object DesignerPersonalizedTextBinding {
    fun fieldForId(elementId: String): DesignerPersonalizedField? =
        DesignerPersonalizedField.entries.firstOrNull { elementId.startsWith(it.idPrefix) }

    fun isBound(elementId: String): Boolean = fieldForId(elementId) != null

    fun renderedLabel(element: DesignerTextElement): String {
        val field = requireNotNull(fieldForId(element.id)) { "Bu metin kişiselleştirilmiş bir alana bağlı değil." }
        val raw = element.text.trim()
        val normalized = if (raw in field.legacyLabels) field.defaultLabel else raw
        return if (normalized.endsWith(':')) normalized else "$normalized:"
    }

    fun render(element: DesignerTextElement, value: String): String =
        if (element.showPersonalizedLabel) "${renderedLabel(element)} $value" else value
}
