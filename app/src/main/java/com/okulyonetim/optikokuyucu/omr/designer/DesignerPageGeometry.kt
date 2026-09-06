package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.FiducialCorner
import com.okulyonetim.optikokuyucu.omr.template.FiducialSpec
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import com.okulyonetim.optikokuyucu.omr.template.TemplateSize
import kotlin.math.min

/** Physical paper dimensions used only to establish the page aspect shown by the editor/export UI. */
data class DesignerPaperDimensions(
    val widthMm: Double,
    val heightMm: Double
) {
    init {
        require(widthMm > 0.0 && heightMm > 0.0)
        require(widthMm <= heightMm) { "Paper dimensions must be stored in portrait orientation." }
    }
}

/**
 * Shared page geometry for the unified editor.
 *
 * A4 short side is the canonical density reference: 210 mm = 1000 canonical units.
 * Every supported physical paper uses that same canonical-units-per-millimetre density.
 * This is still a DPI-independent logical coordinate system; camera registration continues to
 * use the four fiducials rather than paper edges. The stable density only guarantees that OMR
 * primitives such as bubbles and user-selected gaps keep the same physical print size when the
 * selected paper size changes.
 */
object DesignerPageGeometry {
    const val CANONICAL_SHORT_SIDE = 1000.0
    const val A4_SHORT_SIDE_MM = 210.0
    const val CANONICAL_UNITS_PER_MM = CANONICAL_SHORT_SIDE / A4_SHORT_SIDE_MM
    private const val FIDUCIAL_SIZE_RATIO = 0.050
    private const val FIDUCIAL_INSET_RATIO = 0.032
    private const val SAFE_MARGIN_RATIO = 0.085

    fun dimensions(paperSize: DesignerPaperSize): DesignerPaperDimensions? = when (paperSize) {
        DesignerPaperSize.A3 -> DesignerPaperDimensions(297.0, 420.0)
        DesignerPaperSize.A4 -> DesignerPaperDimensions(210.0, 297.0)
        DesignerPaperSize.A5 -> DesignerPaperDimensions(148.0, 210.0)
        DesignerPaperSize.A6 -> DesignerPaperDimensions(105.0, 148.0)
        DesignerPaperSize.A7 -> DesignerPaperDimensions(74.0, 105.0)
        DesignerPaperSize.LETTER -> DesignerPaperDimensions(215.9, 279.4)
        DesignerPaperSize.CUSTOM -> null
    }

    /**
     * The phone editor is a normalized workspace, not a life-size sheet preview. All paper sizes
     * therefore use the full available width. Physical size is represented by the canonical page
     * dimensions and by the PDF page profile.
     */
    fun editorDisplayWidthScale(
        paperSize: DesignerPaperSize,
        orientation: DesignerPageOrientation
    ): Double {
        @Suppress("UNUSED_VARIABLE") val ignored = paperSize to orientation
        return 1.0
    }

    fun canonicalSpace(
        paperSize: DesignerPaperSize,
        orientation: DesignerPageOrientation
    ): TemplateSize {
        val dimensions = dimensions(paperSize)
        val portrait = if (dimensions == null) {
            StandardOmrTemplate.DEFAULT_SPACE
        } else {
            TemplateSize(
                width = dimensions.widthMm * CANONICAL_UNITS_PER_MM,
                height = dimensions.heightMm * CANONICAL_UNITS_PER_MM
            )
        }
        return if (orientation == DesignerPageOrientation.PORTRAIT) {
            portrait
        } else {
            TemplateSize(width = portrait.height, height = portrait.width)
        }
    }

    fun fiducialsFor(space: TemplateSize): List<FiducialSpec> {
        val shortSide = min(space.width, space.height)
        val markerSize = shortSide * FIDUCIAL_SIZE_RATIO
        val inset = shortSide * FIDUCIAL_INSET_RATIO
        return listOf(
            FiducialSpec(
                corner = FiducialCorner.TOP_LEFT,
                markerId = 11,
                bounds = TemplateRect(inset, inset, markerSize, markerSize)
            ),
            FiducialSpec(
                corner = FiducialCorner.TOP_RIGHT,
                markerId = 22,
                bounds = TemplateRect(space.width - inset - markerSize, inset, markerSize, markerSize)
            ),
            FiducialSpec(
                corner = FiducialCorner.BOTTOM_RIGHT,
                markerId = 33,
                bounds = TemplateRect(
                    space.width - inset - markerSize,
                    space.height - inset - markerSize,
                    markerSize,
                    markerSize
                )
            ),
            FiducialSpec(
                corner = FiducialCorner.BOTTOM_LEFT,
                markerId = 44,
                bounds = TemplateRect(inset, space.height - inset - markerSize, markerSize, markerSize)
            )
        )
    }

    fun safeArea(space: TemplateSize): TemplateRect {
        val margin = min(space.width, space.height) * SAFE_MARGIN_RATIO
        return TemplateRect(
            left = margin,
            top = margin,
            width = space.width - margin * 2.0,
            height = space.height - margin * 2.0
        )
    }

    fun apply(
        document: DesignerDocument,
        paperSize: DesignerPaperSize = document.formSpec.paperSize,
        orientation: DesignerPageOrientation = document.formSpec.orientation
    ): DesignerDocument {
        val space = canonicalSpace(paperSize, orientation)
        return document.copy(
            space = space,
            fiducials = fiducialsFor(space),
            formSpec = document.formSpec.copy(
                paperSize = paperSize,
                orientation = orientation
            )
        )
    }
}
