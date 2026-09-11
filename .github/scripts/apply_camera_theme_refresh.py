from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        print(f"skip: {label}")
        return text
    print(f"apply: {label}")
    return text.replace(old, new, 1)

product_path = Path('app/src/main/java/com/okulyonetim/optikokuyucu/ui/ProductUi.kt')
product = product_path.read_text()
product = replace_once(product, 'private val ProductAccent = Color(0xFFD4537E)', 'private val ProductAccent = Color(0xFF378ADD)', 'blue tertiary')
product = replace_once(product, 'private val ProductAccentLight = Color(0xFFFBEAF0)', 'private val ProductAccentLight = Color(0xFFE6F2FC)', 'blue tertiary light')
old_palette = '''private val ProductGradientPalette: List<Pair<Color, Color>> = listOf(
    Color(0xFF7F77DD) to Color(0xFF534AB7), // mor
    Color(0xFFF0997B) to Color(0xFFD85A30), // mercan
    Color(0xFF85B7EB) to Color(0xFF378ADD), // mavi
    Color(0xFFED93B1) to Color(0xFFD4537E), // pembe
    Color(0xFFFAC775) to Color(0xFFEF9F27), // amber
    Color(0xFF97C459) to Color(0xFF639922), // yeşil
    Color(0xFF9FE1CB) to Color(0xFF1D9E75)  // teal
)'''
new_palette = '''private val ProductGradientPalette: List<Pair<Color, Color>> = listOf(
    Color(0xFF756ED1) to Color(0xFF534AB7), // menekşe
    Color(0xFF6CA6DE) to Color(0xFF378ADD), // mavi
    Color(0xFF72C7B0) to Color(0xFF1D9E75), // teal
    Color(0xFFE8B75E) to Color(0xFFBA7517), // amber
    Color(0xFF8FBA62) to Color(0xFF639922)  // yeşil
)'''
product = replace_once(product, old_palette, new_palette, 'cohesive five-color palette')
product = replace_once(product,
    'private val HeroGradientLight = Brush.linearGradient(listOf(Color(0xFF7F77DD), Color(0xFFD4537E)))',
    'private val HeroGradientLight = Brush.linearGradient(listOf(Color(0xFF7367D6), Color(0xFF3E8ED0)))',
    'light hero violet-blue')
product = replace_once(product,
    'private val HeroGradientDark = Brush.linearGradient(listOf(Color(0xFF534AB7), Color(0xFF993556)))',
    'private val HeroGradientDark = Brush.linearGradient(listOf(Color(0xFF443985), Color(0xFF235E92)))',
    'dark hero violet-blue')
product = replace_once(product, '    tertiary = Color(0xFFED93B1),', '    tertiary = Color(0xFF85B7EB),', 'dark tertiary blue')
product = replace_once(product, '    onTertiary = Color(0xFF4B1528),', '    onTertiary = Color(0xFF102C46),', 'dark on tertiary')
product = replace_once(product, '    tertiaryContainer = Color(0xFF72243E),', '    tertiaryContainer = Color(0xFF244A6B),', 'dark tertiary container')
product = replace_once(product, '    onTertiaryContainer = Color(0xFFFBEAF0),', '    onTertiaryContainer = Color(0xFFE6F2FC),', 'dark on tertiary container')
product_path.write_text(product)

camera_path = Path('app/src/main/java/com/okulyonetim/optikokuyucu/ui/OmrCameraScreen.kt')
camera = camera_path.read_text()
header_old = '''    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.70f),
        contentColor = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
    ) {'''
header_new = '''    Surface(
        modifier = modifier,
        color = Color(0xFF151623).copy(alpha = 0.90f),
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.82f))
    ) {'''
camera = replace_once(camera, header_old, header_new, 'camera header glass card')
status_old = '''    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.72f),
        contentColor = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {'''
status_new = '''    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF151623).copy(alpha = 0.90f),
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.58f))
    ) {'''
camera = replace_once(camera, status_old, status_new, 'camera status glass card')
live_old = '''    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.72f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color(0xFF69D49F).copy(alpha = 0.65f))
    ) {'''
live_new = '''    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF151623).copy(alpha = 0.92f),
        contentColor = Color.White,
        border = BorderStroke(2.dp, Color(0xFF69D49F).copy(alpha = 0.78f))
    ) {'''
camera = replace_once(camera, live_old, live_new, 'live result glass card')
camera = replace_once(camera,
    '.padding(horizontal = 8.dp, vertical = 6.dp),\n            title = title,',
    '.padding(horizontal = 10.dp, vertical = 8.dp),\n            title = title,',
    'header breathing room')
camera = replace_once(camera,
    '.padding(horizontal = 8.dp, vertical = 8.dp),\n            verticalArrangement = Arrangement.spacedBy(6.dp)',
    '.padding(horizontal = 10.dp, vertical = 10.dp),\n            verticalArrangement = Arrangement.spacedBy(7.dp)',
    'status breathing room')
camera_path.write_text(camera)

gradle_path = Path('app/build.gradle.kts')
gradle = gradle_path.read_text()
gradle = replace_once(gradle, 'versionCode = 119', 'versionCode = 120', 'version code')
gradle = replace_once(gradle, 'versionName = "0.19.63"', 'versionName = "0.19.64"', 'version name')
gradle_path.write_text(gradle)
