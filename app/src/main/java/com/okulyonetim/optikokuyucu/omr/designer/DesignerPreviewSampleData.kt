package com.okulyonetim.optikokuyucu.omr.designer

import java.util.Locale

/** Example identity values used only in designer preview so the user sees the actual personalized print result. */
object DesignerPreviewSampleData {
    const val STUDENT_NAME = "ALİ İMRAN KARAGÖZ"
    const val STUDENT_CLASS = "8/A"
    const val STUDENT_NUMBER = "123"
    const val SCHOOL_NAME = "KORUK ORTAOKULU"
    const val EXAM_NAME = "LGS DENEME SINAVI"

    fun document(document: DesignerDocument): DesignerDocument = document.copy(
        visualElements = document.visualElements.map { element ->
            if (element !is DesignerTextElement) return@map element
            val field = DesignerPersonalizedTextBinding.fieldForId(element.id) ?: return@map element
            val value = valueFor(field)
            element.copy(text = DesignerPersonalizedTextBinding.render(element, value))
        }
    )

    fun numericHeaderValues(document: DesignerDocument): Map<String, String> {
        val grid = studentNumberGrid(document) ?: return emptyMap()
        val normalized = normalizedStudentNumber(grid) ?: return emptyMap()
        return mapOf(grid.id to normalized)
    }

    fun markedGridChoices(document: DesignerDocument): Map<String, Map<String, Set<String>>> {
        val grid = studentNumberGrid(document) ?: return emptyMap()
        val normalized = normalizedStudentNumber(grid) ?: return emptyMap()
        val marks = normalized.mapIndexedNotNull { index, digit ->
            val value = digit.toString()
            if (value !in grid.values) return@mapIndexedNotNull null
            (index + 1).toString() to setOf(value)
        }.toMap()
        return if (marks.isEmpty()) emptyMap() else mapOf(grid.id to marks)
    }

    fun valueFor(field: DesignerPersonalizedField): String = when (field) {
        DesignerPersonalizedField.STUDENT_NAME -> STUDENT_NAME
        DesignerPersonalizedField.STUDENT_CLASS -> STUDENT_CLASS
        DesignerPersonalizedField.STUDENT_NUMBER -> STUDENT_NUMBER
        DesignerPersonalizedField.STUDENT_IDENTITY_LINE ->
            DesignerPersonalizedTextBinding.studentIdentityLine(STUDENT_NAME, STUDENT_NUMBER, STUDENT_CLASS)
        DesignerPersonalizedField.EXAM_NAME -> EXAM_NAME
        DesignerPersonalizedField.SCHOOL_NAME -> SCHOOL_NAME
    }

    private fun studentNumberGrid(document: DesignerDocument): NumericGridComponent? =
        document.components
            .filterIsInstance<NumericGridComponent>()
            .firstOrNull { component ->
                val normalizedLabel = component.label.lowercase(Locale("tr", "TR"))
                component.id.startsWith("number-") ||
                    (normalizedLabel.contains("öğrenci") &&
                        (normalizedLabel.contains("no") || normalizedLabel.contains("numara")))
            }

    private fun normalizedStudentNumber(grid: NumericGridComponent): String? {
        val digits = STUDENT_NUMBER.filter(Char::isDigit)
        if (digits.isBlank()) return null
        return digits.takeLast(grid.digits).padStart(grid.digits, '0')
    }
}
