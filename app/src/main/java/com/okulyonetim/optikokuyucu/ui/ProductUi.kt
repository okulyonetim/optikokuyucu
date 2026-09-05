package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
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

private val ProductPurple = Color(0xFF6B3FD6)
private val ProductPurpleLight = Color(0xFFEFE9FF)
private val ProductBackground = Color(0xFFF8F7FC)
private val ProductGreen = Color(0xFF2E9B66)
private val ProductGreenSoft = Color(0xFFE8F7F0)
private val ProductOrange = Color(0xFFE48A14)
private val ProductOrangeSoft = Color(0xFFFFF3E3)
private val ProductRed = Color(0xFFD84B45)
private val ProductRedSoft = Color(0xFFFFECEB)

private val LightProductScheme = lightColorScheme(
    primary = ProductPurple,
    onPrimary = Color.White,
    primaryContainer = ProductPurpleLight,
    onPrimaryContainer = Color(0xFF31116F),
    secondary = Color(0xFF7255D8),
    background = ProductBackground,
    surface = Color.White,
    surfaceVariant = Color(0xFFF1EFF7),
    outline = Color(0xFFB8B3C2)
)

private val DarkProductScheme = darkColorScheme(
    primary = Color(0xFFC8B5FF),
    onPrimary = Color(0xFF2F0E71),
    primaryContainer = Color(0xFF4B289A),
    onPrimaryContainer = Color(0xFFE9E0FF),
    secondary = Color(0xFFCEBDFF),
    background = Color(0xFF121116),
    surface = Color(0xFF1C1A21),
    surfaceVariant = Color(0xFF292630),
    outline = Color(0xFF938D9C)
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
        colorScheme = if (isSystemInDarkTheme()) DarkProductScheme else LightProductScheme,
        content = content
    )
}

@Composable
fun ProductTopBar(
    title: String,
    leadingText: String? = null,
    onLeadingClick: (() -> Unit)? = null,
    actionText: String = "⋮",
    onActionClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingText != null && onLeadingClick != null) {
                TextButton(onClick = onLeadingClick) {
                    Text(leadingText, color = MaterialTheme.colorScheme.onPrimary, fontSize = 27.sp)
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }

            Text(
                text = title,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (onActionClick != null) {
                TextButton(onClick = onActionClick) {
                    Text(actionText, color = MaterialTheme.colorScheme.onPrimary, fontSize = 25.sp)
                }
            } else {
                Spacer(Modifier.size(48.dp))
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
    val text = if (count == null) label else "$label   $count"
    if (selected) {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(24.dp),
            contentPadding = ButtonDefaults.ContentPadding
        ) {
            Text(text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text(text, color = MaterialTheme.colorScheme.onSurface)
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
    Surface(color = background, contentColor = foreground, shape = RoundedCornerShape(10.dp)) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            text = text,
            fontWeight = FontWeight.Medium
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
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductBottomItem("Anasayfa", "⌂", selected == ProductTab.HOME) { onSelect(ProductTab.HOME) }
            ProductBottomItem("Kamera", "▣", selected == ProductTab.CAMERA) { onSelect(ProductTab.CAMERA) }
            ProductBottomItem("Öğrenciler", "◉", selected == ProductTab.STUDENTS) { onSelect(ProductTab.STUDENTS) }
            ProductBottomItem("Sonuçlar", "▤", selected == ProductTab.RESULTS) { onSelect(ProductTab.RESULTS) }
            ProductBottomItem("Ayarlar", "⚙", selected == ProductTab.SETTINGS) { onSelect(ProductTab.SETTINGS) }
        }
    }
}

@Composable
private fun ProductBottomItem(
    label: String,
    symbol: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        modifier = Modifier.weight(1f),
        onClick = onClick,
        contentPadding = ButtonDefaults.TextButtonContentPadding
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                symbol,
                fontSize = 21.sp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Text(
                label,
                maxLines = 1,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    }
}
