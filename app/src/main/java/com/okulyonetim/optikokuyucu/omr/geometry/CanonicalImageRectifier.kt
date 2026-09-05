package com.okulyonetim.optikokuyucu.omr.geometry

import com.okulyonetim.optikokuyucu.omr.fiducial.FiducialDetectionResult
import com.okulyonetim.optikokuyucu.omr.template.FiducialCorner
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.roundToInt

/**
 * Warps an arbitrary camera/gallery image into the template's unitless canonical space.
 *
 * Physical paper dimensions, printer margins and print scale never enter this transform.
 * The only registration contract is the four fiducials that were printed together with the form.
 */
object CanonicalImageRectifier {
    fun rectify(
        gray: Mat,
        detection: FiducialDetectionResult,
        template: OmrTemplate
    ): Mat? {
        if (gray.empty() || gray.channels() != 1) return null

        val templateByCorner = template.fiducials.associateBy { it.corner }
        fun detectedCenter(corner: FiducialCorner): ImagePoint? {
            val spec = templateByCorner[corner] ?: return null
            return detection.detectedMarkers[spec.markerId]?.center
        }
        fun templateCenter(corner: FiducialCorner): Point? {
            val center = templateByCorner[corner]?.bounds?.center ?: return null
            return Point(center.x, center.y)
        }

        val source = MatOfPoint2f(
            detectedCenter(FiducialCorner.TOP_LEFT)?.toCvPoint() ?: return null,
            detectedCenter(FiducialCorner.TOP_RIGHT)?.toCvPoint() ?: return null,
            detectedCenter(FiducialCorner.BOTTOM_RIGHT)?.toCvPoint() ?: return null,
            detectedCenter(FiducialCorner.BOTTOM_LEFT)?.toCvPoint() ?: return null
        )
        val target = MatOfPoint2f(
            templateCenter(FiducialCorner.TOP_LEFT) ?: return null,
            templateCenter(FiducialCorner.TOP_RIGHT) ?: return null,
            templateCenter(FiducialCorner.BOTTOM_RIGHT) ?: return null,
            templateCenter(FiducialCorner.BOTTOM_LEFT) ?: return null
        )
        val transform = Mat()
        val canonical = Mat()

        return try {
            Imgproc.getPerspectiveTransform(source, target).copyTo(transform)
            Imgproc.warpPerspective(
                gray,
                canonical,
                transform,
                Size(
                    template.space.width.roundToInt().toDouble(),
                    template.space.height.roundToInt().toDouble()
                ),
                Imgproc.INTER_LINEAR,
                org.opencv.core.Core.BORDER_CONSTANT,
                Scalar(255.0)
            )
            canonical
        } catch (_: Throwable) {
            canonical.release()
            null
        } finally {
            transform.release()
            target.release()
            source.release()
        }
    }

    private fun ImagePoint.toCvPoint() = Point(x, y)
}
