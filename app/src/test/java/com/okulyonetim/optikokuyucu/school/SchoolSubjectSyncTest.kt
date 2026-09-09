package com.okulyonetim.optikokuyucu.school

import org.junit.Assert.assertEquals
import org.junit.Test

class SchoolSubjectSyncTest {
    @Test
    fun mapperReadsSchoolManagementSubjectNameField() {
        val documents = listOf(
            FirestoreDocument("1", mapOf("ad" to " Fen Bilimleri ")),
            FirestoreDocument("2", mapOf("ad" to "Türkçe")),
            FirestoreDocument("3", mapOf("ad" to "fen bilimleri")),
            FirestoreDocument("4", mapOf("ad" to "  ")),
            FirestoreDocument("5", mapOf("isim" to "Matematik"))
        )

        assertEquals(
            listOf("Fen Bilimleri", "Türkçe"),
            SchoolSubjectMapper.names(documents)
        )
    }

    @Test
    fun mapperNormalizesRepeatedWhitespace() {
        val documents = listOf(
            FirestoreDocument("1", mapOf("ad" to "T.C.   İnkılap Tarihi ve Atatürkçülük"))
        )

        assertEquals(
            listOf("T.C. İnkılap Tarihi ve Atatürkçülük"),
            SchoolSubjectMapper.names(documents)
        )
    }
}
