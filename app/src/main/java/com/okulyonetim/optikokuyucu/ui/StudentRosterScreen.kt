package com.okulyonetim.optikokuyucu.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.core.content.res.ResourcesCompat
import com.okulyonetim.optikokuyucu.R
import com.okulyonetim.optikokuyucu.exam.ConfiguredExamReport
import com.okulyonetim.optikokuyucu.exam.ConfiguredExamReportExporter
import com.okulyonetim.optikokuyucu.exam.Exam
import com.okulyonetim.optikokuyucu.exam.ExamPaperLink
import com.okulyonetim.optikokuyucu.exam.ExamReport
import com.okulyonetim.optikokuyucu.exam.ExamReportBuilder
import com.okulyonetim.optikokuyucu.exam.ExamReportRow
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.exam.ReportColumn
import com.okulyonetim.optikokuyucu.exam.ReportPageOrientation
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import com.okulyonetim.optikokuyucu.student.EschoolPdfImportPreview
import com.okulyonetim.optikokuyucu.student.EschoolPdfImporter
import com.okulyonetim.optikokuyucu.student.FileStudentClassRepository
import com.okulyonetim.optikokuyucu.student.FileStudentRosterRepository
import com.okulyonetim.optikokuyucu.student.StudentClassEntry
import com.okulyonetim.optikokuyucu.student.StudentGender
import com.okulyonetim.optikokuyucu.student.StudentNumber
import com.okulyonetim.optikokuyucu.student.StudentRosterEntry
import com.okulyonetim.optikokuyucu.student.StudentSchoolIdentity
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private data class StudentRosterOverview(
    val key: String,
    val roster: StudentRosterEntry?,
    val name: String,
    val number: String,
    val className: String,
    val schoolName: String,
    val guardianName: String,
    val guardianPhone: String,
    val scanCount: Int,
    val latestExamId: String?,
    val latestScanRecordId: String?
)

private data class StudentExamResult(
    val exam: Exam,
    val report: ExamReport,
    val row: ExamReportRow
)

private fun matchesRosterStudent(
    entry: StudentRosterEntry,
    link: ExamPaperLink,
    rosterCountByNumber: Map<String, Int>
): Boolean {
    val number = StudentNumber.normalize(link.studentNumber)
    if (number != entry.studentNumber) return false
    val linkedGrade = StudentSchoolIdentity.gradeLevelFromClassName(link.className)
    return when {
        linkedGrade != null -> StudentSchoolIdentity.sameInstitution(entry.gradeLevel, linkedGrade)
        rosterCountByNumber[entry.studentNumber] == 1 -> true
        else -> false
    }
}

private fun buildStudentRosterOverviews(
    roster: List<StudentRosterEntry>,
    exams: List<Exam>
): List<StudentRosterOverview> {
    val linkedPapers = exams.flatMap { exam -> exam.papers.map { exam.id to it } }
    val rosterCountByNumber = roster.groupingBy { it.studentNumber }.eachCount()
    val consumedScanIds = mutableSetOf<String>()
    val overviews = mutableListOf<StudentRosterOverview>()

    roster.forEach { entry ->
        val matches = linkedPapers.filter { (_, link) ->
            matchesRosterStudent(entry, link, rosterCountByNumber)
        }
        matches.forEach { consumedScanIds += it.second.scanRecordId }
        val latest = matches.maxByOrNull { it.second.linkedAtEpochMs }
        overviews += StudentRosterOverview(
            key = "roster:${entry.identityKey}",
            roster = entry,
            name = entry.fullName,
            number = entry.studentNumber,
            className = entry.className,
            schoolName = entry.schoolName,
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
            val grade = StudentSchoolIdentity.gradeLevelFromClassName(link.className)
            when {
                number.isNotBlank() && grade != null ->
                    "identity:${StudentSchoolIdentity.identityKey(number, grade)}"
                number.isNotBlank() -> "number:$number"
                link.studentName.isNotBlank() -> "name:${link.studentName.trim().lowercase()}|${link.className.trim().lowercase()}"
                else -> "scan:${link.scanRecordId}"
            }
        }
        .forEach { (key, papers) ->
            val latest = papers.maxByOrNull { it.second.linkedAtEpochMs } ?: return@forEach
            val link = latest.second
            val grade = StudentSchoolIdentity.gradeLevelFromClassName(link.className)
            overviews += StudentRosterOverview(
                key = "orphan:$key",
                roster = null,
                name = link.studentName.trim(),
                number = StudentNumber.normalize(link.studentNumber),
                className = link.className.trim(),
                schoolName = grade?.let(StudentSchoolIdentity::schoolNameForGrade).orEmpty(),
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val scanRepository = remember(context) { FileScanRecordRepository(appContext) }
    val keyRepository = remember(context) { FileAnswerKeyRepository(appContext) }
    val reportTypeface = remember(context) { ResourcesCompat.getFont(context, R.font.noto_sans) }
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
    var pendingDeleteStudent by remember { mutableStateOf<StudentRosterEntry?>(null) }
    var pendingStudentPdfBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingStudentPdfName by remember { mutableStateOf("ogrenci-raporu.pdf") }
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

    val studentPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ConfiguredExamReportExporter.PDF_MIME_TYPE)
    ) { uri ->
        val bytes = pendingStudentPdfBytes
        pendingStudentPdfBytes = null
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "PDF çıktı akışı açılamadı." }
                output.write(bytes)
                output.flush()
            }
        }.onSuccess {
            feedback.success("Öğrenci raporu PDF olarak kaydedildi.")
        }.onFailure { error ->
            feedback.error("PDF kaydedilemedi: ${error.message ?: error.javaClass.simpleName}")
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
            student.schoolName.lowercase().contains(normalizedQuery) ||
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
                    modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("${preview.students.size} öğrenci · ${preview.classCounts.size} sınıf", fontWeight = FontWeight.SemiBold)
                    preview.classCounts.forEach { (className, count) -> Text("$className · $count öğrenci") }
                    Spacer(Modifier.height(4.dp))
                    Text("Öğrenciler", fontWeight = FontWeight.SemiBold)
                    preview.students.forEach { student ->
                        val school = student.schoolName.takeIf(String::isNotBlank)?.let { "$it · " }.orEmpty()
                        Text("$school${student.className} · No ${student.studentNumber} · ${student.fullName}", fontSize = 12.sp)
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
            dismissButton = { TextButton(onClick = { importPreview = null }) { Text("Vazgeç") } }
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
                        } else OutlinedButton(onClick = { studentGender = StudentGender.GIRL }) { Text("Kız") }
                        if (studentGender == StudentGender.BOY) {
                            FilledTonalButton(onClick = { studentGender = StudentGender.BOY }) { Text("Erkek") }
                        } else OutlinedButton(onClick = { studentGender = StudentGender.BOY }) { Text("Erkek") }
                        if (studentGender == StudentGender.UNKNOWN) {
                            FilledTonalButton(onClick = { studentGender = StudentGender.UNKNOWN }) { Text("—") }
                        } else OutlinedButton(onClick = { studentGender = StudentGender.UNKNOWN }) { Text("—") }
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
                            val grade = studentGradeText.toIntOrNull() ?: error("Sınıf seviyesi girilmelidir.")
                            val school = StudentSchoolIdentity.schoolNameForGrade(grade).ifBlank { "bu sınıf grubu" }
                            require(rosterRepository.findByNumberAndGrade(normalizedNumber, grade) == null) {
                                "$school içinde bu öğrenci numarası zaten kayıtlı."
                            }
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
                        }.onFailure { error -> feedback.warning(error.message ?: "Öğrenci eklenemedi.") }
                    }
                ) { Text("Ekle") }
            },
            dismissButton = { TextButton(onClick = { manualStudentOpen = false }) { Text("Vazgeç") } }
        )
    }

    if (classManagerOpen) {
        AlertDialog(
            onDismissRequest = { classManagerOpen = false },
            title = { Text("Sınıflar") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    if (classEntries.isEmpty()) Text("Henüz sınıf eklenmedi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    ) { Text("+ Yeni Sınıf") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { classManagerOpen = false }) { Text("Kapat") } }
        )
    }

    if (classEditorOpen) {
        AlertDialog(
            onDismissRequest = { classEditorOpen = false; classManagerOpen = true },
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
                            val grade = classGradeText.toIntOrNull() ?: error("Sınıf seviyesi girilmelidir.")
                            val updatedClass = StudentClassEntry(grade, classBranchText).normalized()
                            val old = editingClass
                            if (old != null && old.className != updatedClass.className) {
                                roster.filter { it.className == old.className }.forEach { student ->
                                    rosterRepository.replace(
                                        student,
                                        student.copy(
                                            gradeLevel = updatedClass.gradeLevel,
                                            branch = updatedClass.branch,
                                            updatedAtEpochMs = System.currentTimeMillis()
                                        )
                                    )
                                }
                                classRepository.delete(old)
                                if (selectedClass == old.className) selectedClass = updatedClass.className
                            }
                            classRepository.save(updatedClass)
                        }.onSuccess {
                            refreshRoster()
                            classEditorOpen = false
                            classManagerOpen = true
                            feedback.success(if (editingClass == null) "Sınıf eklendi." else "Sınıf güncellendi.")
                            editingClass = null
                        }.onFailure { error -> feedback.warning(error.message ?: "Sınıf kaydedilemedi.") }
                    }
                ) { Text("Kaydet") }
            },
            dismissButton = {
                TextButton(onClick = { classEditorOpen = false; classManagerOpen = true }) { Text("Vazgeç") }
            }
        )
    }

    editing?.roster?.let { original ->
        val rosterCountByNumber = remember(roster) { roster.groupingBy { it.studentNumber }.eachCount() }
        val studentResults = remember(original, roster, exams) {
            val records = scanRepository.list()
            val answerKeys = keyRepository.list()
            exams.mapNotNull { exam ->
                val matchingScanIds = exam.papers
                    .filter { link -> matchesRosterStudent(original, link, rosterCountByNumber) }
                    .map { it.scanRecordId }
                    .toSet()
                if (matchingScanIds.isEmpty()) return@mapNotNull null
                val report = ExamReportBuilder.build(exam, records, answerKeys)
                val row = report.rows
                    .filter { it.scanRecordId in matchingScanIds }
                    .maxByOrNull { it.capturedAtEpochMs ?: Long.MIN_VALUE }
                    ?: return@mapNotNull null
                StudentExamResult(exam, report, row)
            }.sortedByDescending { it.exam.examDateEpochDay }
        }

        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(original.fullName) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 570.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        buildString {
                            if (original.schoolName.isNotBlank()) append(original.schoolName).append(" · ")
                            append(original.className).append(" · No ").append(original.studentNumber)
                        }
                    )
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

                    Text(
                        "Öğrenci Raporları",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (studentResults.isEmpty()) {
                        Text(
                            "Bu öğrenciye ait henüz sınav sonucu bulunmuyor.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "${studentResults.size} sınav sonucu · Her sınav için optik kağıdı açabilir veya PDF raporu oluşturabilirsiniz.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        studentResults.forEach { result ->
                            ProductCompactCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        result.exam.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subject = result.exam.subjectName.takeIf(String::isNotBlank)
                                    Text(
                                        listOfNotNull(subject, result.row.className.takeIf(String::isNotBlank))
                                            .joinToString(" · ")
                                            .ifBlank { result.exam.schoolName },
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "D ${result.row.correct ?: "—"} · Y ${result.row.wrong ?: "—"} · B ${result.row.blank ?: "—"} · Net ${formatStudentResultNumber(result.row.net)} · Puan ${formatStudentResultNumber(result.row.points)}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "Genel sıra ${result.row.overallRank ?: "—"} · Sınıf sırası ${result.row.classRank ?: "—"}",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        OutlinedButton(
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                editing = null
                                                onOpenPaper(result.exam.id, result.row.scanRecordId)
                                            }
                                        ) { Text("Optik Kağıt", fontSize = 10.sp) }
                                        OutlinedButton(
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                val config = ConfiguredExamReport(
                                                    report = result.report,
                                                    rows = listOf(result.row),
                                                    columns = listOf(
                                                        ReportColumn.STUDENT,
                                                        ReportColumn.CLASS,
                                                        ReportColumn.NUMBER,
                                                        ReportColumn.SCORE,
                                                        ReportColumn.NET,
                                                        ReportColumn.CORRECT,
                                                        ReportColumn.WRONG,
                                                        ReportColumn.BLANK,
                                                        ReportColumn.OVERALL_RANK,
                                                        ReportColumn.CLASS_RANK,
                                                        ReportColumn.LESSONS
                                                    ),
                                                    orientation = ReportPageOrientation.LANDSCAPE,
                                                    titleSuffix = "Öğrenci Raporu"
                                                )
                                                runCatching {
                                                    ConfiguredExamReportExporter.exportPdfBytes(config, reportTypeface)
                                                }.onSuccess { bytes ->
                                                    pendingStudentPdfBytes = bytes
                                                    pendingStudentPdfName = studentReportFileName(original.fullName, result.exam.name)
                                                    studentPdfLauncher.launch(pendingStudentPdfName)
                                                }.onFailure { error ->
                                                    feedback.error("PDF hazırlanamadı: ${error.message ?: error.javaClass.simpleName}")
                                                }
                                            }
                                        ) { Text("PDF", fontSize = 10.sp) }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            pendingDeleteStudent = original
                            editing = null
                        }
                    ) {
                        Text("Bu Cihazdan Kaldır", color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        "Bu işlem yalnız Optik Okuyucu listesini etkiler; Okul Yönetim'deki öğrenci kaydı silinmez veya değiştirilmez.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Vazgeç") } }
        )
    }

    pendingDeleteStudent?.let { student ->
        AlertDialog(
            onDismissRequest = { pendingDeleteStudent = null },
            title = { Text("Öğrenciyi Bu Cihazdan Kaldır") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        buildString {
                            if (student.schoolName.isNotBlank()) append(student.schoolName).append(" · ")
                            append(student.fullName).append(" · ").append(student.className)
                                .append(" · No ").append(student.studentNumber)
                        }
                    )
                    Text(
                        "Öğrenci yalnız Optik Okuyucu'nun bu cihazdaki listesinden kaldırılacak. Okul Yönetim'deki öğrenci kaydı silinmeyecek ve değiştirilmeyecek.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Sonraki Okul Yönetim senkronunda bu öğrenci otomatik olarak yeniden eklenmeyecek. Aynı numaralı diğer kurum öğrencisi etkilenmeyecek.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching {
                            check(rosterRepository.delete(student.studentNumber, student.gradeLevel)) {
                                "Öğrenci cihazdan kaldırılamadı."
                            }
                        }.onSuccess {
                            pendingDeleteStudent = null
                            refreshRoster()
                            status = "${student.fullName} yalnız bu cihazdan kaldırıldı. Okul Yönetim kaydı korundu."
                            feedback.success("Öğrenci bu cihazdan kaldırıldı.")
                        }.onFailure { error ->
                            status = error.message ?: "Öğrenci cihazdan kaldırılamadı."
                            feedback.error(status)
                        }
                    }
                ) { Text("Kaldır", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteStudent = null }) { Text("Vazgeç") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ProductTopBar(
            title = "Öğrenciler",
            actionText = "⋮",
            onActionClick = { optionsExpanded = true }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                ProductSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Öğrenci, numara, sınıf veya veli ara"
                )
            }
            if (busy) {
                item {
                    Text(
                        "$importSourceLabel okunuyor…",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            item {
                ProductMetricStrip(
                    metrics = listOf(
                        "Öğrenci" to overviews.size.toString(),
                        "Sınıf" to classes.size.toString(),
                        "Kağıt" to overviews.sumOf { it.scanCount }.toString()
                    )
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    ProductEmptyState(
                        title = if (overviews.isEmpty()) "Henüz öğrenci yok" else "Öğrenci bulunamadı",
                        body = if (overviews.isEmpty())
                            "Sağ üstteki seçeneklerden PDF içe aktarabilir veya manuel öğrenci ekleyebilirsiniz."
                        else "Arama metnini veya sınıf filtresini değiştirin."
                    )
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
            item { Spacer(Modifier.height(6.dp)) }
        }
    }

    if (optionsExpanded) {
        ModalBottomSheet(onDismissRequest = { optionsExpanded = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("Öğrenci Seçenekleri", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                StudentOptionRow("⇩", "e-Okul PDF İçe Aktar", enabled = !busy) {
                    optionsExpanded = false
                    importSourceLabel = "e-Okul PDF"
                    pdfPicker.launch(arrayOf("application/pdf"))
                }
                StudentOptionRow("＋", "PDF Öğrenci Ekle", enabled = !busy) {
                    optionsExpanded = false
                    importSourceLabel = "Öğrenci PDF"
                    pdfPicker.launch(arrayOf("application/pdf"))
                }
                StudentOptionRow("＋", "Manuel Öğrenci Ekle") {
                    optionsExpanded = false
                    manualStudentOpen = true
                }
                StudentOptionRow("▤", "Sınıfları Yönet") {
                    optionsExpanded = false
                    classManagerOpen = true
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun StudentOptionRow(
    symbol: String,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(symbol, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            Text(label, fontSize = 14.sp)
        }
    }
}

@Composable
private fun StudentRosterOverviewCard(
    student: StudentRosterOverview,
    onClick: () -> Unit
) {
    val initial = student.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "•"
    ProductCompactCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductInitialBadge(initial)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    student.name.ifBlank { "Öğrenci bilgisi bekliyor" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(student.className.ifBlank { "Sınıf —" })
                        append(" · No: ").append(student.number.ifBlank { "—" })
                        if (student.schoolName.isNotBlank()) append(" · ").append(student.schoolName)
                    },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when {
                        student.roster == null -> "e-Okul roster kaydı yok"
                        student.guardianPhone.isNotBlank() && student.guardianName.isNotBlank() ->
                            "Veli: ${student.guardianName} · ${student.guardianPhone}"
                        student.guardianPhone.isNotBlank() -> "Veli telefonu: ${student.guardianPhone}"
                        else -> "Veli bilgisi eklenmedi"
                    },
                    fontSize = 9.sp,
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

private fun formatStudentResultNumber(value: Double?): String =
    value?.let { String.format(Locale("tr", "TR"), "%.2f", it) } ?: "—"

private fun studentReportFileName(studentName: String, examName: String): String {
    fun safe(value: String): String = value
        .trim()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
        .trim('-')
        .take(36)
        .ifBlank { "rapor" }
    return "${safe(studentName)}-${safe(examName)}-rapor.pdf"
}
