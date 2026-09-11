package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.exam.ExamStatus
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.school.SchoolContentAccess
import com.okulyonetim.optikokuyucu.school.SchoolExamCatalogStore
import com.okulyonetim.optikokuyucu.school.SchoolExamListItem
import com.okulyonetim.optikokuyucu.school.SchoolPortalManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ProductHomeScreen(
    onNewExam: () -> Unit,
    onOpenReportBuilder: () -> Unit,
    onOpenMiniAnswerKey: () -> Unit,
    onOpenOcr: () -> Unit,
    onOpenExams: () -> Unit,
    onOpenExam: (String) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(context) { FileExamRepository(appContext) }
    val catalogStore = remember(context) { SchoolExamCatalogStore(appContext) }
    val manager = remember(context) { SchoolPortalManager.get(appContext) }
    val profile = LocalSchoolAccount.current?.profile
    val themeController = LocalProductThemeController.current
    var localExams by remember(profile?.uid) { mutableStateOf(repository.list()) }
    var cloudCatalog by remember(profile?.uid) { mutableStateOf(catalogStore.list()) }

    LaunchedEffect(profile?.uid) {
        if (profile != null) {
            runCatching { withContext(Dispatchers.IO) { manager.refreshExamCatalog() } }
        }
        localExams = repository.list()
        cloudCatalog = catalogStore.list()
    }

    val examItems = if (profile == null) {
        localExams.map { exam -> SchoolExamListItem(SchoolContentAccess.run { exam.toSummary() }, exam) }
    } else {
        SchoolContentAccess.mergeExamItems(localExams, cloudCatalog, profile)
    }
    val completed = examItems.count { it.localExam?.status == ExamStatus.READ }
    val waiting = examItems.count { it.localExam == null || it.localExam.status == ExamStatus.WAITING }
    val paperCount = examItems.sumOf { it.localExam?.papers?.size ?: 0 }
    val studentCount = examItems
        .flatMap { it.localExam?.papers.orEmpty() }
        .mapNotNull { link -> link.studentNumber.trim().takeIf(String::isNotBlank) }
        .distinct()
        .size

    LazyColumn(
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

@Composable
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

private fun formatHomeDay(epochDay: Long): String = runCatching {
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd"))
}.getOrDefault("--")

private fun formatHomeMonth(epochDay: Long): String = runCatching {
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("MMM", java.util.Locale.forLanguageTag("tr-TR"))).uppercase(java.util.Locale.forLanguageTag("tr-TR"))
}.getOrDefault("---")