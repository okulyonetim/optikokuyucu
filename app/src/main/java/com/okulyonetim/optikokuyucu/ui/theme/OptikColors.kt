package com.okulyonetim.optikokuyucu.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * Optik Okuyucu renkleri. Uygulamadaki TÜM renk kararları bu dosyada verilir; ekranlar ham
 * `Color(0x…)` yerine MaterialTheme.colorScheme veya OptikTheme.colors / OptikOverlay kullanır.
 *
 * Her metin/zemin çifti WCAG AA (4.5:1) sağlar; bu, OptikThemeContrastTest ile doğrulanır.
 * Renk değiştirirken önce o testi çalıştırın.
 */

/** Zemin üzerinde okunan içerik rengi + hafif dolgu rengi (rozetler, uyarı şeritleri). */
@Immutable
class StatusColor(val content: Color, val container: Color)

@Immutable
class OptikStatusColors(
    val success: StatusColor,
    val warning: StatusColor,
    val danger: StatusColor,
    val info: StatusColor
)

/** Sınav sonuçlarındaki doğru/yanlış/şüpheli/bilgi rengi (metin, grafik dolgusu, açıklama). */
@Immutable
class OptikResultColors(
    val correct: Color,
    val wrong: Color,
    val warning: Color,
    val info: Color,
    /** Bu renklerle DOLDURULMUŞ bir yüzeyin (seçili şık, bildirim) üzerindeki metin rengi. */
    val onFill: Color
)

/** Material ColorScheme'in karşılamadığı, uygulamaya özgü renkler. */
@Immutable
class OptikExtendedColors(
    val isDark: Boolean,
    val status: OptikStatusColors,
    val result: OptikResultColors,
    /** Üst çubuk ve vurgu kartlarının gradyanı. */
    val heroBrush: Brush,
    /** [heroBrush] üzerindeki metin/ikon rengi. */
    val onHero: Color
)

/** Ham marka paleti. Yalnız bu paket kullanır. */
internal object OptikPalette {
    val Indigo = Color(0xFF4318C9)
    val IndigoContainer = Color(0xFFEBE6FF)
    val OnIndigoContainer = Color(0xFF2D0E8F)
    val Amber = Color(0xFFB45309)
    val AmberContainer = Color(0xFFFEF3C7)
    val OnAmberContainer = Color(0xFF451A03)
    val Teal = Color(0xFF0E7490)
    val TealContainer = Color(0xFFCFFAFE)
    val OnTealContainer = Color(0xFF164E63)
    val Rose = Color(0xFF9F1239)

    val HeroLightStart = Indigo
    val HeroLightEnd = Teal
    val HeroDarkStart = Color(0xFF1E1257)
    val HeroDarkEnd = Color(0xFF0C3B52)
}

internal fun optikLightColorScheme(): ColorScheme = lightColorScheme(
    primary = OptikPalette.Indigo,
    onPrimary = Color.White,
    primaryContainer = OptikPalette.IndigoContainer,
    onPrimaryContainer = OptikPalette.OnIndigoContainer,
    inversePrimary = Color(0xFFC9BDFF),
    secondary = OptikPalette.Amber,
    onSecondary = Color.White,
    secondaryContainer = OptikPalette.AmberContainer,
    onSecondaryContainer = OptikPalette.OnAmberContainer,
    tertiary = OptikPalette.Teal,
    onTertiary = Color.White,
    tertiaryContainer = OptikPalette.TealContainer,
    onTertiaryContainer = OptikPalette.OnTealContainer,
    background = Color(0xFFF5F3FF),
    onBackground = Color(0xFF1A1535),
    surface = Color.White,
    onSurface = Color(0xFF1A1535),
    surfaceVariant = Color(0xFFEDE9FE),
    onSurfaceVariant = Color(0xFF4B4869),
    surfaceTint = OptikPalette.Indigo,
    inverseSurface = Color(0xFF2B2742),
    inverseOnSurface = Color(0xFFF2EFFF),
    error = OptikPalette.Rose,
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF5F0A22),
    outline = Color(0xFF77729A),
    outlineVariant = Color(0xFFD4D0EA),
    scrim = Color.Black,
    // Tonal yüzey merdiveni: verilmezse Material kendi mor taban tonlarını kullanır ve
    // BottomSheet/Dialog/Card gibi bileşenler uygulamanın paletinden kopuk görünür.
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDFDCEC),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF9FF),
    surfaceContainer = Color(0xFFF3F1FC),
    surfaceContainerHigh = Color(0xFFEDEBF8),
    surfaceContainerHighest = Color(0xFFE7E4F4)
)

internal fun optikDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = Color(0xFFB8A8FF),
    onPrimary = Color(0xFF1E0A6E),
    primaryContainer = Color(0xFF2D0E8F),
    onPrimaryContainer = Color(0xFFEBE6FF),
    inversePrimary = OptikPalette.Indigo,
    secondary = Color(0xFFFCD34D),
    onSecondary = Color(0xFF451A03),
    secondaryContainer = Color(0xFF6B3407),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = Color(0xFF67E8F9),
    onTertiary = Color(0xFF0E4F5E),
    tertiaryContainer = Color(0xFF164E63),
    onTertiaryContainer = Color(0xFFCFFAFE),
    background = Color(0xFF0C0A1A),
    onBackground = Color(0xFFE8E4FF),
    surface = Color(0xFF18162B),
    onSurface = Color(0xFFE8E4FF),
    surfaceVariant = Color(0xFF221F38),
    onSurfaceVariant = Color(0xFFA09BC0),
    surfaceTint = Color(0xFFB8A8FF),
    inverseSurface = Color(0xFFE8E4FF),
    inverseOnSurface = Color(0xFF2B2742),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF7F1D1D),
    errorContainer = Color(0xFF5F1A2A),
    onErrorContainer = Color(0xFFFFD9DE),
    outline = Color(0xFF8A85A8),
    outlineVariant = Color(0xFF2D2B45),
    scrim = Color.Black,
    surfaceBright = Color(0xFF38355A),
    surfaceDim = Color(0xFF0C0A1A),
    surfaceContainerLowest = Color(0xFF090816),
    surfaceContainerLow = Color(0xFF141228),
    surfaceContainer = Color(0xFF1C1A31),
    surfaceContainerHigh = Color(0xFF25223D),
    surfaceContainerHighest = Color(0xFF2F2C49)
)

internal val OptikLightExtendedColors = OptikExtendedColors(
    isDark = false,
    status = OptikStatusColors(
        success = StatusColor(content = Color(0xFF065F46), container = Color(0xFFD1FAE5)),
        warning = StatusColor(content = Color(0xFF92400E), container = Color(0xFFFEF3C7)),
        danger = StatusColor(content = Color(0xFF9F1239), container = Color(0xFFFFE4E6)),
        info = StatusColor(content = Color(0xFF075985), container = Color(0xFFE0F2FE))
    ),
    result = OptikResultColors(
        correct = Color(0xFF17803D),
        wrong = Color(0xFFC62828),
        warning = Color(0xFFB45309),
        info = Color(0xFF1D4ED8),
        onFill = Color.White
    ),
    heroBrush = Brush.linearGradient(listOf(OptikPalette.HeroLightStart, OptikPalette.HeroLightEnd)),
    onHero = Color.White
)

internal val OptikDarkExtendedColors = OptikExtendedColors(
    isDark = true,
    status = OptikStatusColors(
        success = StatusColor(content = Color(0xFF6EE7B7), container = Color(0xFF063B33)),
        warning = StatusColor(content = Color(0xFFFCD34D), container = Color(0xFF4A2608)),
        danger = StatusColor(content = Color(0xFFFDA4AF), container = Color(0xFF4D1024)),
        info = StatusColor(content = Color(0xFF7DD3FC), container = Color(0xFF0C3A5C))
    ),
    result = OptikResultColors(
        correct = Color(0xFF4ADE80),
        wrong = Color(0xFFF87171),
        warning = Color(0xFFFBBF24),
        info = Color(0xFF60A5FA),
        onFill = Color(0xFF0C0A1A)
    ),
    heroBrush = Brush.linearGradient(listOf(OptikPalette.HeroDarkStart, OptikPalette.HeroDarkEnd)),
    onHero = Color.White
)

/**
 * Kamera ve kâğıt görüntüsü üzerine çizilen renkler. Zemin her zaman fotoğraf/kâğıt olduğundan
 * açık/koyu temadan bağımsızdırlar.
 */
object OptikOverlay {
    /** Sayfa kilitlendi / takip ediliyor çerçeveleri (kamera). */
    val locked = Color(0xFF4ADE80)
    val lockedBorder = Color(0xFF69D49F)
    val tracking = Color(0xFFFBBF24)
    val trackingAccent = Color(0xFFFFC75A)

    /** Okunan kâğıt üzerindeki işaret katmanları. */
    val key = Color(0xFF16A05D)
    val correct = Color(0xFF20A861)
    val wrong = Color(0xFFE2464C)
    val doubleMark = Color(0xFFF4B740)
    val info = Color(0xFF3B82F6)

    /** Form editöründe çizilen yazıcı mürekkebi (kırmızımsı) ve kâğıt ızgarası. */
    val formInk = Color(0xFFB54848)
    val formInkDark = Color(0xFF9E3E3E)
    val paperGridMinor = Color(0xFFE9ECF3)
    val paperGridMajor = Color(0xFFD2D8E4)
    val paperFrame = Color(0xFFBFC5D0)
    val paperGuide = Color(0xFFD7DCE6)
    val paperText = Color(0xFF555555)
}

/** Açılış ekranı sanat eserinin krem zemini (görselle birebir aynı olmalı). */
object OptikBrand {
    val splashBackground = Color(0xFFFDFCF7)
}

/**
 * Gradyan dolgusu (beyaz metin taşır) ve kenarlık tonu ayrı verilir: dolgu uçları beyaz metin için
 * koyu, ton ise hem beyaz hem koyu zeminde en az 3:1 kontrast verecek orta parlaklıktadır.
 */
@Immutable
class OptikAccent(val start: Color, val end: Color, val tone: Color) {
    val brush: Brush get() = Brush.linearGradient(listOf(start, end))
}

object OptikAccents {
    private val palette = listOf(
        OptikAccent(Color(0xFF6030E8), Color(0xFF4318C9), Color(0xFF7A5AF0)), // indigo
        OptikAccent(Color(0xFF0E7490), Color(0xFF0F766E), Color(0xFF0E9DB8)), // camgöbeği-yeşil
        OptikAccent(Color(0xFFC2540A), Color(0xFFB45309), Color(0xFFD98324)), // amber-turuncu
        OptikAccent(Color(0xFF7C3AED), Color(0xFF5B21B6), Color(0xFF9467F0)), // mor
        OptikAccent(Color(0xFF047857), Color(0xFF15803D), Color(0xFF1F9D63))  // zümrüt
    )

    /** Aynı metin her zaman aynı vurgu rengini alır (isim, başlık vb.). */
    fun forSeed(seed: String): OptikAccent {
        val hash = seed.trim().uppercase().hashCode()
        val positive = if (hash == Int.MIN_VALUE) 0 else kotlin.math.abs(hash)
        return palette[positive % palette.size]
    }
}
