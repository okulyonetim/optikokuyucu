package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/** Pure geometry helpers for the Stage 9 mobile precision editor. */
data class DesignerPrecisionFrame(
    val xMm: Double,
    val yMm: Double,
    val widthMm: Double,
    val heightMm: Double
)

data class DesignerViewportPan(val x: Double, val y: Double)

data class DesignerAlignmentGuideMatch(
    val deltaX: Double = 0.0,
    val deltaY: Double = 0.0,
    val verticalGuideX: Double? = null,
    val horizontalGuideY: Double? = null
)

object DesignerMobileViewport {
    const val FIT_ZOOM = 1.0
    const val MIN_ZOOM = 1.0
    const val MAX_ZOOM = 8.0
    private const val DP_PER_INCH = 160.0
    private const val MILLIMETERS_PER_INCH = 25.4

    fun clampZoom(value: Double): Double = value.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /** 100% means Android logical 160 dpi physical scale, while FIT_ZOOM means page-to-viewport fit. */
    fun oneHundredPercentZoom(viewportWidthDp: Double, pageWidthMm: Double): Double {
        require(viewportWidthDp > 0.0)
        require(pageWidthMm > 0.0)
        val pageWidthDp = pageWidthMm * DP_PER_INCH / MILLIMETERS_PER_INCH
        return clampZoom(pageWidthDp / viewportWidthDp)
    }

    fun clampPan(
        viewportWidth: Double,
        viewportHeight: Double,
        zoom: Double,
        panX: Double,
        panY: Double
    ): DesignerViewportPan {
        require(viewportWidth >= 0.0 && viewportHeight >= 0.0)
        val safeZoom = clampZoom(zoom)
        val maxX = viewportWidth * (safeZoom - 1.0) / 2.0
        val maxY = viewportHeight * (safeZoom - 1.0) / 2.0
        return DesignerViewportPan(
            x = panX.coerceIn(-maxX, maxX),
            y = panY.coerceIn(-maxY, maxY)
        )
    }
}

object DesignerMobilePrecision {
    const val ALIGNMENT_GUIDE_MM = 1.25
    const val MIN_VISUAL_SIZE_MM = 2.0

    fun componentFrameMm(document: DesignerDocument, componentId: String): DesignerPrecisionFrame? =
        document.components.firstOrNull { it.id == componentId }
            ?.let(DesignerComponentGeometry::bounds)
            ?.let { frameMm(document, it) }

    fun visualFrameMm(document: DesignerDocument, elementId: String): DesignerPrecisionFrame? =
        document.visualElements.firstOrNull { it.id == elementId }
            ?.let(DesignerVisualGeometry::bounds)
            ?.let { frameMm(document, it) }

    fun componentCanResizeWidth(component: DesignerOmrComponent): Boolean = when (component) {
        is QuestionGroupComponent -> component.choices.size > 1 || component.columns > 1
        is NumericGridComponent -> when (component.orientation) {
            NumericGridOrientation.DIGITS_HORIZONTAL -> component.digits > 1
            NumericGridOrientation.DIGITS_VERTICAL -> component.values.size > 1
        }
        is SingleChoiceComponent -> component.axis == ChoiceAxis.HORIZONTAL && component.choices.size > 1
    }

    fun componentCanResizeHeight(component: DesignerOmrComponent): Boolean = when (component) {
        is QuestionGroupComponent -> component.questionCount > 1 || component.columns > 1 || component.choices.size > 1
        is NumericGridComponent -> when (component.orientation) {
            NumericGridOrientation.DIGITS_HORIZONTAL -> component.values.size > 1
            NumericGridOrientation.DIGITS_VERTICAL -> component.digits > 1
        }
        is SingleChoiceComponent -> component.axis == ChoiceAxis.VERTICAL && component.choices.size > 1
    }

    fun setComponentFrameMm(
        document: DesignerDocument,
        componentId: String,
        xMm: Double,
        yMm: Double,
        widthMm: Double,
        heightMm: Double
    ): DesignerDocument {
        val source = document.components.firstOrNull { it.id == componentId } ?: return document
        val units = DesignerEditorLayout.canonicalUnitsPerMillimeter(document)
        val safe = DesignerPageGeometry.safeArea(document.space)
        val desiredLeft = xMm * units
        val desiredTop = yMm * units
        val desiredWidth = max(widthMm * units, sourceBubbleDiameter(source))
        val desiredHeight = max(heightMm * units, sourceBubbleDiameter(source))
        val resized = resizeComponent(source, desiredLeft, desiredTop, desiredWidth, desiredHeight)
        val bounds = DesignerComponentGeometry.bounds(resized)
        val left = desiredLeft.coerceIn(safe.left, max(safe.left, safe.right - bounds.width))
        val top = desiredTop.coerceIn(safe.top, max(safe.top, safe.bottom - bounds.height))
        val placed = moveExact(resized, left - bounds.left, top - bounds.top)
        val placedBounds = DesignerComponentGeometry.bounds(placed)
        if (placedBounds.left < safe.left || placedBounds.top < safe.top || placedBounds.right > safe.right || placedBounds.bottom > safe.bottom) {
            return document
        }
        return document.copy(components = document.components.map { if (it.id == componentId) placed else it })
    }

    fun setVisualFrameMm(
        document: DesignerDocument,
        elementId: String,
        xMm: Double,
        yMm: Double,
        widthMm: Double,
        heightMm: Double
    ): DesignerDocument {
        val source = document.visualElements.firstOrNull { it.id == elementId } ?: return document
        if (source.locked) return document
        val units = DesignerEditorLayout.canonicalUnitsPerMillimeter(document)
        val safe = DesignerPageGeometry.safeArea(document.space)
        val minSize = MIN_VISUAL_SIZE_MM * units
        val width = (widthMm * units).coerceIn(minSize, safe.width)
        val height = (heightMm * units).coerceIn(minSize, safe.height)
        val left = (xMm * units).coerceIn(safe.left, max(safe.left, safe.right - width))
        val top = (yMm * units).coerceIn(safe.top, max(safe.top, safe.bottom - height))
        val target = TemplateRect(left, top, width, height)
        val updated = when (source) {
            is DesignerTextElement -> source.copy(bounds = target)
            is DesignerImageElement -> source.copy(bounds = target)
            is DesignerBoxElement -> source.copy(bounds = target)
            is DesignerLineElement -> resizeLineToBounds(source, target)
        }
        return document.copy(visualElements = document.visualElements.map { if (it.id == elementId) updated else it })
    }

    fun translateComponentExact(document: DesignerDocument, componentId: String, deltaX: Double, deltaY: Double): DesignerDocument =
        document.copy(components = document.components.map { if (it.id == componentId) moveExact(it, deltaX, deltaY) else it })

    fun translateVisualExact(document: DesignerDocument, elementId: String, deltaX: Double, deltaY: Double): DesignerDocument =
        document.copy(visualElements = document.visualElements.map { element ->
            if (element.id != elementId || element.locked) element else moveVisualExact(element, deltaX, deltaY)
        })

    fun resolveAlignmentGuides(
        moving: TemplateRect,
        stationary: List<TemplateRect>,
        safeArea: TemplateRect,
        tolerance: Double
    ): DesignerAlignmentGuideMatch {
        require(tolerance >= 0.0)
        val xGuides = buildList {
            add(safeArea.left); add(safeArea.left + safeArea.width / 2.0); add(safeArea.right)
            stationary.forEach { add(it.left); add(it.left + it.width / 2.0); add(it.right) }
        }
        val yGuides = buildList {
            add(safeArea.top); add(safeArea.top + safeArea.height / 2.0); add(safeArea.bottom)
            stationary.forEach { add(it.top); add(it.top + it.height / 2.0); add(it.bottom) }
        }
        val xMatch = nearestGuide(listOf(moving.left, moving.left + moving.width / 2.0, moving.right), xGuides, tolerance)
        val yMatch = nearestGuide(listOf(moving.top, moving.top + moving.height / 2.0, moving.bottom), yGuides, tolerance)
        return DesignerAlignmentGuideMatch(
            deltaX = xMatch?.first ?: 0.0,
            deltaY = yMatch?.first ?: 0.0,
            verticalGuideX = xMatch?.second,
            horizontalGuideY = yMatch?.second
        )
    }

    private fun frameMm(document: DesignerDocument, rect: TemplateRect): DesignerPrecisionFrame {
        val units = DesignerEditorLayout.canonicalUnitsPerMillimeter(document)
        return DesignerPrecisionFrame(rect.left / units, rect.top / units, rect.width / units, rect.height / units)
    }

    private fun sourceBubbleDiameter(component: DesignerOmrComponent): Double = when (component) {
        is QuestionGroupComponent -> component.bubbleRadius * 2.0
        is NumericGridComponent -> component.bubbleRadius * 2.0
        is SingleChoiceComponent -> component.bubbleRadius * 2.0
    }

    private fun minGap(radius: Double): Double = radius * 2.0 + 3.0

    private fun spacingForSpan(span: Double, count: Int, radius: Double, fallback: Double): Double =
        if (count <= 1) fallback else ((span - radius * 2.0) / (count - 1).toDouble()).coerceAtLeast(minGap(radius))

    private fun resizeComponent(
        component: DesignerOmrComponent,
        left: Double,
        top: Double,
        width: Double,
        height: Double
    ): DesignerOmrComponent = when (component) {
        is NumericGridComponent -> resizeNumeric(component, left, top, width, height)
        is SingleChoiceComponent -> resizeChoice(component, left, top, width, height)
        is QuestionGroupComponent -> resizeQuestion(component, left, top, width, height)
    }

    private fun resizeNumeric(component: NumericGridComponent, left: Double, top: Double, width: Double, height: Double): NumericGridComponent {
        val columnGap: Double
        val rowGap: Double
        when (component.orientation) {
            NumericGridOrientation.DIGITS_HORIZONTAL -> {
                columnGap = spacingForSpan(width, component.digits, component.bubbleRadius, component.columnGap)
                rowGap = spacingForSpan(height, component.values.size, component.bubbleRadius, component.rowGap)
            }
            NumericGridOrientation.DIGITS_VERTICAL -> {
                rowGap = spacingForSpan(width, component.values.size, component.bubbleRadius, component.rowGap)
                columnGap = spacingForSpan(height, component.digits, component.bubbleRadius, component.columnGap)
            }
        }
        return component.copy(
            startX = left + component.bubbleRadius,
            topY = top + component.bubbleRadius,
            columnGap = columnGap,
            rowGap = rowGap
        )
    }

    private fun resizeChoice(component: SingleChoiceComponent, left: Double, top: Double, width: Double, height: Double): SingleChoiceComponent {
        val span = if (component.axis == ChoiceAxis.HORIZONTAL) width else height
        val gap = spacingForSpan(span, component.choices.size, component.bubbleRadius, component.gap)
        return component.copy(
            start = TemplatePoint(left + component.bubbleRadius, top + component.bubbleRadius),
            gap = gap
        )
    }

    private fun resizeQuestion(component: QuestionGroupComponent, left: Double, top: Double, width: Double, height: Double): QuestionGroupComponent {
        val questionsPerBlock = ceil(component.questionCount.toDouble() / component.columns.toDouble()).toInt().coerceAtLeast(1)
        val usedBlocks = ((component.questionCount - 1) / questionsPerBlock) + 1
        val questionsInFirstBlock = minOf(questionsPerBlock, component.questionCount)
        var choiceGap = component.choiceGap
        var rowGap = component.rowGap
        var columnGap = component.columnGap
        when (component.orientation) {
            QuestionGroupOrientation.VERTICAL -> {
                rowGap = spacingForSpan(height, questionsInFirstBlock, component.bubbleRadius, rowGap)
                if (usedBlocks > 1) {
                    val choiceSpan = (component.choices.size - 1) * choiceGap
                    columnGap = ((width - component.bubbleRadius * 2.0 - choiceSpan) / (usedBlocks - 1).toDouble()).coerceAtLeast(minGap(component.bubbleRadius))
                } else {
                    choiceGap = spacingForSpan(width, component.choices.size, component.bubbleRadius, choiceGap)
                }
            }
            QuestionGroupOrientation.HORIZONTAL -> {
                rowGap = spacingForSpan(width, questionsInFirstBlock, component.bubbleRadius, rowGap)
                if (usedBlocks > 1) {
                    val choiceSpan = (component.choices.size - 1) * choiceGap
                    columnGap = ((height - component.bubbleRadius * 2.0 - choiceSpan) / (usedBlocks - 1).toDouble()).coerceAtLeast(minGap(component.bubbleRadius))
                } else {
                    choiceGap = spacingForSpan(height, component.choices.size, component.bubbleRadius, choiceGap)
                }
            }
        }
        return component.copy(
            firstChoiceX = left + component.bubbleRadius,
            topY = top + component.bubbleRadius,
            choiceGap = choiceGap,
            rowGap = rowGap,
            columnGap = columnGap
        )
    }

    private fun moveExact(component: DesignerOmrComponent, deltaX: Double, deltaY: Double): DesignerOmrComponent = when (component) {
        is QuestionGroupComponent -> component.copy(firstChoiceX = component.firstChoiceX + deltaX, topY = component.topY + deltaY)
        is NumericGridComponent -> component.copy(startX = component.startX + deltaX, topY = component.topY + deltaY)
        is SingleChoiceComponent -> component.copy(start = TemplatePoint(component.start.x + deltaX, component.start.y + deltaY))
    }

    private fun moveVisualExact(element: DesignerVisualElement, deltaX: Double, deltaY: Double): DesignerVisualElement = when (element) {
        is DesignerTextElement -> element.copy(bounds = element.bounds.copy(left = element.bounds.left + deltaX, top = element.bounds.top + deltaY))
        is DesignerImageElement -> element.copy(bounds = element.bounds.copy(left = element.bounds.left + deltaX, top = element.bounds.top + deltaY))
        is DesignerBoxElement -> element.copy(bounds = element.bounds.copy(left = element.bounds.left + deltaX, top = element.bounds.top + deltaY))
        is DesignerLineElement -> element.copy(
            start = TemplatePoint(element.start.x + deltaX, element.start.y + deltaY),
            end = TemplatePoint(element.end.x + deltaX, element.end.y + deltaY)
        )
    }

    private fun resizeLineToBounds(line: DesignerLineElement, target: TemplateRect): DesignerLineElement {
        val halfStroke = max(1.0, line.strokeWidth / 2.0)
        val innerLeft = target.left + halfStroke
        val innerRight = max(innerLeft, target.right - halfStroke)
        val innerTop = target.top + halfStroke
        val innerBottom = max(innerTop, target.bottom - halfStroke)
        val leftToRight = line.start.x <= line.end.x
        val topToBottom = line.start.y <= line.end.y
        return line.copy(
            start = TemplatePoint(if (leftToRight) innerLeft else innerRight, if (topToBottom) innerTop else innerBottom),
            end = TemplatePoint(if (leftToRight) innerRight else innerLeft, if (topToBottom) innerBottom else innerTop)
        )
    }

    private fun nearestGuide(movingAnchors: List<Double>, guides: List<Double>, tolerance: Double): Pair<Double, Double>? {
        var best: Pair<Double, Double>? = null
        var bestDistance = Double.POSITIVE_INFINITY
        movingAnchors.forEach { anchor ->
            guides.forEach { guide ->
                val delta = guide - anchor
                val distance = abs(delta)
                if (distance <= tolerance && distance < bestDistance) {
                    bestDistance = distance
                    best = delta to guide
                }
            }
        }
        return best
    }
}
