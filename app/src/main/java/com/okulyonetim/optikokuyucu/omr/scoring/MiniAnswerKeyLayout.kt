package com.okulyonetim.optikokuyucu.omr.scoring

import kotlin.math.ceil

/**
 * Pure layout policy for mini answer-key sheets.
 *
 * Copy count is allowed to grow for short forms, but only while every mini card still has enough
 * physical height to render all subject blocks at the same compact proportions. This prevents a
 * one-subject/20-question key from being stretched vertically just because the selected A4 grid
 * has a tall slot.
 */
object MiniAnswerKeyLayout {
    val supportedCopies: List<Int> = listOf(2, 4, 6, 8, 10, 12, 16, 20)

    internal const val OUTER_MARGIN = 12f
    internal const val SLOT_GAP = 5f
    internal const val CARD_SIDE_INSET = 1.5f
    internal const val HEADER_HEIGHT = 14f
    internal const val CARD_VERTICAL_PADDING = 3f
    internal const val BLOCK_HEIGHT = 36f
    internal const val BLOCK_GAP = 3f

    data class Grid(val columns: Int, val rows: Int)
    data class SlotSize(val width: Float, val height: Float)

    fun grid(copies: Int, orientation: MiniAnswerKeyPdfExporter.Orientation): Grid {
        require(copies in supportedCopies) { "Desteklenmeyen mini cevap anahtarı sayısı: $copies" }
        return when (copies) {
            2 -> if (orientation == MiniAnswerKeyPdfExporter.Orientation.LANDSCAPE) Grid(2, 1) else Grid(1, 2)
            4 -> Grid(2, 2)
            6 -> if (orientation == MiniAnswerKeyPdfExporter.Orientation.LANDSCAPE) Grid(3, 2) else Grid(2, 3)
            8 -> if (orientation == MiniAnswerKeyPdfExporter.Orientation.LANDSCAPE) Grid(4, 2) else Grid(2, 4)
            10 -> if (orientation == MiniAnswerKeyPdfExporter.Orientation.LANDSCAPE) Grid(5, 2) else Grid(2, 5)
            12 -> if (orientation == MiniAnswerKeyPdfExporter.Orientation.LANDSCAPE) Grid(4, 3) else Grid(2, 6)
            16 -> if (orientation == MiniAnswerKeyPdfExporter.Orientation.LANDSCAPE) Grid(4, 4) else Grid(2, 8)
            else -> if (orientation == MiniAnswerKeyPdfExporter.Orientation.LANDSCAPE) Grid(5, 4) else Grid(2, 10)
        }
    }

    fun slotSize(copies: Int, orientation: MiniAnswerKeyPdfExporter.Orientation): SlotSize {
        val pageWidth = if (orientation == MiniAnswerKeyPdfExporter.Orientation.LANDSCAPE) 842f else 595f
        val pageHeight = if (orientation == MiniAnswerKeyPdfExporter.Orientation.LANDSCAPE) 595f else 842f
        val grid = grid(copies, orientation)
        val usableWidth = pageWidth - OUTER_MARGIN * 2f - SLOT_GAP * (grid.columns - 1)
        val usableHeight = pageHeight - OUTER_MARGIN * 2f - SLOT_GAP * (grid.rows - 1)
        return SlotSize(usableWidth / grid.columns, usableHeight / grid.rows)
    }

    fun maxQuestionsPerBlock(slotWidth: Float): Int = when {
        slotWidth >= 250f -> 20
        slotWidth >= 180f -> 15
        else -> 10
    }

    fun blockCount(sections: List<ManualAnswerSection>, slotWidth: Float): Int {
        val maxQuestions = maxQuestionsPerBlock(slotWidth)
        return sections.sumOf { section ->
            if (section.questionIds.isEmpty()) 0
            else ceil(section.questionIds.size / maxQuestions.toDouble()).toInt()
        }
    }

    fun requiredCardHeight(sections: List<ManualAnswerSection>, slotWidth: Float): Float {
        val blocks = blockCount(sections, slotWidth)
        if (blocks == 0) return HEADER_HEIGHT + CARD_VERTICAL_PADDING * 2f
        return HEADER_HEIGHT +
            CARD_VERTICAL_PADDING * 2f +
            blocks * BLOCK_HEIGHT +
            (blocks - 1) * BLOCK_GAP
    }

    fun fits(
        sections: List<ManualAnswerSection>,
        copies: Int,
        orientation: MiniAnswerKeyPdfExporter.Orientation
    ): Boolean {
        val slot = slotSize(copies, orientation)
        return requiredCardHeight(sections, slot.width - CARD_SIDE_INSET * 2f) <= slot.height + 0.5f
    }

    fun availableCopies(
        sections: List<ManualAnswerSection>,
        orientation: MiniAnswerKeyPdfExporter.Orientation
    ): List<Int> = supportedCopies.filter { fits(sections, it, orientation) }

    fun recommendedCopies(
        sections: List<ManualAnswerSection>,
        orientation: MiniAnswerKeyPdfExporter.Orientation
    ): Int = availableCopies(sections, orientation).lastOrNull() ?: supportedCopies.first()
}
