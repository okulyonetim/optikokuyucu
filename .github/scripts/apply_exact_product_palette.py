from pathlib import Path

path = Path('app/src/main/java/com/okulyonetim/optikokuyucu/ui/ProductUi.kt')
text = path.read_text()
start_marker = '// Okul temalı, renkli kimlik:'
end_marker = 'class ProductThemeController internal constructor('
start = text.index(start_marker)
end = text.index(end_marker)

block = '''// ── Birincil: Derin İndigo ──────────────────────────────
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

'''

path.write_text(text[:start] + block + text[end:])
