package com.okulyonetim.optikokuyucu.school

import com.okulyonetim.optikokuyucu.student.StudentSchoolIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

class SchoolInstitutionMapperTest {
    @Test
    fun `institution mapper reads actual okul yonetim school fields`() {
        val info = SchoolInstitutionMapper.from(
            FirestoreDocument(
                id = "ayarlar",
                fields = mapOf(
                    "okulAdi" to " KORUK İLKOKULU-ORTAOKULU ",
                    "ilkokulAdi" to "KORUK İLKOKULU",
                    "ortaokulAdi" to "KORUK ORTAOKULU"
                )
            )
        )

        assertEquals("KORUK İLKOKULU-ORTAOKULU", info.schoolName)
        assertEquals("KORUK İLKOKULU", info.primarySchoolName)
        assertEquals("KORUK ORTAOKULU", info.middleSchoolName)
        assertEquals("KORUK İLKOKULU-ORTAOKULU", info.preferredSchoolName())
    }

    @Test
    fun `preferred school falls back through trusted institution fields`() {
        val middleOnly = SchoolInstitutionInfo("", "", "KORUK ORTAOKULU")
        assertEquals("KORUK ORTAOKULU", middleOnly.preferredSchoolName())

        val empty = SchoolInstitutionInfo("", "", "")
        assertEquals(StudentSchoolIdentity.MIDDLE_SCHOOL_NAME, empty.preferredSchoolName())
    }
}
