package com.okulyonetim.optikokuyucu.omr.gallery

import com.okulyonetim.optikokuyucu.omr.fiducial.FiducialDetectionResult
import com.okulyonetim.optikokuyucu.omr.geometry.CanonicalHomographySolver
import com.okulyonetim.optikokuyucu.omr.geometry.CanonicalRegistration
import com.okulyonetim.optikokuyucu.omr.geometry.ImagePoint
import com.okulyonetim.optikokuyucu.omr.geometry.ImageQuadrilateral
import com.okulyonetim.optikokuyucu.omr.geometry.QuadrilateralQuality
import com.okulyonetim.optikokuyucu.omr.geometry.QuadrilateralQualityEvaluator
import com.okulyonetim.optikokuyucu.omr.template.FiducialCorner
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.geometry.Geometry
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Gallery-only last-resort registration for photographed answer-key sheets whose page border is
 * not returned as one closed Canny contour. A bright paper mask is extracted with Otsu thresholding
 * and the largest page-like quadrilateral is projected back to the template marker centers.
 *
 * This path is intentionally never used by live CameraX scanning.
 */
internal object GalleryBrightPaperRegistrationFallback {
    fun recover(
        gray: Mat,
        template: OmrTemplate,
        detection: FiducialDetectionResult
    ): FiducialDetectionResult? {
        if (gray.empty() || gray.cols() <= 0 || gray.rows() <= 0) return null
        val page = detectBrightPaper(gray, template) ?: return null
        val frameDiagonal = hypot(gray.cols().toDouble(), gray.rows().toDouble()).coerceAtLeast(1.0)
        val hasExpectedMarker = template.fiducials.any { detection.detectedMarkers.containsKey(it.markerId) }

        val candidates = pageRotations(page).mapNotNull { orientedPage ->
            val markerAnchors = projectMarkerAnchors(orientedPage, template) ?: return@mapNotNull null
            val quality = QuadrilateralQualityEvaluator.evaluate(markerAnchors, gray.cols(), gray.rows())
            if (quality.areaRatio < MIN_ANCHOR_AREA_RATIO || quality.totalScore < MIN_ANCHOR_QUALITY) {
                return@mapNotNull null
            }
            val registration = CanonicalHomographySolver.solve(markerAnchors, template)
                ?: return@mapNotNull null
            PageCandidate(
                markerAnchors = markerAnchors,
                quality = quality,
                registration = registration,
                markerFitError = markerFitError(markerAnchors, template, detection, frameDiagonal),
                aspectError = assignedAspectError(orientedPage, template)
            )
        }

        val chosen = if (hasExpectedMarker) {
            candidates
                .filter { it.markerFitError != null && it.markerFitError <= MAX_MARKER_FIT_ERROR }
                .minByOrNull { it.markerFitError ?: Double.MAX_VALUE }
        } else {
            candidates
                .filter { it.aspectError <= MAX_PAGE_ASPECT_ERROR }
                .minByOrNull { it.aspectError }
        } ?: return null

        return detection.copy(
            pageQuadrilateral = chosen.markerAnchors,
            quality = chosen.quality,
            canonicalRegistration = chosen.registration
        )
    }

    private fun detectBrightPaper(gray: Mat, template: OmrTemplate): ImageQuadrilateral? {
        val mask = Mat()
        val closed = Mat()
        val hierarchy = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(15.0, 15.0))
        val contours = mutableListOf<MatOfPoint>()
        return try {
            Imgproc.threshold(
                gray,
                mask,
                0.0,
                255.0,
                Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU
            )
            Imgproc.morphologyEx(mask, closed, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.findContours(
                closed,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val frameArea = gray.cols().toDouble() * gray.rows().toDouble()
            var best: ImageQuadrilateral? = null
            var bestScore = Double.NEGATIVE_INFINITY

            contours.forEach { contour ->
                val area = abs(Geometry.contourArea(contour))
                val areaRatio = area / frameArea
                if (areaRatio < MIN_PAGE_AREA_RATIO || areaRatio > MAX_PAGE_AREA_RATIO) return@forEach

                val curve = MatOfPoint2f(*contour.toArray())
                val approx = MatOfPoint2f()
                try {
                    val perimeter = Geometry.arcLength(curve, true)
                    if (!perimeter.isFinite() || perimeter <= 0.0) return@forEach
                    Geometry.approxPolyDP(curve, approx, perimeter * APPROX_EPSILON_RATIO, true)
                    if (approx.total() != 4L) return@forEach

                    val polygon = MatOfPoint(*approx.toArray())
                    val convex = try {
                        Geometry.isContourConvex(polygon)
                    } finally {
                        polygon.release()
                    }
                    if (!convex) return@forEach

                    val quad = orderQuadrilateral(approx.toArray()) ?: return@forEach
                    val quality = QuadrilateralQualityEvaluator.evaluate(quad, gray.cols(), gray.rows())
                    if (quality.totalScore < MIN_PAGE_QUALITY) return@forEach

                    val aspectError = min(
                        assignedAspectError(quad, template),
                        assignedAspectError(rotatePage(quad, 1), template)
                    )
                    if (aspectError > MAX_PAGE_ASPECT_ERROR) return@forEach

                    val aspectScore = (1.0 - aspectError / MAX_PAGE_ASPECT_ERROR).coerceIn(0.0, 1.0)
                    val score = areaRatio * 0.60 + quality.totalScore * 0.30 + aspectScore * 0.10
                    if (score > bestScore) {
                        best = quad
                        bestScore = score
                    }
                } finally {
                    approx.release()
                    curve.release()
                }
            }
            best
        } finally {
            contours.forEach { it.release() }
            kernel.release()
            hierarchy.release()
            closed.release()
            mask.release()
        }
    }

    private fun projectMarkerAnchors(
        page: ImageQuadrilateral,
        template: OmrTemplate
    ): ImageQuadrilateral? {
        val sourceCorners = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(template.space.width, 0.0),
            Point(template.space.width, template.space.height),
            Point(0.0, template.space.height)
        )
        val destinationCorners = MatOfPoint2f(
            page.topLeft.toCvPoint(),
            page.topRight.toCvPoint(),
            page.bottomRight.toCvPoint(),
            page.bottomLeft.toCvPoint()
        )
        val transform = Geometry.getPerspectiveTransform(sourceCorners, destinationCorners)
        val byCorner = template.fiducials.associateBy { it.corner }
        val markerSource = MatOfPoint2f(
            byCorner[FiducialCorner.TOP_LEFT]?.bounds?.center?.toCvPoint() ?: return null,
            byCorner[FiducialCorner.TOP_RIGHT]?.bounds?.center?.toCvPoint() ?: return null,
            byCorner[FiducialCorner.BOTTOM_RIGHT]?.bounds?.center?.toCvPoint() ?: return null,
            byCorner[FiducialCorner.BOTTOM_LEFT]?.bounds?.center?.toCvPoint() ?: return null
        )
        val markerDestination = MatOfPoint2f()
        return try {
            Core.perspectiveTransform(markerSource, markerDestination, transform)
            val points = markerDestination.toArray()
            if (points.size != 4) return null
            ImageQuadrilateral(
                topLeft = points[0].toImagePoint(),
                topRight = points[1].toImagePoint(),
                bottomRight = points[2].toImagePoint(),
                bottomLeft = points[3].toImagePoint()
            )
        } finally {
            markerDestination.release()
            markerSource.release()
            transform.release()
            destinationCorners.release()
            sourceCorners.release()
        }
    }

    private fun markerFitError(
        markerAnchors: ImageQuadrilateral,
        template: OmrTemplate,
        detection: FiducialDetectionResult,
        frameDiagonal: Double
    ): Double? {
        val projected = mapOf(
            FiducialCorner.TOP_LEFT to markerAnchors.topLeft,
            FiducialCorner.TOP_RIGHT to markerAnchors.topRight,
            FiducialCorner.BOTTOM_RIGHT to markerAnchors.bottomRight,
            FiducialCorner.BOTTOM_LEFT to markerAnchors.bottomLeft
        )
        val errors = template.fiducials.mapNotNull { spec ->
            val actual = detection.detectedMarkers[spec.markerId]?.center ?: return@mapNotNull null
            val expected = projected[spec.corner] ?: return@mapNotNull null
            hypot(actual.x - expected.x, actual.y - expected.y) / frameDiagonal
        }
        return errors.takeIf { it.isNotEmpty() }?.average()
    }

    private fun assignedAspectError(page: ImageQuadrilateral, template: OmrTemplate): Double {
        val width = sqrt(distance(page.topLeft, page.topRight) * distance(page.bottomLeft, page.bottomRight))
        val height = sqrt(distance(page.topLeft, page.bottomLeft) * distance(page.topRight, page.bottomRight))
        if (width <= 1e-6 || height <= 1e-6) return Double.MAX_VALUE
        val ratio = width / height
        val expected = template.space.aspectRatio
        return abs(ratio - expected) / expected.coerceAtLeast(1e-6)
    }

    private fun pageRotations(page: ImageQuadrilateral): List<ImageQuadrilateral> =
        (0 until 4).map { rotatePage(page, it) }

    private fun rotatePage(page: ImageQuadrilateral, offset: Int): ImageQuadrilateral {
        val points = page.points
        val start = ((offset % 4) + 4) % 4
        return ImageQuadrilateral(
            topLeft = points[start],
            topRight = points[(start + 1) % 4],
            bottomRight = points[(start + 2) % 4],
            bottomLeft = points[(start + 3) % 4]
        )
    }

    private fun orderQuadrilateral(points: Array<Point>): ImageQuadrilateral? {
        if (points.size != 4) return null
        val indexed = points.mapIndexed { index, point -> index to point }
        val topLeft = indexed.minByOrNull { (_, point) -> point.x + point.y } ?: return null
        val bottomRight = indexed.maxByOrNull { (_, point) -> point.x + point.y } ?: return null
        val topRight = indexed.maxByOrNull { (_, point) -> point.x - point.y } ?: return null
        val bottomLeft = indexed.minByOrNull { (_, point) -> point.x - point.y } ?: return null
        if (setOf(topLeft.first, topRight.first, bottomRight.first, bottomLeft.first).size != 4) return null
        return ImageQuadrilateral(
            topLeft = topLeft.second.toImagePoint(),
            topRight = topRight.second.toImagePoint(),
            bottomRight = bottomRight.second.toImagePoint(),
            bottomLeft = bottomLeft.second.toImagePoint()
        )
    }

    private fun distance(a: ImagePoint, b: ImagePoint): Double = hypot(a.x - b.x, a.y - b.y)

    private fun ImagePoint.toCvPoint(): Point = Point(x, y)
    private fun com.okulyonetim.optikokuyucu.omr.template.TemplatePoint.toCvPoint(): Point = Point(x, y)
    private fun Point.toImagePoint(): ImagePoint = ImagePoint(x, y)

    private data class PageCandidate(
        val markerAnchors: ImageQuadrilateral,
        val quality: QuadrilateralQuality,
        val registration: CanonicalRegistration,
        val markerFitError: Double?,
        val aspectError: Double
    )

    private const val MIN_PAGE_AREA_RATIO = 0.12
    private const val MAX_PAGE_AREA_RATIO = 0.92
    private const val MIN_PAGE_QUALITY = 0.60
    private const val MAX_PAGE_ASPECT_ERROR = 0.30
    private const val APPROX_EPSILON_RATIO = 0.02
    private const val MIN_ANCHOR_AREA_RATIO = 0.04
    private const val MIN_ANCHOR_QUALITY = 0.60
    private const val MAX_MARKER_FIT_ERROR = 0.08
}
