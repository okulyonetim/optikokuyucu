from pathlib import Path
import re

ROOT = Path('.')

# ---------- ProductUi.kt ----------
p = ROOT / 'app/src/main/java/com/okulyonetim/optikokuyucu/ui/ProductUi.kt'
t = p.read_text()

# Soften the heavy outline system while keeping visible borders.
t = t.replace('return BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))',
              'return BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f))')
t = t.replace('border = BorderStroke(2.dp, borderColor),', 'border = BorderStroke(1.5.dp, borderColor),')
t = t.replace('.border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(14.dp))',
              '.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(14.dp))')
t = t.replace('border = BorderStroke(2.dp, productAccentColor(label).copy(alpha = 0.7f))',
              'border = BorderStroke(1.5.dp, productAccentColor(label).copy(alpha = 0.72f))')

hero_marker = '// Belirgin, her iki modda da görünür 2dp kenarlık.'
if 'fun ProductHeroCard(' not in t:
    hero = '''/** Uygulamanın ana vurgu alanları için ortak indigo-cyan hero yüzeyi. */
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

'''
    t = t.replace(hero_marker, hero + hero_marker)

# Allow Settings cards to choose a restrained accent border.
t = t.replace('''fun ProductSettingsSection(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ProductCompactCard(modifier = modifier.fillMaxWidth()) {''', '''fun ProductSettingsSection(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ProductCompactCard(modifier = modifier.fillMaxWidth(), accentColor = accentColor) {''')

# Make dark status badges derive from the new indigo-era semantic palette instead of legacy tones.
t = t.replace('''ProductBadgeTone.GREEN -> if (light) ProductGreenSoft else Color(0xFF173404)
        ProductBadgeTone.ORANGE -> if (light) ProductOrangeSoft else Color(0xFF412402)
        ProductBadgeTone.RED -> if (light) ProductRedSoft else Color(0xFF501313)''', '''ProductBadgeTone.GREEN -> if (light) ProductGreenSoft else Color(0xFF063B33)
        ProductBadgeTone.ORANGE -> if (light) ProductOrangeSoft else Color(0xFF4A2608)
        ProductBadgeTone.RED -> if (light) ProductRedSoft else Color(0xFF4D1024)''')
t = t.replace('''ProductBadgeTone.GREEN -> if (light) ProductGreen else Color(0xFF97C459)
        ProductBadgeTone.ORANGE -> if (light) ProductOrange else Color(0xFFFAC775)
        ProductBadgeTone.RED -> if (light) ProductRed else Color(0xFFF09595)''', '''ProductBadgeTone.GREEN -> if (light) ProductGreen else Color(0xFF6EE7B7)
        ProductBadgeTone.ORANGE -> if (light) ProductOrange else Color(0xFFFCD34D)
        ProductBadgeTone.RED -> if (light) ProductRed else Color(0xFFFDA4AF)''')
p.write_text(t)

# ---------- ProductHomeScreen.kt ----------
p = ROOT / 'app/src/main/java/com/okulyonetim/optikokuyucu/ui/ProductHomeScreen.kt'
t = p.read_text()
if 'import androidx.compose.ui.graphics.Color' not in t:
    t = t.replace('import androidx.compose.ui.Alignment\n', 'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.graphics.Color\n')

start = t.index('    LazyColumn(\n')
end = t.index('\n@Composable\nprivate fun HomeThemeToggle')
new_body = r'''    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        item {
            ProductHeroCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.17f),
                            contentColor = Color.White,
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Text(
                                "O",
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Optik Okuyucu", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(
                                profile?.displayName?.takeIf(String::isNotBlank) ?: "Sınav yönetim merkezi",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.76f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeHeroBadge(if (profile?.admin == true) "YÖNETİCİ" else "OMR")
                        if (themeController != null) HomeThemeToggle(themeController)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    HomeHeroMetric(Modifier.weight(1f), "Sınav", examItems.size.toString())
                    HomeHeroMetric(Modifier.weight(1f), "Kağıt", paperCount.toString())
                    HomeHeroMetric(Modifier.weight(1f), "Öğrenci", studentCount.toString())
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    HomeHeroBadge("$completed tamamlandı", Modifier.weight(1f))
                    HomeHeroBadge("$waiting bekliyor", Modifier.weight(1f))
                }
            }
        }

        item { Text("Hızlı İşlemler", fontSize = 15.sp, fontWeight = FontWeight.Bold) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeQuickActionCard(
                        modifier = Modifier.weight(1f), symbol = "+", title = "Yeni Sınav",
                        description = "Sınav oluştur", onClick = onNewExam
                    )
                    HomeQuickActionCard(
                        modifier = Modifier.weight(1f), symbol = "OCR", title = "Belge / OCR",
                        description = "Belge ve görsel oku", onClick = onOpenOcr
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeQuickActionCard(
                        modifier = Modifier.weight(1f), symbol = "▤", title = "Rapor Oluştur",
                        description = "Sınav ve öğrenci raporu", onClick = onOpenReportBuilder
                    )
                    HomeQuickActionCard(
                        modifier = Modifier.weight(1f), symbol = "✓", title = "Mini Anahtar",
                        description = "A4 çoklu çıktı", onClick = onOpenMiniAnswerKey
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Son Sınavlar", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (examItems.isEmpty()) "Henüz kayıt yok" else "En son ${minOf(5, examItems.size)} sınav",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onOpenExams) { Text("Tümünü Gör", fontSize = 10.sp) }
            }
        }

        if (examItems.isEmpty()) {
            item {
                ProductCompactCard(modifier = Modifier.fillMaxWidth(), accentColor = MaterialTheme.colorScheme.primary) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("İlk sınavınızı oluşturun", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Sınav, form ve puanlama ayarlarını tek ekrandan belirleyin.", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = onNewExam, shape = RoundedCornerShape(11.dp)) {
                            Text("Başla", fontSize = 10.sp)
                        }
                    }
                }
            }
        } else {
            items(examItems.take(5), key = { it.summary.id }) { item ->
                HomeExamRow(
                    item = item,
                    onClick = {
                        val local = item.localExam
                        if (local != null) onOpenExam(local.id) else onOpenExams()
                    }
                )
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}
'''
t = t[:start] + new_body + t[end:]

helper_start = t.index('@Composable\nprivate fun HomeThemeToggle')
helper_end = t.index('\nprivate fun formatHomeDay')
helpers = r'''@Composable
private fun HomeThemeToggle(controller: ProductThemeController) {
    Surface(
        modifier = Modifier.size(42.dp).clickable { controller.toggleLightDark() },
        color = Color.White.copy(alpha = 0.16f),
        contentColor = Color.White,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (controller.isDark) "☀" else "☾", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun HomeHeroMetric(modifier: Modifier, label: String, value: String) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.14f),
        contentColor = Color.White,
        shape = RoundedCornerShape(13.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 9.sp, color = Color.White.copy(alpha = 0.76f))
        }
    }
}

@Composable
private fun HomeHeroBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.13f),
        contentColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeQuickActionCard(
    modifier: Modifier,
    symbol: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    ProductCompactCard(
        modifier = modifier,
        onClick = onClick,
        accentColor = productAccentColor(title)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            ProductInitialBadge(symbol)
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(description, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HomeExamRow(item: SchoolExamListItem, onClick: () -> Unit) {
    val summary = item.summary
    val exam = item.localExam
    ProductCompactCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        accentColor = productAccentColor(summary.name)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.Transparent,
                contentColor = Color.White,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(productAccentBrush(summary.name), RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(formatHomeDay(summary.examDateEpochDay), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(formatHomeMonth(summary.examDateEpochDay), fontSize = 8.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.84f))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(summary.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(exam?.papers?.size ?: 0)
                        append(" kağıt")
                        if (summary.ownerName.isNotBlank()) append(" · ${summary.ownerName}")
                        if (exam == null) append(" · Bulut")
                    },
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ProductStatusBadge(
                text = when {
                    exam == null -> "BULUT"
                    exam.status == ExamStatus.READ -> "OKUNDU"
                    else -> "BEKLİYOR"
                },
                tone = when {
                    exam == null -> ProductBadgeTone.NEUTRAL
                    exam.status == ExamStatus.READ -> ProductBadgeTone.GREEN
                    else -> ProductBadgeTone.ORANGE
                }
            )
        }
    }
}
'''
t = t[:helper_start] + helpers + t[helper_end:]
# HomeExamRow helper uses background extension.
if 'import androidx.compose.foundation.background' not in t:
    t = t.replace('import androidx.compose.foundation.clickable\n', 'import androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable\n')
p.write_text(t)

# ---------- SettingsSubjectsCard.kt ----------
p = ROOT / 'app/src/main/java/com/okulyonetim/optikokuyucu/ui/SettingsSubjectsCard.kt'
t = p.read_text()
if 'import androidx.compose.ui.graphics.Color' not in t:
    t = t.replace('import androidx.compose.ui.Alignment\n', 'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.graphics.Color\n')
t = t.replace('''        ModalBottomSheet(
            onDismissRequest = {''', '''        ModalBottomSheet(
            containerColor = MaterialTheme.colorScheme.background,
            onDismissRequest = {''')
old_intro = '''                Text("Dersleri Düzenle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (teacherAccount) {
                        "Tek ders sınavlarında bu cihazdaki liste kullanılır. Öğretmen hesaplarında Okul Yönetim ders eşitlemesi yönetici yetkisindedir."
                    } else {
                        "Tek ders sınavlarında bu liste kullanılır. Okul Yönetim'den aktarınca buluttaki ders listesi cihazdaki listeyle değiştirilir."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )'''
new_intro = '''                ProductHeroCard {
                    Text("Dersleri Düzenle", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (teacherAccount) {
                            "Tek ders sınavlarında cihazdaki liste kullanılır. Ders eşitlemesi yönetici yetkisindedir."
                        } else {
                            "Tek ders sınavlarında bu liste kullanılır. Okul Yönetim'den aktarınca liste güvenli biçimde güncellenir."
                        },
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.80f)
                    )
                }'''
if old_intro not in t:
    raise SystemExit('SettingsSubjects intro marker not found')
t = t.replace(old_intro, new_intro)
t = t.replace('ProductCompactCard(modifier = Modifier.fillMaxWidth()) {',
              'ProductCompactCard(modifier = Modifier.fillMaxWidth(), accentColor = productAccentColor(subject)) {')
p.write_text(t)

# ---------- SchoolAccountSettingsCard.kt ----------
p = ROOT / 'app/src/main/java/com/okulyonetim/optikokuyucu/ui/SchoolAccountSettingsCard.kt'
t = p.read_text()
t = t.replace('''    ProductSettingsSection(
        title = "Okul Yönetim Hesabı",
        description = "Kurum bağlantısı ve eşitleme işlemleri"
    ) {''', '''    ProductSettingsSection(
        title = "Okul Yönetim Hesabı",
        description = "Kurum bağlantısı ve eşitleme işlemleri",
        accentColor = MaterialTheme.colorScheme.primary
    ) {''')
p.write_text(t)

# ---------- OmrRootScreen.kt / Settings ----------
p = ROOT / 'app/src/main/java/com/okulyonetim/optikokuyucu/ui/OmrRootScreen.kt'
t = p.read_text()
t = t.replace('''                ProductSettingsSection(
                    title = "Kurum Bilgileri",
                    description = "Okul adı bağlı Okul Yönetim hesabından veya güvenilir kurum kaynağından alınır."
                ) {''', '''                ProductSettingsSection(
                    title = "Kurum Bilgileri",
                    description = "Okul adı bağlı Okul Yönetim hesabından veya güvenilir kurum kaynağından alınır.",
                    accentColor = MaterialTheme.colorScheme.tertiary
                ) {''')
p.write_text(t)

# ---------- StudentRosterScreen.kt / dialogs ----------
p = ROOT / 'app/src/main/java/com/okulyonetim/optikokuyucu/ui/StudentRosterScreen.kt'
t = p.read_text()
if 'import androidx.compose.foundation.shape.RoundedCornerShape' not in t:
    t = t.replace('import androidx.compose.foundation.rememberScrollState\n', 'import androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.shape.RoundedCornerShape\n')
# Apply a consistent, cleaner themed surface to all student detail/management dialogs.
t = t.replace('''AlertDialog(
            onDismissRequest =''', '''AlertDialog(
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            onDismissRequest =''')
t = t.replace('''ModalBottomSheet(
            onDismissRequest =''', '''ModalBottomSheet(
            containerColor = MaterialTheme.colorScheme.background,
            onDismissRequest =''')
p.write_text(t)

# ---------- Version bump ----------
p = ROOT / 'app/build.gradle.kts'
t = p.read_text()
t = t.replace('versionCode = 121', 'versionCode = 122')
t = t.replace('versionName = "0.19.65"', 'versionName = "0.19.66"')
p.write_text(t)

print('UI refresh patch applied')
