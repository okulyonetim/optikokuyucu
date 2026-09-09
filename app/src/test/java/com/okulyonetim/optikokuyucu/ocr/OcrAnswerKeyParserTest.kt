package com.okulyonetim.optikokuyucu.ocr

import com.okulyonetim.optikokuyucu.omr.scoring.ManualAnswerSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrAnswerKeyParserTest {
    @Test
    fun `Turkish subject headers and positioned answers are reconstructed`() {
        val sections = listOf(
            section("tr", "Türkçe", "tr:1", "tr:2"),
            section("ink", "T.C. İnkılap Tarihi", "ink:1", "ink:2"),
            section("din", "Din Kültürü ve Ahlak Bilgisi", "din:1", "din:2"),
            section("eng", "İngilizce", "eng:1", "eng:2"),
            section("mat", "Matematik", "mat:1", "mat:2"),
            section("fen", "Fen Bilimleri", "fen:1", "fen:2")
        )
        val headerX = listOf(50, 150, 250, 350, 450, 550)
        val headerText = listOf("TÜRKÇE", "TC. İnk. Tarh", "DİN", "İngilizce", "Matematik", "Fen Bilimleri")
        val answerPairs = listOf(
            listOf("C", "D"),
            listOf("C", "D"),
            listOf("C", "B"),
            listOf("B", "A"),
            listOf("C", "B"),
            listOf("D", "D")
        )
        val tokens = mutableListOf<OcrToken>()
        headerX.forEachIndexed { index, x ->
            tokens += token(headerText[index], x - 35, 80, x + 35, 100)
            answerPairs[index].forEachIndexed { row, answer ->
                val y = 140 + row * 42
                tokens += token((row + 1).toString(), x - 34, y, x - 14, y + 20)
                tokens += token(answer, x + 10, y, x + 28, y + 20)
            }
        }
        val recognition = OcrRecognitionResult(
            text = "ÖZDEBİR LGS GENEL DENEME SINAVI 2 (A GRUBU)",
            tokens = tokens,
            imageWidth = 600,
            imageHeight = 400
        )

        val parsed = OcrAnswerKeyParser.parse(recognition, sections, listOf("A", "B"))

        assertEquals("A", parsed.detectedBooklet)
        assertEquals("C", parsed.answers["tr:1"])
        assertEquals("D", parsed.answers["tr:2"])
        assertEquals("B", parsed.answers["eng:1"])
        assertEquals("D", parsed.answers["fen:2"])
        assertEquals(12, parsed.answers.size)
    }

    @Test
    fun `invalid answer glyph is left for manual review`() {
        val sections = listOf(section("fen", "Fen Bilimleri", "fen:1", "fen:2"))
        val recognition = OcrRecognitionResult(
            text = "FEN BİLİMLERİ",
            tokens = listOf(
                token("Fen", 20, 20, 70, 40), token("Bilimleri", 72, 20, 160, 40),
                token("1", 30, 80, 45, 100), token("D", 80, 80, 95, 100),
                token("2", 30, 120, 45, 140), token("I", 80, 120, 95, 140)
            ),
            imageWidth = 200,
            imageHeight = 200
        )

        val parsed = OcrAnswerKeyParser.parse(recognition, sections)

        assertEquals("D", parsed.answers["fen:1"])
        assertTrue("fen:2" !in parsed.answers)
        assertTrue(parsed.warnings.any { it.contains("2") })
    }

    private fun section(id: String, label: String, vararg questions: String) = ManualAnswerSection(
        id = id,
        label = label,
        questionIds = questions.toList(),
        allowedChoices = setOf("A", "B", "C", "D")
    )

    private fun token(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrToken(text, left, top, right, bottom)
}
