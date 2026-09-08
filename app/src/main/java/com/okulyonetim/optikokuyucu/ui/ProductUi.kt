package com.okulyonetim.optikokuyucu.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.settings.AppSettingsRepository
import com.okulyonetim.optikokuyucu.settings.AppThemeMode

// Tek merkezli tasarım sistemi: renkler, ortak yüzeyler, arama, filtreler,
// özet alanları, üst/alt navigasyon ve durum rozetleri yalnız burada tanımlanır.
private val ProductPrimary = Color(0xFF0B6048)
private val ProductPrimaryLight = Color(0xFFDDEFE6)
private val ProductSecondary = Color(0xFF2F936E)
private val ProductSecondaryLight = Color(0xFFD8EEE4)
private val ProductAccent = Color(0xFFF16A45)
private val ProductAccentLight = Color(0xFFFFE1D6)
private val ProductBackground = Color(0xFFFAF9F5)
private val ProductSurface = Color(0xFFFFFFFF)

private val ProductGreen = Color(0xFF147A55)
private val ProductGreenSoft = Color(0xFFE0F2E8)
private val ProductOrange = Color(0xFFC98408)
private val ProductOrangeSoft = Color(0xFFFFE9B9)
private val ProductRed = Color(0xFFB43A35)
private val ProductRedSoft = Color(0xFFFFE7E3)

private val LightProductScheme = lightColorScheme(
    primary = ProductPrimary,
    onPrimary = Color.White,
    primaryContainer = ProductPrimaryLight,
    onPrimaryContainer = Color(0xFF0A3B2E),
    secondary = ProductSecondary,
    onSecondary = Color.White,
    secondaryContainer = ProductSecondaryLight,
    onSecondaryContainer = Color(0xFF0B4031),
    tertiary = ProductAccent,
    onTertiary = Color.White,
    tertiaryContainer = ProductAccentLight,
    onTertiaryContainer = Color(0xFF67220F),
    background = ProductBackground,
    onBackground = Color(0xFF17372E),
    surface = ProductSurface,
    onSurface = Color(0xFF17372E),
    surfaceVariant = Color(0xFFF2F4EF),
    onSurfaceVariant = Color(0xFF5A6962),
    outline = Color(0xFF87968F),
    outlineVariant = Color(0xFFDDE3DE),
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val DarkProductScheme = darkColorScheme(
    primary = Color(0xFF69D49F),
    onPrimary = Color(0xFF003928),
    primaryContainer = Color(0xFF0A503B),
    onPrimaryContainer = Color(0xFFD8F5E7),
    secondary = Color(0xFF8AD7B4),
    onSecondary = Color(0xFF073A2D),
    secondaryContainer = Color(0xFF174B39),
    onSecondaryContainer = Color(0xFFD8F4E6),
    tertiary = Color(0xFFFF8D68),
    onTertiary = Color(0xFF511807),
    tertiaryContainer = Color(0xFF6B301E),
    onTertiaryContainer = Color(0xFFFFDDD2),
    background = Color(0xFF071611),
    onBackground = Color(0xFFEAF3EE),
    surface = Color(0xFF0D211A),
    onSurface = Color(0xFFEAF3EE),
    surfaceVariant = Color(0xFF173129),
    onSurfaceVariant = Color(0xFFB9CCC2),
    outline = Color(0xFF91AA9E),
    outlineVariant = Color(0xFF36584B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductTopBar(
    title: String,
    leadingText: String? = null,
    onLeadingClick: (() -> Unit)? = null,
    actionText: String = "⋮",
    onActionClick: (() -> Unit)? = null,
    showAutomaticBack: Boolean = true
) {
    val context = LocalContext.current
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val themeController = LocalProductThemeController.current
    val settingsRepository = remember(context) { AppSettingsRepository(context.applicationContext) }
    var settingsPanelOpen by remember { mutableStateOf(false) }
    val resolvedLeadingText = when {
        leadingText != null -> leadingText
        showAutomaticBack && dispatcher != null -> "‹"
        else -> null
    }
    val resolvedLeadingClick = when {
        onLeadingClick != null -> onLeadingClick
        showAutomaticBack && dispatcher != null -> ({ dispatcher.onBackPressed() })
        else -> null
    }
    val settingsPanelAvailable = title == "Ayarlar" && onActionClick == null && themeController != null
    val resolvedActionText = when {
        onActionClick != null -> actionText
        settingsPanelAvailable -> "◐"
        else -> null
    }
    val resolvedActionClick: (() -> Unit)? = when {
        onActionClick != null -> onActionClick
        settingsPanelAvailable -> ({ settingsPanelOpen = true })
        else -> null
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(46.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderAction(text = resolvedLeadingText, onClick = resolvedLeadingClick)
            Text(
                modifier = Modifier.weight(1f),
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            HeaderAction(text = resolvedActionText, onClick = resolvedActionClick)
        }
    }

    if (title == "Ayarlar" && onActionClick == null) {
        SettingsSubjectsCard(settingsRepository)
    }

    if (settingsPanelOpen && themeController != null) {
        SettingsAppearanceSheet(
            themeController = themeController,
            onDismiss = { settingsPanelOpen = false }
        )
    }
}

@Composable
private fun HeaderAction(text: String?, onClick: (() -> Unit)?) {
    val actionWidth = 56.dp
    if (text != null && onClick != null) {
        Box(
            modifier = Modifier
                .size(width = actionWidth, height = 38.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.primary,
                fontSize = if (text.length > 2) 13.sp else 20.sp,
                fontWeight = if (text.length > 2) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    } else {
        Spacer(Modifier.size(width = actionWidth, height = 38.dp))
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Görünüm", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Uygulamanın açık/koyu görünümünü seçin.",
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
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(22.dp))
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
        FilledTonalButton(modifier = modifier, onClick = { controller.setMode(mode) }) { Text(label, fontSize = 12.sp) }
    } else {
        OutlinedButton(modifier = modifier, onClick = { controller.setMode(mode) }) { Text(label, fontSize = 12.sp) }
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

@Composable
fun ProductCompactCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val resolvedModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Surface(
        modifier = resolvedModifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        content()
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
                        color = MaterialTheme.colorScheme.primary
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

@Composable
fun ProductInitialBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(36.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
            shape = RoundedCornerShape(14.dp),
            contentPadding = ButtonDefaults.ContentPadding
        ) {
            Text(text, fontSize = 12.sp)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
        ProductBadgeTone.GREEN -> if (light) ProductGreenSoft else Color(0xFF153E2D)
        ProductBadgeTone.ORANGE -> if (light) ProductOrangeSoft else Color(0xFF4D3510)
        ProductBadgeTone.RED -> if (light) ProductRedSoft else Color(0xFF4B2621)
        ProductBadgeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when (tone) {
        ProductBadgeTone.GREEN -> if (light) ProductGreen else Color(0xFF77D7A3)
        ProductBadgeTone.ORANGE -> if (light) ProductOrange else Color(0xFFFFC75A)
        ProductBadgeTone.RED -> if (light) ProductRed else Color(0xFFFF9B90)
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
            Surface(
                modifier = Modifier.size(width = 32.dp, height = 27.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(10.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
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
