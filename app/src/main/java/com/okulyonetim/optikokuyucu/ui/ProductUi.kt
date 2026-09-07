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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.settings.AppSettingsRepository
import com.okulyonetim.optikokuyucu.settings.AppThemeMode

private val ProductPrimary = Color(0xFF3159D9)
private val ProductPrimaryLight = Color(0xFFE1E8FF)
private val ProductBackground = Color(0xFFF7F8FC)
private val ProductGreen = Color(0xFF1F7A52)
private val ProductGreenSoft = Color(0xFFE6F5ED)
private val ProductOrange = Color(0xFFB86100)
private val ProductOrangeSoft = Color(0xFFFFEED8)
private val ProductRed = Color(0xFFB92F34)
private val ProductRedSoft = Color(0xFFFFE9EA)

private val LightProductScheme = lightColorScheme(
    primary = ProductPrimary,
    onPrimary = Color.White,
    primaryContainer = ProductPrimaryLight,
    onPrimaryContainer = Color(0xFF13285D),
    secondary = Color(0xFF0F766E),
    onSecondary = Color.White,
    background = ProductBackground,
    onBackground = Color(0xFF171A20),
    surface = Color.White,
    onSurface = Color(0xFF171A20),
    surfaceVariant = Color(0xFFE9EDF4),
    onSurfaceVariant = Color(0xFF4A5260),
    outline = Color(0xFF7A8494),
    outlineVariant = Color(0xFFC8CFDA),
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val DarkProductScheme = darkColorScheme(
    primary = Color(0xFFA9BCFF),
    onPrimary = Color(0xFF0E2453),
    primaryContainer = Color(0xFF263B78),
    onPrimaryContainer = Color(0xFFE6EBFF),
    secondary = Color(0xFF79D9CC),
    onSecondary = Color(0xFF063C37),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFF0F2F7),
    surface = Color(0xFF171B22),
    onSurface = Color(0xFFF0F2F7),
    surfaceVariant = Color(0xFF252B35),
    onSurfaceVariant = Color(0xFFC7CED9),
    outline = Color(0xFF929BAA),
    outlineVariant = Color(0xFF4B5360),
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
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderAction(text = resolvedLeadingText, onClick = resolvedLeadingClick)
            Text(
                modifier = Modifier.weight(1f),
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
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
    val actionWidth = 68.dp
    if (text != null && onClick != null) {
        Box(
            modifier = Modifier
                .size(width = actionWidth, height = 40.dp)
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
        Spacer(Modifier.size(width = actionWidth, height = 40.dp))
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
    val light = !(LocalProductThemeController.current?.isDark ?: isSystemInDarkTheme())
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductBottomItem(Modifier.weight(1f), "Anasayfa", "⌂", selected == ProductTab.HOME) { onSelect(ProductTab.HOME) }
            ProductBottomItem(Modifier.weight(1f), "Sınavlar", "▤", selected == ProductTab.EXAMS) { onSelect(ProductTab.EXAMS) }
            ProductBottomItem(Modifier.weight(1f), "Öğrenciler", "●", selected == ProductTab.STUDENTS) { onSelect(ProductTab.STUDENTS) }
            ProductBottomItem(Modifier.weight(1f), "Optik Formlar", "◎", selected == ProductTab.FORMS) { onSelect(ProductTab.FORMS) }
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
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
