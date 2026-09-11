from pathlib import Path

ROOT = Path('app/src/main/java/com/okulyonetim/optikokuyucu/ui')

# ProductUi.kt — mirror the uploaded centralized design system.
product = ROOT / 'ProductUi.kt'
text = product.read_text(encoding='utf-8')

text = text.replace(
    'import androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.clickable\n',
    'import androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.background\nimport androidx.compose.foundation.border\nimport androidx.compose.foundation.clickable\n',
    1,
)
text = text.replace(
    'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier\nimport androidx.compose.ui.graphics.Color\n',
    'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier\nimport androidx.compose.ui.draw.clip\nimport androidx.compose.ui.graphics.Brush\nimport androidx.compose.ui.graphics.Color\n',
    1,
)

scheme_start = text.index('// Tek merkezli tasarım sistemi:')
scheme_end = text.index('class ProductThemeController internal constructor(')
new_scheme = '''// Tek merkezli tasarım sistemi: renkler, gradyanlar, ortak yüzeyler, arama, filtreler,
// özet alanları, ayar grupları, üst/alt navigasyon ve durum rozetleri yalnız burada tanımlanır.
// Okul temalı, renkli kimlik: mor birincil, amber ikincil, mercan/pembe vurgu.
private val ProductPrimary = Color(0xFF534AB7)
private val ProductPrimaryLight = Color(0xFFEEEDFE)
private val ProductSecondary = Color(0xFFBA7517)
private val ProductSecondaryLight = Color(0xFFFAEEDA)
private val ProductAccent = Color(0xFFD4537E)
private val ProductAccentLight = Color(0xFFFBEAF0)
private val ProductBackground = Color(0xFFF5F2EA)
private val ProductSurface = Color(0xFFFFFFFF)

private val ProductGreen = Color(0xFF3B6D11)
private val ProductGreenSoft = Color(0xFFEAF3DE)
private val ProductOrange = Color(0xFF854F0B)
private val ProductOrangeSoft = Color(0xFFFAEEDA)
private val ProductRed = Color(0xFFA32D2D)
private val ProductRedSoft = Color(0xFFFCEBEB)

// Kart kenarlıkları, avatarlar ve rozetler için dönen okul temalı gradyan paleti.
// Her öğe adına göre sabit bir renge eşlenir; rastgele değil, tutarlıdır.
private val ProductGradientPalette: List<Pair<Color, Color>> = listOf(
    Color(0xFF7F77DD) to Color(0xFF534AB7), // mor
    Color(0xFFF0997B) to Color(0xFFD85A30), // mercan
    Color(0xFF85B7EB) to Color(0xFF378ADD), // mavi
    Color(0xFFED93B1) to Color(0xFFD4537E), // pembe
    Color(0xFFFAC775) to Color(0xFFEF9F27), // amber
    Color(0xFF97C459) to Color(0xFF639922), // yeşil
    Color(0xFF9FE1CB) to Color(0xFF1D9E75)  // teal
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

private val HeroGradientLight = Brush.linearGradient(listOf(Color(0xFF7F77DD), Color(0xFFD4537E)))
private val HeroGradientDark = Brush.linearGradient(listOf(Color(0xFF534AB7), Color(0xFF993556)))

private val LightProductScheme = lightColorScheme(
    primary = ProductPrimary,
    onPrimary = Color.White,
    primaryContainer = ProductPrimaryLight,
    onPrimaryContainer = Color(0xFF26215C),
    secondary = ProductSecondary,
    onSecondary = Color.White,
    secondaryContainer = ProductSecondaryLight,
    onSecondaryContainer = Color(0xFF412402),
    tertiary = ProductAccent,
    onTertiary = Color.White,
    tertiaryContainer = ProductAccentLight,
    onTertiaryContainer = Color(0xFF4B1528),
    background = ProductBackground,
    onBackground = Color(0xFF2C2C2A),
    surface = ProductSurface,
    onSurface = Color(0xFF2C2C2A),
    surfaceVariant = Color(0xFFF1EFE8),
    onSurfaceVariant = Color(0xFF5F5E5A),
    outline = Color(0xFF888780),
    outlineVariant = Color(0xFFD3D1C7),
    error = ProductRed,
    onError = Color.White
)

private val DarkProductScheme = darkColorScheme(
    primary = Color(0xFFAFA9EC),
    onPrimary = Color(0xFF26215C),
    primaryContainer = Color(0xFF3C3489),
    onPrimaryContainer = Color(0xFFEEEDFE),
    secondary = Color(0xFFFAC775),
    onSecondary = Color(0xFF412402),
    secondaryContainer = Color(0xFF633806),
    onSecondaryContainer = Color(0xFFFAEEDA),
    tertiary = Color(0xFFED93B1),
    onTertiary = Color(0xFF4B1528),
    tertiaryContainer = Color(0xFF72243E),
    onTertiaryContainer = Color(0xFFFBEAF0),
    background = Color(0xFF121214),
    onBackground = Color(0xFFEDEDEE),
    surface = Color(0xFF1D1D22),
    onSurface = Color(0xFFEDEDEE),
    surfaceVariant = Color(0xFF232326),
    onSurfaceVariant = Color(0xFFBABABE),
    outline = Color(0xFF838388),
    outlineVariant = Color(0xFF3A3A3D),
    error = Color(0xFFF09595),
    onError = Color(0xFF501313)
)

'''
text = text[:scheme_start] + new_scheme + text[scheme_end:]

old_border = '''@Composable
private fun lightControlBorder(): BorderStroke? {
    val dark = LocalProductThemeController.current?.isDark ?: isSystemInDarkTheme()
    return if (dark) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f))
}
'''
new_border = '''// Belirgin, her iki modda da görünür 2dp kenarlık. "lightControlBorder" adı korunuyor
// (birçok çağrı noktası tarafından kullanılıyor) ama artık koyu modda da kenarlık çiziyor.
@Composable
private fun lightControlBorder(): BorderStroke {
    return BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
}
'''
if old_border not in text:
    raise SystemExit('lightControlBorder block not found')
text = text.replace(old_border, new_border, 1)

top_start = text.index('    Surface(\n        modifier = Modifier.fillMaxWidth(),\n        color = MaterialTheme.colorScheme.background,', text.index('fun ProductTopBar('))
top_end = text.index('\n}\n\n@Composable\nprivate fun HeaderAction', top_start)
new_top = '''    val dark = LocalProductThemeController.current?.isDark ?: isSystemInDarkTheme()
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
    }'''
text = text[:top_start] + new_top + text[top_end:]

header_start = text.index('@Composable\nprivate fun HeaderAction')
header_end = text.index('\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun SettingsAppearanceSheet', header_start)
new_header = '''@Composable
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
'''
text = text[:header_start] + new_header + text[header_end:]

text = text.replace('border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)\n        ) { Text(label, fontSize = 12.sp) }', 'border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)\n        ) { Text(label, fontSize = 12.sp) }', 1)

card_start = text.index('@Composable\nfun ProductCompactCard(')
card_end = text.index('\n@Composable\nfun ProductSettingsSection(', card_start)
new_card = '''/**
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
        border = BorderStroke(2.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = if (light) 1.dp else 0.dp
    ) {
        content()
    }
}
'''
text = text[:card_start] + new_card + text[card_end:]

metric_start = text.index('fun ProductMetricStrip(')
metric_end = text.index('\n@Composable\nfun ProductEmptyState', metric_start)
metric = text[metric_start:metric_end]
metric = metric.replace('color = MaterialTheme.colorScheme.primary', 'color = productAccentColor(label)', 1)
text = text[:metric_start] + metric + text[metric_end:]

badge_start = text.index('@Composable\nfun ProductInitialBadge(')
badge_end = text.index('\n@Composable\nfun ProductFilterPill(', badge_start)
new_badge = '''/** İsme/metne göre sabit okul-temalı bir gradyanla boyanan baş harf rozeti. */
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
'''
text = text[:badge_start] + new_badge + text[badge_end:]

filter_start = text.index('@Composable\nfun ProductFilterPill(')
filter_end = text.index('\nenum class ProductBadgeTone', filter_start)
new_filter = '''@Composable
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
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(2.dp, productAccentColor(label).copy(alpha = 0.7f))
        ) {
            Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
        }
    }
}
'''
text = text[:filter_start] + new_filter + text[filter_end:]

status_start = text.index('@Composable\nfun ProductStatusBadge(')
status_end = text.index('\n@Composable\nfun ProductBottomBar(', status_start)
new_status = '''@Composable
fun ProductStatusBadge(text: String, tone: ProductBadgeTone) {
    val light = !(LocalProductThemeController.current?.isDark ?: isSystemInDarkTheme())
    val background = when (tone) {
        ProductBadgeTone.GREEN -> if (light) ProductGreenSoft else Color(0xFF173404)
        ProductBadgeTone.ORANGE -> if (light) ProductOrangeSoft else Color(0xFF412402)
        ProductBadgeTone.RED -> if (light) ProductRedSoft else Color(0xFF501313)
        ProductBadgeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when (tone) {
        ProductBadgeTone.GREEN -> if (light) ProductGreen else Color(0xFF97C459)
        ProductBadgeTone.ORANGE -> if (light) ProductOrange else Color(0xFFFAC775)
        ProductBadgeTone.RED -> if (light) ProductRed else Color(0xFFF09595)
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
'''
text = text[:status_start] + new_status + text[status_end:]

bottom_start = text.index('@Composable\nprivate fun ProductBottomItem(')
new_bottom = '''@Composable
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
'''
text = text[:bottom_start] + new_bottom
product.write_text(text, encoding='utf-8')

# Student cards: deterministic name-based accent border.
roster = ROOT / 'StudentRosterScreen.kt'
r = roster.read_text(encoding='utf-8')
old_student = '''    ProductCompactCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
'''
new_student = '''    ProductCompactCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        accentColor = productAccentColor(student.name.ifBlank { student.number })
    ) {
'''
if old_student not in r:
    raise SystemExit('student overview card block not found')
r = r.replace(old_student, new_student, 1)
roster.write_text(r, encoding='utf-8')

# Exam list cards: deterministic exam-color border and gradient icon.
exam = ROOT / 'ExamListScreen.kt'
e = exam.read_text(encoding='utf-8')
e = e.replace('import androidx.compose.foundation.clickable\n', 'import androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable\n', 1)
e = e.replace('import androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.LocalContext\n', 'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier\nimport androidx.compose.ui.draw.clip\nimport androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.platform.LocalContext\n', 1)
e = e.replace(
    '    var menuOpen by remember(item.summary.id) { mutableStateOf(false) }\n\n    Card(\n',
    '    var menuOpen by remember(item.summary.id) { mutableStateOf(false) }\n\n    val examAccent = productAccentColor(summary.name.ifBlank { summary.id })\n    Card(\n',
    1,
)
e = e.replace(
    '        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),\n        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)\n',
    '        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),\n        border = BorderStroke(2.dp, examAccent),\n        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)\n',
    1,
)
old_avatar = '''                Surface(
                    modifier = Modifier.size(42.dp),
                    color = if (read) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("▤", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
'''
new_avatar = '''                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(productAccentBrush(summary.name.ifBlank { summary.id })),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▤", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
'''
if old_avatar not in e:
    raise SystemExit('exam icon surface block not found')
e = e.replace(old_avatar, new_avatar, 1)
exam.write_text(e, encoding='utf-8')

# Bump version after the previous LGS/net UI release.
gradle = Path('app/build.gradle.kts')
g = gradle.read_text(encoding='utf-8')
if 'versionCode = 118' not in g or 'versionName = "0.19.62"' not in g:
    raise SystemExit('expected 0.19.62 (118) not found')
g = g.replace('versionCode = 118', 'versionCode = 119', 1)
g = g.replace('versionName = "0.19.62"', 'versionName = "0.19.63"', 1)
gradle.write_text(g, encoding='utf-8')

# Sanity markers from the uploaded design must exist after patching.
checks = {
    product: ['ProductGradientPalette', 'productAccentBrush', 'HeroGradientLight', 'accentColor: Color? = null', 'BorderStroke(2.dp, borderColor)'],
    roster: ['accentColor = productAccentColor(student.name.ifBlank { student.number })'],
    exam: ['val examAccent = productAccentColor', '.background(productAccentBrush(summary.name.ifBlank { summary.id }))'],
}
for path, markers in checks.items():
    data = path.read_text(encoding='utf-8')
    for marker in markers:
        if marker not in data:
            raise SystemExit(f'missing marker {marker!r} in {path}')
