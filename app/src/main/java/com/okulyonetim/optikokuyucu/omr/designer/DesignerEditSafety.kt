package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import kotlin.math.min

/**
 * Direct-editor placement safety.
 *
 * The ordinary page safe area remains intentionally permissive for legacy layouts. Fiducials need
 * a local exclusion zone instead of a larger page-wide margin, otherwise valid dense forms near a
 * side edge would become impossible to edit.
 */
object DesignerEditSafety {
    /** Mirrors the existing readability analyzer's fiducial clearance contract. */
    const val FIDUCIAL_CLEARANCE_RATIO: Double = 0.018

    fun placementIssue(document: DesignerDocument, bounds: TemplateRect): String? {
        val pageSafe = DesignerPageGeometry.safeArea(document.space)
        if (
            bounds.left < pageSafe.left ||
            bounds.top < pageSafe.top ||
            bounds.right > pageSafe.right ||
            bounds.bottom > pageSafe.bottom
        ) {
            return "Öğe kullanılabilir form alanına sığmıyor. Boyutu küçültün veya öğeyi sayfanın içine taşıyın."
        }

        if (fiducialExclusionAreas(document).any { exclusion -> intersects(bounds, exclusion) }) {
            return "Öğe köşe işaretleyicisinin güvenlik alanına taşıyor. Öğeyi biraz daha içeri taşıyın."
        }

        return null
    }

    fun isPlacementSafe(document: DesignerDocument, bounds: TemplateRect): Boolean =
        placementIssue(document, bounds) == null

    fun fiducialExclusionAreas(document: DesignerDocument): List<TemplateRect> {
        val clearance = min(document.space.width, document.space.height) * FIDUCIAL_CLEARANCE_RATIO
        return document.fiducials.map { marker -> expand(marker.bounds, clearance) }
    }

    private fun expand(rect: TemplateRect, amount: Double): TemplateRect = TemplateRect(
        left = rect.left - amount,
        top = rect.top - amount,
        width = rect.width + amount * 2.0,
        height = rect.height + amount * 2.0
    )

    private fun intersects(a: TemplateRect, b: TemplateRect): Boolean =
        a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
}
