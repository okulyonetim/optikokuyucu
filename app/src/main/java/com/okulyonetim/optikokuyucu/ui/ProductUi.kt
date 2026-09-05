package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

private val ProductBlue = Color(0xFF2F61CF)
private val ProductBlueLight = Color(0xFFE4EEFF)
private val ProductBackground = Color(0xFFF7F7FB)
private val ProductGreen = Color(0xFF2E9B66)
private val ProductGreenSoft = Color(0xFFE8F7F0)
private val ProductOrange = Color(0xFFE48A14)
private val ProductOrangeSoft = Color(0xFFFFF3E3)
private val ProductRed = Color(0xFFD84B45)
private val ProductRedSoft = Color(0xFFFFECEB)

private val LightProductScheme = lightColorScheme(
    primary = ProductBlue,
    onPrimary = Color.White,
    primaryContainer = ProductBlueLight,
    onPrimaryContainer = Color(0xFF163A83),
    background = ProductBackground,
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F1F6),
    outline = Color(0xFFB6B8C0)
)

private val DarkProductScheme = darkColorScheme(
    primary = Color(0xFF9AB8FF),
    onPrimary = Color(0xFF062A69),
    primaryContainer = Color(0xFF173F8A),
    background = Color(0xFF111318),
    surface = Color(0xFF1A1C22),
    surfaceVariant = Color(0xFF252830),
    outline = Color(0xFF8E9099)
)

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
    leadingText: String,
    onLeadingClick: () -> Unit,
    actionText: String = "⋮",
    onActionClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onLeadingClick) {
                Text(leadingText, color = MaterialTheme.colorScheme.onPrimary, fontSize = 28.sp)
            }
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 24.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = { onActionClick?.invoke() }, enabled = onActionClick != null) {
                Text(
                    text = actionText,
                    color = if (onActionClick != null) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.primary,
                    fontSize = 25.sp
                )
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
    selected: String,
    onExams: () -> Unit,
    onStudents: () -> Unit,
    onForms: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 5.dp,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductBottomItem("Sınavlar", "▤", selected == "exams", onExams)
            ProductBottomItem("Öğrenciler", "◉", selected == "students", onStudents)
            ProductBottomItem("Optikler", "◎", selected == "forms", onForms)
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
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                symbol,
                fontSize = 24.sp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Text(
                label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    }
}
