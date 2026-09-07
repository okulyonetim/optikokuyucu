package com.okulyonetim.optikokuyucu.omr.scoring

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerKeyPdfExporterTest {
    @Test
    fun singleGeneralKeyIsRepeatedSixTimes() {
        val general = entry(null)
        val sequence = AnswerKeyPdfExporter.buildSheetSequence(listOf(general))

        assertEquals(6, sequence.size)
        assertEquals(List(6) { null }, sequence.map { it.key.variantValue })
    }

    @Test
    fun twoBookletsArePlacedAsThreeSideBySidePairs() {
        val sequence = AnswerKeyPdfExporter.buildSheetSequence(
            listOf(entry("A"), entry("B"))
        )

        assertEquals(listOf("A", "B", "A", "B", "A", "B"), sequence.map { it.key.variantValue })
    }

    @Test
    fun fourBookletsCreateTwoA4PairGroups() {
        val sequence = AnswerKeyPdfExporter.buildSheetSequence(
            listOf(entry("A"), entry("B"), entry("C"), entry("D"))
        )

        assertEquals(12, sequence.size)
        assertEquals(
            listOf("A", "B", "A", "B", "A", "B", "C", "D", "C", "D", "C", "D"),
            sequence.map { it.key.variantValue }
        )
    }

    private fun entry(variant: String?): AnswerKeyPdfExporter.SheetEntry {
        val key = StoredAnswerKey(
            answerKey = AnswerKey(
                templateId = "template",
                templateVersion = 1,
                answers = linkedMapOf("q1" to "A")
            ),
            variantGridId = variant?.let { "booklet" },
            variantValue = variant,
            source = AnswerKeySource.MANUAL
        )
        return AnswerKeyPdfExporter.SheetEntry(
            key = key,
            title = "Deneme",
            sections = listOf(
                ManualAnswerSection(
                    id = "section",
                    label = "Türkçe",
                    questionIds = listOf("q1"),
                    allowedChoices = setOf("A", "B", "C", "D")
                )
            )
        )
    }
}
