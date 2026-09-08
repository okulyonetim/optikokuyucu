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
import com.okulyonetim.optikokuyucu.omr.geometry.CanonicalHomographySolver
import com.okulyonetim.optikokuyucu.omr.geometry.CanonicalImageRectifier
import com.okulyonetim.optikokuyucu.omr.geometry.ImagePoint
import com.okulyonetim.optikokuyucu.omr.geometry.ImageQuadrilateral
import com.okulyonetim.optikokuyucu.omr.markgrid.CanonicalMarkGridReader
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkGridReadResult
import com.okulyonetim.optikokuyucu.omr.template.FiducialCorner
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

/**
 * Offline gallery path sharing the same fiducial, registration, rectification and OMR engines
 * with live CameraX recognition. The caller may supply any compatible logical template.
 */
object GalleryOmrReader {
    fun read(
        context: Context,
        uri: Uri,
        template: OmrTemplate = StandardOmrTemplate.SAMPLE_20_ABCD,
        allowFullFrameFallback: Boolean = false
    ): GalleryOmrResult {
        val decoded = decodeBitmap(context, uri)
        return try {
            readBitmap(decoded, template, allowFullFrameFallback)
        } finally {
            decoded.recycle()
        }
    }

    /** Useful for phone-side synthetic/stress benchmarks without creating temporary files. */
    fun readBitmap(
        source: Bitmap,
        template: OmrTemplate = StandardOmrTemplate.SAMPLE_20_ABCD,
        allowFullFrameFallback: Boolean = false
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
            val detection = detectForGallery(gray, template, allowFullFrameFallback)
            val markerMs = nanosToMs(System.nanoTime() - markerStartedAt)

            val rectificationStartedAt = System.nanoTime()
            canonical = CanonicalImageRectifier.rectify(gray, detection, template)
            val rectificationMs = nanosToMs(System.nanoTime() - rectificationStartedAt)

            val bubbleStartedAt = System.nanoTime()
            val bubbles = canonical?.let {
                CanonicalBubbleReader(template).readCanonical(it)
            } ?: BubbleReadResult(emptyList())
            val bubbleMs = nanosToMs(System.nanoTime() - bubbleStartedAt)

            val markGridStartedAt = System.nanoTime()
            val markGrids = canonical?.let {
                CanonicalMarkGridReader(template).readCanonical(it)
            } ?: MarkGridReadResult.Empty
            val markGridMs = nanosToMs(System.nanoTime() - markGridStartedAt)

            val canonicalWidth = canonical?.cols() ?: 0
            val canonicalHeight = canonical?.rows() ?: 0
            val canonicalLuma = canonical?.let { image ->
                ByteArray(canonicalWidth * canonicalHeight).also { pixels ->
                    image.get(0, 0, pixels)
                }
            }

            return GalleryOmrResult(
                bitmap = bitmap,
                width = gray.cols(),
                height = gray.rows(),
                detection = detection,
                bubbleResult = bubbles,
                markGridResult = markGrids,
                canonicalWidth = canonicalWidth,
                canonicalHeight = canonicalHeight,
                preprocessingMs = preprocessingMs,
                markerMs = markerMs,
                rectificationMs = rectificationMs,
                bubbleMs = bubbleMs,
                markGridMs = markGridMs,
                elapsedMs = nanosToMs(System.nanoTime() - startedAt),
                canonicalLuma = canonicalLuma
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

    private fun detectForGallery(
        gray: Mat,
        template: OmrTemplate,
        allowFullFrameFallback: Boolean
    ): FiducialDetectionResult {
        val detector = OpenCvFiducialDetector(template)
        val primary = detector.detectGray(gray)
        if (primary.canonicalRegistration != null) return primary

        val equalized = Mat()
        return try {
            Imgproc.equalizeHist(gray, equalized)
            val enhanced = detector.detectGray(equalized)
            val best = if (enhanced.detectedMarkers.size > primary.detectedMarkers.size) enhanced else primary
            if (best.canonicalRegistration != null) {
                best
            } else if (allowFullFrameFallback) {
                fullFrameFallback(gray, template) ?: best
            } else {
                best
            }
        } finally {
            equalized.release()
        }
    }

    /**
     * Answer-key imports are often screenshots or image exports of the complete form. When the
     * image aspect ratio already matches the canonical form very closely, the full image itself is
     * a safe registration frame even if compression/downscaling prevents ArUco detection.
     * Camera photos with surrounding background normally fail this strict ratio gate and therefore
     * still require the four real markers.
     */
    private fun fullFrameFallback(gray: Mat, template: OmrTemplate): FiducialDetectionResult? {
        if (gray.cols() < MIN_FULL_FRAME_EDGE || gray.rows() < MIN_FULL_FRAME_EDGE) return null
        val imageRatio = gray.cols().toDouble() / gray.rows().toDouble()
        val templateRatio = template.space.aspectRatio
        val relativeError = abs(imageRatio - templateRatio) / templateRatio
        if (relativeError > MAX_FULL_FRAME_RATIO_ERROR) return null

        val specs = template.fiducials.associateBy { it.corner }
        fun projected(corner: FiducialCorner): ImagePoint? {
            val center = specs[corner]?.bounds?.center ?: return null
            return ImagePoint(
                x = center.x / template.space.width * gray.cols().toDouble(),
                y = center.y / template.space.height * gray.rows().toDouble()
            )
        }

        val quad = ImageQuadrilateral(
            topLeft = projected(FiducialCorner.TOP_LEFT) ?: return null,
            topRight = projected(FiducialCorner.TOP_RIGHT) ?: return null,
            bottomRight = projected(FiducialCorner.BOTTOM_RIGHT) ?: return null,
            bottomLeft = projected(FiducialCorner.BOTTOM_LEFT) ?: return null
        )
        val registration = CanonicalHomographySolver.solve(quad, template) ?: return null
        return FiducialDetectionResult(
            detectedMarkers = emptyMap(),
            pageQuadrilateral = quad,
            quality = null,
            canonicalRegistration = registration
        )
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
    private const val MIN_FULL_FRAME_EDGE = 600
    private const val MAX_FULL_FRAME_RATIO_ERROR = 0.03
}

data class GalleryOmrResult(
    val bitmap: Bitmap,
    val width: Int,
    val height: Int,
    val detection: FiducialDetectionResult,
    val bubbleResult: BubbleReadResult,
    val markGridResult: MarkGridReadResult,
    val canonicalWidth: Int,
    val canonicalHeight: Int,
    val preprocessingMs: Double,
    val markerMs: Double,
    val rectificationMs: Double,
    val bubbleMs: Double,
    val markGridMs: Double,
    val elapsedMs: Double,
    val canonicalLuma: ByteArray? = null
) {
    val markerCount: Int get() = detection.detectedMarkers.size
    val registrationReady: Boolean get() = detection.canonicalRegistration != null
    val rectificationReady: Boolean get() = canonicalWidth > 0 && canonicalHeight > 0
}
