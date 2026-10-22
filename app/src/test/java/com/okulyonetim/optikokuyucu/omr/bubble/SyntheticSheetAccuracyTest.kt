package com.okulyonetim.optikokuyucu.omr.bubble

import com.okulyonetim.optikokuyucu.omr.bubble.SyntheticSheetRenderer.Kind
import com.okulyonetim.optikokuyucu.omr.bubble.SyntheticSheetRenderer.Look
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Measures the real scorer + classifier on seeded synthetic sheets. This is not a substitute for a
 * photographed test set, but it pins the behavior that matters most: a mark must never be read as a
 * different letter, an empty row must never be read as marked, and clean sheets must read perfectly.
 */
class SyntheticSheetAccuracyTest {
    private class Tally {
        var total = 0
        var correct = 0
        var wrongLetter = 0
        var blankReadAsMarked = 0
        var singleLost = 0
        var doubleMissed = 0
        var singles = 0
        var doubles = 0
    }

    private fun measure(template: OmrTemplate, look: Look, seeds: LongRange): Tally {
        val reader = CanonicalBubbleReader(template)
        val tally = Tally()
        for (seed in seeds) {
            val sheet = SyntheticSheetRenderer.render(template, seed, look)
            val result = reader.readCanonical(sheet.image)
            assertEquals(template.bubbleRows.size, result.questions.size)
            for (question in result.questions) {
                val kind = sheet.kinds.getValue(question.questionId)
                val expected = sheet.marked.getValue(question.questionId)
                val read = question.selectedChoice?.let(SyntheticSheetRenderer::choiceOf)
                tally.total += 1
                when (kind) {
                    Kind.SINGLE -> {
                        tally.singles += 1
                        val good = question.state == QuestionState.MARKED && read == expected.first()
                        if (good) tally.correct += 1 else tally.singleLost += 1
                        if (question.state == QuestionState.MARKED && read != expected.first()) tally.wrongLetter += 1
                    }
                    Kind.BLANK -> {
                        if (question.state == QuestionState.BLANK) tally.correct += 1
                        if (question.state == QuestionState.MARKED) tally.blankReadAsMarked += 1
                    }
                    Kind.DOUBLE -> {
                        tally.doubles += 1
                        if (question.state == QuestionState.DOUBLE_MARK) tally.correct += 1 else tally.doubleMissed += 1
                    }
                }
            }
        }
        return tally
    }

    @Test
    fun `clean standard 20 question sheet reads perfectly`() {
        val tally = measure(StandardOmrTemplate.SAMPLE_20_ABCD_STUDENT_6_BOOKLET_AB, Look.CLEAN, 1L..10L)
        assertEquals(tally.total, tally.correct)
    }

    @Test
    fun `clean dense 100 question sheet reads perfectly`() {
        val tally = measure(SyntheticSheetRenderer.denseTemplate(8.0, 4, 25, 5), Look.CLEAN, 1L..6L)
        assertEquals(tally.total, tally.correct)
    }

    @Test
    fun `clean very small bubbles still read perfectly`() {
        val tally = measure(SyntheticSheetRenderer.denseTemplate(6.5, 4, 30, 5), Look.CLEAN, 1L..6L)
        assertEquals(tally.total, tally.correct)
    }

    @Test
    fun `hostile pencil sheets never turn a mark into another letter or a blank into a mark`() {
        val template = SyntheticSheetRenderer.denseTemplate(8.0, 4, 25, 5)
        val tally = measure(template, Look.PENCIL, 1L..12L)

        assertEquals("a filled letter was read as a different letter", 0, tally.wrongLetter)
        assertEquals("an empty row was read as marked", 0, tally.blankReadAsMarked)
        // Faint pencil may be lost, but only rarely.
        assertTrue("single marks lost: ${tally.singleLost}/${tally.singles}", tally.singleLost <= tally.singles * 0.03)
    }

    @Test
    fun `hostile pencil sheets on tiny bubbles keep the same safety guarantees`() {
        val template = SyntheticSheetRenderer.denseTemplate(6.5, 4, 30, 5)
        val tally = measure(template, Look.PENCIL, 1L..10L)

        assertEquals(0, tally.wrongLetter)
        assertEquals(0, tally.blankReadAsMarked)
        assertTrue("single marks lost: ${tally.singleLost}/${tally.singles}", tally.singleLost <= tally.singles * 0.05)
    }
}
