package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import com.okulyonetim.optikokuyucu.omr.template.TemplateSize
import kotlin.math.min

/**
 * Physical page size is intentionally isolated from OMR recognition geometry.
 * Values are PDF points (72 pt/in). Canonical coordinates remain unitless.
 */
enum class PdfPageProfile(
    val widthPoints: Int,
    val heightPoints: Int,
    val marginPoints: Double,
    val displayName: String
) {
    A3(widthPoints = 842, heightPoints = 1191, marginPoints = 34.0, displayName = "A3 Dikey"),
    A3_LANDSCAPE(widthPoints = 1191, heightPoints = 842, marginPoints = 34.0, displayName = "A3 Yatay"),
    A4(widthPoints = 595, heightPoints = 842, marginPoints = 24.0, displayName = "A4 Dikey"),
    A4_LANDSCAPE(widthPoints = 842, heightPoints = 595, marginPoints = 24.0, displayName = "A4 Yatay"),
    A5(widthPoints = 420, heightPoints = 595, marginPoints = 18.0, displayName = "A5 Dikey"),
    A5_LANDSCAPE(widthPoints = 595, heightPoints = 420, marginPoints = 18.0, displayName = "A5 Yatay"),
    A6(widthPoints = 298, heightPoints = 420, marginPoints = 12.0, displayName = "A6 Dikey"),
    A6_LANDSCAPE(widthPoints = 420, heightPoints = 298, marginPoints = 12.0, displayName = "A6 Yatay"),
    A7(widthPoints = 210, heightPoints = 298, marginPoints = 9.0, displayName = "A7 Dikey"),
    A7_LANDSCAPE(widthPoints = 298, heightPoints = 210, marginPoints = 9.0, displayName = "A7 Yatay"),
    LETTER(widthPoints = 612, heightPoints = 792, marginPoints = 24.0, displayName = "Letter Dikey"),
    LETTER_LANDSCAPE(widthPoints = 792, heightPoints = 612, marginPoints = 24.0, displayName = "Letter Yatay")
}

data class CanonicalPageTransform(
    val scale: Double,
    val offsetX: Double,
    val offsetY: Double
) {
    init {
        require(scale > 0.0)
    }

    fun map(point: TemplatePoint): TemplatePoint = TemplatePoint(
        x = offsetX + point.x * scale,
        y = offsetY + point.y * scale
    )

    fun map(rect: TemplateRect): TemplateRect = TemplateRect(
        left = offsetX + rect.left * scale,
        top = offsetY + rect.top * scale,
        width = rect.width * scale,
        height = rect.height * scale
    )

    fun length(value: Double): Double = value * scale
}

object DesignerPdfLayout {
    fun fit(
        space: TemplateSize,
        profile: PdfPageProfile
    ): CanonicalPageTransform {
        require(space.width > 0.0 && space.height > 0.0)

        val usableWidth = profile.widthPoints - profile.marginPoints * 2.0
        val usableHeight = profile.heightPoints - profile.marginPoints * 2.0
        require(usableWidth > 0.0 && usableHeight > 0.0)

        val scale = min(
            usableWidth / space.width,
            usableHeight / space.height
        )
        val contentWidth = space.width * scale
        val contentHeight = space.height * scale

        return CanonicalPageTransform(
            scale = scale,
            offsetX = (profile.widthPoints - contentWidth) / 2.0,
            offsetY = (profile.heightPoints - contentHeight) / 2.0
        )
    }
}

fun DesignerFormSpec.pdfProfile(): PdfPageProfile? = when (paperSize) {
    DesignerPaperSize.A3 -> if (orientation == DesignerPageOrientation.PORTRAIT) PdfPageProfile.A3 else PdfPageProfile.A3_LANDSCAPE
    DesignerPaperSize.A4 -> if (orientation == DesignerPageOrientation.PORTRAIT) PdfPageProfile.A4 else PdfPageProfile.A4_LANDSCAPE
    DesignerPaperSize.A5 -> if (orientation == DesignerPageOrientation.PORTRAIT) PdfPageProfile.A5 else PdfPageProfile.A5_LANDSCAPE
    DesignerPaperSize.A6 -> if (orientation == DesignerPageOrientation.PORTRAIT) PdfPageProfile.A6 else PdfPageProfile.A6_LANDSCAPE
    DesignerPaperSize.A7 -> if (orientation == DesignerPageOrientation.PORTRAIT) PdfPageProfile.A7 else PdfPageProfile.A7_LANDSCAPE
    DesignerPaperSize.LETTER -> if (orientation == DesignerPageOrientation.PORTRAIT) PdfPageProfile.LETTER else PdfPageProfile.LETTER_LANDSCAPE
    DesignerPaperSize.CUSTOM -> null
}

fun StructuredFormConfig.pdfProfile(): PdfPageProfile = when (paperSize) {
    StructuredPaperSize.A4 -> if (orientation == StructuredOrientation.PORTRAIT) {
        PdfPageProfile.A4
    } else {
        PdfPageProfile.A4_LANDSCAPE
    }
    StructuredPaperSize.A5 -> if (orientation == StructuredOrientation.PORTRAIT) {
        PdfPageProfile.A5
    } else {
        PdfPageProfile.A5_LANDSCAPE
    }
    StructuredPaperSize.LETTER -> if (orientation == StructuredOrientation.PORTRAIT) {
        PdfPageProfile.LETTER
    } else {
        PdfPageProfile.LETTER_LANDSCAPE
    }
}
