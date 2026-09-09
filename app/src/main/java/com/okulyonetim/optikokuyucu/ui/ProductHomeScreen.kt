package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
    onOpenExams: () -> Unit,
    onOpenExam: (String) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(context) { FileExamRepository(appContext) }
    val catalogStore = remember(context) { SchoolExamCatalogStore(appContext) }
    val manager = remember(context) { SchoolPortalManager.get(appContext) }
    val profile = LocalSchoolAccount.current?.profile
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
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
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
                        Text("Optik Okuyucu", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            profile?.displayName?.takeIf(String::isNotBlank) ?: "Sınav yönetim merkezi",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                ProductStatusBadge(
                    if (profile?.admin == true) "YÖNETİCİ" else "OMR",
                    if (profile?.admin == true) ProductBadgeTone.GREEN else ProductBadgeTone.NEUTRAL
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Genel Durum", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(examItems.size.toString(), fontSize = 31.sp, fontWeight = FontWeight.Bold)
                                Text("sınav", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 5.dp))
                            }
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ProductStatusBadge("$completed tamamlandı", ProductBadgeTone.GREEN)
                            ProductStatusBadge("$waiting bekliyor", if (waiting > 0) ProductBadgeTone.ORANGE else ProductBadgeTone.NEUTRAL)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HomeInfoPill(Modifier.weight(1f), "Kağıt", paperCount.toString())
                        HomeInfoPill(Modifier.weight(1f), "Öğrenci", studentCount.toString())
                    }
                }
            }
        }

        item { Text("Hızlı İşlemler", fontSize = 15.sp, fontWeight = FontWeight.Bold) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HomeActionRow(
                        symbol = "+",
                        title = "Yeni Sınav",
                        description = "Yeni sınav oluştur ve optik formu seç",
                        onClick = onNewExam
                    )
                    HomeSeparator()
                    HomeActionRow(
                        symbol = "▤",
                        title = "Rapor Oluştur",
                        description = "Sınav veya öğrenci raporu hazırla",
                        onClick = onOpenReportBuilder
                    )
                    HomeSeparator()
                    HomeActionRow(
                        symbol = "✓",
                        title = "Mini Cevap Anahtarı",
                        description = "A4 çoklu dağıtım çıktısı oluştur",
                        onClick = onOpenMiniAnswerKey
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
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
private fun HomeInfoPill(modifier: Modifier, label: String, value: String) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HomeActionRow(symbol: String, title: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(11.dp)
        ) {
            Text(
                symbol,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(description, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeSeparator() {
    Surface(
        modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 13.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    ) {}
}

@Composable
private fun HomeExamRow(item: SchoolExamListItem, onClick: () -> Unit) {
    val summary = item.summary
    val exam = item.localExam
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(formatHomeDay(summary.examDateEpochDay), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(formatHomeMonth(summary.examDateEpochDay), fontSize = 8.sp, fontWeight = FontWeight.Medium)
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
