package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerFilledMark
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPdfPageData
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextElement
import com.okulyonetim.optikokuyucu.omr.designer.NumericGridComponent

object ExamPersonalizedForms {
    fun pages(exam: Exam, document: DesignerDocument): List<DesignerPdfPageData> {
        require(exam.personalizedFormsEnabled) { "Bu sınavda öğrenciye özel form etkin değil." }
        require(exam.participants.isNotEmpty()) { "Sınavda seçili öğrenci yok." }

        return exam.participants.map { participant ->
            DesignerPdfPageData(
                textOverrides = textOverrides(exam, participant, document),
                filledMarks = studentNumberMarks(participant, document)
            )
        }
    }

    private fun textOverrides(
        exam: Exam,
        participant: ExamParticipant,
        document: DesignerDocument
    ): Map<String, String> = buildMap {
        document.visualElements.filterIsInstance<DesignerTextElement>().forEach { element ->
            val value = when {
                element.id.startsWith("student-name-") -> participant.studentName
                element.id.startsWith("student-class-") -> participant.className
                element.id.startsWith("student-number-text-") -> participant.studentNumber
                element.id.startsWith("exam-name-") -> exam.name
                element.id.startsWith("school-name-") -> exam.schoolName
                else -> null
            }
            if (value != null) put(element.id, value)
        }
    }

    private fun studentNumberMarks(
        participant: ExamParticipant,
        document: DesignerDocument
    ): Set<DesignerFilledMark> {
        val grid = document.components
            .filterIsInstance<NumericGridComponent>()
            .firstOrNull { component ->
                component.id.startsWith("number-") ||
                    component.label.lowercase().let { label ->
                        "öğrenci" in label && ("no" in label || "numara" in label)
                    }
            }
            ?: return emptySet()

        val digits = participant.studentNumber.filter(Char::isDigit)
        if (digits.isBlank()) return emptySet()
        val normalized = digits.takeLast(grid.digits).padStart(grid.digits, '0')

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
}
