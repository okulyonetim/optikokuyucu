package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExamTemplateBindingStage12Test {
    private fun exam(version: Int = 1): Exam = Exam(
        id = "exam-stage12",
        name = "Deneme",
        schoolName = "Okul",
        templateSelection = ActiveTemplateSelection(
            source = ActiveTemplateSource.DESIGNER_DOCUMENT,
            templateId = "form-stage12",
            templateVersion = version
        ),
        examDateEpochDay = 1L,
        createdAtEpochMs = 1L
    )

    @Test
    fun `metadata and paper updates keep original template binding`() {
        val original = exam()
        val updated = original.copy(
            name = "Güncellenmiş Deneme",
            papers = listOf(ExamPaperLink("scan-1", linkedAtEpochMs = 2L))
        )

        ExamTemplateBindingPolicy.validateUpdate(original, updated)

        assertEquals(original.templateSelection, updated.templateSelection)
    }

    @Test
    fun `changing template version on existing exam is rejected`() {
        val original = exam(version = 1)
        val changed = original.copy(
            templateSelection = original.templateSelection.copy(templateVersion = 2)
        )

        assertThrows(IllegalArgumentException::class.java) {
            ExamTemplateBindingPolicy.validateUpdate(original, changed)
        }
    }
}
