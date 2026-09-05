package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import org.junit.Assert.assertEquals
import org.junit.Test

class DesignerDocumentCodecTest {
    @Test
    fun `document round trip preserves OMR and visual source data`() {
        val document = DesignerDocument(
            id = "my-form",
            version = 3,
            name = "Deneme Formu",
            components = listOf(
                QuestionGroupComponent(
                    id = "questions",
                    startQuestion = 1,
                    questionCount = 40,
                    choices = listOf("A", "B", "C", "D"),
                    columns = 2,
                    firstChoiceX = 140.0,
                    topY = 240.0,
                    bubbleRadius = 11.0,
                    choiceGap = 45.0,
                    rowGap = 46.0,
                    columnGap = 480.0
                ),
                NumericGridComponent(
                    id = "studentNumber",
                    digits = 6,
                    startX = 120.0,
                    topY = 850.0,
                    bubbleRadius = 10.0,
                    columnGap = 44.0,
                    rowGap = 34.0
                ),
                SingleChoiceComponent(
                    id = "booklet",
                    choices = listOf("A", "B"),
                    start = TemplatePoint(150.0, 1220.0),
                    bubbleRadius = 12.0,
                    gap = 60.0
                )
            ),
            visualElements = listOf(
                DesignerTextElement(
                    id = "title",
                    bounds = TemplateRect(160.0, 120.0, 680.0, 60.0),
                    text = "SINAV OPTİK FORMU",
                    fontSize = 28.0,
                    alignment = DesignerTextAlignment.CENTER
                ),
                DesignerBoxElement(
                    id = "name-box",
                    bounds = TemplateRect(120.0, 190.0, 500.0, 60.0),
                    strokeWidth = 2.0
                ),
                DesignerLineElement(
                    id = "separator",
                    start = TemplatePoint(120.0, 280.0),
                    end = TemplatePoint(880.0, 280.0),
                    strokeWidth = 1.5,
                    locked = true
                )
            )
        )

        val decoded = DesignerDocumentCodec.decode(DesignerDocumentCodec.encode(document))

        assertEquals(document, decoded)
    }
}
