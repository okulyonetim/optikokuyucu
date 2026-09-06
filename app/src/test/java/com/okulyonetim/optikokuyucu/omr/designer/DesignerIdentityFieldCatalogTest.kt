package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerIdentityFieldCatalogTest {
    @Test
    fun `student and exam information fields are exposed in add menu`() {
        val section = DesignerAreaCatalog.sections.first { it.title == "Öğrenci / Sınav Bilgileri" }

        assertEquals(
            listOf(
                DesignerAreaKind.STUDENT_NAME,
                DesignerAreaKind.STUDENT_CLASS,
                DesignerAreaKind.STUDENT_NUMBER_TEXT,
                DesignerAreaKind.EXAM_NAME,
                DesignerAreaKind.SCHOOL_NAME
            ),
            section.kinds
        )
        assertEquals(
            listOf("Öğrenci Adı Soyadı", "Sınıfı", "Öğrenci Numarası", "Sınav Adı", "Okul Adı"),
            section.kinds.map { it.displayName }
        )
    }

    @Test
    fun `preset information fields use existing editable text model`() {
        var document = DesignerPageGeometry.apply(
            DesignerDocument(id = "identity-test", version = 1, name = "Kimlik Alanı Testi")
        )

        val fields = listOf(
            DesignerAreaCatalog.createStudentNameArea(document),
            DesignerAreaCatalog.createStudentClassArea(document),
            DesignerAreaCatalog.createStudentNumberTextArea(document),
            DesignerAreaCatalog.createExamNameArea(document),
            DesignerAreaCatalog.createSchoolNameArea(document)
        )

        val expectedPrefixes = listOf(
            "student-name-",
            "student-class-",
            "student-number-text-",
            "exam-name-",
            "school-name-"
        )
        val expectedText = listOf(
            "Öğrenci Adı Soyadı",
            "Sınıfı",
            "Öğrenci Numarası",
            "Sınav Adı",
            "Okul Adı"
        )

        fields.forEachIndexed { index, field ->
            assertTrue(field.id.startsWith(expectedPrefixes[index]))
            assertEquals(expectedText[index], field.text)
            assertEquals(18.0, field.fontSize, 0.0)
            assertEquals(DesignerTextAlignment.START, field.alignment)
            assertFalse(field.bold)
            assertFalse(field.locked)
            assertNull(DesignerAreaCatalog.descriptionAreaIssue(document, field))
            document = document.copy(visualElements = document.visualElements + field)
        }
    }
}
