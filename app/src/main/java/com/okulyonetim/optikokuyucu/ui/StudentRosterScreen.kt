package com.okulyonetim.optikokuyucu.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.content.ContextCompat
import com.okulyonetim.optikokuyucu.exam.Exam
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.student.EschoolPdfImportPreview
import com.okulyonetim.optikokuyucu.student.EschoolPdfImporter
import com.okulyonetim.optikokuyucu.student.FileStudentRosterRepository
import com.okulyonetim.optikokuyucu.student.StudentGender
import com.okulyonetim.optikokuyucu.student.StudentNumber
import com.okulyonetim.optikokuyucu.student.StudentRosterEntry
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private data class StudentRosterOverview(
    val key: String,
    val roster: StudentRosterEntry?,
    val name: String,
    val number: String,
    val className: String,
    val guardianName: String,
    val guardianPhone: String,
    val scanCount: Int,
    val latestExamId: String?,
    val latestScanRecordId: String?
)

private fun buildStudentRosterOverviews(
    roster: List<StudentRosterEntry>,
    exams: List<Exam>
): List<StudentRosterOverview> {
    val linkedPapers = exams.flatMap { exam -> exam.papers.map { exam.id to it } }
    val papersByNumber = linkedPapers
        .filter { StudentNumber.normalize(it.second.studentNumber).isNotBlank() }
        .groupBy { StudentNumber.normalize(it.second.studentNumber) }
    val consumedScanIds = mutableSetOf<String>()
    val overviews = mutableListOf<StudentRosterOverview>()

    roster.forEach { entry ->
        val matches = papersByNumber[entry.studentNumber].orEmpty()
        matches.forEach { consumedScanIds += it.second.scanRecordId }
        val latest = matches.maxByOrNull { it.second.linkedAtEpochMs }
        overviews += StudentRosterOverview(
            key = "roster:${entry.studentNumber}",
            roster = entry,
            name = entry.fullName,
            number = entry.studentNumber,
            className = entry.className,
            guardianName = entry.guardianName,
            guardianPhone = entry.guardianPhone,
            scanCount = matches.size,
            latestExamId = latest?.first,
            latestScanRecordId = latest?.second?.scanRecordId
        )
    }

    linkedPapers
        .filterNot { it.second.scanRecordId in consumedScanIds }
        .groupBy { (_, link) ->
            val number = StudentNumber.normalize(link.studentNumber)
            when {
                number.isNotBlank() -> "number:$number"
                link.studentName.isNotBlank() -> "name:${link.studentName.trim().lowercase()}|${link.className.trim().lowercase()}"
                else -> "scan:${link.scanRecordId}"
            }
        }
        .forEach { (key, papers) ->
            val latest = papers.maxByOrNull { it.second.linkedAtEpochMs } ?: return@forEach
            val link = latest.second
            overviews += StudentRosterOverview(
                key = "orphan:$key",
                roster = null,
                name = link.studentName.trim(),
                number = StudentNumber.normalize(link.studentNumber),
                className = link.className.trim(),
                guardianName = "",
                guardianPhone = "",
                scanCount = papers.size,
                latestExamId = latest.first,
                latestScanRecordId = link.scanRecordId
            )
        }

    return overviews.sortedWith(
        compareBy<StudentRosterOverview> { it.className.ifBlank { "~" } }
            .thenBy { it.name.ifBlank { "~" } }
            .thenBy { it.number }
    )
}

@Composable
fun StudentRosterScreen(
    onOpenPaper: (String, String) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val rosterRepository = remember(context) { FileStudentRosterRepository(appContext) }
    val examRepository = remember(context) { FileExamRepository(appContext) }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val worker = remember { Executors.newSingleThreadExecutor() }
    val active = remember { AtomicBoolean(true) }

    var roster by remember { mutableStateOf(rosterRepository.list()) }
    var exams by remember { mutableStateOf(examRepository.list()) }
    var query by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var importPreview by remember { mutableStateOf<EschoolPdfImportPreview?>(null) }
    var editing by remember { mutableStateOf<StudentRosterOverview?>(null) }
    var guardianName by remember { mutableStateOf("") }
    var guardianPhone by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            active.set(false)
            worker.shutdownNow()
        }
    }

    fun refreshRoster() {
        roster = rosterRepository.list()
        exams = examRepository.list()
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !busy) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            busy = true
            status = "e-Okul PDF okunuyor…"
            worker.execute {
                val outcome = runCatching { EschoolPdfImporter.read(appContext, uri) }
                mainExecutor.execute {
                    if (active.get()) {
                        busy = false
                        outcome.onSuccess { preview ->
                            importPreview = preview
                            status = "${preview.students.size} öğrenci bulundu. Önizlemeyi kontrol edin."
                        }.onFailure { error ->
                            status = "PDF içe aktarılamadı: ${error.message ?: error.javaClass.simpleName}"
                        }
                    }
                }
            }
        }
    }

    val overviews = remember(roster, exams) { buildStudentRosterOverviews(roster, exams) }
    val classes = remember(overviews) {
        overviews.map { it.className }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val normalizedQuery = query.trim().lowercase()
    val filtered = overviews.filter { student ->
        val matchesClass = selectedClass == null || student.className == selectedClass
        val matchesQuery = normalizedQuery.isBlank() ||
            student.name.lowercase().contains(normalizedQuery) ||
            student.number.lowercase().contains(normalizedQuery) ||
            student.className.lowercase().contains(normalizedQuery) ||
            student.guardianName.lowercase().contains(normalizedQuery) ||
            student.guardianPhone.contains(normalizedQuery)
        matchesClass && matchesQuery
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { importPreview = null },
            title = { Text("e-Okul PDF Önizleme") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${preview.students.size} öğrenci · ${preview.classCounts.size} sınıf",
                        fontWeight = FontWeight.SemiBold
                    )
                    preview.classCounts.forEach { (className, count) ->
                        Text("$className · $count öğrenci")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Öğrenciler", fontWeight = FontWeight.SemiBold)
                    preview.students.forEach { student ->
                        Text(
                            "${student.className} · No ${student.studentNumber} · ${student.fullName}",
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        "Bu PDF veli adı veya telefon içermiyorsa mevcut veli bilgileri korunur; yeni öğrencilerde boş bırakılır.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching { rosterRepository.upsertImported(preview.students) }
                            .onSuccess { summary ->
                                refreshRoster()
                                importPreview = null
                                status = "İçe aktarma tamamlandı · ${summary.inserted} yeni · ${summary.updated} güncellendi · ${summary.unchanged} değişmedi"
                            }
                            .onFailure { error ->
                                status = "Kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
                            }
                    }
                ) { Text("İçe Aktar") }
            },
            dismissButton = {
                TextButton(onClick = { importPreview = null }) { Text("Vazgeç") }
            }
        )
    }

    editing?.roster?.let { original ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(original.fullName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${original.className} · No ${original.studentNumber}")
                    Text(
                        when (original.gender) {
                            StudentGender.GIRL -> "Cinsiyet: Kız"
                            StudentGender.BOY -> "Cinsiyet: Erkek"
                            StudentGender.UNKNOWN -> "Cinsiyet: —"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = guardianName,
                        onValueChange = { guardianName = it },
                        label = { Text("Veli Ad Soyad") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = guardianPhone,
                        onValueChange = { guardianPhone = it },
                        label = { Text("Veli Telefon") },
                        singleLine = true
                    )
                    val latestExamId = editing?.latestExamId
                    val latestScanRecordId = editing?.latestScanRecordId
                    if (latestExamId != null && latestScanRecordId != null) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                editing = null
                                onOpenPaper(latestExamId, latestScanRecordId)
                            }
                        ) {
                            Text("Son Optik Kağıdı Aç")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching {
                            rosterRepository.save(
                                original.copy(
                                    guardianName = guardianName,
                                    guardianPhone = guardianPhone,
                                    updatedAtEpochMs = System.currentTimeMillis()
                                )
                            )
                        }.onSuccess {
                            refreshRoster()
                            editing = null
                            status = "Veli bilgileri kaydedildi."
                        }.onFailure { error ->
                            status = "Veli bilgileri kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
                        }
                    }
                ) { Text("Kaydet") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("Vazgeç") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ProductTopBar(
            title = "Öğrenciler",
            actionText = "PDF +",
            onActionClick = {
                if (!busy) pdfPicker.launch(arrayOf("application/pdf"))
            }
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("e-Okul Öğrenci Listesi", fontWeight = FontWeight.Bold)
                        Text(
                            "Sınıf listesi PDF'sini seçin. Sınıf/şube, öğrenci no, ad-soyad ve cinsiyet önizlemeden sonra cihazda saklanır.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                            onClick = { pdfPicker.launch(arrayOf("application/pdf")) }
                        ) {
                            Text(if (busy) "PDF okunuyor…" else "e-Okul PDF İçe Aktar")
                        }
                        if (status.isNotBlank()) {
                            Text(
                                status,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Öğrenci, numara, sınıf veya veli ara") },
                    leadingIcon = { Text("⌕", fontSize = 22.sp) },
                    shape = RoundedCornerShape(18.dp)
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    RosterStatCard(Modifier.weight(1f), "Öğrenci", overviews.size.toString())
                    RosterStatCard(Modifier.weight(1f), "Sınıf", classes.size.toString())
                    RosterStatCard(Modifier.weight(1f), "Kağıt", overviews.sumOf { it.scanCount }.toString())
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    item {
                        ProductFilterPill(
                            label = "Tümü",
                            count = overviews.size,
                            selected = selectedClass == null,
                            onClick = { selectedClass = null }
                        )
                    }
                    items(classes, key = { it }) { className ->
                        ProductFilterPill(
                            label = className,
                            count = overviews.count { it.className == className },
                            selected = selectedClass == className,
                            onClick = { selectedClass = className }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (overviews.isEmpty()) "Henüz öğrenci yok" else "Öğrenci bulunamadı",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (overviews.isEmpty())
                                    "e-Okul sınıf listesi PDF'sini içe aktararak başlayın."
                                else "Arama metnini veya sınıf filtresini değiştirin.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filtered, key = { it.key }) { student ->
                    StudentRosterOverviewCard(
                        student = student,
                        onClick = {
                            val entry = student.roster
                            if (entry != null) {
                                guardianName = entry.guardianName
                                guardianPhone = entry.guardianPhone
                                editing = student
                            } else {
                                val examId = student.latestExamId
                                val scanId = student.latestScanRecordId
                                if (examId != null && scanId != null) onOpenPaper(examId, scanId)
                            }
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun RosterStatCard(modifier: Modifier, label: String, value: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 9.dp)
        ) {
            Text(
                value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StudentRosterOverviewCard(
    student: StudentRosterOverview,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    student.name.ifBlank { "Öğrenci bilgisi bekliyor" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${student.className.ifBlank { "Sınıf —" }} · No: ${student.number.ifBlank { "—" }}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    when {
                        student.roster == null -> "e-Okul roster kaydı yok"
                        student.guardianPhone.isNotBlank() && student.guardianName.isNotBlank() ->
                            "Veli: ${student.guardianName} · ${student.guardianPhone}"
                        student.guardianPhone.isNotBlank() -> "Veli telefonu: ${student.guardianPhone}"
                        else -> "Veli bilgisi eklenmedi"
                    },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ProductStatusBadge(
                text = "${student.scanCount} KAĞIT",
                tone = if (student.scanCount > 0) ProductBadgeTone.GREEN else ProductBadgeTone.NEUTRAL
            )
        }
    }
}
