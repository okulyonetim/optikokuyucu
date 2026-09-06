package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.designer.DesignerAreaCatalog
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamPersonalizedFormsTest {
    @Test
    fun createsOnePersonalizedPagePerParticipantAndPrefillsNumber() {
        var document = DesignerDocument(id = "personalized", version = 1, name = "Kişisel Form")
        val name = DesignerAreaCatalog.createStudentNameArea(document)
        document = document.copy(visualElements = document.visualElements + name)
        val clazz = DesignerAreaCatalog.createStudentClassArea(document)
        document = document.copy(visualElements = document.visualElements + clazz)
        val numberText = DesignerAreaCatalog.createStudentNumberTextArea(document)
        document = document.copy(visualElements = document.visualElements + numberText)
        val examName = DesignerAreaCatalog.createExamNameArea(document)
        document = document.copy(visualElements = document.visualElements + examName)
        val schoolName = DesignerAreaCatalog.createSchoolNameArea(document)
        document = document.copy(visualElements = document.visualElements + schoolName)
        val numberGrid = DesignerAreaCatalog.createNumberArea(document)
        document = document.copy(components = document.components + numberGrid)

        val exam = ExamFactory.create(
            name = "Deneme 1",
            schoolName = "Örnek Ortaokulu",
            templateSelection = ActiveTemplateSelection(
                ActiveTemplateSource.DESIGNER_DOCUMENT,
                document.id,
                document.version
            ),
            examDateEpochDay = 21000L,
            participants = listOf(
                ExamParticipant("16", "Örnek Öğrenci", "8-A"),
                ExamParticipant("235", "İkinci Öğrenci", "8-B")
            ),
            personalizedFormsEnabled = true,
            createdAtEpochMs = 1L
        )

        val pages = ExamPersonalizedForms.pages(exam, document)
        assertEquals(2, pages.size)
        assertEquals("Örnek Öğrenci", pages[0].textOverrides[name.id])
        assertEquals("8-A", pages[0].textOverrides[clazz.id])
        assertEquals("16", pages[0].textOverrides[numberText.id])
        assertEquals("Deneme 1", pages[0].textOverrides[examName.id])
        assertEquals("Örnek Ortaokulu", pages[0].textOverrides[schoolName.id])
        assertEquals("16".padStart(numberGrid.digits, '0'), pages[0].numericHeaderValues[numberGrid.id])
        assertEquals(numberGrid.digits, pages[0].filledMarks.size)
        assertTrue(pages[0].filledMarks.any { it.columnId == numberGrid.digits.toString() && it.markId == "6" })
    }
}
