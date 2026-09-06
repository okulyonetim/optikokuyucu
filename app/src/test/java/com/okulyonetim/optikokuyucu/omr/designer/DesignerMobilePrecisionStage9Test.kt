package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerMobilePrecisionStage9Test {
    @Test
    fun `fit and one hundred percent zoom use bounded mobile viewport math`() {
        assertEquals(1.0, DesignerMobileViewport.clampZoom(0.25), 0.0)
        assertEquals(8.0, DesignerMobileViewport.clampZoom(20.0), 0.0)
        val hundred = DesignerMobileViewport.oneHundredPercentZoom(viewportWidthDp = 360.0, pageWidthMm = 210.0)
        assertTrue(hundred > 3.0)
        val pan = DesignerMobileViewport.clampPan(360.0, 500.0, 2.0, 999.0, -999.0)
        assertEquals(180.0, pan.x, 0.001)
        assertEquals(-250.0, pan.y, 0.001)
    }

    @Test
    fun `alignment guides snap center without changing document geometry model`() {
        val moving = TemplateRect(100.0, 200.0, 80.0, 60.0)
        val stationary = listOf(TemplateRect(150.0, 400.0, 80.0, 40.0))
        val safe = TemplateRect(40.0, 40.0, 920.0, 1334.0)
        val match = DesignerMobilePrecision.resolveAlignmentGuides(moving, stationary, safe, tolerance = 20.0)
        assertEquals(0.0, match.deltaY, 0.001)
        assertEquals(10.0, match.deltaX, 0.001)
        assertEquals(150.0, match.verticalGuideX!!, 0.001)
    }

    @Test
    fun `numeric precise frame keeps bubble radius and changes spacing`() {
        val base = DesignerPageGeometry.apply(DesignerDocument("number-stage9", 1, "Number"))
        val number = DesignerAreaCatalog.createNumberArea(base)
        val document = base.copy(components = listOf(number))
        val frame = requireNotNull(DesignerMobilePrecision.componentFrameMm(document, number.id))
        val updated = DesignerMobilePrecision.setComponentFrameMm(
            document,
            number.id,
            xMm = frame.xMm + 2.0,
            yMm = frame.yMm + 3.0,
            widthMm = frame.widthMm + 12.0,
            heightMm = frame.heightMm + 10.0
        )
        val result = updated.components.single() as NumericGridComponent
        assertEquals(number.bubbleRadius, result.bubbleRadius, 0.0)
        assertTrue(result.columnGap > number.columnGap)
        assertTrue(result.rowGap > number.rowGap)
        assertNotEquals(number.startX, result.startX, 0.001)
    }

    @Test
    fun `answer precise sizing keeps recognition prefix and standard bubble`() {
        val base = DesignerPageGeometry.apply(DesignerDocument("answer-stage9", 1, "Answer"))
        val answer = DesignerAreaCatalog.createAnswerArea(base)
        val document = base.copy(components = listOf(answer))
        val frame = requireNotNull(DesignerMobilePrecision.componentFrameMm(document, answer.id))
        val updated = DesignerMobilePrecision.setComponentFrameMm(
            document,
            answer.id,
            frame.xMm,
            frame.yMm,
            frame.widthMm + 8.0,
            frame.heightMm + 8.0
        )
        val result = updated.components.single() as QuestionGroupComponent
        assertEquals(answer.questionIdPrefix, result.questionIdPrefix)
        assertEquals(DesignerEditorLayout.STANDARD_BUBBLE_RADIUS, result.bubbleRadius, 0.0)
        val compiled = DesignerTemplateCompiler.compile(updated)
        assertEquals(result.questionCount, compiled.bubbleRows.size)
    }

    @Test
    fun `visual frame edits round trip through millimeters and safe area`() {
        val base = DesignerPageGeometry.apply(DesignerDocument("visual-stage9", 1, "Visual"))
        val element = DesignerAreaCatalog.createDescriptionArea(base)
        val document = base.copy(visualElements = listOf(element))
        val updated = DesignerMobilePrecision.setVisualFrameMm(
            document,
            element.id,
            xMm = 30.0,
            yMm = 40.0,
            widthMm = 70.0,
            heightMm = 25.0
        )
        val frame = requireNotNull(DesignerMobilePrecision.visualFrameMm(updated, element.id))
        assertEquals(30.0, frame.xMm, 0.01)
        assertEquals(40.0, frame.yMm, 0.01)
        assertEquals(70.0, frame.widthMm, 0.01)
        assertEquals(25.0, frame.heightMm, 0.01)
    }

    @Test
    fun `single choice only resizes along its semantic axis`() {
        val base = DesignerPageGeometry.apply(DesignerDocument("booklet-stage9", 1, "Booklet"))
        val booklet = DesignerAreaCatalog.createBookletArea(base)
        assertTrue(DesignerMobilePrecision.componentCanResizeWidth(booklet))
        assertTrue(!DesignerMobilePrecision.componentCanResizeHeight(booklet))
        val document = base.copy(components = listOf(booklet))
        val frame = requireNotNull(DesignerMobilePrecision.componentFrameMm(document, booklet.id))
        val updated = DesignerMobilePrecision.setComponentFrameMm(
            document,
            booklet.id,
            frame.xMm,
            frame.yMm,
            frame.widthMm + 15.0,
            frame.heightMm + 15.0
        )
        val result = updated.components.single() as SingleChoiceComponent
        assertEquals(booklet.bubbleRadius, result.bubbleRadius, 0.0)
        assertTrue(result.gap > booklet.gap)
    }
}
