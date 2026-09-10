package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.designer.DesignerAreaCatalog
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import org.junit.Assert.assertEquals
import org.junit.Test

class ExamCombinedIdentityAreaTest {
    @Test
    fun `combined identity area prints name number and class on one line`() {
        var document = DesignerDocument(id = "combined-identity", version = 1, name = "Kişisel Form")
        val identity = DesignerAreaCatalog.createStudentIdentityLineArea(document)
        document = document.copy(visualElements = listOf(identity))

        val exam = ExamFactory.create(
            name = "LGS Deneme",
            schoolName = "Koruk Ortaokulu",
            templateSelection = ActiveTemplateSelection(
                ActiveTemplateSource.DESIGNER_DOCUMENT,
                document.id,
                document.version
            ),
            examDateEpochDay = 21000L,
            participants = listOf(ExamParticipant("123", "Ali İmran Karagöz", "8/A")),
            personalizedFormsEnabled = true,
            createdAtEpochMs = 1L
        )

        val page = ExamPersonalizedForms.pages(exam, document).single()

        assertEquals(
            "AD SOYAD: Ali İmran Karagöz  •  NUMARA: 123  •  SINIF: 8/A",
            page.textOverrides[identity.id]
        )
    }
}
