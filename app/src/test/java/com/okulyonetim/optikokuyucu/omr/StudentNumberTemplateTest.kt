package com.okulyonetim.optikokuyucu.omr

import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentNumberTemplateTest {
    @Test
    fun `sample student-number grid has six digit columns and ten marks each`() {
        val template = StandardOmrTemplate.SAMPLE_20_ABCD_STUDENT_6
        val grid = template.markGrids.single()

        assertEquals("studentNumber", grid.id)
        assertEquals(6, grid.columns.size)
        assertTrue(grid.columns.all { it.marks.size == 10 })
        assertTrue(grid.columns.all { column ->
            column.marks.map { it.id } == (0..9).map(Int::toString)
        })
    }

    @Test
    fun `student-number marks stay inside canonical space`() {
        val template = StandardOmrTemplate.SAMPLE_20_ABCD_STUDENT_6
        val marks = template.markGrids.flatMap { grid -> grid.columns.flatMap { it.marks } }

        assertEquals(60, marks.size)
        assertTrue(marks.all { mark ->
            mark.center.x - mark.radius >= 0.0 &&
                mark.center.y - mark.radius >= 0.0 &&
                mark.center.x + mark.radius <= template.space.width &&
                mark.center.y + mark.radius <= template.space.height
        })
    }
}
