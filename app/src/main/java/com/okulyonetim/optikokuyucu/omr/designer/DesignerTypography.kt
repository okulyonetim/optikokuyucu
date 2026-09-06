package com.okulyonetim.optikokuyucu.omr.designer

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import com.okulyonetim.optikokuyucu.R

/**
 * Single embedded font contract for optical-form preview, PDF and gallery-test rendering.
 * Noto Sans is packaged in the APK so Turkish shaping never depends on the device font stack.
 */
object DesignerTypography {
    const val FONT_FAMILY_NAME: String = "Noto Sans"
    const val FONT_SOURCE_COMMIT: String = "5e35378e6bda803962ee6fd257e444a7d459660d"
    const val FONT_GIT_BLOB_SHA: String = "75575046c015ff623a848096a15779867ba71453"
    const val TURKISH_GLYPH_SAMPLE: String = "İ ı Ş ş Ğ ğ Ç ç Ö ö Ü ü"

    @Volatile
    private var regularTypeface: Typeface? = null

    @Volatile
    private var boldTypeface: Typeface? = null

    @Synchronized
    fun install(context: Context) {
        if (regularTypeface != null && boldTypeface != null) return

        val base = requireNotNull(
            ResourcesCompat.getFont(context.applicationContext, R.font.noto_sans)
        ) { "Embedded Noto Sans resource could not be loaded." }

        val regular = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(base, 400, false)
        } else {
            Typeface.create(base, Typeface.NORMAL)
        }
        val bold = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(base, 700, false)
        } else {
            Typeface.create(base, Typeface.BOLD)
        }

        val probe = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular }
        val missing = TURKISH_GLYPH_SAMPLE
            .filterNot(Char::isWhitespace)
            .filterNot { probe.hasGlyph(it.toString()) }
        check(missing.isEmpty()) {
            "Embedded Noto Sans is missing Turkish glyphs: $missing"
        }

        regularTypeface = regular
        boldTypeface = bold
    }

    fun typeface(bold: Boolean = false): Typeface = if (bold) {
        boldTypeface ?: Typeface.create(FALLBACK_FONT_FAMILY, Typeface.BOLD)
    } else {
        regularTypeface ?: Typeface.create(FALLBACK_FONT_FAMILY, Typeface.NORMAL)
    }

    /** Applies the shared form typeface plus stable raster text flags to an Android Paint. */
    fun configurePaint(paint: Paint, bold: Boolean = false) {
        paint.typeface = typeface(bold)
        paint.isSubpixelText = true
        paint.isLinearText = true
    }

    private const val FALLBACK_FONT_FAMILY = "sans-serif"
}
