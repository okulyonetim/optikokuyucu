package com.okulyonetim.optikokuyucu.omr

import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Omr100TemplateTest {
    private val template = StandardOmrTemplate.SAMPLE_100_ABCD

    @Test
    fun containsExactly100QuestionsAnd400Bubbles() {
        assertEquals(100, template.bubbleRows.size)
        assertEquals(400, template.bubbleRows.sumOf { it.bubbles.size })
        assertEquals(100, template.bubbleRows.map { it.id }.toSet().size)
        assertTrue(template.bubbleRows.all { row -> row.bubbles.map { it.id }.toSet() == setOf("A", "B", "C", "D") })
    }

    @Test
    fun everyBubbleIsInsideCanonicalSpace() {
        assertTrue(
            template.bubbleRows.flatMap { it.bubbles }.all { bubble ->
                bubble.center.x - bubble.radius >= 0.0 &&
                    bubble.center.y - bubble.radius >= 0.0 &&
                    bubble.center.x + bubble.radius <= template.space.width &&
                    bubble.center.y + bubble.radius <= template.space.height
            }
        )
    }

    @Test
    fun usesFourColumnsOf25Questions() {
        val rows = template.bubbleRows
        val firstChoiceX = rows.map { it.bubbles.first().center.x }
        val groups = firstChoiceX.groupingBy { it }.eachCount()
        assertEquals(4, groups.size)
        assertTrue(groups.values.all { it == 25 })
    }
}
