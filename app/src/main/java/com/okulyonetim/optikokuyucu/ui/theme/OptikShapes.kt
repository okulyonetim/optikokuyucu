package com.okulyonetim.optikokuyucu.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Köşe yuvarlaklığı merdiveni. Daha önce 19 farklı değer (9, 10, 11, 12, 13, 14, 15, 16, 17 …) vardı;
 * artık beş basamak ve hap biçimi kullanılır.
 */
object OptikShape {
    val xs = RoundedCornerShape(6.dp)
    val sm = RoundedCornerShape(10.dp)
    val md = RoundedCornerShape(14.dp)
    val lg = RoundedCornerShape(18.dp)
    val xl = RoundedCornerShape(24.dp)

    /** Tam yuvarlak uçlu rozet/hap. */
    val pill = RoundedCornerShape(percent = 50)
}

internal val OptikMaterialShapes = Shapes(
    extraSmall = OptikShape.xs,
    small = OptikShape.sm,
    medium = OptikShape.md,
    large = OptikShape.lg,
    extraLarge = OptikShape.xl
)
