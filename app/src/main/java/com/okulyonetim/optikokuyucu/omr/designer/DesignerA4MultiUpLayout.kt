package com.okulyonetim.optikokuyucu.omr.designer

import kotlin.math.min

data class DesignerA4MultiUpSlot(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double
)

data class DesignerA4MultiUpPlan(
    val sourceProfile: PdfPageProfile,
    val outputProfile: PdfPageProfile,
    val columns: Int,
    val rows: Int
) {
    init {
        require(columns > 0 && rows > 0)
        require(outputProfile == PdfPageProfile.A4 || outputProfile == PdfPageProfile.A4_LANDSCAPE)
    }

    val itemsPerSheet: Int get() = columns * rows
    val cellWidthPoints: Double get() = outputProfile.widthPoints.toDouble() / columns
    val cellHeightPoints: Double get() = outputProfile.heightPoints.toDouble() / rows
    val pageScale: Double
        get() = min(
            cellWidthPoints / sourceProfile.widthPoints,
            cellHeightPoints / sourceProfile.heightPoints
        ).coerceAtMost(1.0)

    fun slot(slotIndex: Int): DesignerA4MultiUpSlot {
        require(slotIndex in 0 until itemsPerSheet)
        val column = slotIndex % columns
        val row = slotIndex / columns
        return DesignerA4MultiUpSlot(
            left = column * cellWidthPoints,
            top = row * cellHeightPoints,
            width = cellWidthPoints,
            height = cellHeightPoints
        )
    }

    fun transform(base: CanonicalPageTransform, slotIndex: Int): CanonicalPageTransform {
        val slot = slot(slotIndex)
        val scaledPageWidth = sourceProfile.widthPoints * pageScale
        val scaledPageHeight = sourceProfile.heightPoints * pageScale
        val pageOffsetX = slot.left + (slot.width - scaledPageWidth) / 2.0
        val pageOffsetY = slot.top + (slot.height - scaledPageHeight) / 2.0
        return CanonicalPageTransform(
            scale = base.scale * pageScale,
            offsetX = pageOffsetX + base.offsetX * pageScale,
            offsetY = pageOffsetY + base.offsetY * pageScale
        )
    }
}

object DesignerA4MultiUpLayout {
    fun planFor(sourceProfile: PdfPageProfile): DesignerA4MultiUpPlan? = when (sourceProfile) {
        PdfPageProfile.A5 -> DesignerA4MultiUpPlan(sourceProfile, PdfPageProfile.A4_LANDSCAPE, 2, 1)
        PdfPageProfile.A5_LANDSCAPE -> DesignerA4MultiUpPlan(sourceProfile, PdfPageProfile.A4, 1, 2)
        PdfPageProfile.A6 -> DesignerA4MultiUpPlan(sourceProfile, PdfPageProfile.A4, 2, 2)
        PdfPageProfile.A6_LANDSCAPE -> DesignerA4MultiUpPlan(sourceProfile, PdfPageProfile.A4_LANDSCAPE, 2, 2)
        PdfPageProfile.A7 -> DesignerA4MultiUpPlan(sourceProfile, PdfPageProfile.A4_LANDSCAPE, 4, 2)
        PdfPageProfile.A7_LANDSCAPE -> DesignerA4MultiUpPlan(sourceProfile, PdfPageProfile.A4, 2, 4)
        else -> null
    }
}