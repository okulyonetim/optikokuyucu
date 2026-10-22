package com.okulyonetim.optikokuyucu.ocr

import com.okulyonetim.optikokuyucu.omr.scoring.ManualAnswerSection

/** What the review screen should show for one question of a scanned table. */
enum class OcrCellReview {
    /** Read cleanly, or already checked by the user. */
    CLEAN,

    /** Read, but the parser was not sure; the user has to look at it before saving. */
    NEEDS_CHECK,

    /** No usable answer yet. */
    EMPTY
}

/**
 * One booklet's answers as scanned from a table, plus the user's corrections.
 *
 * [table] keeps the original reading (with its doubts) while [answers] is what will be saved. A doubtful cell stays
 * [OcrCellReview.NEEDS_CHECK] until the user confirms or changes it, so an unsure OCR reading never becomes part of
 * an official answer key on its own.
 */
data class OcrBookletDraft(
    val booklet: String?,
    val table: OcrAnswerTable,
    /** Question id -> choice, as it would be stored. */
    val answers: Map<String, String>,
    /** Question ids the user has looked at. */
    val confirmed: Set<String> = emptySet()
) {
    fun review(section: ManualAnswerSection, number: Int): OcrCellReview {
        val questionId = section.questionIds.getOrNull(number - 1) ?: return OcrCellReview.EMPTY
        val answer = answers[questionId]
        if (answer == null || answer !in section.allowedChoices) return OcrCellReview.EMPTY
        val original = originalCell(section, number)
        return if (original != null && original.needsReview && questionId !in confirmed) {
            OcrCellReview.NEEDS_CHECK
        } else {
            OcrCellReview.CLEAN
        }
    }

    fun originalCell(section: ManualAnswerSection, number: Int): OcrTableCell? =
        table.subjects.firstOrNull { it.subject.id == section.id }?.cells?.getOrNull(number - 1)

    fun count(sections: List<ManualAnswerSection>, kind: OcrCellReview): Int = sections.sumOf { section ->
        (1..section.questionIds.size).count { number -> review(section, number) == kind }
    }

    /** Cells that still block saving: empty or unconfirmed doubtful ones. */
    fun pendingCount(sections: List<ManualAnswerSection>): Int =
        count(sections, OcrCellReview.EMPTY) + count(sections, OcrCellReview.NEEDS_CHECK)

    fun answeredCount(sections: List<ManualAnswerSection>): Int =
        sections.sumOf { it.questionIds.size } - count(sections, OcrCellReview.EMPTY)

    /** The user picked [choice]: it replaces the reading and counts as checked. */
    fun withAnswer(questionId: String, choice: String): OcrBookletDraft =
        copy(answers = answers + (questionId to choice), confirmed = confirmed + questionId)

    fun withoutAnswer(questionId: String): OcrBookletDraft =
        copy(answers = answers - questionId, confirmed = confirmed - questionId)

    /** The user compared a subject with the paper and accepts every reading that is currently in doubt. */
    fun withConfirmedSection(section: ManualAnswerSection): OcrBookletDraft {
        val checked = section.questionIds.filterIndexed { index, questionId ->
            questionId in answers && review(section, index + 1) == OcrCellReview.NEEDS_CHECK
        }
        return copy(confirmed = confirmed + checked)
    }

    fun withBooklet(value: String?): OcrBookletDraft = copy(booklet = value)
}

fun List<ManualAnswerSection>.toTableSubjects(): List<OcrTableSubject> = map { section ->
    OcrTableSubject(section.id, section.label, section.questionIds.size, section.allowedChoices)
}

/** Question id -> choice for every cell that was read. */
fun OcrAnswerTable.toAnswerMap(sections: List<ManualAnswerSection>): Map<String, String> {
    val sectionById = sections.associateBy { it.id }
    val answers = linkedMapOf<String, String>()
    subjects.forEach { subject ->
        val section = sectionById[subject.subject.id] ?: return@forEach
        subject.cells.forEach { cell ->
            val choice = cell.choice ?: return@forEach
            val questionId = section.questionIds.getOrNull(cell.number - 1) ?: return@forEach
            answers[questionId] = choice
        }
    }
    return answers
}

fun OcrAnswerTableResult.toDrafts(sections: List<ManualAnswerSection>): List<OcrBookletDraft> =
    tables.map { table -> OcrBookletDraft(table.booklet, table, table.toAnswerMap(sections)) }
