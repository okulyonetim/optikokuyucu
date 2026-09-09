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
        item { Spacer(Modifier.height(10.dp)) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Optik Okuyucu", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            profile?.admin == true -> "Tüm kullanıcı sınavları"
                            profile != null -> "${profile.displayName} · sınav yönetim merkezi"
                            else -> "Sınav yönetim merkezi"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        text = if (profile?.admin == true) "ADMIN" else "OMR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeMetric(Modifier.weight(1f), "Sınav", examItems.size.toString())
                    HomeMetric(Modifier.weight(1f), "Kağıt", paperCount.toString())
                    HomeMetric(Modifier.weight(1f), "Öğrenci", studentCount.toString())
                    HomeMetric(Modifier.weight(1f), "Bekleyen", waiting.toString())
                }
            }
        }

        item { Text("Hızlı İşlemler", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HomePrimaryAction(
                    symbol = "+",
                    title = "Yeni Sınav",
                    description = "Sınav oluşturma ekranını aç",
                    onClick = onNewExam
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeAction(
                        modifier = Modifier.weight(1f),
                        symbol = "▤",
                        title = "Rapor Oluştur",
                        description = "Sınav veya öğrenci raporu",
                        onClick = onOpenReportBuilder
                    )
                    HomeAction(
                        modifier = Modifier.weight(1f),
                        symbol = "✓",
                        title = "Mini Cevap Anahtarı",
                        description = "A4 çoklu dağıtım çıktısı",
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
                Column {
                    Text("Son Sınavlar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "$completed tamamlandı · $waiting bekliyor",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onOpenExams) { Text("Tümünü Gör", fontSize = 11.sp) }
            }
        }

        if (examItems.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Henüz sınav yok", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("İlk sınavınızı oluşturarak başlayın.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = onNewExam, shape = RoundedCornerShape(11.dp)) {
                            Text("Yeni Sınav", fontSize = 10.sp)
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
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun HomeMetric(modifier: Modifier, label: String, value: String) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun HomePrimaryAction(symbol: String, title: String, description: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(72.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(symbol, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f))
            }
            Text("›", fontSize = 22.sp)
        }
    }
}

@Composable
private fun HomeAction(
    modifier: Modifier,
    symbol: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(94.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(symbol, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(description, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HomeExamRow(item: SchoolExamListItem, onClick: () -> Unit) {
    val summary = item.summary
    val exam = item.localExam
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(summary.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(formatHomeDate(summary.examDateEpochDay))
                        append(" · ")
                        append(exam?.papers?.size ?: 0)
                        append(" kağıt")
                        if (summary.ownerName.isNotBlank()) append(" · ${summary.ownerName}")
                        if (exam == null) append(" · Bulut")
                    },
                    fontSize = 10.sp,
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

private fun formatHomeDate(epochDay: Long): String = runCatching {
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}.getOrDefault("-")
