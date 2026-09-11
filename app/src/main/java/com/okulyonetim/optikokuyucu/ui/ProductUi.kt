package com.okulyonetim.optikokuyucu.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.settings.AppSettingsRepository
import com.okulyonetim.optikokuyucu.settings.AppThemeMode

// Tek merkezli tasarım sistemi: renkler, gradyanlar, ortak yüzeyler, arama, filtreler,
// özet alanları, ayar grupları, üst/alt navigasyon ve durum rozetleri yalnız burada tanımlanır.
// ── Birincil: Derin İndigo ──────────────────────────────
private val ProductPrimary        = Color(0xFF4318C9)
private val ProductPrimaryLight   = Color(0xFFEBE6FF)

// ── İkincil: Amber-Turuncu ─────────────────────────────
private val ProductSecondary      = Color(0xFFD97706)
private val ProductSecondaryLight = Color(0xFFFEF3C7)

// ── Üçüncül: Cyan ──────────────────────────────────────
private val ProductAccent         = Color(0xFF0891B2)
private val ProductAccentLight    = Color(0xFFCFFAFE)

// ── Arka plan ──────────────────────────────────────────
private val ProductBackground     = Color(0xFFF5F3FF)   // lavender, krem yerine
private val ProductSurface        = Color(0xFFFFFFFF)

// ── Durum ──────────────────────────────────────────────
private val ProductGreen          = Color(0xFF065F46)
private val ProductGreenSoft      = Color(0xFFD1FAE5)
private val ProductOrange         = Color(0xFF92400E)
private val ProductOrangeSoft     = Color(0xFFFEF3C7)
private val ProductRed            = Color(0xFF9F1239)
private val ProductRedSoft        = Color(0xFFFFE4E6)

// ── Gradyan paleti ─────────────────────────────────────
private val ProductGradientPalette = listOf(
    Color(0xFF6030E8) to Color(0xFF4318C9),   // indigo
    Color(0xFF0891B2) to Color(0xFF0D9488),   // cyan-teal
    Color(0xFFF59E0B) to Color(0xFFEA580C),   // amber-turuncu
    Color(0xFF7C3AED) to Color(0xFF4318C9),   // mor-indigo
    Color(0xFF059669) to Color(0xFF16A34A),   // zümrüt
)

private fun productPaletteIndex(seed: String): Int {
    val hash = seed.trim().uppercase().hashCode()
    val positive = if (hash == Int.MIN_VALUE) 0 else kotlin.math.abs(hash)
    return positive % ProductGradientPalette.size
}

/** Verilen metne (isim, başlık vb.) göre sabit, okul temalı bir gradyan üretir. */
fun productAccentBrush(seed: String): Brush {
    val (start, end) = ProductGradientPalette[productPaletteIndex(seed)]
    return Brush.linearGradient(listOf(start, end))
}

/** Verilen metne göre sabit bir vurgu rengi üretir (kart kenarlıkları, rozet metinleri için). */
fun productAccentColor(seed: String): Color = ProductGradientPalette[productPaletteIndex(seed)].second

// ── Hero gradyanlar ────────────────────────────────────
private val HeroGradientLight = Brush.linearGradient(
    listOf(Color(0xFF4318C9), Color(0xFF0891B2))
)
private val HeroGradientDark = Brush.linearGradient(
    listOf(Color(0xFF1E1257), Color(0xFF0C3B52))
)

private val LightProductScheme = lightColorScheme(
    primary            = ProductPrimary,
    onPrimary          = Color.White,
    primaryContainer   = ProductPrimaryLight,
    onPrimaryContainer = Color(0xFF2D0E8F),
    secondary          = ProductSecondary,
    onSecondary        = Color.White,
    secondaryContainer = ProductSecondaryLight,
    onSecondaryContainer = Color(0xFF451A03),
    tertiary           = ProductAccent,
    onTertiary         = Color.White,
    tertiaryContainer  = ProductAccentLight,
    onTertiaryContainer = Color(0xFF164E63),
    background         = ProductBackground,
    onBackground       = Color(0xFF1A1535),
    surface            = ProductSurface,
    onSurface          = Color(0xFF1A1535),
    surfaceVariant     = Color(0xFFEDE9FE),
    onSurfaceVariant   = Color(0xFF4B4869),
    outline            = Color(0xFF8B85AD),
    outlineVariant     = Color(0xFFD4D0EA),
    error              = ProductRed,
    onError            = Color.White,
)

private val DarkProductScheme = darkColorScheme(
    primary            = Color(0xFFB8A8FF),
    onPrimary          = Color(0xFF1E0A6E),
    primaryContainer   = Color(0xFF2D0E8F),
    onPrimaryContainer = Color(0xFFEBE6FF),
    secondary          = Color(0xFFFCD34D),
    onSecondary        = Color(0xFF451A03),
    secondaryContainer = Color(0xFF6B3407),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary           = Color(0xFF67E8F9),
    onTertiary         = Color(0xFF0E4F5E),
    tertiaryContainer  = Color(0xFF164E63),
    onTertiaryContainer = Color(0xFFCFFAFE),
    background         = Color(0xFF0C0A1A),
    onBackground       = Color(0xFFE8E4FF),
    surface            = Color(0xFF18162B),
    onSurface          = Color(0xFFE8E4FF),
    surfaceVariant     = Color(0xFF221F38),
    onSurfaceVariant   = Color(0xFFA09BC0),
    outline            = Color(0xFF6B6685),
    outlineVariant     = Color(0xFF2D2B45),
    error              = Color(0xFFFCA5A5),
    onError            = Color(0xFF7F1D1D),
)

class ProductThemeController internal constructor(
    val mode: AppThemeMode,
    val isDark: Boolean,
    private val changeMode: (AppThemeMode) -> Unit
) {
    fun setMode(mode: AppThemeMode) = changeMode(mode)

    fun toggleLightDark() {
        setMode(if (isDark) AppThemeMode.LIGHT else AppThemeMode.DARK)
    }
}

val LocalProductThemeController = staticCompositionLocalOf<ProductThemeController?> { null }

enum class ProductTab {
    HOME,
    EXAMS,
    STUDENTS,
    FORMS,
    SETTINGS
}

@Composable
fun OptikProductTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { AppSettingsRepository(context.applicationContext) }
    var themeMode by remember { mutableStateOf(repository.load().themeMode) }
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val controller = remember(themeMode, dark, repository) {
        ProductThemeController(themeMode, dark) { next ->
            if (next != themeMode) {
                runCatching { repository.saveThemeMode(next) }
                    .onSuccess { themeMode = next }
            }
        }
    }

    CompositionLocalProvider(LocalProductThemeController provides controller) {
        MaterialTheme(
            colorScheme = if (dark) DarkProductScheme else LightProductScheme
        ) {
            AppFeedbackProvider {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    content()
                }
            }
        }
    }
}

/** Uygulamanın ana vurgu alanları için ortak indigo-cyan hero yüzeyi. */
@Composable
fun ProductHeroCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = LocalProductThemeController.current?.isDark ?: isSystemInDarkTheme()
    val brush = if (dark) HeroGradientDark else HeroGradientLight
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(brush)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

// Belirgin, her iki modda da görünür 2dp kenarlık. "lightControlBorder" adı korunuyor
// (birçok çağrı noktası tarafından kullanılıyor) ama artık koyu modda da kenarlık çiziyor.
@Composable
private fun lightControlBorder(): BorderStroke {
    return BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductTopBar(
    title: String,
    leadingText: String? = null,
    onLeadingClick: (() -> Unit)? = null,
    actionText: String = "⋮",
    onActionClick: (() -> Unit)? = null,
    showAutomaticBack: Boolean = true,
    includeStatusBarPadding: Boolean = true
) {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val themeController = LocalProductThemeController.current
    val rootTabTitle = title == "Sınavlar" || title == "Öğrenciler" || title == "Ayarlar" || title == "Optik Formlar"
    val automaticBackEnabled = showAutomaticBack || title == "Sınavlar"
    val resolvedLeadingText = when {
        leadingText != null -> leadingText
        automaticBackEnabled && dispatcher != null -> "‹"
        else -> null
    }
    val resolvedLeadingClick = when {
        onLeadingClick != null -> onLeadingClick
        automaticBackEnabled && dispatcher != null -> ({ dispatcher.onBackPressed() })
        else -> null
    }
    val resolvedActionText = actionText.takeIf { onActionClick != null }
    val compactRowModifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .padding(horizontal = 4.dp)
    val rowModifier = if (includeStatusBarPadding && !rootTabTitle) {
        Modifier.statusBarsPadding().then(compactRowModifier)
    } else {
        compactRowModifier
    }

    val dark = LocalProductThemeController.current?.isDark ?: isSystemInDarkTheme()
    val heroBrush = if (dark) HeroGradientDark else HeroGradientLight
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(heroBrush)
    ) {
        Box(modifier = rowModifier) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderAction(text = resolvedLeadingText, onClick = resolvedLeadingClick, onGradient = true)
            }
            Text(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 118.dp),
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (resolvedActionText != null && onActionClick != null) {
                    HeaderAction(text = resolvedActionText, onClick = onActionClick, onGradient = true)
                }
                if (themeController != null) {
                    HeaderAction(
                        text = if (themeController.isDark) "☀" else "☾",
                        onClick = { themeController.toggleLightDark() },
                        onGradient = true
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderAction(text: String?, onClick: (() -> Unit)?, onGradient: Boolean = false) {
    val actionWidth = 54.dp
    val actionHeight = 44.dp
    val fgColor = if (onGradient) Color.White else MaterialTheme.colorScheme.primary
    val border = if (onGradient) {
        BorderStroke(2.dp, Color.White.copy(alpha = 0.55f))
    } else {
        lightControlBorder()
    }
    if (text != null && onClick != null) {
        Surface(
            modifier = Modifier
                .size(width = actionWidth, height = actionHeight)
                .clickable(onClick = onClick),
            color = Color.Transparent,
            contentColor = fgColor,
            shape = RoundedCornerShape(12.dp),
            border = border
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = text,
                    color = fgColor,
                    fontSize = when {
                        text.length > 2 -> 13.sp
                        text == "‹" -> 30.sp
                        text == "☀" || text == "☾" -> 25.sp
                        else -> 24.sp
                    },
                    fontWeight = if (text.length > 2) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    } else {
        Spacer(Modifier.size(width = actionWidth, height = actionHeight))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsAppearanceSheet(
    themeController: ProductThemeController,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Görünüm", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Uygulamanın açık/koyu görünümünü seçin.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                ThemeModeButton(Modifier.weight(1f), "Sistem", AppThemeMode.SYSTEM, themeController)
                ThemeModeButton(Modifier.weight(1f), "Açık", AppThemeMode.LIGHT, themeController)
                ThemeModeButton(Modifier.weight(1f), "Koyu", AppThemeMode.DARK, themeController)
            }
            Text(
                "Tema değişikliği anında uygulanır ve cihazda saklanır.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ThemeModeButton(
    modifier: Modifier,
    label: String,
    mode: AppThemeMode,
    controller: ProductThemeController
) {
    if (controller.mode == mode) {
        FilledTonalButton(
            modifier = modifier,
            onClick = { controller.setMode(mode) },
            border = lightControlBorder()
        ) { Text(label, fontSize = 12.sp) }
    } else {
        OutlinedButton(
            modifier = modifier,
            onClick = { controller.setMode(mode) },
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
        ) { Text(label, fontSize = 12.sp) }
    }
}

@Composable
fun ProductSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = {
            Text(
                text = placeholder,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Text(
                text = "⌕",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            )
        },
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(14.dp)
    )
}

/**
 * Belirgin, 2dp kenarlıklı kart. [accentColor] verilirse kenarlık o renkte olur
 * (ör. öğrenci/sınav kartlarında isme göre sabit bir okul rengi); verilmezse
 * nötr ama yine de belirgin bir kenarlık kullanılır.
 */
@Composable
fun ProductCompactCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accentColor: Color? = null,
    content: @Composable () -> Unit
) {
    val resolvedModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    val light = !(LocalProductThemeController.current?.isDark ?: isSystemInDarkTheme())
    val borderColor = accentColor
        ?: if (light) MaterialTheme.colorScheme.outline.copy(alpha = 0.85f) else MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = resolvedModifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = if (light) 1.dp else 0.dp
    ) {
        content()
    }
}

@Composable
fun ProductSettingsSection(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ProductCompactCard(modifier = modifier.fillMaxWidth(), accentColor = accentColor) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@Composable
fun ProductSettingsLink(
    symbol: String,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (title == "Optik Formlar" || title == "Gelişmiş Araçlar") return

    ProductCompactCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductInitialBadge(symbol)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ProductMetricStrip(
    metrics: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    ProductCompactCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            metrics.take(4).forEach { (label, value) ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = value,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = productAccentColor(label)
                    )
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun ProductEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    ProductCompactCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(body, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** İsme/metne göre sabit okul-temalı bir gradyanla boyanan baş harf rozeti. */
@Composable
fun ProductInitialBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .background(productAccentBrush(text), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(productAccentBrush(label))
                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, productAccentColor(label).copy(alpha = 0.72f))
        ) {
            Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
        }
    }
}

enum class ProductBadgeTone { GREEN, ORANGE, RED, NEUTRAL }

@Composable
fun ProductStatusBadge(text: String, tone: ProductBadgeTone) {
    val light = !(LocalProductThemeController.current?.isDark ?: isSystemInDarkTheme())
    val background = when (tone) {
        ProductBadgeTone.GREEN -> if (light) ProductGreenSoft else Color(0xFF063B33)
        ProductBadgeTone.ORANGE -> if (light) ProductOrangeSoft else Color(0xFF4A2608)
        ProductBadgeTone.RED -> if (light) ProductRedSoft else Color(0xFF4D1024)
        ProductBadgeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when (tone) {
        ProductBadgeTone.GREEN -> if (light) ProductGreen else Color(0xFF6EE7B7)
        ProductBadgeTone.ORANGE -> if (light) ProductOrange else Color(0xFFFCD34D)
        ProductBadgeTone.RED -> if (light) ProductRed else Color(0xFFFDA4AF)
        ProductBadgeTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(20.dp),
        border = if (tone == ProductBadgeTone.NEUTRAL) null else BorderStroke(1.5.dp, foreground.copy(alpha = 0.8f))
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
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
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductBottomItem(Modifier.weight(1f), "Anasayfa", Icons.Rounded.Home, selected == ProductTab.HOME) { onSelect(ProductTab.HOME) }
            ProductBottomItem(Modifier.weight(1f), "Sınavlar", Icons.Rounded.Assignment, selected == ProductTab.EXAMS) { onSelect(ProductTab.EXAMS) }
            ProductBottomItem(Modifier.weight(1f), "Öğrenciler", Icons.Rounded.People, selected == ProductTab.STUDENTS) { onSelect(ProductTab.STUDENTS) }
            ProductBottomItem(Modifier.weight(1f), "Formlar", Icons.Rounded.RadioButtonChecked, selected == ProductTab.FORMS) { onSelect(ProductTab.FORMS) }
            ProductBottomItem(Modifier.weight(1f), "Ayarlar", Icons.Rounded.Settings, selected == ProductTab.SETTINGS) { onSelect(ProductTab.SETTINGS) }
        }
    }
}

@Composable
private fun ProductBottomItem(
    modifier: Modifier,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        modifier = modifier.height(52.dp),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            val iconBoxModifier = Modifier.size(width = 32.dp, height = 27.dp)
            if (selected) {
                Box(
                    modifier = iconBoxModifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(productAccentBrush(label)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            } else {
                Box(modifier = iconBoxModifier, contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Text(
                label,
                maxLines = 1,
                fontSize = 9.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}
