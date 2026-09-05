package com.okulyonetim.optikokuyucu.omr.geometry

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Pixel-space point used only by live detection/tracking. */
data class ImagePoint(
    val x: Double,
    val y: Double
)

data class ImageQuadrilateral(
    val topLeft: ImagePoint,
    val topRight: ImagePoint,
    val bottomRight: ImagePoint,
    val bottomLeft: ImagePoint
) {
    val points: List<ImagePoint>
        get() = listOf(topLeft, topRight, bottomRight, bottomLeft)
}

data class QuadrilateralQuality(
    val areaRatio: Double,
    val convexityScore: Double,
    val cornerAngleScore: Double,
    val oppositeEdgeScore: Double,
    val totalScore: Double
)

/**
 * Cheap geometric gate that runs before expensive homography/OMR work.
 * It intentionally does not decide whether a page is an OMR sheet; it only rejects
 * obviously bad four-corner candidates.
 */
object QuadrilateralQualityEvaluator {
    fun evaluate(
        quad: ImageQuadrilateral,
        frameWidth: Int,
        frameHeight: Int
    ): QuadrilateralQuality {
        if (frameWidth <= 0 || frameHeight <= 0) return zero()

        val frameArea = frameWidth.toDouble() * frameHeight.toDouble()
        val area = abs(polygonSignedArea(quad.points))
        val areaRatio = (area / frameArea).coerceIn(0.0, 1.0)

        val convexity = if (isConvex(quad.points)) 1.0 else 0.0
        val angleScore = cornerAngleScore(quad.points)
        val edgeScore = oppositeEdgeSimilarity(quad)

        // The sheet should occupy a useful amount of the camera frame.
        val areaScore = when {
            areaRatio < 0.08 -> areaRatio / 0.08
            areaRatio > 0.92 -> ((1.0 - areaRatio) / 0.08).coerceAtLeast(0.0)
            else -> 1.0
        }

        val total = (
            areaScore * 0.30 +
                convexity * 0.25 +
                angleScore * 0.25 +
                edgeScore * 0.20
            ).coerceIn(0.0, 1.0)

        return QuadrilateralQuality(
            areaRatio = areaRatio,
            convexityScore = convexity,
            cornerAngleScore = angleScore,
            oppositeEdgeScore = edgeScore,
            totalScore = total
        )
    }

    private fun polygonSignedArea(points: List<ImagePoint>): Double {
        var sum = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        return sum / 2.0
    }

    private fun isConvex(points: List<ImagePoint>): Boolean {
        var sign = 0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val c = points[(i + 2) % points.size]
            val cross = cross(a, b, c)
            if (abs(cross) < 1e-9) continue
            val current = if (cross > 0.0) 1 else -1
            if (sign == 0) sign = current else if (sign != current) return false
        }
        return sign != 0
    }

    private fun cornerAngleScore(points: List<ImagePoint>): Double {
        var total = 0.0
        for (i in points.indices) {
            val previous = points[(i + points.size - 1) % points.size]
            val current = points[i]
            val next = points[(i + 1) % points.size]
            val angle = angleDegrees(previous, current, next)
            // Perspective is allowed, but very sharp/flat corners are suspicious.
            val deviation = abs(angle - 90.0)
            total += (1.0 - deviation / 65.0).coerceIn(0.0, 1.0)
        }
        return total / points.size
    }

    private fun oppositeEdgeSimilarity(quad: ImageQuadrilateral): Double {
        val top = distance(quad.topLeft, quad.topRight)
        val bottom = distance(quad.bottomLeft, quad.bottomRight)
        val left = distance(quad.topLeft, quad.bottomLeft)
        val right = distance(quad.topRight, quad.bottomRight)

        val horizontal = ratioScore(top, bottom)
        val vertical = ratioScore(left, right)
        return (horizontal + vertical) / 2.0
    }

    private fun ratioScore(a: Double, b: Double): Double {
        val high = max(a, b)
        val low = min(a, b)
        if (high <= 1e-9) return 0.0
        // Strong perspective can change opposite edge lengths substantially.
        return (low / high / 0.45).coerceIn(0.0, 1.0)
    }

    private fun angleDegrees(a: ImagePoint, center: ImagePoint, b: ImagePoint): Double {
        val ax = a.x - center.x
        val ay = a.y - center.y
        val bx = b.x - center.x
        val by = b.y - center.y
        val magA = hypot(ax, ay)
        val magB = hypot(bx, by)
        if (magA <= 1e-9 || magB <= 1e-9) return 0.0
        val cosine = ((ax * bx + ay * by) / (magA * magB)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosine))
    }

    private fun distance(a: ImagePoint, b: ImagePoint): Double = hypot(a.x - b.x, a.y - b.y)

    private fun cross(a: ImagePoint, b: ImagePoint, c: ImagePoint): Double =
        (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)

    private fun zero() = QuadrilateralQuality(0.0, 0.0, 0.0, 0.0, 0.0)
}
