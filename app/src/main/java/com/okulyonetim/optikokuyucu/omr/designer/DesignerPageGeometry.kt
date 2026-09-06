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
 * The short side is normalized to 1000 canonical units. This keeps editor/reader geometry
 * independent from DPI while preserving the real aspect of each supported physical paper size.
 */
object DesignerPageGeometry {
    const val CANONICAL_SHORT_SIDE = 1000.0
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
     * Relative editor width using A4 in the same orientation as the on-screen reference.
     * A4 fills the available editor width; smaller paper sizes are visibly smaller while A3
     * is capped to the available width because a phone cannot render it wider without a second
     * outer scrolling coordinate system.
     */
    fun editorDisplayWidthScale(
        paperSize: DesignerPaperSize,
        orientation: DesignerPageOrientation
    ): Double {
        val page = dimensions(paperSize) ?: return 1.0
        val reference = requireNotNull(dimensions(DesignerPaperSize.A4))
        val pageWidth = if (orientation == DesignerPageOrientation.PORTRAIT) page.widthMm else page.heightMm
        val referenceWidth = if (orientation == DesignerPageOrientation.PORTRAIT) reference.widthMm else reference.heightMm
        return (pageWidth / referenceWidth).coerceIn(0.20, 1.0)
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
                width = CANONICAL_SHORT_SIDE,
                height = CANONICAL_SHORT_SIDE * dimensions.heightMm / dimensions.widthMm
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
