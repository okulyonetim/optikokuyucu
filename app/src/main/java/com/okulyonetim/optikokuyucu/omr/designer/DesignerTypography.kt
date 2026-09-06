package com.okulyonetim.optikokuyucu.omr.designer

import android.graphics.Typeface

/**
 * Single Android font-family contract for designer preview, test assets and PDF output.
 * Android's sans-serif family is the platform Roboto-compatible family and contains Turkish glyphs.
 */
object DesignerTypography {
    const val ANDROID_FONT_FAMILY: String = "sans-serif"
    const val TURKISH_GLYPH_SAMPLE: String = "İ ı Ş ş Ğ ğ Ç ç Ö ö Ü ü"

    fun typeface(bold: Boolean = false): Typeface = Typeface.create(
        ANDROID_FONT_FAMILY,
        if (bold) Typeface.BOLD else Typeface.NORMAL
    )
}
