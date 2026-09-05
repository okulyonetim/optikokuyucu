package com.okulyonetim.optikokuyucu.omr.template

/**
 * Physical template primitives are expressed in millimetres.
 * Camera pixels are never stored in the template model.
 */
data class MmPoint(
    val x: Double,
    val y: Double
)

data class MmSize(
    val width: Double,
    val height: Double
)

data class MmRect(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double
) {
    val right: Double get() = left + width
    val bottom: Double get() = top + height
    val center: MmPoint get() = MmPoint(left + width / 2.0, top + height / 2.0)

    fun isInside(page: MmSize): Boolean =
        left >= 0.0 && top >= 0.0 && right <= page.width && bottom <= page.height
}

enum class FiducialCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_RIGHT,
    BOTTOM_LEFT
}

data class FiducialSpec(
    val corner: FiducialCorner,
    /** Stable marker id. The detector implementation may later map this to ArUco/AprilTag. */
    val markerId: Int,
    val boundsMm: MmRect
)

data class BubbleSpec(
    val id: String,
    val centerMm: MmPoint,
    val radiusMm: Double
)

data class BubbleRowSpec(
    val id: String,
    val bubbles: List<BubbleSpec>
)

data class OmrTemplate(
    val id: String,
    val version: Int,
    val pageMm: MmSize,
    val fiducials: List<FiducialSpec>,
    val bubbleRows: List<BubbleRowSpec> = emptyList()
) {
    init {
        require(id.isNotBlank())
        require(version > 0)
        require(pageMm.width > 0.0 && pageMm.height > 0.0)
        require(fiducials.size == 4) { "Exactly four fiducials are required." }
        require(fiducials.map { it.corner }.toSet().size == 4) { "Fiducial corners must be unique." }
        require(fiducials.map { it.markerId }.toSet().size == 4) { "Fiducial marker ids must be unique." }
        require(fiducials.all { it.boundsMm.isInside(pageMm) }) { "All fiducials must be inside the page." }
    }
}

object StandardOmrTemplate {
    /** ISO A4 portrait: 210 x 297 mm. */
    val A4_PAGE = MmSize(width = 210.0, height = 297.0)

    private const val MARKER_SIZE_MM = 12.0
    private const val MARKER_INSET_MM = 8.0

    /**
     * Baseline template used by geometry and synthetic tests.
     * Bubble geometry will be layered on top after fiducial tracking is proven.
     */
    val A4: OmrTemplate = OmrTemplate(
        id = "a4-baseline",
        version = 1,
        pageMm = A4_PAGE,
        fiducials = listOf(
            FiducialSpec(
                corner = FiducialCorner.TOP_LEFT,
                markerId = 11,
                boundsMm = MmRect(MARKER_INSET_MM, MARKER_INSET_MM, MARKER_SIZE_MM, MARKER_SIZE_MM)
            ),
            FiducialSpec(
                corner = FiducialCorner.TOP_RIGHT,
                markerId = 22,
                boundsMm = MmRect(
                    A4_PAGE.width - MARKER_INSET_MM - MARKER_SIZE_MM,
                    MARKER_INSET_MM,
                    MARKER_SIZE_MM,
                    MARKER_SIZE_MM
                )
            ),
            FiducialSpec(
                corner = FiducialCorner.BOTTOM_RIGHT,
                markerId = 33,
                boundsMm = MmRect(
                    A4_PAGE.width - MARKER_INSET_MM - MARKER_SIZE_MM,
                    A4_PAGE.height - MARKER_INSET_MM - MARKER_SIZE_MM,
                    MARKER_SIZE_MM,
                    MARKER_SIZE_MM
                )
            ),
            FiducialSpec(
                corner = FiducialCorner.BOTTOM_LEFT,
                markerId = 44,
                boundsMm = MmRect(
                    MARKER_INSET_MM,
                    A4_PAGE.height - MARKER_INSET_MM - MARKER_SIZE_MM,
                    MARKER_SIZE_MM,
                    MARKER_SIZE_MM
                )
            )
        )
    )
}
