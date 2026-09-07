package com.okulyonetim.optikokuyucu.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedStartupSplash(content: @Composable () -> Unit) {
    var splashVisible by remember { mutableStateOf(true) }
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (splashVisible) OmrStartupSplash { splashVisible = false }
    }
}

@Composable
private fun OmrStartupSplash(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(SPLASH_ANIMATION_MS, easing = LinearEasing))
        delay(SPLASH_END_HOLD_MS.toLong())
        onFinished()
    }

    val p = progress.value
    val entrance = easedPhase(p, 0f, 0.16f)
    val exit = easedPhase(p, 0.92f, 1f)
    val artworkScale = 0.92f + 0.08f * entrance + 0.018f * exit

    Box(
        modifier = Modifier.fillMaxSize().background(SplashBrush).graphicsLayer { alpha = 1f - exit },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(258.dp).graphicsLayer {
                scaleX = artworkScale
                scaleY = artworkScale
                translationY = -6f * exit
            }
        ) {
            val unit = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)
            val ambience = easedPhase(p, 0f, 0.18f)
            drawCircle(AmbientGreen.copy(alpha = 0.075f * ambience), unit * 0.34f, Offset(center.x - unit * 0.14f, center.y + unit * 0.03f))
            drawCircle(AmbientGold.copy(alpha = 0.052f * ambience), unit * 0.22f, Offset(center.x + unit * 0.23f, center.y - unit * 0.26f))

            val orbitProgress = easedPhase(p, 0.03f, 0.31f)
            val orbitRadiusX = unit * 0.315f
            val orbitRadiusY = unit * 0.345f
            val orbitWidth = unit * 0.066f
            val orbitTopLeft = Offset(center.x - orbitRadiusX, center.y - orbitRadiusY)
            val orbitSize = Size(orbitRadiusX * 2f, orbitRadiusY * 2f)
            drawArc(OrbitGreen.copy(alpha = 0.13f * orbitProgress), 205f, 314f * orbitProgress, false, Offset(orbitTopLeft.x, orbitTopLeft.y + unit * 0.012f), orbitSize, 1f, Stroke(orbitWidth * 1.2f, cap = StrokeCap.Round))
            drawArc(OrbitGreen, 205f, 314f * orbitProgress, false, orbitTopLeft, orbitSize, 1f, Stroke(orbitWidth, cap = StrokeCap.Round))
            drawArc(OrbitHighlight.copy(alpha = 0.76f * orbitProgress), 205f, 105f * orbitProgress, false, orbitTopLeft, orbitSize, 1f, Stroke(orbitWidth * 0.24f, cap = StrokeCap.Round))

            if (orbitProgress > 0.05f) {
                val angle = Math.toRadians((205f + 314f * orbitProgress).toDouble())
                val spark = Offset(center.x + orbitRadiusX * cos(angle).toFloat(), center.y + orbitRadiusY * sin(angle).toFloat())
                drawCircle(OrbitHighlight.copy(alpha = 0.2f * orbitProgress), unit * 0.04f, spark)
                drawCircle(Color.White.copy(alpha = 0.92f * orbitProgress), unit * 0.009f, spark)
            }

            val sheetProgress = easedPhase(p, 0.12f, 0.38f)
            val sheetWidth = unit * 0.455f
            val sheetHeight = unit * 0.665f
            val sheetLeft = center.x - sheetWidth * 0.5f
            val sheetTop = center.y - sheetHeight * 0.52f + (1f - sheetProgress) * unit * 0.11f
            rotate(-4.5f + 4.5f * sheetProgress, center) {
                drawRoundRect(SheetShadow.copy(alpha = 0.07f * sheetProgress), Offset(sheetLeft + unit * 0.027f, sheetTop + unit * 0.038f), Size(sheetWidth, sheetHeight), CornerRadius(unit * 0.052f))
                drawRoundRect(SheetShadow.copy(alpha = 0.105f * sheetProgress), Offset(sheetLeft + unit * 0.014f, sheetTop + unit * 0.021f), Size(sheetWidth, sheetHeight), CornerRadius(unit * 0.052f))
                drawRoundRect(SheetColor.copy(alpha = sheetProgress), Offset(sheetLeft, sheetTop), Size(sheetWidth, sheetHeight), CornerRadius(unit * 0.052f))
                drawRoundRect(SheetWarmTint.copy(alpha = 0.34f * sheetProgress), Offset(sheetLeft + unit * 0.007f, sheetTop + unit * 0.007f), Size(sheetWidth - unit * 0.014f, sheetHeight * 0.19f), CornerRadius(unit * 0.045f))

                val bubbleReveal = phase(p, 0.29f, 0.67f)
                val bubbleRadius = unit * 0.025f
                val xStep = sheetWidth * 0.245f
                val yStep = sheetHeight * 0.176f
                val startX = sheetLeft + sheetWidth * 0.27f
                val startY = sheetTop + sheetHeight * 0.235f
                val selected = setOf(0, 4, 8, 11)
                repeat(4) { row ->
                    repeat(3) { col ->
                        val index = row * 3 + col
                        val local = phase(bubbleReveal, index / 18f, (index + 5f) / 18f)
                        val bubbleCenter = Offset(startX + col * xStep, startY + row * yStep)
                        drawCircle(BubbleOutline.copy(alpha = 0.84f * local), bubbleRadius, bubbleCenter, style = Stroke(unit * 0.008f))
                        if (index in selected) {
                            val fill = phase(bubbleReveal, index / 18f + 0.08f, (index + 4f) / 18f)
                            drawCircle(BubbleFill.copy(alpha = fill), bubbleRadius * 0.72f * fill, bubbleCenter)
                        }
                    }
                }

                val scan = phase(p, 0.42f, 0.73f)
                if (scan > 0f && scan < 1f) {
                    val scanY = sheetTop + sheetHeight * (0.16f + 0.68f * scan)
                    drawLine(ScanOrange.copy(alpha = 0.1f), Offset(sheetLeft + unit * 0.03f, scanY), Offset(sheetLeft + sheetWidth - unit * 0.03f, scanY), unit * 0.024f, StrokeCap.Round)
                    drawLine(ScanOrange.copy(alpha = 0.8f), Offset(sheetLeft + unit * 0.04f, scanY), Offset(sheetLeft + sheetWidth - unit * 0.04f, scanY), unit * 0.006f, StrokeCap.Round)
                }
            }

            val corners = easedPhase(p, 0.2f, 0.45f)
            val cornerStroke = unit * 0.03f
            val cornerLength = unit * 0.092f
            val insetX = unit * 0.16f
            val insetY = unit * 0.105f
            val tl = Offset(insetX, insetY)
            val br = Offset(size.width - insetX, size.height - insetY)
            fun corners(color: Color, width: Float) {
                drawLine(color, tl, Offset(tl.x + cornerLength * corners, tl.y), width, StrokeCap.Round)
                drawLine(color, tl, Offset(tl.x, tl.y + cornerLength * corners), width, StrokeCap.Round)
                drawLine(color, br, Offset(br.x - cornerLength * corners, br.y), width, StrokeCap.Round)
                drawLine(color, br, Offset(br.x, br.y - cornerLength * corners), width, StrokeCap.Round)
            }
            corners(ScanOrange.copy(alpha = 0.1f * corners), cornerStroke * 1.55f)
            corners(ScanOrange.copy(alpha = corners), cornerStroke)

            val badgeProgress = easedPhase(p, 0.69f, 0.82f)
            val badgeCenter = Offset(center.x + unit * 0.205f, center.y + unit * 0.235f)
            val badgeRadius = unit * 0.12f * badgeProgress
            if (badgeProgress > 0f) {
                drawCircle(ConfirmGold.copy(alpha = 0.12f * badgeProgress), badgeRadius * 1.34f, badgeCenter)
                drawCircle(Color.White.copy(alpha = 0.9f * badgeProgress), badgeRadius * 1.08f, badgeCenter)
                drawCircle(ConfirmGold.copy(alpha = badgeProgress), badgeRadius, badgeCenter)
                val check = easedPhase(p, 0.75f, 0.85f)
                val p1 = Offset(badgeCenter.x - unit * 0.048f, badgeCenter.y)
                val p2 = Offset(badgeCenter.x - unit * 0.011f, badgeCenter.y + unit * 0.039f)
                val p3 = Offset(badgeCenter.x + unit * 0.061f, badgeCenter.y - unit * 0.046f)
                drawLine(Color.White.copy(alpha = check), p1, Offset(p1.x + (p2.x - p1.x) * check, p1.y + (p2.y - p1.y) * check), unit * 0.031f, StrokeCap.Round)
                if (check > 0.45f) {
                    val second = phase(check, 0.45f, 1f)
                    drawLine(Color.White.copy(alpha = second), p2, Offset(p2.x + (p3.x - p2.x) * second, p2.y + (p3.y - p2.y) * second), unit * 0.031f, StrokeCap.Round)
                }
            }
        }
    }
}

private fun phase(value: Float, start: Float, end: Float): Float = ((value - start) / (end - start)).coerceIn(0f, 1f)
private fun easedPhase(value: Float, start: Float, end: Float): Float = FastOutSlowInEasing.transform(phase(value, start, end))
private const val SPLASH_ANIMATION_MS = 4200
private const val SPLASH_END_HOLD_MS = 150
private val SplashBrush = Brush.radialGradient(listOf(Color.White, Color(0xFFFCFAF4), Color(0xFFF7F3E9)))
private val AmbientGreen = Color(0xFF2A9A70)
private val AmbientGold = Color(0xFFE7B34A)
private val OrbitGreen = Color(0xFF157A58)
private val OrbitHighlight = Color(0xFF45C18A)
private val SheetColor = Color(0xFFFFFDF7)
private val SheetWarmTint = Color(0xFFFFF2DA)
private val SheetShadow = Color(0xFF17362C)
private val BubbleOutline = Color(0xFF173C34)
private val BubbleFill = Color(0xFF2D9A6A)
private val ScanOrange = Color(0xFFF0643B)
private val ConfirmGold = Color(0xFFE3A128)
