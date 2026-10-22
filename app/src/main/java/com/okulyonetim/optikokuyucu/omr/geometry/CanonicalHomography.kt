package com.okulyonetim.optikokuyucu.omr.geometry

import com.okulyonetim.optikokuyucu.omr.template.FiducialCorner
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Projective transform between camera/image pixels and the template's unitless canonical space.
 *
 * The transform intentionally knows nothing about A4, A5, DPI, printer margins or physical size.
 * Any global translation, uniform/non-uniform scale, rotation and perspective introduced by
 * printing and camera pose is absorbed by the homography.
 */
data class ProjectiveTransform internal constructor(
    private val h: DoubleArray
) {
    init {
        require(h.size == 9)
    }

    fun map(x: Double, y: Double): Pair<Double, Double>? {
        val w = h[6] * x + h[7] * y + h[8]
        if (!w.isFinite() || abs(w) < 1e-12) return null

        val mappedX = (h[0] * x + h[1] * y + h[2]) / w
        val mappedY = (h[3] * x + h[4] * y + h[5]) / w
        if (!mappedX.isFinite() || !mappedY.isFinite()) return null
        return mappedX to mappedY
    }

    fun mapImage(point: ImagePoint): TemplatePoint? =
        map(point.x, point.y)?.let { (x, y) -> TemplatePoint(x, y) }

    fun mapTemplate(point: TemplatePoint): ImagePoint? =
        map(point.x, point.y)?.let { (x, y) -> ImagePoint(x, y) }

    /** Internal bridge for OpenCV warpPerspective; callers receive an immutable copy. */
    internal fun coefficients(): DoubleArray = h.copyOf()
}

data class CanonicalRegistration(
    val imageToTemplate: ProjectiveTransform,
    val templateToImage: ProjectiveTransform,
    /**
     * RMS reprojection error normalized by canonical-space diagonal. Lower is better.
     * With exactly four correspondences the transform is exact, so this is 0 by construction and
     * says nothing; a least-squares fit over the 16 marker corners makes it a real consistency check.
     */
    val normalizedReprojectionError: Double
) {
    /** False when the marker corners disagree with the template (wrong form, bent sheet, bad detection). */
    val isReliable: Boolean
        get() = normalizedReprojectionError.isFinite() &&
            normalizedReprojectionError <= CanonicalHomographySolver.MAX_RELIABLE_REPROJECTION_ERROR
}

/** One observed point paired with the position the template says it must have. */
data class PointCorrespondence(
    val image: ImagePoint,
    val template: TemplatePoint
)

object CanonicalHomographySolver {
    /**
     * About half a bubble radius (≈5 canonical units on the 1000×1414 standard sheet). A registration
     * worse than this places bubble sample rings visibly off their targets.
     */
    const val MAX_RELIABLE_REPROJECTION_ERROR = 0.003

    fun solve(
        imageAnchors: ImageQuadrilateral,
        template: OmrTemplate
    ): CanonicalRegistration? {
        val templateByCorner = template.fiducials.associateBy { it.corner }
        val imagePoints = listOf(
            imageAnchors.topLeft,
            imageAnchors.topRight,
            imageAnchors.bottomRight,
            imageAnchors.bottomLeft
        )
        val templatePoints = listOf(
            templateByCorner[FiducialCorner.TOP_LEFT]?.bounds?.center ?: return null,
            templateByCorner[FiducialCorner.TOP_RIGHT]?.bounds?.center ?: return null,
            templateByCorner[FiducialCorner.BOTTOM_RIGHT]?.bounds?.center ?: return null,
            templateByCorner[FiducialCorner.BOTTOM_LEFT]?.bounds?.center ?: return null
        )

        val imageToTemplate = solveFourPointTransform(
            source = imagePoints.map { it.x to it.y },
            target = templatePoints.map { it.x to it.y }
        ) ?: return null

        val templateToImage = solveFourPointTransform(
            source = templatePoints.map { it.x to it.y },
            target = imagePoints.map { it.x to it.y }
        ) ?: return null

        val diagonal = hypot(template.space.width, template.space.height).coerceAtLeast(1e-9)
        var squaredError = 0.0
        for (index in imagePoints.indices) {
            val mapped = imageToTemplate.mapImage(imagePoints[index]) ?: return null
            val expected = templatePoints[index]
            val dx = mapped.x - expected.x
            val dy = mapped.y - expected.y
            squaredError += dx * dx + dy * dy
        }
        val rms = kotlin.math.sqrt(squaredError / imagePoints.size)

        return CanonicalRegistration(
            imageToTemplate = imageToTemplate,
            templateToImage = templateToImage,
            normalizedReprojectionError = rms / diagonal
        )
    }

    /**
     * Least-squares projective fit over any number (≥4) of correspondences, typically the 16 corners
     * of the four fiducial markers instead of only their four centers. Averaging sixteen sub-pixel
     * corners halves the per-point detection noise and, unlike an exact 4-point solve, leaves a
     * non-zero residual when the detected markers do not belong to this template.
     *
     * Both point sets are Hartley-normalized (centroid at the origin, mean distance √2) so the normal
     * equations stay well conditioned for pixel coordinates in the thousands.
     */
    fun solveLeastSquares(
        correspondences: List<PointCorrespondence>,
        template: OmrTemplate
    ): CanonicalRegistration? {
        if (correspondences.size < 4) return null
        val images = correspondences.map { it.image.x to it.image.y }
        val templates = correspondences.map { it.template.x to it.template.y }
        if (images.any { !it.first.isFinite() || !it.second.isFinite() }) return null
        if (templates.any { !it.first.isFinite() || !it.second.isFinite() }) return null

        val imageNorm = normalizationOf(images) ?: return null
        val templateNorm = normalizationOf(templates) ?: return null

        val normal = Array(8) { DoubleArray(9) }
        for (i in correspondences.indices) {
            val (x, y) = applyNormalization(imageNorm, images[i])
            val (u, v) = applyNormalization(templateNorm, templates[i])
            val rowU = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y)
            val rowV = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y)
            accumulateNormalEquations(normal, rowU, u)
            accumulateNormalEquations(normal, rowV, v)
        }

        val solution = gaussianSolve(normal) ?: return null
        val normalized = doubleArrayOf(
            solution[0], solution[1], solution[2],
            solution[3], solution[4], solution[5],
            solution[6], solution[7], 1.0
        )
        // H = T_template⁻¹ · H_n · T_image
        val templateInverse = invert3(normalizationMatrix(templateNorm)) ?: return null
        val h = multiply3(templateInverse, multiply3(normalized, normalizationMatrix(imageNorm)))
        val imageToTemplate = projectiveFrom(h) ?: return null
        val templateToImage = projectiveFrom(invert3(h) ?: return null) ?: return null

        val diagonal = hypot(template.space.width, template.space.height).coerceAtLeast(1e-9)
        var squaredError = 0.0
        for (index in correspondences.indices) {
            val mapped = imageToTemplate.mapImage(correspondences[index].image) ?: return null
            val expected = correspondences[index].template
            val dx = mapped.x - expected.x
            val dy = mapped.y - expected.y
            squaredError += dx * dx + dy * dy
        }
        val rms = sqrt(squaredError / correspondences.size)
        if (!rms.isFinite()) return null

        return CanonicalRegistration(
            imageToTemplate = imageToTemplate,
            templateToImage = templateToImage,
            normalizedReprojectionError = rms / diagonal
        )
    }

    private class Normalization(val centerX: Double, val centerY: Double, val scale: Double)

    private fun normalizationOf(points: List<Pair<Double, Double>>): Normalization? {
        val cx = points.sumOf { it.first } / points.size
        val cy = points.sumOf { it.second } / points.size
        val meanDistance = points.sumOf { hypot(it.first - cx, it.second - cy) } / points.size
        if (!meanDistance.isFinite() || meanDistance < 1e-9) return null
        return Normalization(cx, cy, sqrt(2.0) / meanDistance)
    }

    private fun applyNormalization(n: Normalization, p: Pair<Double, Double>): Pair<Double, Double> =
        (p.first - n.centerX) * n.scale to (p.second - n.centerY) * n.scale

    private fun normalizationMatrix(n: Normalization): DoubleArray = doubleArrayOf(
        n.scale, 0.0, -n.scale * n.centerX,
        0.0, n.scale, -n.scale * n.centerY,
        0.0, 0.0, 1.0
    )

    /** normal += rowᵀ·row (8×8 block) and normal[:, 8] += row·value. */
    private fun accumulateNormalEquations(normal: Array<DoubleArray>, row: DoubleArray, value: Double) {
        for (r in 0 until 8) {
            if (row[r] == 0.0) continue
            for (c in 0 until 8) normal[r][c] += row[r] * row[c]
            normal[r][8] += row[r] * value
        }
    }

    private fun multiply3(a: DoubleArray, b: DoubleArray): DoubleArray {
        val result = DoubleArray(9)
        for (r in 0 until 3) for (c in 0 until 3) {
            var sum = 0.0
            for (k in 0 until 3) sum += a[r * 3 + k] * b[k * 3 + c]
            result[r * 3 + c] = sum
        }
        return result
    }

    private fun invert3(m: DoubleArray): DoubleArray? {
        val c00 = m[4] * m[8] - m[5] * m[7]
        val c01 = m[5] * m[6] - m[3] * m[8]
        val c02 = m[3] * m[7] - m[4] * m[6]
        val determinant = m[0] * c00 + m[1] * c01 + m[2] * c02
        if (!determinant.isFinite() || abs(determinant) < 1e-18) return null
        val inv = doubleArrayOf(
            c00, m[2] * m[7] - m[1] * m[8], m[1] * m[5] - m[2] * m[4],
            c01, m[0] * m[8] - m[2] * m[6], m[2] * m[3] - m[0] * m[5],
            c02, m[1] * m[6] - m[0] * m[7], m[0] * m[4] - m[1] * m[3]
        )
        for (i in inv.indices) inv[i] /= determinant
        return inv.takeIf { values -> values.all { it.isFinite() } }
    }

    private fun projectiveFrom(h: DoubleArray): ProjectiveTransform? {
        val scale = h[8]
        if (!scale.isFinite() || abs(scale) < 1e-12) return null
        val scaled = DoubleArray(9) { h[it] / scale }
        return scaled.takeIf { values -> values.all { it.isFinite() } }?.let { ProjectiveTransform(it) }
    }

    /**
     * Exact four-correspondence projective transform with h22 fixed to 1.
     * Uses Gaussian elimination with partial pivoting; no native OpenCV call is needed here,
     * which keeps the geometry independently unit-testable on the JVM.
     */
    internal fun solveFourPointTransform(
        source: List<Pair<Double, Double>>,
        target: List<Pair<Double, Double>>
    ): ProjectiveTransform? {
        if (source.size != 4 || target.size != 4) return null

        val augmented = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val (x, y) = source[i]
            val (u, v) = target[i]
            if (!x.isFinite() || !y.isFinite() || !u.isFinite() || !v.isFinite()) return null

            val rowX = i * 2
            augmented[rowX][0] = x
            augmented[rowX][1] = y
            augmented[rowX][2] = 1.0
            augmented[rowX][6] = -u * x
            augmented[rowX][7] = -u * y
            augmented[rowX][8] = u

            val rowY = rowX + 1
            augmented[rowY][3] = x
            augmented[rowY][4] = y
            augmented[rowY][5] = 1.0
            augmented[rowY][6] = -v * x
            augmented[rowY][7] = -v * y
            augmented[rowY][8] = v
        }

        val solution = gaussianSolve(augmented) ?: return null
        return ProjectiveTransform(
            doubleArrayOf(
                solution[0], solution[1], solution[2],
                solution[3], solution[4], solution[5],
                solution[6], solution[7], 1.0
            )
        )
    }

    private fun gaussianSolve(matrix: Array<DoubleArray>): DoubleArray? {
        val n = 8
        for (column in 0 until n) {
            var pivotRow = column
            var pivotMagnitude = abs(matrix[column][column])
            for (row in column + 1 until n) {
                val magnitude = abs(matrix[row][column])
                if (magnitude > pivotMagnitude) {
                    pivotMagnitude = magnitude
                    pivotRow = row
                }
            }
            if (!pivotMagnitude.isFinite() || pivotMagnitude < 1e-10) return null

            if (pivotRow != column) {
                val tmp = matrix[column]
                matrix[column] = matrix[pivotRow]
                matrix[pivotRow] = tmp
            }

            val pivot = matrix[column][column]
            for (j in column until n + 1) matrix[column][j] /= pivot

            for (row in 0 until n) {
                if (row == column) continue
                val factor = matrix[row][column]
                if (abs(factor) < 1e-15) continue
                for (j in column until n + 1) {
                    matrix[row][j] -= factor * matrix[column][j]
                }
            }
        }

        return DoubleArray(n) { row -> matrix[row][n] }.takeIf { values ->
            values.all { it.isFinite() }
        }
    }
}
