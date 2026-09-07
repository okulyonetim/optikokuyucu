package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.exam.Exam
import com.okulyonetim.optikokuyucu.exam.ExamStatus
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.school.SchoolContentAccess
import com.okulyonetim.optikokuyucu.school.SchoolExamCatalogStore
import com.okulyonetim.optikokuyucu.school.SchoolExamListItem
import com.okulyonetim.optikokuyucu.school.SchoolPortalManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class ExamListFilter { ALL, READ, WAITING }

@Suppress("UNUSED_PARAMETER")
@Composable
fun ExamListScreen(
    onNewExam: () -> Unit,
    onOpenExam: (String) -> Unit,
    onOpenTools: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(context) { FileExamRepository(appContext) }
    val catalogStore = remember(context) { SchoolExamCatalogStore(appContext) }
    val manager = remember(context) { SchoolPortalManager.get(appContext) }
    val account = LocalSchoolAccount.current
    val profile = account?.profile
    val feedback = LocalAppFeedback.current
    val scope = rememberCoroutineScope()
    var localExams by remember { mutableStateOf(repository.list()) }
    var cloudCatalog by remember { mutableStateOf(catalogStore.list()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ExamListFilter.ALL) }

    fun currentItems(): List<SchoolExamListItem> = if (profile == null) {
        localExams.map { exam -> SchoolExamListItem(SchoolContentAccess.run { exam.toSummary() }, exam) }
    } else {
        SchoolContentAccess.mergeExamItems(localExams, cloudCatalog, profile)
    }

    val items = currentItems()
    val normalizedQuery = query.trim().lowercase()
    val filtered = items.filter { item ->
        val summary = item.summary
        val exam = item.localExam
        val matchesQuery = normalizedQuery.isBlank() ||
            summary.name.lowercase().contains(normalizedQuery) ||
            summary.schoolName.lowercase().contains(normalizedQuery) ||
            exam?.folderName?.lowercase()?.contains(normalizedQuery) == true ||
            summary.ownerName.lowercase().contains(normalizedQuery)
        val matchesFilter = when (filter) {
            ExamListFilter.ALL -> true
            ExamListFilter.READ -> exam?.status == ExamStatus.READ
            ExamListFilter.WAITING -> exam == null || exam.status == ExamStatus.WAITING
        }
        matchesQuery && matchesFilter
    }

    fun refreshLocal() {
        localExams = repository.list()
        cloudCatalog = catalogStore.list()
    }

    fun togglePublic(item: SchoolExamListItem) {
        val signed = profile ?: return
        if (!signed.admin) return
        val next = !item.summary.isPublic
        val local = item.localExam
        if (local != null) {
            runCatching {
                repository.save(local.copy(isPublic = next))
                manager.invalidateCloudSync()
            }.onSuccess {
                refreshLocal()
                feedback.success(if (next) "Sınav herkese açıldı." else "Sınav özel yapıldı.")
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            manager.syncExamsAndResults(force = true)
                            manager.refreshExamCatalog()
                        }
                    }
                    refreshLocal()
                }
            }.onFailure { feedback.error(it.message ?: "Sınav paylaşımı değiştirilemedi.") }
        } else {
            scope.launch {
                val outcome = runCatching {
                    withContext(Dispatchers.IO) { manager.setExamPublic(item.summary.id, next) }
                }
                outcome.onSuccess {
                    cloudCatalog = catalogStore.list()
                    feedback.success(if (next) "Bulut sınavı herkese açıldı." else "Bulut sınavı özel yapıldı.")
                }.onFailure { feedback.error(it.message ?: "Sınav paylaşımı değiştirilemedi.") }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ProductTopBar(
                title = "Sınavlar",
                showAutomaticBack = false
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewExam) {
                Text("＋  Yeni Sınav", fontSize = 17.sp)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(2.dp))
            if (profile != null) {
                Text(
                    if (profile.admin) "Admin görünümü · tüm kullanıcı sınavları" else "${profile.displayName} · kendi sınavlarınız ve herkese açık sınavlar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Sınav, sahibi veya klasör ara") },
                leadingIcon = { Text("⌕", fontSize = 25.sp) },
                shape = RoundedCornerShape(30.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProductFilterPill("Tümü", items.size, filter == ExamListFilter.ALL) { filter = ExamListFilter.ALL }
                ProductFilterPill("Okundu", items.count { it.localExam?.status == ExamStatus.READ }, filter == ExamListFilter.READ) { filter = ExamListFilter.READ }
                ProductFilterPill("Bekliyor", items.count { it.localExam == null || it.localExam.status == ExamStatus.WAITING }, filter == ExamListFilter.WAITING) { filter = ExamListFilter.WAITING }
            }

            if (filtered.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (items.isEmpty()) "Henüz görünür sınav yok" else "Filtreye uygun sınav bulunamadı", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (items.isEmpty()) "Yeni Sınav ile kendi sınavınızı oluşturabilirsiniz."
                            else "Arama metnini veya durum filtresini değiştirin.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filtered, key = { it.summary.id }) { item ->
                        ExamListCard(
                            item = item,
                            admin = profile?.admin == true,
                            onClick = {
                                val local = item.localExam
                                if (local != null) onOpenExam(local.id)
                                else feedback.info("Bu sınav başka bir cihazdan geldi; bulut özeti salt okunur.")
                            },
                            onTogglePublic = if (profile?.admin == true) ({ togglePublic(item) }) else null
                        )
                    }
                    item { Spacer(Modifier.height(92.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ExamListCard(
    item: SchoolExamListItem,
    admin: Boolean,
    onClick: () -> Unit,
    onTogglePublic: (() -> Unit)?
) {
    val exam = item.localExam
    val summary = item.summary
    val read = exam?.status == ExamStatus.READ
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    color = if (read) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("▤", fontSize = 25.sp, color = MaterialTheme.colorScheme.primary) }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(summary.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatExamDate(summary.examDateEpochDay), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        buildString {
                            if (summary.ownerName.isNotBlank()) append(summary.ownerName)
                            if (summary.isPublic) {
                                if (isNotBlank()) append(" · ")
                                append("Herkese açık")
                            }
                            if (exam == null) {
                                if (isNotBlank()) append(" · ")
                                append("Bulut")
                            }
                        }.ifBlank { "Kişisel sınav" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (summary.isPublic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ProductStatusBadge(
                    text = when {
                        exam == null -> "BULUT"
                        read -> "OKUNDU (${exam.papers.size})"
                        else -> "BEKLİYOR"
                    },
                    tone = when {
                        exam == null -> ProductBadgeTone.NEUTRAL
                        read -> ProductBadgeTone.GREEN
                        else -> ProductBadgeTone.ORANGE
                    }
                )
            }
            if (admin && onTogglePublic != null) {
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onTogglePublic, shape = RoundedCornerShape(12.dp)) {
                    Text(if (summary.isPublic) "Özel Yap" else "Herkese Aç", fontSize = 11.sp)
                }
            }
        }
    }
}

private fun formatExamDate(epochDay: Long): String = runCatching {
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}.getOrDefault("-")
