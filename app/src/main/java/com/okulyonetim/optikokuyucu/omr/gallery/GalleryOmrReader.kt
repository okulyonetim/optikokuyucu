package com.okulyonetim.optikokuyucu.omr.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.okulyonetim.optikokuyucu.omr.bubble.BubbleReadResult
import com.okulyonetim.optikokuyucu.omr.bubble.CanonicalBubbleReader
import com.okulyonetim.optikokuyucu.omr.fiducial.FiducialDetectionResult
import com.okulyonetim.optikokuyucu.omr.fiducial.OpenCvFiducialDetector
import com.okulyonetim.optikokuyucu.omr.geometry.CanonicalImageRectifier
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Offline gallery path sharing the same fiducial, registration, rectification and bubble engines
 * with live CameraX recognition. The caller may supply any compatible logical template.
 */
object GalleryOmrReader {
    fun read(
        context: Context,
        uri: Uri,
        template: OmrTemplate = StandardOmrTemplate.SAMPLE_20_ABCD
    ): GalleryOmrResult {
        val decoded = decodeBitmap(context, uri)
        return try {
            readBitmap(decoded, template)
        } finally {
            decoded.recycle()
        }
    }

    /** Useful for phone-side synthetic/stress benchmarks without creating temporary files. */
    fun readBitmap(
        source: Bitmap,
        template: OmrTemplate = StandardOmrTemplate.SAMPLE_20_ABCD
    ): GalleryOmrResult {
        val startedAt = System.nanoTime()
        val bitmap = source.copy(Bitmap.Config.ARGB_8888, false)

        val rgba = Mat()
        val gray = Mat()
        var canonical: Mat? = null
        try {
            val preprocessingStartedAt = System.nanoTime()
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            val preprocessingMs = nanosToMs(System.nanoTime() - preprocessingStartedAt)

            val markerStartedAt = System.nanoTime()
            val detection = OpenCvFiducialDetector(template).detectGray(gray)
            val markerMs = nanosToMs(System.nanoTime() - markerStartedAt)

            val rectificationStartedAt = System.nanoTime()
            canonical = CanonicalImageRectifier.rectify(gray, detection, template)
            val rectificationMs = nanosToMs(System.nanoTime() - rectificationStartedAt)

            val bubbleStartedAt = System.nanoTime()
            val bubbles = canonical?.let {
                CanonicalBubbleReader(template).readCanonical(it)
            } ?: BubbleReadResult(emptyList())
            val bubbleMs = nanosToMs(System.nanoTime() - bubbleStartedAt)

            return GalleryOmrResult(
                bitmap = bitmap,
                width = gray.cols(),
                height = gray.rows(),
                detection = detection,
                bubbleResult = bubbles,
                canonicalWidth = canonical?.cols() ?: 0,
                canonicalHeight = canonical?.rows() ?: 0,
                preprocessingMs = preprocessingMs,
                markerMs = markerMs,
                rectificationMs = rectificationMs,
                bubbleMs = bubbleMs,
                elapsedMs = nanosToMs(System.nanoTime() - startedAt)
            )
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        } finally {
            canonical?.release()
            gray.release()
            rgba.release()
        }
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val longest = maxOf(width, height)
                if (longest > MAX_DECODE_EDGE) {
                    val scale = MAX_DECODE_EDGE.toDouble() / longest.toDouble()
                    decoder.setTargetSize(
                        (width * scale).toInt().coerceAtLeast(1),
                        (height * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            context.contentResolver.openInputStream(uri).use { stream ->
                requireNotNull(BitmapFactory.decodeStream(stream)) { "Görsel açılamadı." }
            }
        }
    }

    private fun nanosToMs(value: Long): Double = value / 1_000_000.0

    private const val MAX_DECODE_EDGE = 2400
}

data class GalleryOmrResult(
    val bitmap: Bitmap,
    val width: Int,
    val height: Int,
    val detection: FiducialDetectionResult,
    val bubbleResult: BubbleReadResult,
    val canonicalWidth: Int,
    val canonicalHeight: Int,
    val preprocessingMs: Double,
    val markerMs: Double,
    val rectificationMs: Double,
    val bubbleMs: Double,
    val elapsedMs: Double
) {
    val markerCount: Int get() = detection.detectedMarkers.size
    val registrationReady: Boolean get() = detection.canonicalRegistration != null
    val rectificationReady: Boolean get() = canonicalWidth > 0 && canonicalHeight > 0
}
