package com.okulyonetim.optikokuyucu.ocr

import com.okulyonetim.optikokuyucu.omr.scoring.ManualAnswerSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrBookletDraftTest {
    private val abcd = setOf("A", "B", "C", "D")
    private val section = ManualAnswerSection("tr", "Türkçe", listOf("tr:1", "tr:2", "tr:3"), abcd)
    private val other = ManualAnswerSection("mat", "Matematik", listOf("mat:1", "mat:2"), abcd)
    private val sections = listOf(section, other)

    private fun table(): OcrAnswerTable = OcrAnswerTable(
        booklet = "A",
        subjects = listOf(
            OcrTableSubjectResult(
                OcrTableSubject("tr", "Türkçe", 3, abcd), true,
                listOf(
                    OcrTableCell(1, "A", OcrCellStatus.OK),
                    OcrTableCell(2, "B", OcrCellStatus.SUSPICIOUS, "düşük güven"),
                    OcrTableCell(3, null, OcrCellStatus.MISSING)
                )
            ),
            OcrTableSubjectResult(
                OcrTableSubject("mat", "Matematik", 2, abcd), true,
                listOf(OcrTableCell(1, "C", OcrCellStatus.OK), OcrTableCell(2, "D", OcrCellStatus.OK))
            )
        )
    )

    private fun draft() = OcrBookletDraft("A", table(), table().toAnswerMap(sections))

    @Test
    fun `table cells become question ids and unknown subjects are ignored`() {
        assertEquals(
            mapOf("tr:1" to "A", "tr:2" to "B", "mat:1" to "C", "mat:2" to "D"),
            table().toAnswerMap(sections)
        )
        assertEquals(mapOf("tr:1" to "A", "tr:2" to "B"), table().toAnswerMap(listOf(section)))
    }

    @Test
    fun `sections become table subjects with their question counts`() {
        val subjects = sections.toTableSubjects()

        assertEquals(listOf("tr", "mat"), subjects.map { it.id })
        assertEquals(listOf(3, 2), subjects.map { it.questionCount })
        assertEquals(abcd, subjects.first().allowedChoices)
    }

    @Test
    fun `doubtful and empty cells block saving until handled`() {
        val draft = draft()

        assertEquals(OcrCellReview.CLEAN, draft.review(section, 1))
        assertEquals(OcrCellReview.NEEDS_CHECK, draft.review(section, 2))
        assertEquals(OcrCellReview.EMPTY, draft.review(section, 3))
        assertEquals(2, draft.pendingCount(sections))
        assertEquals(4, draft.answeredCount(sections))
    }

    @Test
    fun `choosing an answer fills an empty cell and confirms a doubtful one`() {
        val filled = draft().withAnswer("tr:3", "C")
        assertEquals(OcrCellReview.CLEAN, filled.review(section, 3))
        assertEquals(1, filled.pendingCount(sections))

        val confirmed = filled.withAnswer("tr:2", "B")
        assertEquals(OcrCellReview.CLEAN, confirmed.review(section, 2))
        assertEquals(0, confirmed.pendingCount(sections))
        assertEquals("C", confirmed.answers["tr:3"])
    }

    @Test
    fun `confirming a subject accepts doubtful cells but never invents missing ones`() {
        val confirmed = draft().withConfirmedSection(section)

        assertEquals(OcrCellReview.CLEAN, confirmed.review(section, 2))
        assertEquals(OcrCellReview.EMPTY, confirmed.review(section, 3))
        assertEquals(1, confirmed.pendingCount(sections))
    }

    @Test
    fun `clearing a cell makes it empty again`() {
        val cleared = draft().withAnswer("tr:3", "C").withoutAnswer("tr:3")

        assertEquals(OcrCellReview.EMPTY, cleared.review(section, 3))
        assertTrue("tr:3" !in cleared.answers)
    }

    @Test
    fun `an answer outside the allowed choices counts as empty`() {
        val invalid = draft().copy(answers = draft().answers + ("tr:1" to "E"))

        assertEquals(OcrCellReview.EMPTY, invalid.review(section, 1))
    }

    @Test
    fun `result converts to one draft per table with its booklet`() {
        val result = OcrAnswerTableResult(listOf(table(), table().copy(booklet = "B")), emptyList())
        val drafts = result.toDrafts(sections)

        assertEquals(listOf("A", "B"), drafts.map { it.booklet })
        assertEquals(4, drafts.first().answers.size)
        assertEquals(drafts.first().withBooklet("C").booklet, "C")
    }
}
