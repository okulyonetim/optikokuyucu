package com.okulyonetim.optikokuyucu.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Lightweight vector startup animation inspired by the app's OMR identity.
 *
 * No bitmap/video asset is required: the mark sheet, orbit, scan corners and confirmation badge are
 * rendered by Compose, so the splash remains sharp on every density and does not add decode cost.
 */
@Composable
fun AnimatedStartupSplash(
    content: @Composable () -> Unit
) {
    var splashVisible by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (splashVisible) {
            OmrStartupSplash(onFinished = { splashVisible = false })
        }
    }
}

@Composable
private fun OmrStartupSplash(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = SPLASH_ANIMATION_MS,
                easing = FastOutSlowInEasing
            )
        )
        delay(SPLASH_HOLD_MS.toLong())
        onFinished()
    }

    val p = progress.value
    val exitAlpha = if (p < 0.9f) 1f else ((1f - p) / 0.1f).coerceIn(0f, 1f)
    val entranceScale = 0.92f + 0.08f * phase(p, 0f, 0.35f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackground)
            .graphicsLayer {
                alpha = exitAlpha
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(270.dp)
                .graphicsLayer {
                    scaleX = entranceScale
                    scaleY = entranceScale
                }
        ) {
            val unit = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)

            val orbitProgress = phase(p, 0.02f, 0.44f)
            val orbitWidth = unit * 0.075f
            val orbitRadiusX = unit * 0.39f
            val orbitRadiusY = unit * 0.29f
            drawArc(
                color = OrbitGreen,
                startAngle = 205f,
                sweepAngle = 315f * orbitProgress,
                useCenter = false,
                topLeft = Offset(center.x - orbitRadiusX, center.y - orbitRadiusY),
                size = Size(orbitRadiusX * 2f, orbitRadiusY * 2f),
                style = Stroke(width = orbitWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = OrbitHighlight.copy(alpha = 0.62f * orbitProgress),
                startAngle = 205f,
                sweepAngle = 112f * orbitProgress,
                useCenter = false,
                topLeft = Offset(center.x - orbitRadiusX, center.y - orbitRadiusY),
                size = Size(orbitRadiusX * 2f, orbitRadiusY * 2f),
                style = Stroke(width = orbitWidth * 0.25f, cap = StrokeCap.Round)
            )

            val sheetProgress = phase(p, 0.12f, 0.48f)
            val sheetWidth = unit * 0.54f
            val sheetHeight = unit * 0.66f
            val sheetLeft = center.x - sheetWidth * 0.5f
            val sheetTop = center.y - sheetHeight * 0.52f + (1f - sheetProgress) * unit * 0.12f

            rotate(degrees = -5f + 5f * sheetProgress, pivot = center) {
                drawRoundRect(
                    color = SheetShadow.copy(alpha = 0.12f * sheetProgress),
                    topLeft = Offset(sheetLeft + unit * 0.018f, sheetTop + unit * 0.025f),
                    size = Size(sheetWidth, sheetHeight),
                    cornerRadius = CornerRadius(unit * 0.055f)
                )
                drawRoundRect(
                    color = SheetColor.copy(alpha = sheetProgress),
                    topLeft = Offset(sheetLeft, sheetTop),
                    size = Size(sheetWidth, sheetHeight),
                    cornerRadius = CornerRadius(unit * 0.055f)
                )

                val bubbleReveal = phase(p, 0.34f, 0.72f)
                val cols = 3
                val rows = 4
                val bubbleRadius = unit * 0.028f
                val xStep = sheetWidth * 0.245f
                val yStep = sheetHeight * 0.175f
                val startX = sheetLeft + sheetWidth * 0.27f
                val startY = sheetTop + sheetHeight * 0.22f
                val selected = setOf(0, 4, 8, 11)

                repeat(rows) { row ->
                    repeat(cols) { col ->
                        val index = row * cols + col
                        val bubbleProgress = phase(
                            bubbleReveal,
                            index / 18f,
                            (index + 5f) / 18f
                        )
                        val bubbleCenter = Offset(
                            x = startX + col * xStep,
                            y = startY + row * yStep
                        )
                        drawCircle(
                            color = BubbleOutline.copy(alpha = 0.86f * bubbleProgress),
                            radius = bubbleRadius,
                            center = bubbleCenter,
                            style = Stroke(width = unit * 0.009f)
                        )
                        if (index in selected) {
                            val fillProgress = phase(bubbleReveal, index / 18f + 0.08f, (index + 4f) / 18f)
                            drawCircle(
                                color = BubbleFill.copy(alpha = fillProgress),
                                radius = bubbleRadius * 0.72f * fillProgress,
                                center = bubbleCenter
                            )
                        }
                    }
                }
            }

            val corners = phase(p, 0.24f, 0.58f)
            val cornerColor = ScanOrange.copy(alpha = corners)
            val cornerStroke = unit * 0.035f
            val cornerLength = unit * 0.115f
            val inset = unit * 0.09f
            val topLeft = Offset(inset, inset)
            val bottomRight = Offset(size.width - inset, size.height - inset)

            drawLine(
                color = cornerColor,
                start = topLeft,
                end = Offset(topLeft.x + cornerLength * corners, topLeft.y),
                strokeWidth = cornerStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = cornerColor,
                start = topLeft,
                end = Offset(topLeft.x, topLeft.y + cornerLength * corners),
                strokeWidth = cornerStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = cornerColor,
                start = bottomRight,
                end = Offset(bottomRight.x - cornerLength * corners, bottomRight.y),
                strokeWidth = cornerStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = cornerColor,
                start = bottomRight,
                end = Offset(bottomRight.x, bottomRight.y - cornerLength * corners),
                strokeWidth = cornerStroke,
                cap = StrokeCap.Round
            )

            val badgeProgress = phase(p, 0.67f, 0.86f)
            val badgeCenter = Offset(center.x + unit * 0.255f, center.y + unit * 0.225f)
            val badgeRadius = unit * 0.135f * badgeProgress
            if (badgeProgress > 0f) {
                drawCircle(
                    color = ConfirmGold.copy(alpha = badgeProgress),
                    radius = badgeRadius,
                    center = badgeCenter
                )
                val checkProgress = phase(p, 0.76f, 0.9f)
                val checkStroke = unit * 0.035f
                val p1 = Offset(badgeCenter.x - unit * 0.055f, badgeCenter.y)
                val p2 = Offset(badgeCenter.x - unit * 0.012f, badgeCenter.y + unit * 0.045f)
                val p3 = Offset(badgeCenter.x + unit * 0.07f, badgeCenter.y - unit * 0.052f)
                drawLine(
                    color = Color.White.copy(alpha = checkProgress),
                    start = p1,
                    end = Offset(
                        x = p1.x + (p2.x - p1.x) * checkProgress,
                        y = p1.y + (p2.y - p1.y) * checkProgress
                    ),
                    strokeWidth = checkStroke,
                    cap = StrokeCap.Round
                )
                if (checkProgress > 0.45f) {
                    val second = phase(checkProgress, 0.45f, 1f)
                    drawLine(
                        color = Color.White.copy(alpha = second),
                        start = p2,
                        end = Offset(
                            x = p2.x + (p3.x - p2.x) * second,
                            y = p2.y + (p3.y - p2.y) * second
                        ),
                        strokeWidth = checkStroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

private fun phase(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    return ((value - start) / (end - start)).coerceIn(0f, 1f)
}

private const val SPLASH_ANIMATION_MS = 1450
private const val SPLASH_HOLD_MS = 90

private val SplashBackground = Color(0xFFFBF9F3)
private val OrbitGreen = Color(0xFF157A58)
private val OrbitHighlight = Color(0xFF39A977)
private val SheetColor = Color(0xFFFFFDF7)
private val SheetShadow = Color(0xFF17362C)
private val BubbleOutline = Color(0xFF173C34)
private val BubbleFill = Color(0xFF2D9A6A)
private val ScanOrange = Color(0xFFF0643B)
private val ConfirmGold = Color(0xFFE3A128)
