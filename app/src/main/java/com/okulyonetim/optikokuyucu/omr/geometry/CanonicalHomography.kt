package com.okulyonetim.optikokuyucu.omr.geometry

import com.okulyonetim.optikokuyucu.omr.template.FiducialCorner
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import kotlin.math.abs
import kotlin.math.hypot

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
}

data class CanonicalRegistration(
    val imageToTemplate: ProjectiveTransform,
    val templateToImage: ProjectiveTransform,
    /** RMS reprojection error normalized by canonical-space diagonal. Lower is better. */
    val normalizedReprojectionError: Double
)

object CanonicalHomographySolver {

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
