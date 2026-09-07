package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ExamParticipantSchoolIdentityTest {
    private val selection = ActiveTemplateSelection(
        source = ActiveTemplateSource.STANDARD,
        templateId = "template",
        templateVersion = 1
    )

    @Test
    fun `same student number can participate once from primary and once from middle school`() {
        val primary = ExamParticipant("3", "İlkokul Öğrencisi", "3-A")
        val middle = ExamParticipant("003", "Ortaokul Öğrencisi", "6-A")

        val exam = ExamFactory.create(
            name = "Karma Sınav",
            schoolName = "Koruk",
            templateSelection = selection,
            examDateEpochDay = 1L,
            participants = listOf(primary, middle),
            id = "exam-school-identity",
            createdAtEpochMs = 1L
        )

        assertEquals(2, exam.participants.size)
        assertEquals("3", exam.participants[0].studentNumber)
        assertEquals("3", exam.participants[1].studentNumber)
        assertNotEquals(exam.participants[0].identityKey, exam.participants[1].identityKey)
    }

    @Test
    fun `duplicate same number inside same school is still deduplicated`() {
        val first = ExamParticipant("3", "Öğrenci", "2-A")
        val second = ExamParticipant("003", "Öğrenci", "4-A")

        val exam = ExamFactory.create(
            name = "İlkokul Sınavı",
            schoolName = "Koruk İlkokulu",
            templateSelection = selection,
            examDateEpochDay = 1L,
            participants = listOf(first, second),
            id = "exam-primary-dedupe",
            createdAtEpochMs = 1L
        )

        assertEquals(1, exam.participants.size)
    }
}
