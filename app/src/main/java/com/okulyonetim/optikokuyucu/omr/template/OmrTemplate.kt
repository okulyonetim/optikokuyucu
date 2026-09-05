package com.okulyonetim.optikokuyucu.omr.template

/**
 * OMR recognition geometry lives in a logical, unitless template space.
 *
 * IMPORTANT:
 * - No millimetres, DPI, printer margins or physical paper dimensions are stored here.
 * - The same form may be printed on A4, A5 or another paper size.
 * - Recognition is registered from fiducials to this canonical space.
 */
data class TemplatePoint(
    val x: Double,
    val y: Double
)

data class TemplateSize(
    val width: Double,
    val height: Double
) {
    val aspectRatio: Double get() = width / height
}

data class TemplateRect(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double
) {
    val right: Double get() = left + width
    val bottom: Double get() = top + height
    val center: TemplatePoint get() = TemplatePoint(left + width / 2.0, top + height / 2.0)

    fun isInside(space: TemplateSize): Boolean =
        left >= 0.0 &&
            top >= 0.0 &&
            width > 0.0 &&
            height > 0.0 &&
            right <= space.width &&
            bottom <= space.height

    fun cornersClockwise(): List<TemplatePoint> = listOf(
        TemplatePoint(left, top),
        TemplatePoint(right, top),
        TemplatePoint(right, bottom),
        TemplatePoint(left, bottom)
    )
}

enum class FiducialCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_RIGHT,
    BOTTOM_LEFT
}

data class FiducialSpec(
    val corner: FiducialCorner,
    val markerId: Int,
    val bounds: TemplateRect
)

data class BubbleSpec(
    val id: String,
    val center: TemplatePoint,
    val radius: Double
)

data class BubbleRowSpec(
    val id: String,
    val bubbles: List<BubbleSpec>
)

/**
 * One selectable mark column. Example: a student-number digit position with marks 0..9.
 * The same model can later represent booklet A/B, school number, TC/private number, etc.
 */
data class MarkGridColumnSpec(
    val id: String,
    val marks: List<BubbleSpec>
) {
    init {
        require(id.isNotBlank())
        require(marks.isNotEmpty())
        require(marks.map { it.id }.toSet().size == marks.size) {
            "Mark ids must be unique inside a grid column."
        }
    }
}

data class MarkGridSpec(
    val id: String,
    val columns: List<MarkGridColumnSpec>
) {
    init {
        require(id.isNotBlank())
        require(columns.isNotEmpty())
        require(columns.map { it.id }.toSet().size == columns.size) {
            "Mark-grid column ids must be unique."
        }
    }
}

data class OmrTemplate(
    val id: String,
    val version: Int,
    val space: TemplateSize,
    val fiducials: List<FiducialSpec>,
    val bubbleRows: List<BubbleRowSpec> = emptyList(),
    val markGrids: List<MarkGridSpec> = emptyList()
) {
    init {
        require(id.isNotBlank())
        require(version > 0)
        require(space.width > 0.0 && space.height > 0.0)
        require(fiducials.size == 4) { "Exactly four fiducials are required." }
        require(fiducials.map { it.corner }.toSet().size == 4) { "Fiducial corners must be unique." }
        require(fiducials.map { it.markerId }.toSet().size == 4) { "Fiducial marker ids must be unique." }
        require(fiducials.all { it.bounds.isInside(space) }) {
            "All fiducials must be inside canonical template space."
        }
        require(markGrids.map { it.id }.toSet().size == markGrids.size) {
            "Mark-grid ids must be unique."
        }

        val allMarks = buildList {
            addAll(bubbleRows.flatMap { it.bubbles })
            addAll(markGrids.flatMap { grid -> grid.columns.flatMap { it.marks } })
        }
        require(
            allMarks.all {
                it.radius > 0.0 &&
                    it.center.x - it.radius >= 0.0 &&
                    it.center.y - it.radius >= 0.0 &&
                    it.center.x + it.radius <= space.width &&
                    it.center.y + it.radius <= space.height
            }
        ) { "All marks must be inside canonical template space." }
    }
}

object StandardOmrTemplate {
    val DEFAULT_SPACE = TemplateSize(
        width = 1000.0,
        height = 1414.213562373095
    )

    private const val MARKER_SIZE = 58.0
    private const val MARKER_INSET = 38.0

    val DEFAULT: OmrTemplate = OmrTemplate(
        id = "scale-invariant-baseline",
        version = 2,
        space = DEFAULT_SPACE,
        fiducials = listOf(
            FiducialSpec(
                corner = FiducialCorner.TOP_LEFT,
                markerId = 11,
                bounds = TemplateRect(MARKER_INSET, MARKER_INSET, MARKER_SIZE, MARKER_SIZE)
            ),
            FiducialSpec(
                corner = FiducialCorner.TOP_RIGHT,
                markerId = 22,
                bounds = TemplateRect(
                    DEFAULT_SPACE.width - MARKER_INSET - MARKER_SIZE,
                    MARKER_INSET,
                    MARKER_SIZE,
                    MARKER_SIZE
                )
            ),
            FiducialSpec(
                corner = FiducialCorner.BOTTOM_RIGHT,
                markerId = 33,
                bounds = TemplateRect(
                    DEFAULT_SPACE.width - MARKER_INSET - MARKER_SIZE,
                    DEFAULT_SPACE.height - MARKER_INSET - MARKER_SIZE,
                    MARKER_SIZE,
                    MARKER_SIZE
                )
            ),
            FiducialSpec(
                corner = FiducialCorner.BOTTOM_LEFT,
                markerId = 44,
                bounds = TemplateRect(
                    MARKER_INSET,
                    DEFAULT_SPACE.height - MARKER_INSET - MARKER_SIZE,
                    MARKER_SIZE,
                    MARKER_SIZE
                )
            )
        )
    )

    /**
     * First closed-loop test template. The app can render it to an image, the user can mark the
     * image on the phone, and the exact same geometry is then used by gallery and live readers.
     */
    val SAMPLE_20_ABCD: OmrTemplate = DEFAULT.copy(
        id = "sample-20-abcd",
        version = 1,
        bubbleRows = (1..20).map { question ->
            val y = 300.0 + (question - 1) * 48.0
            BubbleRowSpec(
                id = question.toString(),
                bubbles = listOf("A", "B", "C", "D").mapIndexed { index, choice ->
                    BubbleSpec(
                        id = choice,
                        center = TemplatePoint(
                            x = 430.0 + index * 95.0,
                            y = y
                        ),
                        radius = 14.0
                    )
                }
            )
        }
    )

    /**
     * Phase-3 speed/accuracy template: 100 ABCD questions in four 25-question columns.
     * It uses the exact same unitless form space and fiducials as every other template.
     */
    val SAMPLE_100_ABCD: OmrTemplate = DEFAULT.copy(
        id = "sample-100-abcd",
        version = 1,
        bubbleRows = (1..100).map { question ->
            val column = (question - 1) / 25
            val row = (question - 1) % 25
            val firstChoiceX = 110.0 + column * 240.0
            val y = 175.0 + row * 43.0
            BubbleRowSpec(
                id = question.toString(),
                bubbles = listOf("A", "B", "C", "D").mapIndexed { choiceIndex, choice ->
                    BubbleSpec(
                        id = choice,
                        center = TemplatePoint(
                            x = firstChoiceX + choiceIndex * 38.0,
                            y = y
                        ),
                        radius = 10.5
                    )
                }
            )
        }
    )

    /**
     * Phase-4 student-number test form. Six digits are used only as a diagnostic sample;
     * production templates may define any number of columns through [MarkGridSpec].
     */
    val SAMPLE_20_ABCD_STUDENT_6: OmrTemplate = SAMPLE_20_ABCD.copy(
        id = "sample-20-abcd-student-6",
        version = 1,
        markGrids = listOf(
            MarkGridSpec(
                id = "studentNumber",
                columns = (0 until 6).map { position ->
                    MarkGridColumnSpec(
                        id = (position + 1).toString(),
                        marks = (0..9).map { digit ->
                            BubbleSpec(
                                id = digit.toString(),
                                center = TemplatePoint(
                                    x = 115.0 + position * 47.0,
                                    y = 315.0 + digit * 39.0
                                ),
                                radius = 10.5
                            )
                        }
                    )
                }
            )
        )
    )

    /** Diagnostic form proving the same generic mark-grid reader can handle booklet selection. */
    val SAMPLE_20_ABCD_STUDENT_6_BOOKLET_AB: OmrTemplate = SAMPLE_20_ABCD_STUDENT_6.copy(
        id = "sample-20-abcd-student-6-booklet-ab",
        version = 1,
        markGrids = SAMPLE_20_ABCD_STUDENT_6.markGrids +
            MarkGridSpec(
                id = "booklet",
                columns = listOf(
                    MarkGridColumnSpec(
                        id = "type",
                        marks = listOf(
                            BubbleSpec("A", TemplatePoint(155.0, 760.0), 12.0),
                            BubbleSpec("B", TemplatePoint(215.0, 760.0), 12.0)
                        )
                    )
                )
            )
    )
}
