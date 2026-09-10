package com.okulyonetim.optikokuyucu.ui

import com.okulyonetim.optikokuyucu.student.StudentSchoolIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExamSchoolSelectionTest {
    @Test
    fun `grades one to four resolve to primary school`() {
        assertEquals(
            StudentSchoolIdentity.PRIMARY_SCHOOL_NAME,
            ExamSchoolSelection.schoolForGrades(listOf(1, 2, 4))
        )
    }

    @Test
    fun `grades five to eight resolve to middle school`() {
        assertEquals(
            StudentSchoolIdentity.MIDDLE_SCHOOL_NAME,
            ExamSchoolSelection.schoolForGrades(listOf(5, 7, 8))
        )
    }

    @Test
    fun `mixed primary and middle grades do not override manual school`() {
        assertNull(ExamSchoolSelection.schoolForGrades(listOf(4, 5)))
    }

    @Test
    fun `configured Koruk school name is canonicalized and not duplicated`() {
        val options = ExamSchoolSelection.options("KORUK ORTAOKULU")

        assertEquals(
            listOf(
                StudentSchoolIdentity.MIDDLE_SCHOOL_NAME,
                StudentSchoolIdentity.PRIMARY_SCHOOL_NAME
            ),
            options
        )
    }
}
