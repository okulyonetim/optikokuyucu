package com.okulyonetim.optikokuyucu.student

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentRosterBackendTest {
    @Test
    fun `e okul class list parser keeps class branch student number and gender`() {
        val text = """
            5. Sınıf / Geçici Şube Sınıf Listesi
             1 16 ALİ TEST Erkek
             2 44 AYŞE TEST Kız
            6. Sınıf / A Şubesi Sınıf Listesi
             1 19 DENİZ TEST Erkek
        """.trimIndent()

        val students = EschoolClassListParser.parse(text, importedAtEpochMs = 123L)

        assertEquals(3, students.size)
        assertEquals("16", students[0].studentNumber)
        assertEquals("5-Geçici", students[0].className)
        assertEquals(StudentGender.BOY, students[0].gender)
        assertEquals("44", students[1].studentNumber)
        assertEquals(StudentGender.GIRL, students[1].gender)
        assertEquals("6-A", students[2].className)
    }

    @Test
    fun `student number normalization matches fixed width omr digits`() {
        assertEquals("16", StudentNumber.normalize("000016"))
        assertEquals("16", StudentNumber.normalize(" 16 "))
        assertEquals("0", StudentNumber.normalize("000000"))
        assertEquals("", StudentNumber.normalize(""))
    }

    @Test
    fun `student codec preserves guardian fields`() {
        val source = StudentRosterEntry(
            studentNumber = "000019",
            fullName = "DENİZ TEST",
            gender = StudentGender.BOY,
            gradeLevel = 6,
            branch = "A Şubesi",
            guardianName = "VELİ TEST",
            guardianPhone = "05320000000",
            updatedAtEpochMs = 456L
        )

        val decoded = StudentRosterCodec.decode(StudentRosterCodec.encode(source))

        assertEquals("19", decoded.studentNumber)
        assertEquals("6-A", decoded.className)
        assertEquals("VELİ TEST", decoded.guardianName)
        assertEquals("05320000000", decoded.guardianPhone)
        assertEquals(456L, decoded.updatedAtEpochMs)
    }

    @Test
    fun `dynamic result message resolves supported placeholders`() {
        val rendered = StudentResultMessageTemplate.render(
            template = "{veliAdi}: {ogrenciAdi} {sinif} {sinavAdi} D:{dogru} Y:{yanlis} B:{bos} Net:{net}",
            values = StudentResultMessageValues(
                studentName = "ALİ TEST",
                studentNumber = "16",
                className = "5-A",
                guardianName = "VELİ TEST",
                examName = "Deneme 1",
                correct = 15,
                wrong = 3,
                blank = 2,
                doubleMark = 0,
                suspicious = 0,
                net = 14.25
            )
        )

        assertEquals("VELİ TEST: ALİ TEST 5-A Deneme 1 D:15 Y:3 B:2 Net:14,25", rendered)
        assertTrue(StudentResultMessageTemplate.placeholders.contains("{ogrenciAdi}"))
        assertTrue(StudentResultMessageTemplate.placeholders.contains("{sinavAdi}"))
    }
}
