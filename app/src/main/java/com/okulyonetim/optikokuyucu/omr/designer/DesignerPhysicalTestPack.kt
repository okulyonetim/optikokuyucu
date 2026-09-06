package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect

/**
 * Canonical physical-test document. It is a normal DesignerDocument, not a second OMR model.
 * The same id/version can be selected for PDF, camera, answer key and exam scoring.
 */
object DesignerPhysicalTestPack {
    const val TEMPLATE_ID: String = "starter-tr-physical-20-abcd"
    const val TEMPLATE_VERSION: Int = 1
    const val STUDENT_NUMBER: String = "123456"
    const val BOOKLET_CODE: String = "B"
    const val NUMBER_COMPONENT_ID: String = "number-1"
    const val BOOKLET_COMPONENT_ID: String = "booklet-1"
    const val ANSWER_COMPONENT_ID: String = "answers-1"

    private val answerPattern = listOf("A", "B", "C", "D")

    fun answerFor(questionNumber: Int): String {
        require(questionNumber in 1..20)
        return answerPattern[(questionNumber - 1) % answerPattern.size]
    }

    fun answerSummary(): String = (1..20).joinToString(" · ") { "$it=${answerFor(it)}" }

    fun document(): DesignerDocument = DesignerDocument(
        id = TEMPLATE_ID,
        version = TEMPLATE_VERSION,
        name = "Türkçe OMR Testi · 20 Soru",
        components = listOf(
            NumericGridComponent(
                id = NUMBER_COMPONENT_ID,
                digits = 6,
                startX = 180.0,
                topY = 260.0,
                bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS,
                columnGap = DesignerEditorLayout.NUMBER_POSITION_GAP,
                rowGap = DesignerEditorLayout.NUMBER_VALUE_GAP,
                label = "Öğrenci Numarası",
                showLabel = true,
                labelAlignment = DesignerTextAlignment.CENTER
            ),
            SingleChoiceComponent(
                id = BOOKLET_COMPONENT_ID,
                choices = listOf("A", "B"),
                start = TemplatePoint(180.0, 620.0),
                bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS,
                gap = DesignerEditorLayout.BOOKLET_GAP,
                axis = ChoiceAxis.HORIZONTAL,
                label = "Kitapçık Türü",
                showLabel = true,
                labelAlignment = DesignerTextAlignment.CENTER
            ),
            QuestionGroupComponent(
                id = ANSWER_COMPONENT_ID,
                startQuestion = 1,
                questionCount = 20,
                choices = answerPattern,
                columns = 1,
                firstChoiceX = 650.0,
                topY = 260.0,
                bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS,
                choiceGap = DesignerEditorLayout.ANSWER_CHOICE_GAP,
                rowGap = DesignerEditorLayout.ANSWER_ROW_GAP,
                columnGap = 220.0,
                questionIdPrefix = ANSWER_COMPONENT_ID,
                label = "Fen Bilimleri",
                showLabel = true,
                labelAlignment = DesignerTextAlignment.CENTER
            )
        ),
        visualElements = listOf(
            DesignerTextElement(
                id = "turkish-font-probe",
                bounds = TemplateRect(180.0, 125.0, 640.0, 55.0),
                text = "Türkçe OMR Testi · ${DesignerTypography.TURKISH_GLYPH_SAMPLE}",
                fontSize = 24.0,
                alignment = DesignerTextAlignment.CENTER,
                bold = true,
                locked = true
            )
        )
    )
}
