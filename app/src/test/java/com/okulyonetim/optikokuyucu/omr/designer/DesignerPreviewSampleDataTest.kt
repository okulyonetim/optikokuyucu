package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerPreviewSampleDataTest {
    @Test
    fun `sample identity uses requested realistic values`() {
        assertEquals("ALİ İMRAN KARAGÖZ", DesignerPreviewSampleData.STUDENT_NAME)
        assertEquals("123", DesignerPreviewSampleData.STUDENT_NUMBER)
        assertEquals("8/A", DesignerPreviewSampleData.STUDENT_CLASS)
        assertEquals("KORUK ORTAOKULU", DesignerPreviewSampleData.SCHOOL_NAME)
        assertEquals("LGS DENEME SINAVI", DesignerPreviewSampleData.EXAM_NAME)
    }

    @Test
    fun `student number header and bubbles use the same normalized digits`() {
        val numberGrid = NumericGridComponent(
            id = "number-student",
            digits = 6,
            startX = 100.0,
            topY = 100.0,
            bubbleRadius = 5.0,
            columnGap = 20.0,
            rowGap = 20.0,
            label = "Öğrenci Numarası"
        )
        val unrelatedGrid = NumericGridComponent(
            id = "other-grid",
            digits = 4,
            startX = 300.0,
            topY = 100.0,
            bubbleRadius = 5.0,
            columnGap = 20.0,
            rowGap = 20.0,
            label = "Kod"
        )
        val document = DesignerDocument(
            id = "preview-test",
            version = 1,
            name = "Preview Test",
            components = listOf(numberGrid, unrelatedGrid)
        )

        val headers = DesignerPreviewSampleData.numericHeaderValues(document)
        val marks = DesignerPreviewSampleData.markedGridChoices(document)

        assertEquals(mapOf("number-student" to "000123"), headers)
        assertEquals(setOf("0"), marks.getValue("number-student").getValue("1"))
        assertEquals(setOf("0"), marks.getValue("number-student").getValue("2"))
        assertEquals(setOf("0"), marks.getValue("number-student").getValue("3"))
        assertEquals(setOf("1"), marks.getValue("number-student").getValue("4"))
        assertEquals(setOf("2"), marks.getValue("number-student").getValue("5"))
        assertEquals(setOf("3"), marks.getValue("number-student").getValue("6"))
        assertFalse(headers.containsKey("other-grid"))
        assertFalse(marks.containsKey("other-grid"))
        assertTrue(marks.getValue("number-student").size == numberGrid.digits)
    }
}
