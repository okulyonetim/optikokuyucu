package com.okulyonetim.optikokuyucu.omr.fiducial

import androidx.camera.core.ImageProxy
import com.okulyonetim.optikokuyucu.omr.geometry.CanonicalHomographySolver
import com.okulyonetim.optikokuyucu.omr.geometry.CanonicalRegistration
import com.okulyonetim.optikokuyucu.omr.geometry.ImagePoint
import com.okulyonetim.optikokuyucu.omr.geometry.ImageQuadrilateral
import com.okulyonetim.optikokuyucu.omr.geometry.QuadrilateralQuality
import com.okulyonetim.optikokuyucu.omr.geometry.QuadrilateralQualityEvaluator
import com.okulyonetim.optikokuyucu.omr.template.FiducialCorner
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Objdetect

/**
 * Detects the four fiducials directly from CameraX's luminance plane.
 *
 * Page edges and physical paper size are intentionally ignored. The four stable marker IDs
 * define the form coordinate frame. Therefore an identical form printed on A4, A5 or at a
 * different printer scale is still the same OMR template as long as all fiducials remain visible.
 *
 * The Mat wraps the ImageProxy Y ByteBuffer with its native row stride, so no RGB/Bitmap
 * conversion and no full-frame byte-array copy is required for the common pixelStride=1 case.
 */
class OpenCvFiducialDetector(
    private val template: OmrTemplate = StandardOmrTemplate.DEFAULT
) {
    private val dictionary = Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50)
    private val parameters = DetectorParameters().apply {
        // Conservative defaults first. We will tune with real captured sheets later.
        set_cornerRefinementMethod(Objdetect.CORNER_REFINE_SUBPIX)
    }
    private val detector = ArucoDetector(dictionary, parameters)

    private val expectedIdByCorner = template.fiducials.associate {
        it.corner to it.markerId
    }

    fun detect(image: ImageProxy): FiducialDetectionResult {
        val plane = image.planes.firstOrNull() ?: return FiducialDetectionResult.Empty
        if (plane.pixelStride != 1 || image.width <= 0 || image.height <= 0) {
            return FiducialDetectionResult.Empty
        }

        val buffer = plane.buffer.duplicate().apply { rewind() }
        val gray = Mat(
            image.height,
            image.width,
            CvType.CV_8UC1,
            buffer,
            plane.rowStride.toLong()
        )
        val ids = Mat()
        val corners = mutableListOf<Mat>()

        return try {
            detector.detectMarkers(gray, corners, ids)

            val markers = buildMap<Int, DetectedFiducial> {
                for (index in corners.indices) {
                    val idValue = readFlattenedId(ids, index) ?: continue
                    val markerCorners = readMarkerCorners(corners[index]) ?: continue
                    put(
                        idValue,
                        DetectedFiducial(
                            markerId = idValue,
                            corners = markerCorners,
                            center = markerCenter(markerCorners)
                        )
                    )
                }
            }

            val anchorQuad = buildAnchorQuadrilateral(markers)
            val quality = anchorQuad?.let {
                QuadrilateralQualityEvaluator.evaluate(it, image.width, image.height)
            }
            val registration = anchorQuad?.let {
                CanonicalHomographySolver.solve(it, template)
            }

            FiducialDetectionResult(
                detectedMarkers = markers,
                pageQuadrilateral = anchorQuad,
                quality = quality,
                canonicalRegistration = registration
            )
        } finally {
            corners.forEach { it.release() }
            ids.release()
            gray.release()
        }
    }

    private fun readFlattenedId(ids: Mat, index: Int): Int? {
        if (index < 0 || index >= ids.total()) return null
        val columns = ids.cols().coerceAtLeast(1)
        val row = index / columns
        val column = index % columns
        return ids.get(row, column)?.firstOrNull()?.toInt()
    }

    private fun readMarkerCorners(mat: Mat): List<ImagePoint>? {
        if (mat.total() < 4L) return null
        val result = ArrayList<ImagePoint>(4)
        for (cornerIndex in 0 until 4) {
            val value = mat.get(0, cornerIndex) ?: return null
            if (value.size < 2) return null
            result += ImagePoint(value[0], value[1])
        }
        return result
    }

    private fun markerCenter(corners: List<ImagePoint>): ImagePoint = ImagePoint(
        x = corners.sumOf { it.x } / corners.size,
        y = corners.sumOf { it.y } / corners.size
    )

    private fun buildAnchorQuadrilateral(
        markers: Map<Int, DetectedFiducial>
    ): ImageQuadrilateral? {
        fun center(corner: FiducialCorner): ImagePoint? {
            val markerId = expectedIdByCorner[corner] ?: return null
            return markers[markerId]?.center
        }

        return ImageQuadrilateral(
            topLeft = center(FiducialCorner.TOP_LEFT) ?: return null,
            topRight = center(FiducialCorner.TOP_RIGHT) ?: return null,
            bottomRight = center(FiducialCorner.BOTTOM_RIGHT) ?: return null,
            bottomLeft = center(FiducialCorner.BOTTOM_LEFT) ?: return null
        )
    }
}

data class DetectedFiducial(
    val markerId: Int,
    val corners: List<ImagePoint>,
    val center: ImagePoint
)

data class FiducialDetectionResult(
    val detectedMarkers: Map<Int, DetectedFiducial>,
    /** Marker-center quadrilateral. It is an anchor frame, not a detected paper edge. */
    val pageQuadrilateral: ImageQuadrilateral?,
    val quality: QuadrilateralQuality?,
    /** Image-pixel ↔ canonical-template registration; null until all four expected markers exist. */
    val canonicalRegistration: CanonicalRegistration?
) {
    companion object {
        val Empty = FiducialDetectionResult(
            detectedMarkers = emptyMap(),
            pageQuadrilateral = null,
            quality = null,
            canonicalRegistration = null
        )
    }
}
