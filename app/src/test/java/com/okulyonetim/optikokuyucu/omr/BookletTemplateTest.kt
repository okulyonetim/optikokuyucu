package com.okulyonetim.optikokuyucu.omr

import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookletTemplateTest {
    @Test
    fun `combined sample contains student number and A-B booklet grids`() {
        val template = StandardOmrTemplate.SAMPLE_20_ABCD_STUDENT_6_BOOKLET_AB

        assertEquals(listOf("studentNumber", "booklet"), template.markGrids.map { it.id })

        val booklet = template.markGrids.first { it.id == "booklet" }
        assertEquals(1, booklet.columns.size)
        assertEquals("type", booklet.columns.single().id)
        assertEquals(listOf("A", "B"), booklet.columns.single().marks.map { it.id })
    }

    @Test
    fun `booklet marks stay inside canonical form`() {
        val template = StandardOmrTemplate.SAMPLE_20_ABCD_STUDENT_6_BOOKLET_AB
        val booklet = template.markGrids.first { it.id == "booklet" }

        assertTrue(booklet.columns.single().marks.all { mark ->
            mark.center.x - mark.radius >= 0.0 &&
                mark.center.y - mark.radius >= 0.0 &&
                mark.center.x + mark.radius <= template.space.width &&
                mark.center.y + mark.radius <= template.space.height
        })
    }
}
