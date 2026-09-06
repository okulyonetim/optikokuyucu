package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerFilledMark
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPdfPageData
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPersonalizedField
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPersonalizedTextBinding
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextElement
import com.okulyonetim.optikokuyucu.omr.designer.NumericGridComponent

object ExamPersonalizedForms {
    fun pages(exam: Exam, document: DesignerDocument): List<DesignerPdfPageData> {
        require(exam.personalizedFormsEnabled) { "Bu sınavda öğrenciye özel form etkin değil." }
        require(exam.participants.isNotEmpty()) { "Sınavda seçili öğrenci yok." }

        return exam.participants.map { participant ->
            DesignerPdfPageData(
                textOverrides = textOverrides(exam, participant, document),
                filledMarks = studentNumberMarks(participant, document),
                numericHeaderValues = studentNumberHeaderValues(participant, document)
            )
        }
    }

    private fun textOverrides(
        exam: Exam,
        participant: ExamParticipant,
        document: DesignerDocument
    ): Map<String, String> = buildMap {
        document.visualElements.filterIsInstance<DesignerTextElement>().forEach { element ->
            val field = DesignerPersonalizedTextBinding.fieldForId(element.id) ?: return@forEach
            val value = when (field) {
                DesignerPersonalizedField.STUDENT_NAME -> participant.studentName
                DesignerPersonalizedField.STUDENT_CLASS -> participant.className
                DesignerPersonalizedField.STUDENT_NUMBER -> participant.studentNumber
                DesignerPersonalizedField.EXAM_NAME -> exam.name
                DesignerPersonalizedField.SCHOOL_NAME -> exam.schoolName
            }
            put(element.id, DesignerPersonalizedTextBinding.render(element, value))
        }
    }

    private fun studentNumberMarks(
        participant: ExamParticipant,
        document: DesignerDocument
    ): Set<DesignerFilledMark> {
        val grid = studentNumberGrid(document) ?: return emptySet()
        val normalized = normalizedStudentNumber(participant, grid) ?: return emptySet()

        return normalized.mapIndexedNotNull { index, digit ->
            val value = digit.toString()
            if (value !in grid.values) return@mapIndexedNotNull null
            DesignerFilledMark(
                gridId = grid.id,
                columnId = (index + 1).toString(),
                markId = value
            )
        }.toSet()
    }

    private fun studentNumberHeaderValues(
        participant: ExamParticipant,
        document: DesignerDocument
    ): Map<String, String> {
        val grid = studentNumberGrid(document) ?: return emptyMap()
        val normalized = normalizedStudentNumber(participant, grid) ?: return emptyMap()
        return mapOf(grid.id to normalized)
    }

    private fun studentNumberGrid(document: DesignerDocument): NumericGridComponent? =
        document.components
            .filterIsInstance<NumericGridComponent>()
            .firstOrNull { component ->
                component.id.startsWith("number-") ||
                    component.label.lowercase().let { label ->
                        "öğrenci" in label && ("no" in label || "numara" in label)
                    }
            }

    private fun normalizedStudentNumber(
        participant: ExamParticipant,
        grid: NumericGridComponent
    ): String? {
        val digits = participant.studentNumber.filter(Char::isDigit)
        if (digits.isBlank()) return null
        return digits.takeLast(grid.digits).padStart(grid.digits, '0')
    }
}
