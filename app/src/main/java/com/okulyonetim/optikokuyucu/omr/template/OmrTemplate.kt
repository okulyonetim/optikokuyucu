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
    /** Stable marker id used for orientation and registration. */
    val markerId: Int,
    /** Marker location in canonical template coordinates, not paper coordinates. */
    val bounds: TemplateRect
)

data class BubbleSpec(
    val id: String,
    /** Bubble center in canonical template coordinates. */
    val center: TemplatePoint,
    /** Radius in canonical template units. Physical printed radius is intentionally unknown. */
    val radius: Double
)

data class BubbleRowSpec(
    val id: String,
    val bubbles: List<BubbleSpec>
)

data class OmrTemplate(
    val id: String,
    val version: Int,
    /** Logical design canvas. It preserves design aspect ratio but has no physical unit. */
    val space: TemplateSize,
    val fiducials: List<FiducialSpec>,
    val bubbleRows: List<BubbleRowSpec> = emptyList()
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
        require(
            bubbleRows.flatMap { it.bubbles }.all {
                it.radius > 0.0 &&
                    it.center.x - it.radius >= 0.0 &&
                    it.center.y - it.radius >= 0.0 &&
                    it.center.x + it.radius <= space.width &&
                    it.center.y + it.radius <= space.height
            }
        ) { "All bubbles must be inside canonical template space." }
    }
}

/**
 * Default portrait form coordinate system.
 *
 * The 1000 x sqrt(2)*1000 canvas only describes the form's logical proportions.
 * It does NOT mean A4, A5, millimetres or pixels. Printing the same form at 50%, 70.7%,
 * 100% or another size does not change these coordinates.
 */
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
}
