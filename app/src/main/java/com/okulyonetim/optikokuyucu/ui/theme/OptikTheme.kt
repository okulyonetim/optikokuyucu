package com.okulyonetim.optikokuyucu.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

internal val LocalOptikColors = staticCompositionLocalOf { OptikLightExtendedColors }

/** `OptikTheme.colors.status.success.content` gibi erişim için. Material'in `MaterialTheme` nesnesine paraleldir. */
object OptikTheme {
    val colors: OptikExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalOptikColors.current

    val status: OptikStatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalOptikColors.current.status

    val result: OptikResultColors
        @Composable
        @ReadOnlyComposable
        get() = LocalOptikColors.current.result

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalOptikColors.current.isDark
}

/**
 * Uygulamanın tek tema girişi: renk şeması, yazı tipi, şekiller, uygulamaya özgü renkler ve sistem
 * çubukları burada bağlanır. [darkTheme] çağıran tarafından (sistem/açık/koyu seçimiyle) çözülür.
 */
@Composable
fun OptikTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val fontFamily = rememberOptikFontFamily()
    val typography = remember(fontFamily) { optikTypography(fontFamily) }
    val colorScheme = remember(darkTheme) { if (darkTheme) optikDarkColorScheme() else optikLightColorScheme() }
    val extended = if (darkTheme) OptikDarkExtendedColors else OptikLightExtendedColors

    SystemBarsAppearance(darkTheme)

    CompositionLocalProvider(LocalOptikColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = OptikMaterialShapes,
            content = content
        )
    }
}

/**
 * Durum/gezinme çubuğu simgelerini UYGULAMANIN seçili temasına göre ayarlar. enableEdgeToEdge()
 * yalnızca sistem temasını izler; uygulamada "Koyu" seçilip telefon açık moddaysa simgeler koyu
 * zeminde koyu kalıyordu.
 */
@Composable
private fun SystemBarsAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
