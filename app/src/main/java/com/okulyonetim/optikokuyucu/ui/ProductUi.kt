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
import com.okulyonetim.optikokuyucu.ui.theme.OptikAccents
import com.okulyonetim.optikokuyucu.ui.theme.OptikBorder
import com.okulyonetim.optikokuyucu.ui.theme.OptikTheme
import com.okulyonetim.optikokuyucu.ui.theme.OptikTextSize
import com.okulyonetim.optikokuyucu.ui.theme.OptikShape

// Ürün arayüzü bileşenleri (üst çubuk, kartlar, rozetler, alt gezinme). Renk, yazı tipi, şekil ve
// boşluk kararları ui/theme paketindedir; bu dosya yalnızca onları kullanır.

/** Verilen metne (isim, başlık vb.) göre sabit, okul temalı bir gradyan üretir. */
fun productAccentBrush(seed: String): Brush = OptikAccents.forSeed(seed).brush

/**
 * Verilen metne göre sabit bir vurgu tonu üretir (kart kenarlıkları, rozet metinleri için). Ton,
 * hem açık hem koyu zeminde okunacak orta parlaklıktadır.
 */
fun productAccentColor(seed: String): Color = OptikAccents.forSeed(seed).tone

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
        OptikTheme(darkTheme = dark) {
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(OptikShape.xl)
            .background(OptikTheme.colors.heroBrush)
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
    return BorderStroke(OptikBorder.regular, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f))
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

    val onHero = OptikTheme.colors.onHero
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(OptikTheme.colors.heroBrush)
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
                color = onHero,
                fontSize = OptikTextSize.title,
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
    val onHero = OptikTheme.colors.onHero
    val fgColor = if (onGradient) onHero else MaterialTheme.colorScheme.primary
    val border = if (onGradient) {
        BorderStroke(OptikBorder.strong, onHero.copy(alpha = 0.55f))
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
            shape = OptikShape.md,
            border = border
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = text,
                    color = fgColor,
                    fontSize = when {
                        text.length > 2 -> OptikTextSize.body
                        text == "‹" -> OptikTextSize.hero
                        text == "☀" || text == "☾" -> OptikTextSize.display
                        else -> OptikTextSize.display
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
                fontSize = OptikTextSize.small,
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
                fontSize = OptikTextSize.micro,
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
        ) { Text(label, fontSize = OptikTextSize.small) }
    } else {
        OutlinedButton(
            modifier = modifier,
            onClick = { controller.setMode(mode) },
            border = BorderStroke(OptikBorder.strong, MaterialTheme.colorScheme.outline)
        ) { Text(label, fontSize = OptikTextSize.small) }
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
                fontSize = OptikTextSize.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Text(
                text = "⌕",
                fontSize = OptikTextSize.heading,
                color = MaterialTheme.colorScheme.primary
            )
        },
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = OptikShape.md
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
    val light = !OptikTheme.isDark
    val borderColor = accentColor
        ?: if (light) MaterialTheme.colorScheme.outline.copy(alpha = 0.85f) else MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = resolvedModifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = OptikShape.md,
        border = BorderStroke(OptikBorder.regular, borderColor),
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
                fontSize = OptikTextSize.body,
                fontWeight = FontWeight.SemiBold
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    fontSize = OptikTextSize.micro,
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
                Text(title, fontSize = OptikTextSize.body, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    fontSize = OptikTextSize.micro,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("›", fontSize = OptikTextSize.heading, color = MaterialTheme.colorScheme.primary)
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
                        fontSize = OptikTextSize.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = productAccentColor(label)
                    )
                    Text(
                        text = label,
                        fontSize = OptikTextSize.micro,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = OptikTextSize.body)
            Text(body, fontSize = OptikTextSize.micro, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            .background(productAccentBrush(text), OptikShape.md),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = OptikTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = OptikTheme.colors.onHero)
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
                .clip(OptikShape.md)
                .background(productAccentBrush(label))
                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), OptikShape.md)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            Text(text, color = OptikTheme.colors.onHero, fontSize = OptikTextSize.small, fontWeight = FontWeight.SemiBold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = OptikShape.md,
            border = BorderStroke(OptikBorder.regular, productAccentColor(label).copy(alpha = 0.72f))
        ) {
            Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = OptikTextSize.small)
        }
    }
}

enum class ProductBadgeTone { GREEN, ORANGE, RED, NEUTRAL }

@Composable
fun ProductStatusBadge(text: String, tone: ProductBadgeTone) {
    val status = OptikTheme.status
    val semantic = when (tone) {
        ProductBadgeTone.GREEN -> status.success
        ProductBadgeTone.ORANGE -> status.warning
        ProductBadgeTone.RED -> status.danger
        ProductBadgeTone.NEUTRAL -> null
    }
    val background = semantic?.container ?: MaterialTheme.colorScheme.surfaceVariant
    val foreground = semantic?.content ?: MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = background,
        contentColor = foreground,
        shape = OptikShape.xl,
        border = if (tone == ProductBadgeTone.NEUTRAL) null else BorderStroke(OptikBorder.regular, foreground.copy(alpha = 0.8f))
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            text = text,
            fontSize = OptikTextSize.micro,
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
        border = BorderStroke(OptikBorder.hairline, MaterialTheme.colorScheme.outlineVariant)
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
        shape = OptikShape.md,
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
                        .clip(OptikShape.sm)
                        .background(productAccentBrush(label)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = OptikTheme.colors.onHero,
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
                fontSize = OptikTextSize.nav,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}
