package com.okulyonetim.optikokuyucu.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
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
import com.okulyonetim.optikokuyucu.student.FileStudentClassRepository
import com.okulyonetim.optikokuyucu.student.FileStudentRosterRepository
import com.okulyonetim.optikokuyucu.student.StudentClassEntry
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
    val feedback = LocalAppFeedback.current
    val appContext = context.applicationContext
    val rosterRepository = remember(context) { FileStudentRosterRepository(appContext) }
    val classRepository = remember(context) { FileStudentClassRepository(appContext) }
    val examRepository = remember(context) { FileExamRepository(appContext) }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val worker = remember { Executors.newSingleThreadExecutor() }
    val active = remember { AtomicBoolean(true) }

    var roster by remember { mutableStateOf(rosterRepository.list()) }
    var storedClasses by remember { mutableStateOf(classRepository.list()) }
    var exams by remember { mutableStateOf(examRepository.list()) }
    var query by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var importPreview by remember { mutableStateOf<EschoolPdfImportPreview?>(null) }
    var importSourceLabel by remember { mutableStateOf("e-Okul PDF") }
    var editing by remember { mutableStateOf<StudentRosterOverview?>(null) }
    var guardianName by remember { mutableStateOf("") }
    var guardianPhone by remember { mutableStateOf("") }
    var optionsExpanded by remember { mutableStateOf(false) }

    var manualStudentOpen by remember { mutableStateOf(false) }
    var studentNumberText by remember { mutableStateOf("") }
    var studentNameText by remember { mutableStateOf("") }
    var studentGradeText by remember { mutableStateOf("") }
    var studentBranchText by remember { mutableStateOf("") }
    var studentGuardianText by remember { mutableStateOf("") }
    var studentPhoneText by remember { mutableStateOf("") }
    var studentGender by remember { mutableStateOf(StudentGender.UNKNOWN) }

    var classManagerOpen by remember { mutableStateOf(false) }
    var classEditorOpen by remember { mutableStateOf(false) }
    var editingClass by remember { mutableStateOf<StudentClassEntry?>(null) }
    var classGradeText by remember { mutableStateOf("") }
    var classBranchText by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            active.set(false)
            worker.shutdownNow()
        }
    }

    fun refreshRoster() {
        roster = rosterRepository.list()
        storedClasses = classRepository.list()
        exams = examRepository.list()
    }

    fun launchPdf(label: String) {
        if (!busy) {
            importSourceLabel = label
            pdfPicker@ run {
                // launcher is invoked after declaration below through the menu callbacks.
            }
        }
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
            status = "$importSourceLabel okunuyor…"
            feedback.info(status)
            worker.execute {
                val outcome = runCatching { EschoolPdfImporter.read(appContext, uri) }
                mainExecutor.execute {
                    if (active.get()) {
                        busy = false
                        outcome.onSuccess { preview ->
                            importPreview = preview
                            status = "${preview.students.size} öğrenci bulundu. Önizlemeyi kontrol edin."
                            feedback.info(status)
                        }.onFailure { error ->
                            status = "PDF içe aktarılamadı: ${error.message ?: error.javaClass.simpleName}"
                            feedback.error(status)
                        }
                    }
                }
            }
        }
    }

    val overviews = remember(roster, exams) { buildStudentRosterOverviews(roster, exams) }
    val classEntries = remember(roster, storedClasses) {
        (storedClasses + roster.map { StudentClassEntry(it.gradeLevel, it.branch).normalized() })
            .distinctBy { it.className }
            .sortedWith(compareBy<StudentClassEntry> { it.gradeLevel }.thenBy { it.branch })
    }
    val classes = remember(classEntries) { classEntries.map { it.className } }
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
            title = { Text("$importSourceLabel Önizleme") },
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
                        "PDF veli adı veya telefon içermiyorsa mevcut veli bilgileri korunur; yeni öğrencilerde boş bırakılır.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching {
                            val summary = rosterRepository.upsertImported(preview.students)
                            preview.students
                                .map { StudentClassEntry(it.gradeLevel, it.branch).normalized() }
                                .distinctBy { it.className }
                                .forEach(classRepository::save)
                            summary
                        }.onSuccess { summary ->
                            refreshRoster()
                            importPreview = null
                            status = "İçe aktarma tamamlandı · ${summary.inserted} yeni · ${summary.updated} güncellendi · ${summary.unchanged} değişmedi"
                            feedback.success("Öğrenciler içe aktarıldı · ${summary.inserted} yeni · ${summary.updated} güncellendi")
                        }.onFailure { error ->
                            status = "Kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
                            feedback.error(status)
                        }
                    }
                ) { Text("İçe Aktar") }
            },
            dismissButton = {
                TextButton(onClick = { importPreview = null }) { Text("Vazgeç") }
            }
        )
    }

    if (manualStudentOpen) {
        AlertDialog(
            onDismissRequest = { manualStudentOpen = false },
            title = { Text("Manuel Öğrenci Ekle") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = studentNumberText,
                        onValueChange = { studentNumberText = it.filter(Char::isDigit).take(12) },
                        label = { Text("Öğrenci No") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = studentNameText,
                        onValueChange = { studentNameText = it },
                        label = { Text("Ad Soyad") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = studentGradeText,
                            onValueChange = { studentGradeText = it.filter(Char::isDigit).take(2) },
                            label = { Text("Sınıf") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = studentBranchText,
                            onValueChange = { studentBranchText = it.take(20) },
                            label = { Text("Şube") },
                            singleLine = true
                        )
                    }
                    Text("Cinsiyet", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (studentGender == StudentGender.GIRL) {
                            FilledTonalButton(onClick = { studentGender = StudentGender.GIRL }) { Text("Kız") }
                        } else {
                            OutlinedButton(onClick = { studentGender = StudentGender.GIRL }) { Text("Kız") }
                        }
                        if (studentGender == StudentGender.BOY) {
                            FilledTonalButton(onClick = { studentGender = StudentGender.BOY }) { Text("Erkek") }
                        } else {
                            OutlinedButton(onClick = { studentGender = StudentGender.BOY }) { Text("Erkek") }
                        }
                        if (studentGender == StudentGender.UNKNOWN) {
                            FilledTonalButton(onClick = { studentGender = StudentGender.UNKNOWN }) { Text("—") }
                        } else {
                            OutlinedButton(onClick = { studentGender = StudentGender.UNKNOWN }) { Text("—") }
                        }
                    }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = studentGuardianText,
                        onValueChange = { studentGuardianText = it },
                        label = { Text("Veli Ad Soyad") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = studentPhoneText,
                        onValueChange = { studentPhoneText = it },
                        label = { Text("Veli Telefon") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching {
                            val normalizedNumber = StudentNumber.normalize(studentNumberText)
                            require(normalizedNumber.isNotBlank()) { "Öğrenci numarası zorunludur." }
                            require(rosterRepository.findByNumber(normalizedNumber) == null) { "Bu öğrenci numarası zaten kayıtlı." }
                            val grade = studentGradeText.toIntOrNull()
                                ?: error("Sınıf seviyesi girilmelidir.")
                            val entry = StudentRosterEntry(
                                studentNumber = normalizedNumber,
                                fullName = studentNameText,
                                gender = studentGender,
                                gradeLevel = grade,
                                branch = studentBranchText,
                                guardianName = studentGuardianText,
                                guardianPhone = studentPhoneText
                            ).normalized()
                            rosterRepository.save(entry)
                            classRepository.save(StudentClassEntry(entry.gradeLevel, entry.branch))
                        }.onSuccess {
                            refreshRoster()
                            manualStudentOpen = false
                            studentNumberText = ""
                            studentNameText = ""
                            studentGradeText = ""
                            studentBranchText = ""
                            studentGuardianText = ""
                            studentPhoneText = ""
                            studentGender = StudentGender.UNKNOWN
                            feedback.success("Öğrenci eklendi.")
                        }.onFailure { error ->
                            feedback.warning(error.message ?: "Öğrenci eklenemedi.")
                        }
                    }
                ) { Text("Ekle") }
            },
            dismissButton = {
                TextButton(onClick = { manualStudentOpen = false }) { Text("Vazgeç") }
            }
        )
    }

    if (classManagerOpen) {
        AlertDialog(
            onDismissRequest = { classManagerOpen = false },
            title = { Text("Sınıflar") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    if (classEntries.isEmpty()) {
                        Text("Henüz sınıf eklenmedi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    classEntries.forEach { entry ->
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                editingClass = entry
                                classGradeText = entry.gradeLevel.toString()
                                classBranchText = entry.branch
                                classManagerOpen = false
                                classEditorOpen = true
                            }
                        ) {
                            Text("${entry.className} · ${roster.count { it.className == entry.className }} öğrenci")
                        }
                    }
                    FilledTonalButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            editingClass = null
                            classGradeText = ""
                            classBranchText = ""
                            classManagerOpen = false
                            classEditorOpen = true
                        }
                    ) {
                        Text("+ Yeni Sınıf")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { classManagerOpen = false }) { Text("Kapat") }
            }
        )
    }

    if (classEditorOpen) {
        AlertDialog(
            onDismissRequest = {
                classEditorOpen = false
                classManagerOpen = true
            },
            title = { Text(if (editingClass == null) "Sınıf Ekle" else "Sınıfı Düzenle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = classGradeText,
                        onValueChange = { classGradeText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Sınıf seviyesi") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = classBranchText,
                        onValueChange = { classBranchText = it.take(20) },
                        label = { Text("Şube") },
                        singleLine = true
                    )
                    editingClass?.let { old ->
                        val affected = roster.count { it.className == old.className }
                        if (affected > 0) {
                            Text(
                                "$affected öğrencinin sınıf bilgisi de güncellenecek.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching {
                            val grade = classGradeText.toIntOrNull()
                                ?: error("Sınıf seviyesi girilmelidir.")
                            val updatedClass = StudentClassEntry(grade, classBranchText).normalized()
                            val old = editingClass
                            if (old != null && old.className != updatedClass.className) {
                                roster.filter { it.className == old.className }.forEach { student ->
                                    rosterRepository.save(
                                        student.copy(
                                            gradeLevel = updatedClass.gradeLevel,
                                            branch = updatedClass.branch,
                                            updatedAtEpochMs = System.currentTimeMillis()
                                        )
                                    )
                                }
                                classRepository.delete(old)
                            }
                            classRepository.save(updatedClass)
                        }.onSuccess {
                            refreshRoster()
                            selectedClass = editingClass?.className
                                ?.takeIf { oldName -> oldName in classes }
                                ?.let { null }
                            classEditorOpen = false
                            classManagerOpen = true
                            feedback.success(if (editingClass == null) "Sınıf eklendi." else "Sınıf güncellendi.")
                            editingClass = null
                        }.onFailure { error ->
                            feedback.warning(error.message ?: "Sınıf kaydedilemedi.")
                        }
                    }
                ) { Text("Kaydet") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        classEditorOpen = false
                        classManagerOpen = true
                    }
                ) { Text("Vazgeç") }
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
                            feedback.success(status)
                        }.onFailure { error ->
                            status = "Veli bilgileri kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
                            feedback.error(status)
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
        Box(modifier = Modifier.fillMaxWidth()) {
            ProductTopBar(
                title = "Öğrenciler",
                actionText = "⋮",
                onActionClick = { optionsExpanded = true }
            )
            DropdownMenu(
                modifier = Modifier.align(Alignment.TopEnd),
                expanded = optionsExpanded,
                onDismissRequest = { optionsExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("e-Okul PDF İçe Aktar") },
                    onClick = {
                        optionsExpanded = false
                        importSourceLabel = "e-Okul PDF"
                        if (!busy) pdfPicker.launch(arrayOf("application/pdf"))
                    }
                )
                DropdownMenuItem(
                    text = { Text("PDF Öğrenci Ekle") },
                    onClick = {
                        optionsExpanded = false
                        importSourceLabel = "Öğrenci PDF"
                        if (!busy) pdfPicker.launch(arrayOf("application/pdf"))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Manuel Öğrenci Ekle") },
                    onClick = {
                        optionsExpanded = false
                        manualStudentOpen = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("Sınıfları Yönet") },
                    onClick = {
                        optionsExpanded = false
                        classManagerOpen = true
                    }
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
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
            if (busy) {
                item {
                    Text(
                        "$importSourceLabel okunuyor…",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
                                    "Sağ üstteki seçeneklerden PDF içe aktarabilir veya manuel öğrenci ekleyebilirsiniz."
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
