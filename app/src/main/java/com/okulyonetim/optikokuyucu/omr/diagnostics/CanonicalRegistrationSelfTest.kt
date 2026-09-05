package com.okulyonetim.optikokuyucu.omr.diagnostics

import com.okulyonetim.optikokuyucu.omr.geometry.CanonicalHomographySolver
import com.okulyonetim.optikokuyucu.omr.geometry.ImagePoint
import com.okulyonetim.optikokuyucu.omr.geometry.ImageQuadrilateral
import com.okulyonetim.optikokuyucu.omr.template.FiducialCorner
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import kotlin.math.hypot

/**
 * Printer-free projective registration test.
 *
 * Simulates three ways the same logical form may reach the camera:
 * 1) A4-like form printed smaller (A5 / fit-to-page style),
 * 2) different horizontal and vertical printer scaling + margins,
 * 3) perspective camera pose.
 */
object CanonicalRegistrationSelfTest {
    private val template = StandardOmrTemplate.DEFAULT

    fun run(): RegistrationSelfTestResult {
        val startedAt = System.nanoTime()
        val scenarios = listOf<(Double, Double) -> ImagePoint>(
            { x, y -> ImagePoint(68.0 + x * 0.47, 42.0 + y * 0.47) },
            { x, y -> ImagePoint(91.0 + x * 0.58, 33.0 + y * 0.53) },
            { x, y ->
                val w = 1.0 + 0.00016 * x + 0.00007 * y
                ImagePoint(
                    x = (0.70 * x + 0.055 * y + 135.0) / w,
                    y = (0.028 * x + 0.61 * y + 82.0) / w
                )
            }
        )

        var passed = 0
        var maxRoundTripError = 0.0

        for (scenario in scenarios) {
            val quad = projectAnchors(scenario)
            val registration = CanonicalHomographySolver.solve(quad, template) ?: continue
            val samples = listOf(
                TemplatePoint(500.0, 707.1067811865475),
                TemplatePoint(220.0, 360.0),
                TemplatePoint(790.0, 1110.0),
                TemplatePoint(430.0, 980.0)
            )

            var scenarioOk = true
            for (sample in samples) {
                val image = registration.templateToImage.mapTemplate(sample)
                val recovered = image?.let { registration.imageToTemplate.mapImage(it) }
                if (recovered == null) {
                    scenarioOk = false
                    continue
                }
                val error = hypot(recovered.x - sample.x, recovered.y - sample.y)
                maxRoundTripError = maxOf(maxRoundTripError, error)
                if (error > 1e-4) scenarioOk = false
            }
            if (scenarioOk) passed += 1
        }

        return RegistrationSelfTestResult(
            passed = passed == scenarios.size,
            passedScenarios = passed,
            totalScenarios = scenarios.size,
            maxRoundTripError = maxRoundTripError,
            elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0
        )
    }

    private fun projectAnchors(transform: (Double, Double) -> ImagePoint): ImageQuadrilateral {
        val byCorner = template.fiducials.associateBy { it.corner }
        fun point(corner: FiducialCorner): ImagePoint {
            val canonical = requireNotNull(byCorner[corner]).bounds.center
            return transform(canonical.x, canonical.y)
        }
        return ImageQuadrilateral(
            topLeft = point(FiducialCorner.TOP_LEFT),
            topRight = point(FiducialCorner.TOP_RIGHT),
            bottomRight = point(FiducialCorner.BOTTOM_RIGHT),
            bottomLeft = point(FiducialCorner.BOTTOM_LEFT)
        )
    }
}

data class RegistrationSelfTestResult(
    val passed: Boolean,
    val passedScenarios: Int,
    val totalScenarios: Int,
    val maxRoundTripError: Double,
    val elapsedMs: Double
)
