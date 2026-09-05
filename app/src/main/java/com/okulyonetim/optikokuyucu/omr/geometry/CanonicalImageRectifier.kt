package com.okulyonetim.optikokuyucu.omr.geometry

import com.okulyonetim.optikokuyucu.omr.fiducial.FiducialDetectionResult
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
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
        val registration = detection.canonicalRegistration ?: return null

        val transform = Mat(3, 3, CvType.CV_64FC1)
        val canonical = Mat()
        return try {
            transform.put(0, 0, registration.imageToTemplate.coefficients())
            Imgproc.warpPerspective(
                gray,
                canonical,
                transform,
                Size(
                    template.space.width.roundToInt().toDouble(),
                    template.space.height.roundToInt().toDouble()
                ),
                Imgproc.INTER_LINEAR,
                Core.BORDER_CONSTANT,
                Scalar(255.0)
            )
            canonical
        } catch (_: Throwable) {
            canonical.release()
            null
        } finally {
            transform.release()
        }
    }
}
