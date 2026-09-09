package com.okulyonetim.optikokuyucu.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.core.content.FileProvider
import com.okulyonetim.optikokuyucu.exam.ExamReport
import com.okulyonetim.optikokuyucu.exam.ExamReportBuilder
import com.okulyonetim.optikokuyucu.exam.ExamReportPdfExporter
import com.okulyonetim.optikokuyucu.exam.ExamReportRow
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.exam.examLessonDisplayName
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import com.okulyonetim.optikokuyucu.student.FileStudentRosterRepository
import com.okulyonetim.optikokuyucu.student.StudentNumber
import com.okulyonetim.optikokuyucu.student.StudentRosterEntry
import com.okulyonetim.optikokuyucu.student.StudentSchoolIdentity
import java.io.File
import java.util.Locale

private data class ParentShareItem(
    val row: ExamReportRow,
    val roster: StudentRosterEntry?
)

@Composable
fun ParentResultShareScreen(examId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val examRepository = remember(context) { FileExamRepository(appContext) }
    val scanRepository = remember(context) { FileScanRecordRepository(appContext) }
    val keyRepository = remember(context) { FileAnswerKeyRepository(appContext) }
    val rosterRepository = remember(context) { FileStudentRosterRepository(appContext) }
    val exam = remember(examId) { examRepository.load(examId) }
    val report = remember(examId, exam) {
        exam?.let { ExamReportBuilder.build(it, scanRepository.list(), keyRepository.list()) }
    }
    val roster = remember { rosterRepository.list() }
    var selectedClass by remember { mutableStateOf<String?>(null) }
    var includeScore by remember { mutableStateOf(true) }
    var includeRank by remember { mutableStateOf(true) }
    var includeDyb by remember { mutableStateOf(false) }
    var includeLessons by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }

    if (exam == null || report == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            ProductTopBar(title = "Velilere Sonuç Gönder", leadingText = "‹", onLeadingClick = onBack)
            ProductEmptyState("Sınav bulunamadı", "Geçerli bir sınav sonucu olmadan veli mesajı hazırlanamaz.")
        }
        return
    }

    val shareItems = remember(report, roster) {
        report.rows.map { row -> ParentShareItem(row, findRosterEntry(row, roster)) }
    }
    val classes = shareItems.map { it.row.className }.filter(String::isNotBlank).distinct().sorted()
    val filtered = shareItems.filter { selectedClass == null || it.row.className == selectedClass }
    val phoneReady = filtered.count { !it.roster?.guardianPhone.isNullOrBlank() }

    Column(modifier = Modifier.fillMaxSize()) {
        ProductTopBar(title = "Velilere Sonuç Gönder", leadingText = "‹", onLeadingClick = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exam.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "$phoneReady / ${filtered.size} veli telefonu hazır",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ProductStatusBadge(
                                if (phoneReady == filtered.size && filtered.isNotEmpty()) "HAZIR" else "$phoneReady HAZIR",
                                if (phoneReady > 0) ProductBadgeTone.GREEN else ProductBadgeTone.ORANGE
                            )
                        }
                        if (classes.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item {
                                    FilterChip(
                                        selected = selectedClass == null,
                                        onClick = { selectedClass = null },
                                        label = { Text("Tümü", fontSize = 9.sp) }
                                    )
                                }
                                items(classes) { className ->
                                    FilterChip(
                                        selected = selectedClass == className,
                                        onClick = { selectedClass = className },
                                        label = { Text(className, fontSize = 9.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                ProductSettingsSection(
                    title = "Mesajda Göster",
                    description = "Veli mesajına eklenecek sonuç alanlarını seçin."
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ParentOptionChip(
                            modifier = Modifier.weight(1f),
                            label = "Puan / Net",
                            checked = includeScore,
                            onChecked = { includeScore = it }
                        )
                        ParentOptionChip(
                            modifier = Modifier.weight(1f),
                            label = "Sıralama",
                            checked = includeRank,
                            onChecked = { includeRank = it }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ParentOptionChip(
                            modifier = Modifier.weight(1f),
                            label = "D / Y / B",
                            checked = includeDyb,
                            onChecked = { includeDyb = it }
                        )
                        ParentOptionChip(
                            modifier = Modifier.weight(1f),
                            label = "Ders Netleri",
                            checked = includeLessons,
                            onChecked = { includeLessons = it }
                        )
                    }
                }
            }
            if (filtered.isEmpty()) {
                item { ProductEmptyState("Öğrenci sonucu yok", "Seçili sınıfta gönderilecek sonuç bulunamadı.") }
            } else {
                items(filtered, key = { it.row.scanRecordId }) { item ->
                    ParentResultCard(
                        examName = exam.name,
                        report = report,
                        item = item,
                        includeScore = includeScore,
                        includeRank = includeRank,
                        includeDyb = includeDyb,
                        includeLessons = includeLessons,
                        onStatus = { status = it }
                    )
                }
            }
            if (status.isNotBlank()) {
                item { Text(status, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary) }
            }
            item { Spacer(Modifier.padding(5.dp)) }
        }
    }
}

@Composable
private fun ParentOptionChip(
    modifier: Modifier,
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    FilterChip(
        modifier = modifier,
        selected = checked,
        onClick = { onChecked(!checked) },
        label = {
            Text(
                if (checked) "✓ $label" else label,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Composable
private fun ParentResultCard(
    examName: String,
    report: ExamReport,
    item: ParentShareItem,
    includeScore: Boolean,
    includeRank: Boolean,
    includeDyb: Boolean,
    includeLessons: Boolean,
    onStatus: (String) -> Unit
) {
    val context = LocalContext.current
    val row = item.row
    val roster = item.roster
    val message = remember(row, roster, includeScore, includeRank, includeDyb, includeLessons) {
        composeParentMessage(examName, row, roster, includeScore, includeRank, includeDyb, includeLessons)
    }
    val phone = roster?.guardianPhone.orEmpty()
    val ready = phone.isNotBlank()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        row.studentName.ifBlank { row.studentNumber.ifBlank { "İsimsiz Öğrenci" } },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(
                            row.className.takeIf(String::isNotBlank),
                            row.studentNumber.takeIf(String::isNotBlank)?.let { "No $it" },
                            roster?.guardianName?.takeIf(String::isNotBlank)
                        ).joinToString(" · ").ifBlank { "Veli bilgisi eşleşmedi" },
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ProductStatusBadge(
                    if (ready) "TELEFON HAZIR" else "TELEFON YOK",
                    if (ready) ProductBadgeTone.GREEN else ProductBadgeTone.ORANGE
                )
            }
            Text(
                message,
                fontSize = 9.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = ready,
                    shape = RoundedCornerShape(11.dp),
                    onClick = {
                        openWhatsApp(context, phone, message)
                            .onSuccess { onStatus("WhatsApp mesajı açıldı: ${row.studentName}") }
                            .onFailure { onStatus("WhatsApp açılamadı: ${it.message ?: it.javaClass.simpleName}") }
                    }
                ) { Text("WhatsApp", fontSize = 9.sp) }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = ready,
                    shape = RoundedCornerShape(11.dp),
                    onClick = {
                        openSms(context, phone, message)
                            .onSuccess { onStatus("SMS ekranı açıldı: ${row.studentName}") }
                            .onFailure { onStatus("SMS açılamadı: ${it.message ?: it.javaClass.simpleName}") }
                    }
                ) { Text("SMS", fontSize = 9.sp) }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(11.dp),
                    onClick = {
                        sharePersonalPdf(context, report, row, message)
                            .onSuccess { onStatus("Kişisel PDF paylaşımı açıldı: ${row.studentName}") }
                            .onFailure { onStatus("PDF paylaşılamadı: ${it.message ?: it.javaClass.simpleName}") }
                    }
                ) { Text("PDF", fontSize = 9.sp) }
            }
        }
    }
}

private fun findRosterEntry(row: ExamReportRow, roster: List<StudentRosterEntry>): StudentRosterEntry? {
    val number = StudentNumber.normalize(row.studentNumber)
    if (number.isBlank()) return null
    val byNumber = roster.filter { it.studentNumber == number }
    if (byNumber.isEmpty()) return null
    val rowGrade = StudentSchoolIdentity.gradeLevelFromClassName(row.className)
    if (rowGrade != null) {
        byNumber.firstOrNull { StudentSchoolIdentity.sameInstitution(rowGrade, it.gradeLevel) }?.let { return it }
    }
    if (byNumber.size == 1) return byNumber.single()
    val normalizedName = row.studentName.trim().lowercase(Locale.forLanguageTag("tr-TR"))
    return byNumber.firstOrNull { it.fullName.trim().lowercase(Locale.forLanguageTag("tr-TR")) == normalizedName }
}

private fun composeParentMessage(
    examName: String,
    row: ExamReportRow,
    roster: StudentRosterEntry?,
    includeScore: Boolean,
    includeRank: Boolean,
    includeDyb: Boolean,
    includeLessons: Boolean
): String = buildString {
    val guardian = roster?.guardianName?.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
    append("Sayın Velimiz$guardian, ")
    append(row.studentName.ifBlank { "öğrencimizin" })
    append(" · ").append(examName).append(" sonucu")
    if (includeScore) {
        row.points?.let { append(" · Puan ").append(parentNumber(it)) }
        row.net?.let { append(" · Net ").append(parentNumber(it)) }
    }
    if (includeRank) {
        row.overallRank?.let { append(" · Genel sıra ").append(it) }
        row.classRank?.let { append(" · Sınıf sıra ").append(it) }
    }
    if (includeDyb) {
        append(" · D/Y/B ").append(row.correct ?: 0).append('/').append(row.wrong ?: 0).append('/').append(row.blank ?: 0)
    }
    if (includeLessons && row.lessons.isNotEmpty()) {
        append(" · ")
        append(
            row.lessons.joinToString(" | ") { lesson ->
                "${examLessonDisplayName(lesson.lessonId)} ${parentNumber(lesson.net)} net"
            }
        )
    }
    append(".")
}

private fun openWhatsApp(context: android.content.Context, rawPhone: String, message: String): Result<Unit> = runCatching {
    val phone = normalizePhone(rawPhone)
    require(phone.isNotBlank()) { "Veli telefonu boş." }
    val uri = Uri.parse("https://wa.me/$phone?text=${Uri.encode(message)}")
    val direct = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.whatsapp") }
    runCatching { context.startActivity(direct) }.getOrElse {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}

private fun openSms(context: android.content.Context, rawPhone: String, message: String): Result<Unit> = runCatching {
    val phone = normalizePhone(rawPhone)
    require(phone.isNotBlank()) { "Veli telefonu boş." }
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:+$phone")).apply {
        putExtra("sms_body", message)
    }
    context.startActivity(intent)
}

private fun sharePersonalPdf(
    context: android.content.Context,
    report: ExamReport,
    row: ExamReportRow,
    message: String
): Result<Unit> = runCatching {
    val directory = File(context.cacheDir, "result-reports").apply { mkdirs() }
    val safeName = row.studentName.ifBlank { row.studentNumber.ifBlank { "ogrenci" } }
        .replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(48).ifBlank { "ogrenci" }
    val file = File(directory, "$safeName-sonuc.pdf")
    file.outputStream().use { output ->
        ExamReportPdfExporter.export(report.copy(rows = listOf(row)), output)
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = ExamReportPdfExporter.MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, message)
        putExtra(Intent.EXTRA_SUBJECT, "${report.examName} · Öğrenci Sonucu")
        clipData = ClipData.newRawUri("Öğrenci sonucu", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, "Öğrenci sonucunu paylaş"))
}

private fun normalizePhone(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    return when {
        digits.length == 10 && digits.startsWith("5") -> "90$digits"
        digits.length == 11 && digits.startsWith("0") -> "90${digits.drop(1)}"
        digits.length == 12 && digits.startsWith("90") -> digits
        else -> digits
    }
}

private fun parentNumber(value: Double): String = String.format(Locale("tr", "TR"), "%.2f", value)
