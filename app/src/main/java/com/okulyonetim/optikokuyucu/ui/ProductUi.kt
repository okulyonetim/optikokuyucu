package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ProductPurple = Color(0xFF7357E8)
private val ProductPurpleLight = Color(0xFFEDE8FF)
private val ProductBackground = Color(0xFFF7F7FC)
private val ProductGreen = Color(0xFF25865E)
private val ProductGreenSoft = Color(0xFFE8F7F0)
private val ProductOrange = Color(0xFFCF7A11)
private val ProductOrangeSoft = Color(0xFFFFF2E2)
private val ProductRed = Color(0xFFC94743)
private val ProductRedSoft = Color(0xFFFFEBEA)

private val LightProductScheme = lightColorScheme(
    primary = ProductPurple,
    onPrimary = Color.White,
    primaryContainer = ProductPurpleLight,
    onPrimaryContainer = Color(0xFF2E176A),
    secondary = Color(0xFF6652C6),
    background = ProductBackground,
    surface = Color.White,
    surfaceVariant = Color(0xFFF1EFF8),
    outline = Color(0xFFD4D0DF)
)

private val DarkProductScheme = darkColorScheme(
    primary = Color(0xFFBCA9FF),
    onPrimary = Color(0xFF28115E),
    primaryContainer = Color(0xFF352861),
    onPrimaryContainer = Color(0xFFEAE4FF),
    secondary = Color(0xFFCABEFF),
    background = Color(0xFF101016),
    surface = Color(0xFF19181F),
    surfaceVariant = Color(0xFF24222C),
    outline = Color(0xFF827D8C)
)

enum class ProductTab {
    HOME,
    CAMERA,
    STUDENTS,
    RESULTS,
    SETTINGS
}

@Composable
fun OptikProductTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkProductScheme else LightProductScheme
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            content()
        }
    }
}

@Composable
fun ProductTopBar(
    title: String,
    leadingText: String? = null,
    onLeadingClick: (() -> Unit)? = null,
    actionText: String = "⋮",
    onActionClick: (() -> Unit)? = null,
    actionMenu: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingText != null && onLeadingClick != null) {
                TextButton(onClick = onLeadingClick) {
                    Text(leadingText, color = MaterialTheme.colorScheme.primary, fontSize = 23.sp)
                }
            } else {
                Spacer(Modifier.size(42.dp))
            }

            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (onActionClick != null) {
                Box {
                    TextButton(onClick = onActionClick) {
                        Text(actionText, color = MaterialTheme.colorScheme.primary, fontSize = 22.sp)
                    }
                    actionMenu?.invoke()
                }
            } else {
                Spacer(Modifier.size(42.dp))
            }
        }
    }
}

@Composable
fun ProductFilterPill(
    label: String,
    count: Int? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    val text = if (count == null) label else "$label  $count"
    if (selected) {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            contentPadding = ButtonDefaults.ContentPadding
        ) {
            Text(text, fontSize = 12.sp)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
        }
    }
}

enum class ProductBadgeTone { GREEN, ORANGE, RED, NEUTRAL }

@Composable
fun ProductStatusBadge(text: String, tone: ProductBadgeTone) {
    val light = !isSystemInDarkTheme()
    val background = when (tone) {
        ProductBadgeTone.GREEN -> if (light) ProductGreenSoft else Color(0xFF173A2D)
        ProductBadgeTone.ORANGE -> if (light) ProductOrangeSoft else Color(0xFF463015)
        ProductBadgeTone.RED -> if (light) ProductRedSoft else Color(0xFF472421)
        ProductBadgeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when (tone) {
        ProductBadgeTone.GREEN -> if (light) ProductGreen else Color(0xFF78D6A6)
        ProductBadgeTone.ORANGE -> if (light) ProductOrange else Color(0xFFFFB65A)
        ProductBadgeTone.RED -> if (light) ProductRed else Color(0xFFFF938C)
        ProductBadgeTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = background, contentColor = foreground, shape = RoundedCornerShape(8.dp)) {
        Text(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun ProductBottomBar(
    selected: ProductTab,
    onSelect: (ProductTab) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductBottomItem(Modifier.weight(1f), "Anasayfa", "⌂", selected == ProductTab.HOME) { onSelect(ProductTab.HOME) }
            ProductBottomItem(Modifier.weight(1f), "Kamera", "▣", selected == ProductTab.CAMERA) { onSelect(ProductTab.CAMERA) }
            ProductBottomItem(Modifier.weight(1f), "Öğrenciler", "●", selected == ProductTab.STUDENTS) { onSelect(ProductTab.STUDENTS) }
            ProductBottomItem(Modifier.weight(1f), "Sonuçlar", "▥", selected == ProductTab.RESULTS) { onSelect(ProductTab.RESULTS) }
            ProductBottomItem(Modifier.weight(1f), "Ayarlar", "⚙", selected == ProductTab.SETTINGS) { onSelect(ProductTab.SETTINGS) }
        }
    }
}

@Composable
private fun ProductBottomItem(
    modifier: Modifier,
    label: String,
    symbol: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        modifier = modifier.padding(horizontal = 2.dp),
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(symbol, fontSize = 18.sp)
            Text(
                label,
                maxLines = 1,
                fontSize = 9.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
