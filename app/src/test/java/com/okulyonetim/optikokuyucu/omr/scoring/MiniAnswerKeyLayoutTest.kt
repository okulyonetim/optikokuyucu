package com.okulyonetim.optikokuyucu.omr.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniAnswerKeyLayoutTest {
    @Test
    fun `twenty question single subject uses twenty copies on portrait A4`() {
        val sections = listOf(section("Türkçe", 20))

        assertEquals(
            20,
            MiniAnswerKeyLayout.recommendedCopies(sections, MiniAnswerKeyPdfExporter.Orientation.PORTRAIT)
        )
        assertTrue(
            MiniAnswerKeyLayout.fits(sections, 20, MiniAnswerKeyPdfExporter.Orientation.PORTRAIT)
        )
    }

    @Test
    fun `two twenty question subjects stop at sixteen copies instead of stretching`() {
        val sections = listOf(section("Türkçe", 20), section("Matematik", 20))

        assertEquals(
            16,
            MiniAnswerKeyLayout.recommendedCopies(sections, MiniAnswerKeyPdfExporter.Orientation.PORTRAIT)
        )
        assertTrue(MiniAnswerKeyLayout.fits(sections, 16, MiniAnswerKeyPdfExporter.Orientation.PORTRAIT))
        assertFalse(MiniAnswerKeyLayout.fits(sections, 20, MiniAnswerKeyPdfExporter.Orientation.PORTRAIT))
    }

    @Test
    fun `LGS sized six subject key keeps readable six copy layout`() {
        val sections = listOf(
            section("Türkçe", 20),
            section("T.C. İnkılap Tarihi", 10),
            section("Din", 10),
            section("İngilizce", 10),
            section("Matematik", 20),
            section("Fen Bilimleri", 20)
        )

        assertEquals(
            6,
            MiniAnswerKeyLayout.recommendedCopies(sections, MiniAnswerKeyPdfExporter.Orientation.PORTRAIT)
        )
        assertTrue(MiniAnswerKeyLayout.fits(sections, 6, MiniAnswerKeyPdfExporter.Orientation.PORTRAIT))
        assertFalse(MiniAnswerKeyLayout.fits(sections, 8, MiniAnswerKeyPdfExporter.Orientation.PORTRAIT))
    }

    @Test
    fun `new copy grids are stable`() {
        assertEquals(
            MiniAnswerKeyLayout.Grid(2, 10),
            MiniAnswerKeyLayout.grid(20, MiniAnswerKeyPdfExporter.Orientation.PORTRAIT)
        )
        assertEquals(
            MiniAnswerKeyLayout.Grid(4, 4),
            MiniAnswerKeyLayout.grid(16, MiniAnswerKeyPdfExporter.Orientation.LANDSCAPE)
        )
    }

    private fun section(label: String, questions: Int): ManualAnswerSection = ManualAnswerSection(
        id = label,
        label = label,
        questionIds = (1..questions).map { "$label:$it" },
        allowedChoices = setOf("A", "B", "C", "D")
    )
}
