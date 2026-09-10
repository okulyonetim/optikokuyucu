package com.okulyonetim.optikokuyucu.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrRecognitionPostProcessorTest {
    @Test
    fun `secondary pass adds a missing character in primary coordinates`() {
        val primaryToken = token("A", 10, 10, 24, 30, 0.91f)
        val primary = result(
            width = 100,
            height = 100,
            tokens = listOf(primaryToken)
        )
        val secondary = result(
            width = 200,
            height = 200,
            tokens = listOf(
                token("A", 20, 20, 48, 60, 0.88f),
                token("B", 100, 20, 128, 60, 0.93f)
            )
        )

        val merged = OcrRecognitionPostProcessor.mergePasses(primary, secondary)

        assertEquals(2, merged.tokens.size)
        val added = merged.tokens.first { it.text == "B" }
        assertEquals(50, added.left)
        assertEquals(10, added.top)
        assertEquals(64, added.right)
        assertEquals(30, added.bottom)
        assertEquals(100, merged.pages.single().imageWidth)
        assertEquals("content://ocr/page", merged.pages.single().sourceUri)
    }

    @Test
    fun `better overlapping recognition replaces weaker token without duplication`() {
        val primary = result(
            300,
            200,
            listOf(token("MATEMATIK", 20, 40, 150, 68, 0.52f))
        )
        val secondary = result(
            300,
            200,
            listOf(token("MATEMATİK", 20, 40, 150, 68, 0.96f))
        )

        val merged = OcrRecognitionPostProcessor.mergePasses(primary, secondary)

        assertEquals(1, merged.tokens.size)
        assertEquals("MATEMATİK", merged.tokens.single().text)
    }

    @Test
    fun `extract plain text follows positioned rows and columns`() {
        val tokens = listOf(
            token("TÜRKÇE", 10, 10, 80, 30),
            token("MATEMATİK", 160, 10, 260, 30),
            token("1", 10, 50, 22, 70),
            token("C", 45, 50, 60, 70),
            token("1", 160, 50, 172, 70),
            token("B", 195, 50, 210, 70),
            token("2", 10, 86, 22, 106),
            token("D", 45, 86, 60, 106)
        )
        val recognition = result(300, 160, tokens)

        val text = OcrRecognitionPostProcessor.extractPlainText(recognition)

        assertEquals(
            "TÜRKÇE MATEMATİK\n1 C 1 B\n2 D",
            text
        )
        assertTrue(text.contains("TÜRKÇE"))
    }

    private fun result(width: Int, height: Int, tokens: List<OcrToken>): OcrRecognitionResult =
        OcrRecognitionResult(
            text = tokens.joinToString(" ") { it.text },
            tokens = tokens,
            imageWidth = width,
            imageHeight = height,
            pages = listOf(OcrPageLayout("content://ocr/page", width, height, tokens))
        )

    private fun token(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        confidence: Float? = 0.8f
    ) = OcrToken(
        text = text,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        confidence = confidence
    )
}
