package com.okulyonetim.optikokuyucu.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.okulyonetim.optikokuyucu.R

/**
 * Yazı boyutu merdiveni. Ekranlarda `fontSize = 10.sp` gibi sabit sayılar yerine bunlar kullanılır.
 * En küçük boyut [micro] (11sp): daha küçük yazı telefonda okunmaz.
 */
object OptikTextSize {
    /** Alt gezinme etiketi gibi 5 öğenin yan yana sığması gereken yerler. */
    val nav = 10.sp
    val micro = 11.sp
    val small = 12.sp
    val body = 13.sp
    val bodyLarge = 14.sp
    val title = 16.sp
    val titleLarge = 18.sp
    val heading = 20.sp
    val display = 24.sp
    val hero = 30.sp
}

/**
 * Basılı optik formda kullanılan Noto Sans ile aynı aile; böylece ekran ve çıktı tutarlı görünür ve
 * görünüm telefon markasının sistem yazı tipine bağlı kalmaz. Yazı tipi yüklenemezse sistem yazı
 * tipine düşer (metin asla kaybolmaz).
 */
@Composable
internal fun rememberOptikFontFamily(): FontFamily {
    val context = LocalContext.current
    return remember(context) {
        val available = runCatching {
            ResourcesCompat.getFont(context, R.font.noto_sans) != null
        }.getOrDefault(false)
        if (available) {
            FontFamily(
                Font(R.font.noto_sans, FontWeight.Normal),
                Font(R.font.noto_sans, FontWeight.Medium),
                Font(R.font.noto_sans, FontWeight.SemiBold),
                Font(R.font.noto_sans, FontWeight.Bold)
            )
        } else {
            FontFamily.Default
        }
    }
}

internal fun optikTypography(family: FontFamily): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium)
    )
}
