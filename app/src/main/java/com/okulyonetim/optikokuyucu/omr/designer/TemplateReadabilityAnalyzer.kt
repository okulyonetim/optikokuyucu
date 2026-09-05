package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class ReadabilitySeverity {
    WARNING,
    ERROR
}

enum class ReadabilityIssueType {
    MARK_TOO_SMALL,
    MARK_OVERLAP,
    MARK_SPACING,
    FIDUCIAL_CLEARANCE,
    EDGE_CLEARANCE
}

data class ReadabilityIssue(
    val severity: ReadabilitySeverity,
    val type: ReadabilityIssueType,
    val elementIds: List<String>,
    val message: String
)

data class TemplateReadabilityReport(
    val score: Int,
    val issues: List<ReadabilityIssue>
) {
    val canSave: Boolean get() = issues.none { it.severity == ReadabilitySeverity.ERROR }
    val errorCount: Int get() = issues.count { it.severity == ReadabilitySeverity.ERROR }
    val warningCount: Int get() = issues.count { it.severity == ReadabilitySeverity.WARNING }
}

/**
 * Fast save-time geometry analysis for form-designer templates.
 *
 * Thresholds are ratios of canonical form width, so they remain independent from A4/A5/DPI.
 * Real-device benchmarks may tune these ratios later without changing editor coordinates.
 */
object TemplateReadabilityAnalyzer {
    fun analyze(template: OmrTemplate): TemplateReadabilityReport {
        val base = min(template.space.width, template.space.height)
        val minRadius = base * MIN_RADIUS_RATIO
        val warningRadius = base * WARNING_RADIUS_RATIO
        val minEdgeGap = base * MIN_EDGE_GAP_RATIO
        val preferredMarkGap = base * PREFERRED_MARK_GAP_RATIO
        val fiducialSafety = base * FIDUCIAL_SAFETY_RATIO

        val issues = mutableListOf<ReadabilityIssue>()
        val marks = collectMarks(template)

        marks.forEach { mark ->
            when {
                mark.radius < minRadius -> issues += ReadabilityIssue(
                    severity = ReadabilitySeverity.ERROR,
                    type = ReadabilityIssueType.MARK_TOO_SMALL,
                    elementIds = listOf(mark.id),
                    message = "İşaret güvenilir okuma için çok küçük."
                )
                mark.radius < warningRadius -> issues += ReadabilityIssue(
                    severity = ReadabilitySeverity.WARNING,
                    type = ReadabilityIssueType.MARK_TOO_SMALL,
                    elementIds = listOf(mark.id),
                    message = "İşaret boyutu düşük çözünürlüklü kamerada riskli olabilir."
                )
            }

            val edgeGap = minOf(
                mark.x - mark.radius,
                mark.y - mark.radius,
                template.space.width - (mark.x + mark.radius),
                template.space.height - (mark.y + mark.radius)
            )
            if (edgeGap < minEdgeGap) {
                issues += ReadabilityIssue(
                    severity = ReadabilitySeverity.WARNING,
                    type = ReadabilityIssueType.EDGE_CLEARANCE,
                    elementIds = listOf(mark.id),
                    message = "İşaret canonical form kenarına çok yakın."
                )
            }

            template.fiducials.forEach { fiducial ->
                if (circleTouchesExpandedRect(mark, fiducial.bounds, fiducialSafety)) {
                    issues += ReadabilityIssue(
                        severity = ReadabilitySeverity.ERROR,
                        type = ReadabilityIssueType.FIDUCIAL_CLEARANCE,
                        elementIds = listOf(mark.id, "marker:${fiducial.markerId}"),
                        message = "OMR işareti marker güvenli alanına giriyor."
                    )
                }
            }
        }

        for (i in 0 until marks.size) {
            val first = marks[i]
            for (j in i + 1 until marks.size) {
                val second = marks[j]
                val distance = hypot(first.x - second.x, first.y - second.y)
                val edgeGap = distance - first.radius - second.radius

                if (edgeGap < 0.0) {
                    issues += ReadabilityIssue(
                        severity = ReadabilitySeverity.ERROR,
                        type = ReadabilityIssueType.MARK_OVERLAP,
                        elementIds = listOf(first.id, second.id),
                        message = "İki OMR işareti birbiriyle çakışıyor."
                    )
                } else if (edgeGap < preferredMarkGap) {
                    issues += ReadabilityIssue(
                        severity = ReadabilitySeverity.WARNING,
                        type = ReadabilityIssueType.MARK_SPACING,
                        elementIds = listOf(first.id, second.id),
                        message = "İki OMR işareti birbirine fazla yakın."
                    )
                }
            }
        }

        val errorCount = issues.count { it.severity == ReadabilitySeverity.ERROR }
        val warningCount = issues.count { it.severity == ReadabilitySeverity.WARNING }
        val score = (100 - errorCount * ERROR_PENALTY - warningCount * WARNING_PENALTY)
            .coerceIn(0, 100)

        return TemplateReadabilityReport(score = score, issues = issues)
    }

    private fun collectMarks(template: OmrTemplate): List<MarkRef> = buildList {
        template.bubbleRows.forEach { row ->
            row.bubbles.forEach { bubble ->
                add(
                    MarkRef(
                        id = "question:${row.id}:${bubble.id}",
                        x = bubble.center.x,
                        y = bubble.center.y,
                        radius = bubble.radius
                    )
                )
            }
        }
        template.markGrids.forEach { grid ->
            grid.columns.forEach { column ->
                column.marks.forEach { mark ->
                    add(
                        MarkRef(
                            id = "grid:${grid.id}:${column.id}:${mark.id}",
                            x = mark.center.x,
                            y = mark.center.y,
                            radius = mark.radius
                        )
                    )
                }
            }
        }
    }

    private fun circleTouchesExpandedRect(
        mark: MarkRef,
        rect: TemplateRect,
        expansion: Double
    ): Boolean {
        val left = rect.left - expansion
        val top = rect.top - expansion
        val right = rect.right + expansion
        val bottom = rect.bottom + expansion
        val nearestX = max(left, min(mark.x, right))
        val nearestY = max(top, min(mark.y, bottom))
        val distance = hypot(mark.x - nearestX, mark.y - nearestY)
        return distance < mark.radius
    }

    private data class MarkRef(
        val id: String,
        val x: Double,
        val y: Double,
        val radius: Double
    )

    private const val MIN_RADIUS_RATIO = 0.006
    private const val WARNING_RADIUS_RATIO = 0.009
    private const val MIN_EDGE_GAP_RATIO = 0.008
    private const val PREFERRED_MARK_GAP_RATIO = 0.006
    private const val FIDUCIAL_SAFETY_RATIO = 0.018
    private const val ERROR_PENALTY = 20
    private const val WARNING_PENALTY = 4
}
