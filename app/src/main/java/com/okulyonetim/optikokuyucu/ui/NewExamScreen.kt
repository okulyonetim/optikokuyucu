package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.okulyonetim.optikokuyucu.exam.ExamFactory
import com.okulyonetim.optikokuyucu.exam.ExamParticipant
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.exam.WrongAnswerPolicy
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateDefaults
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import com.okulyonetim.optikokuyucu.omr.template.FileActiveTemplateSelectionRepository
import com.okulyonetim.optikokuyucu.settings.AppSettingsRepository
import com.okulyonetim.optikokuyucu.student.FileStudentRosterRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private data class ExamTemplateOption(
    val name: String,
    val selection: ActiveTemplateSelection
)

@Composable
fun NewExamScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    examId: String? = null
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(context) { FileExamRepository(appContext) }
    val settingsRepository = remember(context) { AppSettingsRepository(appContext) }
    val roster = remember(context) { FileStudentRosterRepository(appContext).list() }
    val classNames = remember(roster) { roster.map { it.className }.distinct().sorted() }
    val options = remember(context) { loadExamTemplateOptions(appContext) }
    val activeSelection = remember(context) { FileActiveTemplateSelectionRepository(appContext).load() }
    val existingExam = remember(examId) { examId?.let(repository::load) }
    val editing = examId != null

    if (editing && existingExam == null) {
        Scaffold(
            topBar = {
                ProductTopBar(
                    title = "Sınavı Düzenle",
                    leadingText = "×",
                    onLeadingClick = onBack,
                    onActionClick = null
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Sınav kaydı bulunamadı.", color = MaterialTheme.colorScheme.error)
            }
        }
        return
    }

    val initialTemplate = remember(existingExam, options, activeSelection) {
        existingExam?.let { exam ->
            options.firstOrNull { it.selection == exam.templateSelection }
                ?: ExamTemplateOption("Kayıtlı Optik Form · v${exam.templateSelection.templateVersion}", exam.templateSelection)
        } ?: options.firstOrNull { it.selection == activeSelection } ?: options.first()
    }

    var examName by remember(examId) { mutableStateOf(existingExam?.name.orEmpty()) }
    var schoolName by remember(examId) {
        mutableStateOf(existingExam?.schoolName ?: settingsRepository.load().schoolName)
    }
    var folderName by remember(examId) { mutableStateOf(existingExam?.folderName.orEmpty()) }
    var dateText by remember(examId) {
        mutableStateOf(existingExam?.let { formatExamDate(it.examDateEpochDay) } ?: todayText())
    }
    var selectedTemplate by remember(examId, initialTemplate) { mutableStateOf(initialTemplate) }
    var wrongPolicy by remember(examId) {
        mutableStateOf(existingExam?.wrongAnswerPolicy ?: WrongAnswerPolicy.KEEP_AS_IS)
    }
    var selectedClasses by remember(examId) { mutableStateOf(emptySet<String>()) }
    var selectedStudentNumbers by remember(examId) {
        mutableStateOf(existingExam?.participants?.map { it.studentNumber }?.toSet().orEmpty())
    }
    var bookletCount by remember(examId) { mutableStateOf(existingExam?.bookletCount ?: 1) }
    var personalizedFormsEnabled by remember(examId) {
        mutableStateOf(existingExam?.personalizedFormsEnabled ?: false)
    }
    var templateMenuOpen by remember { mutableStateOf(false) }
    var wrongMenuOpen by remember { mutableStateOf(false) }
    var classMenuOpen by remember { mutableStateOf(false) }
    var studentMenuOpen by remember { mutableStateOf(false) }
    var bookletMenuOpen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val rosterParticipants = roster.filter { student ->
        student.className in selectedClasses || student.studentNumber in selectedStudentNumbers
    }.map { student ->
        ExamParticipant(
            studentNumber = student.studentNumber,
            studentName = student.fullName,
            className = student.className
        )
    }
    val rosterNumbers = roster.map { it.studentNumber }.toSet()
    val preservedMissingParticipants = existingExam?.participants.orEmpty().filter { participant ->
        participant.studentNumber in selectedStudentNumbers && participant.studentNumber !in rosterNumbers
    }
    val selectedParticipants = (rosterParticipants + preservedMissingParticipants)
        .map(ExamParticipant::normalized)
        .distinctBy { it.studentNumber }
    val designerBackedForm = selectedTemplate.selection.source == ActiveTemplateSource.DESIGNER_DOCUMENT

    val saveExam = {
        val parsedDate = parseExamDate(dateText)
        when {
            examName.isBlank() -> status = "Sınav adı zorunludur."
            schoolName.isBlank() -> status = "Okul alanı zorunludur. Ayarlar bölümünden okul adını kaydedebilirsiniz."
            parsedDate == null -> status = "Tarih GG.AA.YYYY biçiminde olmalıdır."
            personalizedFormsEnabled && selectedParticipants.isEmpty() ->
                status = "Öğrenciye özel form için en az bir sınıf veya öğrenci seçin."
            personalizedFormsEnabled && !designerBackedForm ->
                status = "Öğrenciye özel form için Form Editörü ile oluşturulmuş bir optik form seçin."
            else -> {
                runCatching {
                    val exam = existingExam?.copy(
                        name = examName.trim(),
                        schoolName = schoolName.trim(),
                        wrongAnswerPolicy = wrongPolicy,
                        folderName = folderName.trim(),
                        examDateEpochDay = parsedDate.toEpochDay(),
                        participants = selectedParticipants,
                        bookletCount = bookletCount,
                        personalizedFormsEnabled = personalizedFormsEnabled
                    ) ?: ExamFactory.create(
                        name = examName,
                        schoolName = schoolName,
                        templateSelection = selectedTemplate.selection,
                        examDateEpochDay = parsedDate.toEpochDay(),
                        wrongAnswerPolicy = wrongPolicy,
                        folderName = folderName,
                        participants = selectedParticipants,
                        bookletCount = bookletCount,
                        personalizedFormsEnabled = personalizedFormsEnabled
                    )
                    repository.save(exam)
                    exam
                }.onSuccess { exam ->
                    onSaved(exam.id)
                }.onFailure { error ->
                    status = "Sınav kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }
        Unit
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ProductTopBar(
                title = if (editing) "Sınavı Düzenle" else "Yeni Sınav",
                leadingText = "×",
                onLeadingClick = onBack,
                actionText = "Kaydet",
                onActionClick = saveExam
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Sınav Bilgileri",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (editing) {
                            "Sınav bilgilerini güncelleyebilirsiniz. Optik form sürümü, okunmuş kağıtlarla uyumu korumak için değiştirilemez."
                        } else {
                            "Okul adı Ayarlar bölümündeki kurum bilgisinden otomatik gelir; bu sınav için ayrıca değiştirilebilir."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    RoundedExamField(
                        value = examName,
                        onValueChange = { examName = it },
                        label = "Sınav Adı *",
                        prefix = "✎"
                    )
                    RoundedExamField(
                        value = schoolName,
                        onValueChange = { schoolName = it },
                        label = "Okul *",
                        prefix = "⌂"
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !editing,
                            onClick = { templateMenuOpen = true },
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text("Optik Form *", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    selectedTemplate.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = templateMenuOpen && !editing,
                            onDismissRequest = { templateMenuOpen = false }
                        ) {
                            options.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            option.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    onClick = {
                                        selectedTemplate = option
                                        if (option.selection.source != ActiveTemplateSource.DESIGNER_DOCUMENT) {
                                            personalizedFormsEnabled = false
                                        }
                                        templateMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { wrongMenuOpen = true },
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text("Yanlış Cevaplar", style = MaterialTheme.typography.labelSmall)
                                Text(wrongPolicyLabel(wrongPolicy), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        DropdownMenu(
                            expanded = wrongMenuOpen,
                            onDismissRequest = { wrongMenuOpen = false }
                        ) {
                            WrongAnswerPolicy.entries.forEach { policy ->
                                DropdownMenuItem(
                                    text = { Text(wrongPolicyLabel(policy)) },
                                    onClick = {
                                        wrongPolicy = policy
                                        wrongMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { bookletMenuOpen = true },
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text("Kitapçık Sayısı", style = MaterialTheme.typography.labelSmall)
                                Text("$bookletCount kitapçık", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        DropdownMenu(
                            expanded = bookletMenuOpen,
                            onDismissRequest = { bookletMenuOpen = false }
                        ) {
                            (1..8).forEach { count ->
                                DropdownMenuItem(
                                    text = { Text("$count kitapçık") },
                                    onClick = {
                                        bookletCount = count
                                        bookletMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    RoundedExamField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        label = "Sınav Klasörü",
                        prefix = "□"
                    )
                    RoundedExamField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = "Sınav Tarihi",
                        prefix = "▣"
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Sınava Girecek Öğrenciler",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Sınıfları toplu seçebilir veya öğrencileri tek tek ekleyebilirsiniz. Aynı öğrenci yalnız bir kez sınava eklenir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { classMenuOpen = true },
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text("Toplu Sınıf Seçimi", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    if (selectedClasses.isEmpty()) "Sınıf seçin" else selectedClasses.sorted().joinToString(", "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = classMenuOpen,
                            onDismissRequest = { classMenuOpen = false }
                        ) {
                            if (classNames.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Önce Öğrenciler bölümünden öğrenci içe aktarın") },
                                    enabled = false,
                                    onClick = {}
                                )
                            } else {
                                classNames.forEach { className ->
                                    val count = roster.count { it.className == className }
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = className in selectedClasses,
                                                    onCheckedChange = null
                                                )
                                                Text("$className · $count öğrenci")
                                            }
                                        },
                                        onClick = {
                                            selectedClasses = if (className in selectedClasses) {
                                                selectedClasses - className
                                            } else {
                                                selectedClasses + className
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { studentMenuOpen = true },
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text("Bireysel Öğrenci Seçimi", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    if (selectedStudentNumbers.isEmpty()) "Öğrenci seçin" else "${selectedStudentNumbers.size} öğrenci tek tek seçildi",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = studentMenuOpen,
                            onDismissRequest = { studentMenuOpen = false }
                        ) {
                            if (roster.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Önce Öğrenciler bölümünden öğrenci içe aktarın") },
                                    enabled = false,
                                    onClick = {}
                                )
                            } else {
                                roster.forEach { student ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = student.studentNumber in selectedStudentNumbers,
                                                    onCheckedChange = null
                                                )
                                                Column {
                                                    Text(
                                                        student.fullName,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        "${student.className} · No ${student.studentNumber}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedStudentNumbers = if (student.studentNumber in selectedStudentNumbers) {
                                                selectedStudentNumbers - student.studentNumber
                                            } else {
                                                selectedStudentNumbers + student.studentNumber
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (preservedMissingParticipants.isNotEmpty()) {
                        Text(
                            "${preservedMissingParticipants.size} kayıtlı katılımcı mevcut öğrenci listesinde bulunmuyor; sınav kaydı korunuyor.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        if (selectedParticipants.isEmpty()) {
                            "Katılımcı seçilmedi. Sınav serbest taramaya açık kalır."
                        } else {
                            "Toplam ${selectedParticipants.size} öğrenci sınava eklenecek."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Öğrenciye Özel Form", fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    !designerBackedForm -> "Form Editörü ile oluşturulmuş bir form seçildiğinde kullanılabilir."
                                    selectedParticipants.isEmpty() -> "Önce en az bir sınıf veya öğrenci seçin."
                                    else -> "Seçilen öğrenciler için ad, sınıf, numara, sınav ve okul bilgileriyle kişiselleştirilmiş form üretimini etkinleştirir."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = personalizedFormsEnabled,
                            onCheckedChange = { personalizedFormsEnabled = it },
                            enabled = designerBackedForm && selectedParticipants.isNotEmpty()
                        )
                    }
                }
            }

            if (status.isNotBlank()) {
                Text(
                    status,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RoundedExamField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    prefix: String
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Text(prefix) },
        singleLine = true,
        shape = RoundedCornerShape(18.dp)
    )
}

private fun loadExamTemplateOptions(context: android.content.Context): List<ExamTemplateOption> {
    val starter = DesignerStarterTemplates.all().map { document ->
        ExamTemplateOption(
            name = document.name,
            selection = ActiveTemplateSelection(
                source = ActiveTemplateSource.DESIGNER_DOCUMENT,
                templateId = document.id,
                templateVersion = document.version
            )
        )
    }
    val saved = FileDesignerDocumentRepository(context).list().map { document ->
        ExamTemplateOption(
            name = "${document.name} · Kayıtlı",
            selection = ActiveTemplateSelection(
                source = ActiveTemplateSource.DESIGNER_DOCUMENT,
                templateId = document.id,
                templateVersion = document.version
            )
        )
    }
    return (listOf(
        ExamTemplateOption(
            name = ActiveOmrTemplateDefaults.displayName,
            selection = ActiveOmrTemplateDefaults.selection
        )
    ) + starter + saved).distinctBy {
        Triple(it.selection.source, it.selection.templateId, it.selection.templateVersion)
    }
}

private val ExamDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("tr-TR"))

private fun todayText(): String = LocalDate.now().format(ExamDateFormatter)

private fun formatExamDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(ExamDateFormatter)

private fun parseExamDate(value: String): LocalDate? = try {
    LocalDate.parse(value.trim(), ExamDateFormatter)
} catch (_: DateTimeParseException) {
    null
}

private fun wrongPolicyLabel(policy: WrongAnswerPolicy): String = when (policy) {
    WrongAnswerPolicy.KEEP_AS_IS -> "Olduğu gibi bırak"
    WrongAnswerPolicy.FOUR_WRONG_ONE_CORRECT -> "4 yanlış 1 doğruyu götürsün"
    WrongAnswerPolicy.THREE_WRONG_ONE_CORRECT -> "3 yanlış 1 doğruyu götürsün"
}
