package com.okulyonetim.optikokuyucu.school

import com.okulyonetim.optikokuyucu.student.StudentSchoolIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolStudentInstitutionIdentityTest {
    @Test
    fun `directory mapper assigns primary and middle school from class grade`() {
        val classes = mapOf(
            "3a" to SchoolClassIdentity("3a", "3-A", 3, "A"),
            "6a" to SchoolClassIdentity("6a", "6-A", 6, "A")
        )
        val primary = requireNotNull(
            SchoolDirectoryMapper.student(
                FirestoreDocument(
                    id = "primary-3",
                    fields = mapOf(
                        "ogrenciNo" to "3",
                        "ogrenciAdi" to "İlkokul Öğrencisi",
                        "sinifId" to "3a"
                    )
                ),
                classes,
                updatedAtEpochMs = 1L
            ).entry
        )
        val middle = requireNotNull(
            SchoolDirectoryMapper.student(
                FirestoreDocument(
                    id = "middle-3",
                    fields = mapOf(
                        "ogrenciNo" to "003",
                        "ogrenciAdi" to "Ortaokul Öğrencisi",
                        "sinifId" to "6a"
                    )
                ),
                classes,
                updatedAtEpochMs = 2L
            ).entry
        )

        assertEquals("3", primary.studentNumber)
        assertEquals("3", middle.studentNumber)
        assertEquals(StudentSchoolIdentity.PRIMARY_SCHOOL_NAME, primary.schoolName)
        assertEquals(StudentSchoolIdentity.MIDDLE_SCHOOL_NAME, middle.schoolName)
        assertNotEquals(primary.identityKey, middle.identityKey)
    }

    @Test
    fun `cloud identity map keeps same number once per school`() {
        val identities = linkedMapOf<String, String>()

        assertTrue(SchoolStudentMatch.upsertBySchoolAndStudentNumber(identities, "3", 3, "primary-doc"))
        assertTrue(SchoolStudentMatch.upsertBySchoolAndStudentNumber(identities, "003", 6, "middle-doc"))

        assertEquals(2, identities.size)
        assertEquals("primary-doc", identities["koruk-ilkokulu:3"])
        assertEquals("middle-doc", identities["koruk-ortaokulu:3"])
    }
}
