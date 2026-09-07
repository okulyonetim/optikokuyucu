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

/**
 * Lightweight, text-free vector startup animation for the OMR identity.
 *
 * The artwork is rendered entirely by Compose: a soft premium background, orbit, answer sheet,
 * scan corners, sequential marks and a confirmation badge. No bitmap or video decoding is needed.
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
    val exit = phase(p, 0.9f, 1f)
    val exitAlpha = 1f - exit
    val entrance = phase(p, 0f, 0.34f)
    val artworkScale = 0.9f + 0.1f * entrance + 0.025f * exit

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBrush)
            .graphicsLayer {
                alpha = exitAlpha
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(286.dp)
                .graphicsLayer {
                    scaleX = artworkScale
                    scaleY = artworkScale
                    translationY = -8f * exit
                }
        ) {
            val unit = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)

            val ambience = phase(p, 0f, 0.34f)
            drawCircle(
                color = AmbientGreen.copy(alpha = 0.08f * ambience),
                radius = unit * 0.38f,
                center = Offset(center.x - unit * 0.23f, center.y + unit * 0.02f)
            )
            drawCircle(
                color = AmbientGold.copy(alpha = 0.055f * ambience),
                radius = unit * 0.25f,
                center = Offset(center.x + unit * 0.3f, center.y - unit * 0.3f)
            )
            drawArc(
                color = OrbitGreen.copy(alpha = 0.13f * ambience),
                startAngle = 194f,
                sweepAngle = 132f * ambience,
                useCenter = false,
                topLeft = Offset(unit * 0.025f, unit * 0.11f),
                size = Size(unit * 0.95f, unit * 0.78f),
                style = Stroke(width = unit * 0.008f, cap = StrokeCap.Round)
            )
            drawArc(
                color = ConfirmGold.copy(alpha = 0.12f * ambience),
                startAngle = 10f,
                sweepAngle = 96f * ambience,
                useCenter = false,
                topLeft = Offset(unit * 0.1f, unit * 0.08f),
                size = Size(unit * 0.82f, unit * 0.82f),
                style = Stroke(width = unit * 0.006f, cap = StrokeCap.Round)
            )

            drawCircle(
                color = ConfirmGold.copy(alpha = 0.9f * phase(p, 0.1f, 0.3f)),
                radius = unit * 0.018f,
                center = Offset(unit * 0.12f, unit * 0.29f)
            )
            drawCircle(
                color = OrbitGreen.copy(alpha = 0.9f * phase(p, 0.18f, 0.38f)),
                radius = unit * 0.012f,
                center = Offset(unit * 0.86f, unit * 0.75f)
            )

            val orbitProgress = phase(p, 0.02f, 0.45f)
            val orbitWidth = unit * 0.075f
            val orbitRadiusX = unit * 0.39f
            val orbitRadiusY = unit * 0.29f
            drawArc(
                color = OrbitGreen.copy(alpha = 0.16f * orbitProgress),
                startAngle = 205f,
                sweepAngle = 315f * orbitProgress,
                useCenter = false,
                topLeft = Offset(center.x - orbitRadiusX, center.y - orbitRadiusY + unit * 0.012f),
                size = Size(orbitRadiusX * 2f, orbitRadiusY * 2f),
                style = Stroke(width = orbitWidth * 1.18f, cap = StrokeCap.Round)
            )
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
                color = OrbitHighlight.copy(alpha = 0.72f * orbitProgress),
                startAngle = 205f,
                sweepAngle = 122f * orbitProgress,
                useCenter = false,
                topLeft = Offset(center.x - orbitRadiusX, center.y - orbitRadiusY),
                size = Size(orbitRadiusX * 2f, orbitRadiusY * 2f),
                style = Stroke(width = orbitWidth * 0.24f, cap = StrokeCap.Round)
            )

            if (orbitProgress > 0.08f) {
                val angle = Math.toRadians((205f + 315f * orbitProgress).toDouble())
                val spark = Offset(
                    x = center.x + orbitRadiusX * cos(angle).toFloat(),
                    y = center.y + orbitRadiusY * sin(angle).toFloat()
                )
                drawCircle(
                    color = OrbitHighlight.copy(alpha = 0.18f * orbitProgress),
                    radius = unit * 0.045f,
                    center = spark
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.92f * orbitProgress),
                    radius = unit * 0.011f,
                    center = spark
                )
            }

            val sheetProgress = phase(p, 0.12f, 0.5f)
            val sheetWidth = unit * 0.54f
            val sheetHeight = unit * 0.66f
            val sheetLeft = center.x - sheetWidth * 0.5f
            val sheetTop = center.y - sheetHeight * 0.52f + (1f - sheetProgress) * unit * 0.13f

            rotate(degrees = -6f + 6f * sheetProgress, pivot = center) {
                drawRoundRect(
                    color = SheetShadow.copy(alpha = 0.055f * sheetProgress),
                    topLeft = Offset(sheetLeft + unit * 0.035f, sheetTop + unit * 0.045f),
                    size = Size(sheetWidth, sheetHeight),
                    cornerRadius = CornerRadius(unit * 0.058f)
                )
                drawRoundRect(
                    color = SheetShadow.copy(alpha = 0.11f * sheetProgress),
                    topLeft = Offset(sheetLeft + unit * 0.018f, sheetTop + unit * 0.025f),
                    size = Size(sheetWidth, sheetHeight),
                    cornerRadius = CornerRadius(unit * 0.058f)
                )
                drawRoundRect(
                    color = SheetColor.copy(alpha = sheetProgress),
                    topLeft = Offset(sheetLeft, sheetTop),
                    size = Size(sheetWidth, sheetHeight),
                    cornerRadius = CornerRadius(unit * 0.058f)
                )
                drawRoundRect(
                    color = SheetWarmTint.copy(alpha = 0.34f * sheetProgress),
                    topLeft = Offset(sheetLeft + unit * 0.008f, sheetTop + unit * 0.008f),
                    size = Size(sheetWidth - unit * 0.016f, sheetHeight * 0.22f),
                    cornerRadius = CornerRadius(unit * 0.05f)
                )

                val bubbleReveal = phase(p, 0.34f, 0.73f)
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
                            color = BubbleOutline.copy(alpha = 0.84f * bubbleProgress),
                            radius = bubbleRadius,
                            center = bubbleCenter,
                            style = Stroke(width = unit * 0.009f)
                        )
                        if (index in selected) {
                            val fillProgress = phase(
                                bubbleReveal,
                                index / 18f + 0.08f,
                                (index + 4f) / 18f
                            )
                            drawCircle(
                                color = BubbleFill.copy(alpha = fillProgress),
                                radius = bubbleRadius * 0.72f * fillProgress,
                                center = bubbleCenter
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.16f * fillProgress),
                                radius = bubbleRadius * 0.22f * fillProgress,
                                center = Offset(
                                    bubbleCenter.x - bubbleRadius * 0.2f,
                                    bubbleCenter.y - bubbleRadius * 0.2f
                                )
                            )
                        }
                    }
                }
            }

            val corners = phase(p, 0.23f, 0.59f)
            val cornerColor = ScanOrange.copy(alpha = corners)
            val cornerGlow = ScanOrange.copy(alpha = 0.12f * corners)
            val cornerStroke = unit * 0.035f
            val cornerLength = unit * 0.115f
            val inset = unit * 0.09f
            val topLeft = Offset(inset, inset)
            val bottomRight = Offset(size.width - inset, size.height - inset)

            drawLine(
                color = cornerGlow,
                start = topLeft,
                end = Offset(topLeft.x + cornerLength * corners, topLeft.y),
                strokeWidth = cornerStroke * 1.55f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = cornerGlow,
                start = topLeft,
                end = Offset(topLeft.x, topLeft.y + cornerLength * corners),
                strokeWidth = cornerStroke * 1.55f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = cornerGlow,
                start = bottomRight,
                end = Offset(bottomRight.x - cornerLength * corners, bottomRight.y),
                strokeWidth = cornerStroke * 1.55f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = cornerGlow,
                start = bottomRight,
                end = Offset(bottomRight.x, bottomRight.y - cornerLength * corners),
                strokeWidth = cornerStroke * 1.55f,
                cap = StrokeCap.Round
            )

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

            val badgeProgress = phase(p, 0.66f, 0.87f)
            val badgeCenter = Offset(center.x + unit * 0.255f, center.y + unit * 0.225f)
            val badgeRadius = unit * 0.135f * badgeProgress
            if (badgeProgress > 0f) {
                drawCircle(
                    color = ConfirmGold.copy(alpha = 0.12f * badgeProgress),
                    radius = badgeRadius * 1.34f,
                    center = badgeCenter
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f * badgeProgress),
                    radius = badgeRadius * 1.08f,
                    center = badgeCenter
                )
                drawCircle(
                    color = ConfirmGold.copy(alpha = badgeProgress),
                    radius = badgeRadius,
                    center = badgeCenter
                )
                drawCircle(
                    color = ConfirmHighlight.copy(alpha = 0.42f * badgeProgress),
                    radius = badgeRadius * 0.82f,
                    center = Offset(
                        badgeCenter.x - badgeRadius * 0.18f,
                        badgeCenter.y - badgeRadius * 0.2f
                    )
                )

                val checkProgress = phase(p, 0.75f, 0.91f)
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

private const val SPLASH_ANIMATION_MS = 1480
private const val SPLASH_HOLD_MS = 80

private val SplashBrush = Brush.radialGradient(
    colors = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFFCFAF4),
        Color(0xFFF7F3E9)
    )
)
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
private val ConfirmHighlight = Color(0xFFF6C667)
