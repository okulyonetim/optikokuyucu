package com.okulyonetim.optikokuyucu.exam

import org.junit.Assert.assertEquals
import org.junit.Test

class ExamLessonDisplayNameTest {
    @Test
    fun structuredAnswerGroupsUseReadableLgsLessonNames() {
        assertEquals("Türkçe", examLessonDisplayName("answers-1"))
        assertEquals("İnkılap Tarihi", examLessonDisplayName("answers-2"))
        assertEquals("Din Kültürü", examLessonDisplayName("answers-3"))
        assertEquals("Yabancı Dil", examLessonDisplayName("answers-4"))
        assertEquals("Matematik", examLessonDisplayName("answers-5"))
        assertEquals("Fen Bilimleri", examLessonDisplayName("answers-6"))
    }

    @Test
    fun knownCanonicalLessonIdsStayReadable() {
        assertEquals("Türkçe", examLessonDisplayName("turkce"))
        assertEquals("Fen Bilimleri", examLessonDisplayName("fen"))
        assertEquals("Genel", examLessonDisplayName("genel"))
    }

    @Test
    fun customLessonIdsStillFallBackToHumanizedText() {
        assertEquals("Geometri İleri", examLessonDisplayName("geometri-ileri"))
    }
}
