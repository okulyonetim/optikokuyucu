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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.okulyonetim.optikokuyucu.exam.Exam
import com.okulyonetim.optikokuyucu.exam.ExamStatus
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class ExamListFilter { ALL, READ, WAITING }

@Composable
fun ExamListScreen(
    onNewExam: () -> Unit,
    onOpenExam: (String) -> Unit,
    onOpenTools: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { FileExamRepository(context.applicationContext) }
    var exams by remember { mutableStateOf(repository.list()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ExamListFilter.ALL) }

    val normalizedQuery = query.trim().lowercase()
    val filtered = exams.filter { exam ->
        val matchesQuery = normalizedQuery.isBlank() ||
            exam.name.lowercase().contains(normalizedQuery) ||
            exam.schoolName.lowercase().contains(normalizedQuery) ||
            exam.folderName.lowercase().contains(normalizedQuery)
        val matchesFilter = when (filter) {
            ExamListFilter.ALL -> true
            ExamListFilter.READ -> exam.status == ExamStatus.READ
            ExamListFilter.WAITING -> exam.status == ExamStatus.WAITING
        }
        matchesQuery && matchesFilter
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ProductTopBar(
                title = "Sınavlar",
                leadingText = "☰",
                onLeadingClick = onOpenTools,
                onActionClick = {
                    exams = repository.list()
                }
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
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Sınav veya klasör ara") },
                leadingIcon = { Text("⌕", fontSize = 25.sp) },
                shape = RoundedCornerShape(30.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProductFilterPill(
                    label = "Tümü",
                    count = exams.size,
                    selected = filter == ExamListFilter.ALL,
                    onClick = { filter = ExamListFilter.ALL }
                )
                ProductFilterPill(
                    label = "Okundu",
                    count = exams.count { it.status == ExamStatus.READ },
                    selected = filter == ExamListFilter.READ,
                    onClick = { filter = ExamListFilter.READ }
                )
                ProductFilterPill(
                    label = "Bekliyor",
                    count = exams.count { it.status == ExamStatus.WAITING },
                    selected = filter == ExamListFilter.WAITING,
                    onClick = { filter = ExamListFilter.WAITING }
                )
            }

            if (filtered.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (exams.isEmpty()) "Henüz sınav yok" else "Filtreye uygun sınav bulunamadı",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (exams.isEmpty()) "Sağ alttaki Yeni Sınav düğmesiyle ilk sınavınızı oluşturabilirsiniz."
                            else "Arama metnini veya durum filtresini değiştirebilirsiniz.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { exam ->
                        ExamListCard(exam = exam, onClick = { onOpenExam(exam.id) })
                    }
                    item { Spacer(Modifier.height(92.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ExamListCard(exam: Exam, onClick: () -> Unit) {
    val read = exam.status == ExamStatus.READ
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    color = if (read) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("▤", fontSize = 25.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        exam.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formatExamDate(exam.examDateEpochDay),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (exam.folderName.isNotBlank()) {
                        Text(
                            exam.folderName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("⋮", color = MaterialTheme.colorScheme.outline, fontSize = 24.sp)
                ProductStatusBadge(
                    text = if (read) "OKUNDU (${exam.papers.size})" else "BEKLİYOR",
                    tone = if (read) ProductBadgeTone.GREEN else ProductBadgeTone.ORANGE
                )
            }
        }
    }
}

private fun formatExamDate(epochDay: Long): String = runCatching {
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}.getOrDefault("-")
