package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Protects recognition geometry from printable visual ink. */
object DesignerVisualSafetyAnalyzer {
    fun analyze(document: DesignerDocument, template: OmrTemplate): List<ReadabilityIssue> {
        if (document.visualElements.isEmpty()) return emptyList()
        val base = min(template.space.width, template.space.height)
        val bubbleExtraClearance = base * BUBBLE_EXTRA_CLEARANCE_RATIO
        val fiducialClearance = base * FIDUCIAL_CLEARANCE_RATIO
        val marks = collectMarks(template)
        val issues = mutableListOf<ReadabilityIssue>()

        document.visualElements.forEach { element ->
            val bounds = DesignerVisualGeometry.bounds(element)
            if (!bounds.isInside(template.space)) {
                issues += ReadabilityIssue(
                    ReadabilitySeverity.ERROR,
                    ReadabilityIssueType.VISUAL_EDGE_CLEARANCE,
                    listOf("visual:${element.id}"),
                    "Görsel öğe form sınırlarının dışına taşıyor."
                )
            }
            marks.forEach { mark ->
                val guardRadius = max(mark.radius * BUBBLE_RING_MULTIPLIER, mark.radius + bubbleExtraClearance)
                if (visualTouchesCircle(element, mark.center, guardRadius)) {
                    issues += ReadabilityIssue(
                        ReadabilitySeverity.ERROR,
                        ReadabilityIssueType.VISUAL_MARK_CLEARANCE,
                        listOf("visual:${element.id}", mark.id),
                        "Görsel öğe OMR işaretinin güvenli okuma alanına giriyor."
                    )
                }
            }
            template.fiducials.forEach { fiducial ->
                if (visualTouchesRect(element, expand(fiducial.bounds, fiducialClearance))) {
                    issues += ReadabilityIssue(
                        ReadabilitySeverity.ERROR,
                        ReadabilityIssueType.VISUAL_FIDUCIAL_CLEARANCE,
                        listOf("visual:${element.id}", "marker:${fiducial.markerId}"),
                        "Görsel öğe marker güvenli alanına giriyor."
                    )
                }
            }
        }
        return issues
    }

    private fun collectMarks(template: OmrTemplate): List<MarkRef> = buildList {
        template.bubbleRows.forEach { row -> row.bubbles.forEach { bubble ->
            add(MarkRef("question:${row.id}:${bubble.id}", bubble.center, bubble.radius))
        } }
        template.markGrids.forEach { grid -> grid.columns.forEach { column -> column.marks.forEach { mark ->
            add(MarkRef("grid:${grid.id}:${column.id}:${mark.id}", mark.center, mark.radius))
        } } }
    }

    private fun visualTouchesCircle(
        element: DesignerVisualElement,
        center: TemplatePoint,
        radius: Double
    ): Boolean = when (element) {
        is DesignerTextElement -> circleTouchesRect(center, radius, element.bounds)
        is DesignerImageElement -> circleTouchesRect(center, radius, element.bounds)
        is DesignerLineElement -> distancePointToSegment(center, element.start, element.end) <= radius + element.strokeWidth / 2.0
        is DesignerBoxElement -> boxEdges(element).any { (start, end) ->
            distancePointToSegment(center, start, end) <= radius + element.strokeWidth / 2.0
        }
    }

    private fun visualTouchesRect(element: DesignerVisualElement, rect: TemplateRect): Boolean = when (element) {
        is DesignerTextElement -> rectsOverlap(element.bounds, rect)
        is DesignerImageElement -> rectsOverlap(element.bounds, rect)
        is DesignerLineElement -> segmentIntersectsRect(element.start, element.end, expand(rect, element.strokeWidth / 2.0))
        is DesignerBoxElement -> {
            val expanded = expand(rect, element.strokeWidth / 2.0)
            boxEdges(element).any { (start, end) -> segmentIntersectsRect(start, end, expanded) }
        }
    }

    private fun boxEdges(element: DesignerBoxElement): List<Pair<TemplatePoint, TemplatePoint>> {
        val topLeft = TemplatePoint(element.bounds.left, element.bounds.top)
        val topRight = TemplatePoint(element.bounds.right, element.bounds.top)
        val bottomRight = TemplatePoint(element.bounds.right, element.bounds.bottom)
        val bottomLeft = TemplatePoint(element.bounds.left, element.bounds.bottom)
        return listOf(topLeft to topRight, topRight to bottomRight, bottomRight to bottomLeft, bottomLeft to topLeft)
    }

    private fun circleTouchesRect(center: TemplatePoint, radius: Double, rect: TemplateRect): Boolean {
        val nearestX = max(rect.left, min(center.x, rect.right))
        val nearestY = max(rect.top, min(center.y, rect.bottom))
        return hypot(center.x - nearestX, center.y - nearestY) <= radius
    }

    private fun distancePointToSegment(point: TemplatePoint, start: TemplatePoint, end: TemplatePoint): Double {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 1e-12) return hypot(point.x - start.x, point.y - start.y)
        val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(point.x - (start.x + t * dx), point.y - (start.y + t * dy))
    }

    private fun segmentIntersectsRect(start: TemplatePoint, end: TemplatePoint, rect: TemplateRect): Boolean {
        var tMin = 0.0
        var tMax = 1.0
        val dx = end.x - start.x
        val dy = end.y - start.y
        val p = doubleArrayOf(-dx, dx, -dy, dy)
        val q = doubleArrayOf(start.x - rect.left, rect.right - start.x, start.y - rect.top, rect.bottom - start.y)
        for (index in p.indices) {
            val pi = p[index]
            val qi = q[index]
            if (kotlin.math.abs(pi) < 1e-12) {
                if (qi < 0.0) return false
                continue
            }
            val ratio = qi / pi
            if (pi < 0.0) tMin = max(tMin, ratio) else tMax = min(tMax, ratio)
            if (tMin > tMax) return false
        }
        return true
    }

    private fun rectsOverlap(first: TemplateRect, second: TemplateRect): Boolean =
        first.left <= second.right && first.right >= second.left && first.top <= second.bottom && first.bottom >= second.top

    private fun expand(rect: TemplateRect, padding: Double): TemplateRect = TemplateRect(
        rect.left - padding,
        rect.top - padding,
        rect.width + padding * 2.0,
        rect.height + padding * 2.0
    )

    private data class MarkRef(val id: String, val center: TemplatePoint, val radius: Double)

    private const val BUBBLE_RING_MULTIPLIER = 1.90
    private const val BUBBLE_EXTRA_CLEARANCE_RATIO = 0.005
    private const val FIDUCIAL_CLEARANCE_RATIO = 0.018
}
