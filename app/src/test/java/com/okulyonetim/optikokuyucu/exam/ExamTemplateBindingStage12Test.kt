package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExamTemplateBindingStage12Test {
    private fun exam(version: Int = 1): Exam = Exam(
        id = "exam-stage12",
        name = "Sentetik Deneme",
        schoolName = "Sentetik Okul",
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
            name = "Güncellenmiş Sentetik Deneme",
            papers = listOf(ExamPaperLink("scan-1", linkedAtEpochMs = 2L))
        )

        ExamTemplateBindingPolicy.validateUpdate(original, updated)

        assertEquals(original.templateSelection, updated.templateSelection)
    }

    @Test
    fun `template can change before any paper is linked`() {
        val original = exam(version = 1)
        val changed = original.copy(
            templateSelection = original.templateSelection.copy(templateVersion = 2)
        )

        ExamTemplateBindingPolicy.validateUpdate(original, changed)

        assertEquals(2, changed.templateSelection.templateVersion)
    }

    @Test
    fun `changing template after a paper is linked is rejected`() {
        val original = exam(version = 1).copy(
            papers = listOf(ExamPaperLink("scan-sentetik", linkedAtEpochMs = 2L))
        )
        val changed = original.copy(
            templateSelection = original.templateSelection.copy(templateVersion = 2)
        )

        assertThrows(IllegalArgumentException::class.java) {
            ExamTemplateBindingPolicy.validateUpdate(original, changed)
        }
    }
}
