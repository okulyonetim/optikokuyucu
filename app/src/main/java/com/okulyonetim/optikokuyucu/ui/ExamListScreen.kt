package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class ExamListFilter { ALL, READ, WAITING }
private enum class ExamListSubScreen { LIST, EDIT, REPORT, PARENT_SHARE }

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
    var subScreen by remember { mutableStateOf(ExamListSubScreen.LIST) }
    var actionExamId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<SchoolExamListItem?>(null) }

    fun currentItems(): List<SchoolExamListItem> = if (profile == null) {
        localExams.map { exam -> SchoolExamListItem(SchoolContentAccess.run { exam.toSummary() }, exam) }
    } else {
        SchoolContentAccess.mergeExamItems(localExams, cloudCatalog, profile)
    }

    fun refreshLocal() {
        localExams = repository.list()
        cloudCatalog = catalogStore.list()
    }

    fun returnToList() {
        actionExamId = null
        subScreen = ExamListSubScreen.LIST
        refreshLocal()
    }

    if (subScreen != ExamListSubScreen.LIST) {
        val id = actionExamId
        if (id == null) {
            subScreen = ExamListSubScreen.LIST
        } else {
            when (subScreen) {
                ExamListSubScreen.EDIT -> NewExamScreen(
                    examId = id,
                    onBack = ::returnToList,
                    onSaved = {
                        manager.invalidateCloudSync()
                        returnToList()
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { manager.syncExamsAndResults(force = true) } }
                        }
                    }
                )
                ExamListSubScreen.REPORT -> ReportBuilderScreen(
                    initialExamId = id,
                    onBack = ::returnToList,
                    onExamChanged = { actionExamId = it },
                    onShareParents = { examId ->
                        actionExamId = examId
                        subScreen = ExamListSubScreen.PARENT_SHARE
                    }
                )
                ExamListSubScreen.PARENT_SHARE -> ParentResultShareScreen(
                    examId = id,
                    onBack = { subScreen = ExamListSubScreen.REPORT }
                )
                ExamListSubScreen.LIST -> Unit
            }
            return
        }
    }

    fun togglePublic(item: SchoolExamListItem) {
        val signed = profile ?: return
        if (!signed.admin) {
            feedback.warning("Sınavı herkese açma / özel yapma yetkisi yalnız yöneticidedir.")
            return
        }
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
                    }.onSuccess { refreshLocal() }
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

    fun deleteExam(item: SchoolExamListItem) {
        val local = item.localExam
        val canModify = when {
            profile == null -> local != null
            local != null -> SchoolContentAccess.canModifyExam(local, profile)
            else -> profile.admin || item.summary.ownerUid == profile.uid
        }
        if (!canModify) {
            feedback.warning("Bu sınavı silme yetkiniz yok.")
            return
        }
        runCatching {
            local?.let { repository.delete(it.id) }
            manager.invalidateCloudSync()
        }.onFailure {
            feedback.error(it.message ?: "Sınav silinemedi.")
            return
        }
        localExams = repository.list()
        cloudCatalog = cloudCatalog.filterNot { it.id == item.summary.id }
        feedback.success("Sınav silindi.")
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { manager.deleteExamCloudCopy(item.summary.id) }
            }.onSuccess {
                cloudCatalog = catalogStore.list()
            }.onFailure {
                feedback.warning("Sınav cihazdan silindi; bulut silme işlemi internet geldiğinde yeniden denenebilir.")
            }
        }
    }

    pendingDelete?.let { item ->
        val paperCount = item.localExam?.papers?.size ?: 0
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Sınav silinsin mi?") },
            text = {
                Text(
                    buildString {
                        append("${item.summary.name} kalıcı olarak silinecek.")
                        if (paperCount > 0) append(" Bu sınava bağlı $paperCount öğrenci sonucu da sınav listesinden kaldırılacak.")
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    deleteExam(item)
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Vazgeç") }
            }
        )
    }

    val allItems = currentItems()
    val normalizedQuery = query.trim().lowercase()
    val filtered = allItems.filter { item ->
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ProductTopBar(title = "Sınavlar", showAutomaticBack = false)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("${allItems.size} sınav", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            profile?.admin == true -> "Tüm kullanıcı sınavları"
                            profile != null -> "Kendi sınavlarınız ve herkese açık sınavlar"
                            else -> "Sınavlarınızı yönetin"
                        },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(onClick = onNewExam, shape = RoundedCornerShape(13.dp)) {
                    Text("＋ Yeni Sınav", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Sınav, sahibi veya klasör ara", fontSize = 12.sp) },
                leadingIcon = { Text("⌕", fontSize = 20.sp) },
                shape = RoundedCornerShape(16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProductFilterPill("Tümü", allItems.size, filter == ExamListFilter.ALL) { filter = ExamListFilter.ALL }
                ProductFilterPill(
                    "Okundu",
                    allItems.count { it.localExam?.status == ExamStatus.READ },
                    filter == ExamListFilter.READ
                ) { filter = ExamListFilter.READ }
                ProductFilterPill(
                    "Bekliyor",
                    allItems.count { it.localExam == null || it.localExam.status == ExamStatus.WAITING },
                    filter == ExamListFilter.WAITING
                ) { filter = ExamListFilter.WAITING }
            }

            if (filtered.isEmpty()) {
                ProductEmptyState(
                    title = if (allItems.isEmpty()) "Henüz görünür sınav yok" else "Filtreye uygun sınav bulunamadı",
                    body = if (allItems.isEmpty()) "Yeni Sınav ile ilk sınavınızı oluşturabilirsiniz."
                    else "Arama metnini veya durum filtresini değiştirin."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.summary.id }) { item ->
                        val local = item.localExam
                        val canModify = when {
                            profile == null -> local != null
                            local != null -> SchoolContentAccess.canModifyExam(local, profile)
                            else -> profile.admin || item.summary.ownerUid == profile.uid
                        }
                        ExamListCard(
                            item = item,
                            canEdit = canModify && local != null,
                            canDelete = canModify,
                            canReport = local != null,
                            canInformParents = canModify && local != null,
                            canPublish = profile?.admin == true,
                            onClick = {
                                if (local != null) onOpenExam(local.id)
                                else feedback.info("Bu sınav başka bir cihazdan geldi; bulut özeti salt okunur.")
                            },
                            onEdit = {
                                actionExamId = item.summary.id
                                subScreen = ExamListSubScreen.EDIT
                            },
                            onDelete = { pendingDelete = item },
                            onReport = {
                                actionExamId = item.summary.id
                                subScreen = ExamListSubScreen.REPORT
                            },
                            onTogglePublic = { togglePublic(item) },
                            onInformParents = {
                                actionExamId = item.summary.id
                                subScreen = ExamListSubScreen.PARENT_SHARE
                            }
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ExamListCard(
    item: SchoolExamListItem,
    canEdit: Boolean,
    canDelete: Boolean,
    canReport: Boolean,
    canInformParents: Boolean,
    canPublish: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onTogglePublic: () -> Unit,
    onInformParents: () -> Unit
) {
    val exam = item.localExam
    val summary = item.summary
    val read = exam?.status == ExamStatus.READ
    var menuOpen by remember(item.summary.id) { mutableStateOf(false) }

    val examAccent = productAccentColor(summary.name.ifBlank { summary.id })
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, examAccent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(productAccentBrush(summary.name.ifBlank { summary.id })),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▤", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(summary.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append(formatExamDate(summary.examDateEpochDay))
                            if (summary.schoolName.isNotBlank()) append(" · ${summary.schoolName}")
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
                        read -> "OKUNDU"
                        else -> "BEKLİYOR"
                    },
                    tone = when {
                        exam == null -> ProductBadgeTone.NEUTRAL
                        read -> ProductBadgeTone.GREEN
                        else -> ProductBadgeTone.ORANGE
                    }
                )
                Box {
                    Surface(
                        modifier = Modifier.size(36.dp).clickable { menuOpen = true },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(11.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("⋮", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        DropdownMenuItem(
                            text = { Text("Düzenle") },
                            enabled = canEdit,
                            onClick = { menuOpen = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Rapor") },
                            enabled = canReport,
                            onClick = { menuOpen = false; onReport() }
                        )
                        DropdownMenuItem(
                            text = { Text("Veli Bilgilendirme") },
                            enabled = canInformParents,
                            onClick = { menuOpen = false; onInformParents() }
                        )
                        DropdownMenuItem(
                            text = { Text(if (summary.isPublic) "Özel Yap" else "Herkese Açık Yap") },
                            enabled = canPublish,
                            onClick = { menuOpen = false; onTogglePublic() }
                        )
                        DropdownMenuItem(
                            text = { Text("Sil", color = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
                            enabled = canDelete,
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
                        } else {
                            if (isNotBlank()) append(" · ")
                            append("${exam.papers.size} kağıt")
                        }
                    }.ifBlank { "Kişisel sınav" },
                    modifier = Modifier.weight(1f),
                    fontSize = 10.sp,
                    color = if (summary.isPublic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("Seçenekler ⋮", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatExamDate(epochDay: Long): String = runCatching {
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}.getOrDefault("-")
