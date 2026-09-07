package com.okulyonetim.optikokuyucu.omr.live

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/** Relative focus score used only to rank neighboring locked frames of the same sheet. */
object CanonicalFrameSharpness {
    fun score(gray: Mat): Double {
        if (gray.empty() || gray.channels() != 1 || gray.rows() < 3 || gray.cols() < 3) return 0.0
        val laplacian = Mat()
        val absolute = Mat()
        return try {
            Imgproc.Laplacian(gray, laplacian, CvType.CV_32F, 3)
            Core.convertScaleAbs(laplacian, absolute)
            Core.mean(absolute).`val`.firstOrNull()?.coerceAtLeast(0.0) ?: 0.0
        } catch (_: Throwable) {
            0.0
        } finally {
            absolute.release()
            laplacian.release()
        }
    }
}
