package com.okulyonetim.optikokuyucu.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.max

/**
 * Lightweight live-camera analyzer used before the real OMR pipeline is attached.
 *
 * The analyzer intentionally reads only the luminance (Y) plane. It does not convert
 * frames to RGB or Bitmap and does not copy the complete frame into a new byte array.
 */
class CameraFrameAnalyzer(
    private val onStats: (CameraFrameStats) -> Unit
) : ImageAnalysis.Analyzer {

    private var frameCount = 0
    private var windowStartedAtNs = System.nanoTime()

    override fun analyze(image: ImageProxy) {
        try {
            frameCount += 1
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
                        averageLuma = sampleAverageLuma(image)
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
    val averageLuma: Int
) {
    companion object {
        val Empty = CameraFrameStats(
            width = 0,
            height = 0,
            rotationDegrees = 0,
            fps = 0.0,
            averageLuma = 0
        )
    }
}
