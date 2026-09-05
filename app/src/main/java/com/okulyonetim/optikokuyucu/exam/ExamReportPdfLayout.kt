package com.okulyonetim.optikokuyucu.exam

data class ExamReportPdfPageSlice(
    val pageNumber: Int,
    val fromIndex: Int,
    val toIndexExclusive: Int
) {
    val rowCount: Int get() = toIndexExclusive - fromIndex
}

object ExamReportPdfLayout {
    const val ROWS_PER_PAGE = 24

    fun pageSlices(rowCount: Int): List<ExamReportPdfPageSlice> {
        require(rowCount >= 0) { "Satır sayısı negatif olamaz." }
        if (rowCount == 0) {
            return listOf(ExamReportPdfPageSlice(pageNumber = 1, fromIndex = 0, toIndexExclusive = 0))
        }

        return buildList {
            var from = 0
            var page = 1
            while (from < rowCount) {
                val to = minOf(rowCount, from + ROWS_PER_PAGE)
                add(
                    ExamReportPdfPageSlice(
                        pageNumber = page,
                        fromIndex = from,
                        toIndexExclusive = to
                    )
                )
                from = to
                page += 1
            }
        }
    }
}
