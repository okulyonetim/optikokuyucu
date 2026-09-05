package com.okulyonetim.optikokuyucu.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExamReportPdfLayoutTest {
    @Test
    fun `empty report still has one printable page`() {
        val pages = ExamReportPdfLayout.pageSlices(0)

        assertEquals(1, pages.size)
        assertEquals(1, pages.single().pageNumber)
        assertEquals(0, pages.single().rowCount)
    }

    @Test
    fun `rows are split on fixed page boundary without loss`() {
        val count = ExamReportPdfLayout.ROWS_PER_PAGE * 2 + 3
        val pages = ExamReportPdfLayout.pageSlices(count)

        assertEquals(3, pages.size)
        assertEquals(ExamReportPdfLayout.ROWS_PER_PAGE, pages[0].rowCount)
        assertEquals(ExamReportPdfLayout.ROWS_PER_PAGE, pages[1].rowCount)
        assertEquals(3, pages[2].rowCount)
        assertEquals(0, pages.first().fromIndex)
        assertEquals(count, pages.last().toIndexExclusive)
        assertEquals(listOf(1, 2, 3), pages.map { it.pageNumber })
    }

    @Test
    fun `negative row count is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExamReportPdfLayout.pageSlices(-1)
        }
    }
}
