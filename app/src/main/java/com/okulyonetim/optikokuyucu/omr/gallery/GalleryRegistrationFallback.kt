package com.okulyonetim.optikokuyucu.omr.gallery

import com.okulyonetim.optikokuyucu.omr.geometry.ImagePoint
import com.okulyonetim.optikokuyucu.omr.geometry.ImageQuadrilateral
import com.okulyonetim.optikokuyucu.omr.template.FiducialCorner

/**
 * Gallery-only recovery for flat/scanned forms where exactly one expected corner marker cannot be
 * decoded after image compression or resizing. Live CameraX recognition never uses this fallback.
 *
 * Three marker centers define an affine page frame. The fourth center is inferred as the missing
 * corner of that frame; the caller must still apply image-bound and geometry-quality gates before
 * accepting it for rectification.
 */
object GalleryRegistrationFallback {
    fun inferAnchorQuadrilateral(
        centers: Map<FiducialCorner, ImagePoint>
    ): ImageQuadrilateral? {
        val expected = setOf(
            FiducialCorner.TOP_LEFT,
            FiducialCorner.TOP_RIGHT,
            FiducialCorner.BOTTOM_RIGHT,
            FiducialCorner.BOTTOM_LEFT
        )
        if (centers.keys.intersect(expected).size != 3) return null

        val topLeft = centers[FiducialCorner.TOP_LEFT]
        val topRight = centers[FiducialCorner.TOP_RIGHT]
        val bottomRight = centers[FiducialCorner.BOTTOM_RIGHT]
        val bottomLeft = centers[FiducialCorner.BOTTOM_LEFT]

        val resolvedTopLeft = topLeft ?: combine(topRight, bottomLeft, bottomRight) ?: return null
        val resolvedTopRight = topRight ?: combine(topLeft, bottomRight, bottomLeft) ?: return null
        val resolvedBottomRight = bottomRight ?: combine(topRight, bottomLeft, topLeft) ?: return null
        val resolvedBottomLeft = bottomLeft ?: combine(topLeft, bottomRight, topRight) ?: return null

        return ImageQuadrilateral(
            topLeft = resolvedTopLeft,
            topRight = resolvedTopRight,
            bottomRight = resolvedBottomRight,
            bottomLeft = resolvedBottomLeft
        )
    }

    private fun combine(a: ImagePoint?, b: ImagePoint?, subtract: ImagePoint?): ImagePoint? {
        if (a == null || b == null || subtract == null) return null
        return ImagePoint(
            x = a.x + b.x - subtract.x,
            y = a.y + b.y - subtract.y
        ).takeIf { it.x.isFinite() && it.y.isFinite() }
    }
}
