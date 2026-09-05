package com.okulyonetim.optikokuyucu.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.okulyonetim.optikokuyucu.omr.fiducial.FiducialDetectionResult
import com.okulyonetim.optikokuyucu.omr.fiducial.OpenCvFiducialDetector
import com.okulyonetim.optikokuyucu.omr.tracking.PageLockTracker
import com.okulyonetim.optikokuyucu.omr.tracking.PageTrackingPhase
import kotlin.math.max

/**
 * Live CameraX analyzer.
 *
 * Frames stay in single-channel luminance form. When OpenCV is available, the same Y-plane
 * ByteBuffer is wrapped by OpenCV for fiducial detection; no RGB/Bitmap conversion is used.
 */
class CameraFrameAnalyzer(
    openCvReady: Boolean,
    private val onStats: (CameraFrameStats) -> Unit
) : ImageAnalysis.Analyzer {

    private val fiducialDetector = if (openCvReady) OpenCvFiducialDetector() else null
    private val pageTracker = PageLockTracker()

    private var frameCount = 0
    private var windowStartedAtNs = System.nanoTime()
    private var latestDetection = FiducialDetectionResult.Empty
    private var latestTrackingPhase = PageTrackingPhase.SEARCHING
    private var latestTrackingConfidence = 0.0
    private var latestMotionRatio = 1.0
    private var detectorHealthy = openCvReady

    override fun analyze(image: ImageProxy) {
        try {
            frameCount += 1

            if (fiducialDetector != null) {
                latestDetection = runCatching {
                    fiducialDetector.detect(image)
                }.onFailure {
                    detectorHealthy = false
                }.getOrDefault(FiducialDetectionResult.Empty)

                val tracking = pageTracker.onCandidate(
                    quad = latestDetection.pageQuadrilateral,
                    quality = latestDetection.quality,
                    frameWidth = image.width,
                    frameHeight = image.height
                )
                latestTrackingPhase = tracking.phase
                latestTrackingConfidence = tracking.confidence
                latestMotionRatio = tracking.motionRatio
            }

            val nowNs = System.nanoTime()
            val elapsedNs = nowNs - windowStartedAtNs

            if (elapsedNs >= STATS_WINDOW_NS) {
                val elapsedSeconds = elapsedNs / 1_000_000_000.0
                val fps = if (elapsedSeconds > 0.0) frameCount / elapsedSeconds else 0.0

                onStats(
                    CameraFrameStats(
                        width = image.width,
                        height = image.height,
                        rotationDegrees = image.imageInfo.rotationDegrees,
                        fps = fps,
                        averageLuma = sampleAverageLuma(image),
                        openCvReady = fiducialDetector != null && detectorHealthy,
                        markerCount = latestDetection.detectedMarkers.size,
                        pageConfidence = latestTrackingConfidence,
                        trackingPhase = latestTrackingPhase,
                        motionRatio = latestMotionRatio
                    )
                )

                frameCount = 0
                windowStartedAtNs = nowNs
            }
        } finally {
            image.close()
        }
    }

    private fun sampleAverageLuma(image: ImageProxy): Int {
        val yPlane = image.planes.firstOrNull() ?: return 0
        val buffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride

        if (image.width <= 0 || image.height <= 0 || buffer.limit() <= 0) return 0

        val stepX = max(1, image.width / SAMPLE_COLUMNS)
        val stepY = max(1, image.height / SAMPLE_ROWS)

        var sum = 0L
        var samples = 0

        var y = stepY / 2
        while (y < image.height) {
            val rowOffset = y * rowStride
            var x = stepX / 2

            while (x < image.width) {
                val index = rowOffset + x * pixelStride
                if (index in 0 until buffer.limit()) {
                    sum += buffer.get(index).toInt() and 0xFF
                    samples += 1
                }
                x += stepX
            }
            y += stepY
        }

        return if (samples == 0) 0 else (sum / samples).toInt()
    }

    private companion object {
        const val STATS_WINDOW_NS = 500_000_000L
        const val SAMPLE_COLUMNS = 24
        const val SAMPLE_ROWS = 24
    }
}

data class CameraFrameStats(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val fps: Double,
    val averageLuma: Int,
    val openCvReady: Boolean,
    val markerCount: Int,
    val pageConfidence: Double,
    val trackingPhase: PageTrackingPhase,
    val motionRatio: Double
) {
    companion object {
        val Empty = CameraFrameStats(
            width = 0,
            height = 0,
            rotationDegrees = 0,
            fps = 0.0,
            averageLuma = 0,
            openCvReady = false,
            markerCount = 0,
            pageConfidence = 0.0,
            trackingPhase = PageTrackingPhase.SEARCHING,
            motionRatio = 1.0
        )
    }
}
