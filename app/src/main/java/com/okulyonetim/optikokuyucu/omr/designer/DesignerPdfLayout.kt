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
    A4(widthPoints = 595, heightPoints = 842, marginPoints = 24.0, displayName = "A4"),
    A5(widthPoints = 420, heightPoints = 595, marginPoints = 18.0, displayName = "A5")
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
