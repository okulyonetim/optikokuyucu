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
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Offline gallery path that shares marker, registration, rectification and bubble engines with
 * the future stable-frame camera path.
 */
object GalleryOmrReader {
    private val template = StandardOmrTemplate.SAMPLE_20_ABCD

    fun read(context: Context, uri: Uri): GalleryOmrResult {
        val decoded = decodeBitmap(context, uri)
        return try {
            readBitmap(decoded)
        } finally {
            decoded.recycle()
        }
    }

    /** Useful for phone-side synthetic/stress benchmarks without creating temporary files. */
    fun readBitmap(source: Bitmap): GalleryOmrResult {
        val startedAt = System.nanoTime()
        val bitmap = source.copy(Bitmap.Config.ARGB_8888, false)

        val rgba = Mat()
        val gray = Mat()
        var canonical: Mat? = null
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)

            val detection = OpenCvFiducialDetector(template).detectGray(gray)
            canonical = CanonicalImageRectifier.rectify(gray, detection, template)
            val bubbles = canonical?.let {
                CanonicalBubbleReader(template).readCanonical(it)
            } ?: BubbleReadResult(emptyList())

            return GalleryOmrResult(
                bitmap = bitmap,
                width = gray.cols(),
                height = gray.rows(),
                detection = detection,
                bubbleResult = bubbles,
                canonicalWidth = canonical?.cols() ?: 0,
                canonicalHeight = canonical?.rows() ?: 0,
                elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0
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
    val elapsedMs: Double
) {
    val markerCount: Int get() = detection.detectedMarkers.size
    val registrationReady: Boolean get() = detection.canonicalRegistration != null
    val rectificationReady: Boolean get() = canonicalWidth > 0 && canonicalHeight > 0
}
