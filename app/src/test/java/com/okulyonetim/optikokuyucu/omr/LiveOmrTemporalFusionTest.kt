package com.okulyonetim.optikokuyucu.omr

import com.okulyonetim.optikokuyucu.omr.bubble.BubbleReadResult
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionRead
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionState
import com.okulyonetim.optikokuyucu.omr.live.LiveOmrTemporalFusion
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkColumnRead
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkColumnState
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkGridRead
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkGridReadResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveOmrTemporalFusionTest {
    @Test
    fun `one weak camera frame does not erase a persistent light answer`() {
        val fusion = LiveOmrTemporalFusion(windowSize = 3)
        assertNull(fusion.offer(bubbles(0.15, 0.03), MarkGridReadResult.Empty))
        assertNull(fusion.offer(bubbles(0.06, 0.03), MarkGridReadResult.Empty))
        val fused = requireNotNull(fusion.offer(bubbles(0.16, 0.03), MarkGridReadResult.Empty))
        val question = fused.bubbleResult.questions.single()
        assertEquals(QuestionState.MARKED, question.state)
        assertEquals("A", question.selectedChoice)
        assertEquals(0.15, question.choiceScores.getValue("A"), 0.0001)
    }

    @Test
    fun `one glare or dirt spike does not create a false answer`() {
        val fusion = LiveOmrTemporalFusion(windowSize = 3)
        fusion.offer(bubbles(0.03, 0.025), MarkGridReadResult.Empty)
        fusion.offer(bubbles(0.18, 0.025), MarkGridReadResult.Empty)
        val fused = requireNotNull(fusion.offer(bubbles(0.03, 0.025), MarkGridReadResult.Empty))
        val question = fused.bubbleResult.questions.single()
        assertEquals(QuestionState.BLANK, question.state)
        assertNull(question.selectedChoice)
        assertEquals(0.03, question.choiceScores.getValue("A"), 0.0001)
    }

    @Test
    fun `persistent double answer survives one asymmetric frame`() {
        val fusion = LiveOmrTemporalFusion(windowSize = 3)
        fusion.offer(bubbles(a = 0.24, c = 0.22), MarkGridReadResult.Empty)
        fusion.offer(bubbles(a = 0.23, c = 0.07), MarkGridReadResult.Empty)
        val fused = requireNotNull(fusion.offer(bubbles(a = 0.25, c = 0.21), MarkGridReadResult.Empty))
        assertEquals(QuestionState.DOUBLE_MARK, fused.bubbleResult.questions.single().state)
    }

    @Test
    fun `student number digit uses the same temporal fusion`() {
        val fusion = LiveOmrTemporalFusion(windowSize = 3)
        fusion.offer(BubbleReadResult(emptyList()), grid(digit4 = 0.15, digit7 = 0.03))
        fusion.offer(BubbleReadResult(emptyList()), grid(digit4 = 0.06, digit7 = 0.03))
        val fused = requireNotNull(
            fusion.offer(BubbleReadResult(emptyList()), grid(digit4 = 0.16, digit7 = 0.03))
        )
        val column = fused.markGridResult.grids.single().columns.single()
        assertEquals(MarkColumnState.MARKED, column.state)
        assertEquals("4", column.selectedValue)
    }

    @Test
    fun `template structure change clears the temporal window`() {
        val fusion = LiveOmrTemporalFusion(windowSize = 3)
        fusion.offer(bubbles(0.15, 0.03, questionId = "1"), MarkGridReadResult.Empty)
        fusion.offer(bubbles(0.16, 0.03, questionId = "1"), MarkGridReadResult.Empty)
        val result = fusion.offer(bubbles(0.17, 0.03, questionId = "2"), MarkGridReadResult.Empty)
        assertNull(result)
        assertEquals(1, fusion.currentFrames())
    }

    @Test
    fun `five frame window fuses only the three sharpest frames`() {
        val fusion = LiveOmrTemporalFusion(windowSize = 5, fusedFrameCount = 3)
        assertNull(fusion.offer(bubbles(0.30, 0.02), MarkGridReadResult.Empty, frameQuality = 1.0))
        assertNull(fusion.offer(bubbles(0.02, 0.20), MarkGridReadResult.Empty, frameQuality = 2.0))
        assertNull(fusion.offer(bubbles(0.14, 0.02), MarkGridReadResult.Empty, frameQuality = 10.0))
        assertNull(fusion.offer(bubbles(0.16, 0.02), MarkGridReadResult.Empty, frameQuality = 12.0))
        val fused = requireNotNull(
            fusion.offer(bubbles(0.18, 0.02), MarkGridReadResult.Empty, frameQuality = 11.0)
        )
        val question = fused.bubbleResult.questions.single()
        assertEquals(QuestionState.MARKED, question.state)
        assertEquals("A", question.selectedChoice)
        assertEquals(0.16, question.choiceScores.getValue("A"), 0.0001)
        assertEquals(listOf(12.0, 11.0, 10.0), fused.selectedFrameQualities)
    }

    private fun bubbles(a: Double, c: Double, questionId: String = "1") = BubbleReadResult(
        listOf(
            QuestionRead(
                questionId = questionId,
                state = QuestionState.SUSPICIOUS,
                selectedChoice = null,
                confidence = 0.0,
                choiceScores = mapOf("A" to a, "B" to 0.02, "C" to c, "D" to 0.02)
            )
        )
    )

    private fun grid(digit4: Double, digit7: Double) = MarkGridReadResult(
        listOf(
            MarkGridRead(
                gridId = "number-1",
                columns = listOf(
                    MarkColumnRead(
                        columnId = "1",
                        state = MarkColumnState.SUSPICIOUS,
                        selectedValue = null,
                        confidence = 0.0,
                        scores = mapOf("4" to digit4, "7" to digit7, "0" to 0.02)
                    )
                )
            )
        )
    )
}
