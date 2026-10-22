package com.okulyonetim.optikokuyucu.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Tema renklerinin okunabilirliğini cihaza gerek kalmadan doğrular (WCAG 2.x kontrast oranı).
 * Metin için en az 4.5:1, çizgi/ikon gibi grafik öğeler için en az 3:1.
 */
class OptikThemeContrastTest {
    private fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertText(name: String, foreground: Color, background: Color, minimum: Double = 4.5) {
        val ratio = contrast(foreground, background)
        assertTrue("$name kontrastı %.2f, en az $minimum olmalı".format(ratio), ratio >= minimum)
    }

    private fun schemes(): Map<String, ColorScheme> = mapOf(
        "açık" to optikLightColorScheme(),
        "koyu" to optikDarkColorScheme()
    )

    @Test
    fun `material roles keep readable text on their containers`() {
        schemes().forEach { (mode, c) ->
            assertText("$mode onPrimary", c.onPrimary, c.primary)
            assertText("$mode onPrimaryContainer", c.onPrimaryContainer, c.primaryContainer)
            assertText("$mode onSecondary", c.onSecondary, c.secondary)
            assertText("$mode onSecondaryContainer", c.onSecondaryContainer, c.secondaryContainer)
            assertText("$mode onTertiary", c.onTertiary, c.tertiary)
            assertText("$mode onTertiaryContainer", c.onTertiaryContainer, c.tertiaryContainer)
            assertText("$mode onError", c.onError, c.error)
            assertText("$mode onErrorContainer", c.onErrorContainer, c.errorContainer)
            assertText("$mode onBackground", c.onBackground, c.background)
            assertText("$mode inverseOnSurface", c.inverseOnSurface, c.inverseSurface)
        }
    }

    @Test
    fun `body and secondary text are readable on every surface level`() {
        schemes().forEach { (mode, c) ->
            val surfaces = mapOf(
                "background" to c.background,
                "surface" to c.surface,
                "surfaceVariant" to c.surfaceVariant,
                "containerLowest" to c.surfaceContainerLowest,
                "containerLow" to c.surfaceContainerLow,
                "container" to c.surfaceContainer,
                "containerHigh" to c.surfaceContainerHigh,
                "containerHighest" to c.surfaceContainerHighest
            )
            surfaces.forEach { (name, color) ->
                assertText("$mode onSurface/$name", c.onSurface, color)
                assertText("$mode onSurfaceVariant/$name", c.onSurfaceVariant, color)
            }
            assertText("$mode primary/background", c.primary, c.background)
            assertText("$mode primary/surface", c.primary, c.surface)
        }
    }

    @Test
    fun `outlines are visible against surfaces`() {
        schemes().forEach { (mode, c) ->
            assertText("$mode outline/surface", c.outline, c.surface, 3.0)
            assertText("$mode outline/background", c.outline, c.background, 3.0)
        }
    }

    @Test
    fun `status and result colors are readable in both modes`() {
        listOf(
            Triple("açık", OptikLightExtendedColors, optikLightColorScheme()),
            Triple("koyu", OptikDarkExtendedColors, optikDarkColorScheme())
        ).forEach { (mode, e, c) ->
            mapOf(
                "success" to e.status.success,
                "warning" to e.status.warning,
                "danger" to e.status.danger,
                "info" to e.status.info
            ).forEach { (name, status) ->
                assertText("$mode status.$name içerik/dolgu", status.content, status.container)
                assertText("$mode status.$name içerik/yüzey", status.content, c.surface)
            }
            mapOf(
                "correct" to e.result.correct,
                "wrong" to e.result.wrong,
                "warning" to e.result.warning,
                "info" to e.result.info
            ).forEach { (name, color) ->
                assertText("$mode result.$name/surface", color, c.surface)
                assertText("$mode result.$name/background", color, c.background)
                assertText("$mode result.$name onFill", e.result.onFill, color)
            }
        }
    }

    @Test
    fun `hero gradient ends carry white text`() {
        listOf(
            OptikPalette.HeroLightStart,
            OptikPalette.HeroLightEnd,
            OptikPalette.HeroDarkStart,
            OptikPalette.HeroDarkEnd
        ).forEach { end -> assertText("hero uç noktası", OptikLightExtendedColors.onHero, end) }
    }

    @Test
    fun `accent fills carry white text and tones stay visible on light and dark surfaces`() {
        val lightSurface = optikLightColorScheme().surface
        val darkSurface = optikDarkColorScheme().surface
        val seen = HashSet<Color>()
        // Paletteki her rengi yakalayacak kadar çok farklı tohumdan örnekle.
        (0 until 400).map { OptikAccents.forSeed("tohum-$it") }.forEach { accent ->
            if (!seen.add(accent.start)) return@forEach
            assertText("vurgu başlangıç", Color.White, accent.start)
            assertText("vurgu bitiş", Color.White, accent.end)
            assertText("vurgu tonu/açık", accent.tone, lightSurface, 3.0)
            assertText("vurgu tonu/koyu", accent.tone, darkSurface, 3.0)
        }
        assertEquals("beş vurgu rengi de denenmeli", 5, seen.size)
    }

    @Test
    fun `accent choice is stable for the same text`() {
        val first = OptikAccents.forSeed("  Ayşe Yılmaz ")
        val second = OptikAccents.forSeed("AYŞE YILMAZ")
        assertTrue(first.tone == second.tone && first.start == second.start)
    }
}
