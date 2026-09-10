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
    fun `two dimensional subject blocks stay in their own table regions`() {
        val sections = listOf(
            section("tr", "Türkçe", "tr:1", "tr:2"),
            section("ink", "T.C. İnkılap Tarihi", "ink:1", "ink:2"),
            section("din", "Din Kültürü ve Ahlak Bilgisi", "din:1", "din:2"),
            section("eng", "İngilizce", "eng:1", "eng:2"),
            section("mat", "Matematik", "mat:1", "mat:2"),
            section("fen", "Fen Bilimleri", "fen:1", "fen:2")
        )
        val tokens = mutableListOf<OcrToken>()
        val headers = listOf(
            Triple("TÜRKÇE", 100, 50),
            Triple("TC. İnk. Tarh", 300, 50),
            Triple("DİN", 500, 50),
            Triple("İngilizce", 100, 280),
            Triple("Matematik", 300, 280),
            Triple("Fen Bilimleri", 500, 280)
        )
        val answerPairs = listOf(
            listOf("C", "D"), listOf("A", "B"), listOf("B", "C"),
            listOf("D", "A"), listOf("C", "B"), listOf("A", "D")
        )
        headers.forEachIndexed { index, (label, x, y) ->
            tokens += token(label, x - 55, y, x + 55, y + 22)
            answerPairs[index].forEachIndexed { row, answer ->
                val rowY = y + 62 + row * 42
                tokens += token((row + 1).toString(), x - 42, rowY, x - 23, rowY + 20)
                tokens += token(answer, x + 18, rowY, x + 37, rowY + 20)
            }
        }

        val parsed = OcrAnswerKeyParser.parse(
            OcrRecognitionResult(
                text = "ÖZDEBİR LGS GENEL DENEME SINAVI 2 A GRUBU",
                tokens = tokens,
                imageWidth = 600,
                imageHeight = 520
            ),
            sections,
            listOf("A", "B")
        )

        assertEquals(12, parsed.answers.size)
        assertEquals("C", parsed.answers["tr:1"])
        assertEquals("B", parsed.answers["ink:2"])
        assertEquals("C", parsed.answers["din:2"])
        assertEquals("D", parsed.answers["eng:1"])
        assertEquals("B", parsed.answers["mat:2"])
        assertEquals("D", parsed.answers["fen:2"])
    }

    @Test
    fun `missing question numbers are reconstructed from table rows`() {
        val sections = listOf(
            section("tr", "Türkçe", "tr:1", "tr:2", "tr:3", "tr:4", "tr:5"),
            section("fen", "Fen Bilimleri", "fen:1", "fen:2", "fen:3", "fen:4", "fen:5")
        )
        val tokens = mutableListOf(
            token("TÜRKÇE", 45, 50, 135, 70),
            token("Fen", 245, 50, 280, 70),
            token("Bilimleri", 282, 50, 355, 70)
        )
        val ys = listOf(110, 150, 190, 230, 270)
        val trAnswers = listOf("C", "D", "B", "C", "A")
        val fenAnswers = listOf("D", "C", "B", "A", "D")

        listOf(1, 2, 4, 5).forEach { number ->
            val y = ys[number - 1]
            tokens += token(number.toString(), 55, y, 75, y + 18)
        }
        trAnswers.forEachIndexed { index, answer ->
            val y = ys[index]
            tokens += token(answer, 110, y, 128, y + 18)
        }
        fenAnswers.forEachIndexed { index, answer ->
            val y = ys[index]
            tokens += token(answer, 310, y, 328, y + 18)
        }

        val parsed = OcrAnswerKeyParser.parse(
            OcrRecognitionResult(
                text = "TÜRKÇE FEN BİLİMLERİ",
                tokens = tokens,
                imageWidth = 400,
                imageHeight = 340
            ),
            sections
        )

        assertEquals(10, parsed.answers.size)
        assertEquals("B", parsed.answers["tr:3"])
        assertEquals("D", parsed.answers["fen:1"])
        assertEquals("B", parsed.answers["fen:3"])
        assertEquals("D", parsed.answers["fen:5"])
    }

    @Test
    fun `high confidence symbol recovers merged answer and low confidence symbol is rejected`() {
        val sections = listOf(section("fen", "Fen Bilimleri", "fen:1", "fen:2"))
        val recognition = OcrRecognitionResult(
            text = "FEN BİLİMLERİ",
            tokens = listOf(
                token("Fen", 20, 20, 70, 40), token("Bilimleri", 72, 20, 160, 40),
                token("1", 30, 80, 45, 100),
                OcrToken(
                    text = "xA",
                    left = 74,
                    top = 79,
                    right = 103,
                    bottom = 101,
                    confidence = 0.42f,
                    symbols = listOf(OcrSymbol("A", 84, 80, 98, 100, confidence = 0.94f))
                ),
                token("2", 30, 120, 45, 140),
                OcrToken(
                    text = "xB",
                    left = 74,
                    top = 119,
                    right = 103,
                    bottom = 141,
                    confidence = 0.42f,
                    symbols = listOf(OcrSymbol("B", 84, 120, 98, 140, confidence = 0.28f))
                )
            ),
            imageWidth = 200,
            imageHeight = 200
        )

        val parsed = OcrAnswerKeyParser.parse(recognition, sections)

        assertEquals("A", parsed.answers["fen:1"])
        assertTrue("fen:2" !in parsed.answers)
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
