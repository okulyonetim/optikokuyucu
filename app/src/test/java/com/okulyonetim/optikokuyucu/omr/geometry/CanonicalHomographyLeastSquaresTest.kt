package com.okulyonetim.optikokuyucu.omr.geometry

import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class CanonicalHomographyLeastSquaresTest {
    private val template = StandardOmrTemplate.DEFAULT

    /** Ground-truth camera: rotation, scale and mild perspective, template → image pixels. */
    private fun camera(x: Double, y: Double): ImagePoint {
        val angle = Math.toRadians(7.0)
        val scale = 1.25
        val cx = x - 500.0
        val cy = y - 707.0
        val rx = (cos(angle) * cx - sin(angle) * cy) * scale
        val ry = (sin(angle) * cx + cos(angle) * cy) * scale
        val w = 1.0 + 0.00012 * rx + 0.00008 * ry
        return ImagePoint(rx / w + 960.0, ry / w + 540.0)
    }

    private fun markerCorners(bounds: com.okulyonetim.optikokuyucu.omr.template.TemplateRect) = listOf(
        TemplatePoint(bounds.left, bounds.top),
        TemplatePoint(bounds.right, bounds.top),
        TemplatePoint(bounds.right, bounds.bottom),
        TemplatePoint(bounds.left, bounds.bottom)
    )

    private fun correspondences(random: Random?, noise: Double): List<PointCorrespondence> =
        template.fiducials.flatMap { fiducial ->
            markerCorners(fiducial.bounds).map { corner ->
                val seen = camera(corner.x, corner.y)
                val noisy = if (random == null) seen else {
                    ImagePoint(seen.x + random.nextGaussian() * noise, seen.y + random.nextGaussian() * noise)
                }
                PointCorrespondence(noisy, corner)
            }
        }

    @Test
    fun `exact data is recovered with zero residual`() {
        val registration = CanonicalHomographySolver.solveLeastSquares(correspondences(null, 0.0), template)
        assertNotNull(registration)
        assertTrue(registration!!.normalizedReprojectionError < 1e-9)
        assertTrue(registration.isReliable)

        for ((x, y) in listOf(100.0 to 200.0, 500.0 to 700.0, 900.0 to 1300.0)) {
            val expected = camera(x, y)
            val mapped = registration.templateToImage.mapTemplate(TemplatePoint(x, y))!!
            assertEquals(expected.x, mapped.x, 1e-6)
            assertEquals(expected.y, mapped.y, 1e-6)
            val back = registration.imageToTemplate.mapImage(expected)!!
            assertEquals(x, back.x, 1e-6)
            assertEquals(y, back.y, 1e-6)
        }
    }

    @Test
    fun `sixteen corners locate bubbles more accurately than four centers under detection noise`() {
        val random = Random(7)
        val probes = (0 until 120).map {
            TemplatePoint(100.0 + random.nextDouble() * 800.0, 100.0 + random.nextDouble() * 1200.0)
        }
        var leastSquaresError = 0.0
        var fourPointError = 0.0
        val trials = 150

        repeat(trials) {
            val observed = correspondences(random, noise = 0.35)
            val leastSquares = CanonicalHomographySolver.solveLeastSquares(observed, template)!!
            val centers = template.fiducials.indices.map { index ->
                val corners = observed.subList(index * 4, index * 4 + 4).map { it.image }
                ImagePoint(corners.sumOf { it.x } / 4.0, corners.sumOf { it.y } / 4.0)
            }
            val fourPoint = CanonicalHomographySolver.solve(
                ImageQuadrilateral(centers[0], centers[1], centers[2], centers[3]),
                template
            )!!

            fun rms(registration: CanonicalRegistration): Double {
                var squared = 0.0
                for (probe in probes) {
                    val truth = camera(probe.x, probe.y)
                    val mapped = registration.templateToImage.mapTemplate(probe)!!
                    squared += (mapped.x - truth.x).pow(2) + (mapped.y - truth.y).pow(2)
                }
                return sqrt(squared / probes.size)
            }
            leastSquaresError += rms(leastSquares)
            fourPointError += rms(fourPoint)
        }

        assertTrue(
            "LS=${leastSquaresError / trials} 4pt=${fourPointError / trials}",
            leastSquaresError < fourPointError
        )
    }

    @Test
    fun `realistic noise stays far below the reliability limit`() {
        val random = Random(11)
        repeat(100) {
            val registration = CanonicalHomographySolver.solveLeastSquares(correspondences(random, 0.5), template)!!
            assertTrue(registration.isReliable)
            assertTrue(registration.normalizedReprojectionError < CanonicalHomographySolver.MAX_RELIABLE_REPROJECTION_ERROR / 4)
        }
    }

    @Test
    fun `markers that do not match the template are flagged unreliable`() {
        // Same camera, but the printed sheet has its markers in different places than the template says.
        val wrongSheet = template.fiducials.flatMapIndexed { index, fiducial ->
            val shift = if (index % 2 == 0) 45.0 else -30.0
            markerCorners(fiducial.bounds).map { corner ->
                PointCorrespondence(camera(corner.x + shift, corner.y - shift / 2), corner)
            }
        }
        val registration = CanonicalHomographySolver.solveLeastSquares(wrongSheet, template)!!
        assertFalse("error=${registration.normalizedReprojectionError}", registration.isReliable)
    }

    @Test
    fun `fewer than four correspondences or degenerate points are rejected`() {
        assertNull(CanonicalHomographySolver.solveLeastSquares(correspondences(null, 0.0).take(3), template))

        val collapsed = List(8) {
            PointCorrespondence(ImagePoint(10.0, 10.0), TemplatePoint(100.0, 100.0))
        }
        assertNull(CanonicalHomographySolver.solveLeastSquares(collapsed, template))

        val nan = correspondences(null, 0.0).toMutableList().also {
            it[0] = PointCorrespondence(ImagePoint(Double.NaN, 1.0), it[0].template)
        }
        assertNull(CanonicalHomographySolver.solveLeastSquares(nan, template))
    }
}
