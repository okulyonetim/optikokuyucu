from pathlib import Path

ui = Path('app/src/main/java/com/okulyonetim/optikokuyucu/ui/ProductUi.kt')
text = ui.read_text()
replacements = {
"// Okul temalı, renkli kimlik: mor birincil, amber ikincil, mercan/pembe vurgu.": "// Okul temalı, daha dengeli kimlik: indigo/mavi ana tonlar, teal ve amber yardımcı vurgular.",
"private val ProductAccent = Color(0xFFD4537E)": "private val ProductAccent = Color(0xFF2F80ED)",
"private val ProductAccentLight = Color(0xFFFBEAF0)": "private val ProductAccentLight = Color(0xFFE7F1FF)",
"    Color(0xFF7F77DD) to Color(0xFF534AB7), // mor\n    Color(0xFFF0997B) to Color(0xFFD85A30), // mercan\n    Color(0xFF85B7EB) to Color(0xFF378ADD), // mavi\n    Color(0xFFED93B1) to Color(0xFFD4537E), // pembe\n    Color(0xFFFAC775) to Color(0xFFEF9F27), // amber\n    Color(0xFF97C459) to Color(0xFF639922), // yeşil\n    Color(0xFF9FE1CB) to Color(0xFF1D9E75)  // teal": "    Color(0xFF7F77DD) to Color(0xFF534AB7), // indigo\n    Color(0xFF6FA8FF) to Color(0xFF2F80ED), // mavi\n    Color(0xFF6ED7C1) to Color(0xFF1D9E75), // teal\n    Color(0xFF8BCB8F) to Color(0xFF4E9F55), // yeşil\n    Color(0xFFF4C66A) to Color(0xFFCF8A17), // amber\n    Color(0xFF91A4D8) to Color(0xFF5E6FAE), // slate mavi\n    Color(0xFF75C7D6) to Color(0xFF2F8FA3)  // camgöbeği",
"private val HeroGradientLight = Brush.linearGradient(listOf(Color(0xFF7F77DD), Color(0xFFD4537E)))": "private val HeroGradientLight = Brush.linearGradient(listOf(Color(0xFF665CCF), Color(0xFF2F80ED)))",
"private val HeroGradientDark = Brush.linearGradient(listOf(Color(0xFF534AB7), Color(0xFF993556)))": "private val HeroGradientDark = Brush.linearGradient(listOf(Color(0xFF403780), Color(0xFF245B92)))",
"    tertiary = ProductAccent,\n    onTertiary = Color.White,\n    tertiaryContainer = ProductAccentLight,\n    onTertiaryContainer = Color(0xFF4B1528),": "    tertiary = ProductAccent,\n    onTertiary = Color.White,\n    tertiaryContainer = ProductAccentLight,\n    onTertiaryContainer = Color(0xFF123A64),",
"    tertiary = Color(0xFFED93B1),\n    onTertiary = Color(0xFF4B1528),\n    tertiaryContainer = Color(0xFF72243E),\n    onTertiaryContainer = Color(0xFFFBEAF0),": "    tertiary = Color(0xFF78B7FF),\n    onTertiary = Color(0xFF0B2D4A),\n    tertiaryContainer = Color(0xFF174B73),\n    onTertiaryContainer = Color(0xFFE7F1FF),",
}
for old, new in replacements.items():
    if old not in text:
        raise SystemExit(f'Missing expected block: {old[:80]}')
    text = text.replace(old, new)
ui.write_text(text)

gradle = Path('app/build.gradle.kts')
g = gradle.read_text()
g = g.replace('versionCode = 119', 'versionCode = 120').replace('versionName = "0.19.63"', 'versionName = "0.19.64"')
gradle.write_text(g)
