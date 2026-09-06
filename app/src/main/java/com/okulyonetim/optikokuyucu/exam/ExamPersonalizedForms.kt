package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerFilledMark
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPdfPageData
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPersonalizedField
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPersonalizedTextBinding
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextElement
import com.okulyonetim.optikokuyucu.omr.designer.NumericGridComponent
import com.okulyonetim.optikokuyucu.omr.designer.SingleChoiceComponent
import com.okulyonetim.optikokuyucu.omr.template.OmrRecognitionBindingsResolver

object ExamPersonalizedForms {
    fun pages(exam: Exam, document: DesignerDocument): List<DesignerPdfPageData> {
        require(exam.personalizedFormsEnabled) { "Bu sınavda öğrenciye özel form etkin değil." }
        require(exam.participants.isNotEmpty()) { "Sınavda seçili öğrenci yok." }
        val bookletPlan = bookletPlan(exam, document)

        return exam.participants.mapIndexed { index, participant ->
            DesignerPdfPageData(
                textOverrides = textOverrides(exam, participant, document),
                filledMarks = studentNumberMarks(participant, document) + bookletMarks(index, bookletPlan),
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

    private fun bookletPlan(exam: Exam, document: DesignerDocument): BookletPlan? {
        val template = DesignerTemplateCompiler.compile(document)
        val bookletGridId = OmrRecognitionBindingsResolver.fromTemplate(template).bookletGridId
        if (bookletGridId == null) {
            require(exam.bookletCount == 1) {
                "Sınav ${exam.bookletCount} kitapçık kullanıyor ancak seçili optik formda kitapçık alanı yok."
            }
            return null
        }

        val grid = requireNotNull(
            document.components.filterIsInstance<SingleChoiceComponent>()
                .firstOrNull { it.id == bookletGridId }
        ) { "Optik formdaki kitapçık alanı çözümlenemedi." }
        require(grid.choices.size >= exam.bookletCount) {
            "Sınav ${exam.bookletCount} kitapçık kullanıyor ancak optik form yalnız ${grid.choices.size} kitapçık seçeneği içeriyor."
        }
        return BookletPlan(grid = grid, choices = grid.choices.take(exam.bookletCount))
    }

    private fun bookletMarks(index: Int, plan: BookletPlan?): Set<DesignerFilledMark> {
        if (plan == null) return emptySet()
        val choice = plan.choices[index % plan.choices.size]
        return setOf(
            DesignerFilledMark(
                gridId = plan.grid.id,
                columnId = "value",
                markId = choice
            )
        )
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

    private data class BookletPlan(
        val grid: SingleChoiceComponent,
        val choices: List<String>
    )
}
