package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.bubble.QuestionState
import com.okulyonetim.optikokuyucu.omr.diagnostics.SyntheticOmrRenderer
import com.okulyonetim.optikokuyucu.omr.gallery.GalleryOmrReader
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkColumnState

/**
 * Phone-side, printer-free end-to-end validation for the currently edited form.
 *
 * It deliberately runs through the same synthetic raster -> fiducial detection -> canonical
 * rectification -> bubble/mark-grid readers used by gallery/live recognition. Geometry-only
 * readability checks are therefore supplemented with an actual recognition round trip.
 */
object DesignerTemplateSelfTest {
    fun run(document: DesignerDocument): DesignerTemplateSelfTestResult {
        val startedAt = System.nanoTime()
        val template = DesignerTemplateCompiler.compile(document)
        val readability = TemplateReadabilityAnalyzer.analyze(document, template)
        if (!readability.canSave) {
            return DesignerTemplateSelfTestResult(
                passed = false,
                markerCount = 0,
                expectedQuestionCount = template.bubbleRows.size,
                correctQuestionCount = 0,
                expectedGridColumnCount = template.markGrids.sumOf { it.columns.size },
                correctGridColumnCount = 0,
                failedIds = listOf("readability"),
                elapsedMs = nanosToMs(System.nanoTime() - startedAt)
            )
        }

        val expectedQuestions = template.bubbleRows.mapIndexed { index, row ->
            row.id to row.bubbles[index % row.bubbles.size].id
        }.toMap()
        val expectedGrids = template.markGrids.associate { grid ->
            grid.id to grid.columns.mapIndexed { index, column ->
                column.id to column.marks[index % column.marks.size].id
            }.toMap()
        }

        val bitmap = SyntheticOmrRenderer.render(
            template = template,
            markedChoicesByRow = expectedQuestions.mapValues { (_, choice) -> setOf(choice) },
            markedGridChoices = expectedGrids.mapValues { (_, columns) ->
                columns.mapValues { (_, value) -> setOf(value) }
            }
        )

        return try {
            val read = GalleryOmrReader.readBitmap(bitmap, template)
            try {
                val failed = mutableListOf<String>()

                val correctQuestions = read.bubbleResult.questions.count { question ->
                    val expected = expectedQuestions[question.questionId]
                    val ok = question.state == QuestionState.MARKED &&
                        question.selectedChoice == expected
                    if (!ok) failed += "Q${question.questionId}"
                    ok
                }
                expectedQuestions.keys
                    .filterNot { expectedId ->
                        read.bubbleResult.questions.any { it.questionId == expectedId }
                    }
                    .forEach { failed += "Q$it:missing" }

                var correctGridColumns = 0
                expectedGrids.forEach { (gridId, expectedColumns) ->
                    val actualGrid = read.markGridResult.grid(gridId)
                    expectedColumns.forEach { (columnId, expectedValue) ->
                        val actual = actualGrid?.columns?.firstOrNull { it.columnId == columnId }
                        val ok = actual?.state == MarkColumnState.MARKED &&
                            actual.selectedValue == expectedValue
                        if (ok) {
                            correctGridColumns += 1
                        } else {
                            failed += "$gridId:$columnId"
                        }
                    }
                }

                val expectedGridColumnCount = expectedGrids.values.sumOf { it.size }
                val markerReady = read.markerCount == template.fiducials.size &&
                    read.registrationReady && read.rectificationReady
                if (!markerReady) failed += "registration"

                DesignerTemplateSelfTestResult(
                    passed = markerReady &&
                        correctQuestions == expectedQuestions.size &&
                        correctGridColumns == expectedGridColumnCount,
                    markerCount = read.markerCount,
                    expectedQuestionCount = expectedQuestions.size,
                    correctQuestionCount = correctQuestions,
                    expectedGridColumnCount = expectedGridColumnCount,
                    correctGridColumnCount = correctGridColumns,
                    failedIds = failed.distinct(),
                    elapsedMs = nanosToMs(System.nanoTime() - startedAt)
                )
            } finally {
                read.bitmap.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun nanosToMs(value: Long): Double = value / 1_000_000.0
}

data class DesignerTemplateSelfTestResult(
    val passed: Boolean,
    val markerCount: Int,
    val expectedQuestionCount: Int,
    val correctQuestionCount: Int,
    val expectedGridColumnCount: Int,
    val correctGridColumnCount: Int,
    val failedIds: List<String>,
    val elapsedMs: Double
)
