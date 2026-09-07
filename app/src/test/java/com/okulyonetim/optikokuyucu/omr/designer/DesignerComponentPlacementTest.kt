package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerComponentPlacementTest {
    @Test
    fun `answer area moved outside page is fitted back into safe area`() {
        val document = DesignerStarterTemplates.questions20Abcd()
        val original = document.components.filterIsInstance<QuestionGroupComponent>().single()
        val outside = DesignerComponentPlacement.translate(original, 900.0, 700.0) as QuestionGroupComponent

        val fitted = DesignerComponentPlacement.fitInsideSafeArea(document, outside) as QuestionGroupComponent

        assertTrue(DesignerComponentPlacement.fitsInsideSafeArea(document, fitted))
        assertEquals(original.questionCount, fitted.questionCount)
        assertEquals(original.choices, fitted.choices)
    }

    @Test
    fun `manual movement keeps answer geometry unchanged`() {
        val document = DesignerStarterTemplates.questions20Abcd()
        val original = document.components.filterIsInstance<QuestionGroupComponent>().single()

        val moved = DesignerComponentPlacement.translate(original, 12.0, -8.0) as QuestionGroupComponent

        assertEquals(original.firstChoiceX + 12.0, moved.firstChoiceX, 0.001)
        assertEquals(original.topY - 8.0, moved.topY, 0.001)
        assertEquals(original.choiceGap, moved.choiceGap, 0.001)
        assertEquals(original.rowGap, moved.rowGap, 0.001)
    }
}
