package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.exam.ExamFactory
import com.okulyonetim.optikokuyucu.exam.ExamParticipant
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.exam.WrongAnswerPolicy
import com.okulyonetim.optikokuyucu.omr.designer.DesignerExamMode
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateDefaults
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import com.okulyonetim.optikokuyucu.omr.template.FileActiveTemplateSelectionRepository
import com.okulyonetim.optikokuyucu.settings.AppSettingsRepository
import com.okulyonetim.optikokuyucu.settings.ReadyTemplateVisibilityRepository
import com.okulyonetim.optikokuyucu.student.FileStudentRosterRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private data class ExamTemplateOption(
    val name: String,
    val selection: ActiveTemplateSelection,
    val examMode: DesignerExamMode = DesignerExamMode.UNSPECIFIED
)

@Composable
fun NewExamScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val feedback = LocalAppFeedback.current
    val appContext = context.applicationContext
    val repository = remember(context) { FileExamRepository(appContext) }
    val settingsRepository = remember(context) { AppSettingsRepository(appContext) }
    val roster = remember(context) { FileStudentRosterRepository(appContext).list() }
    val classNames = remember(roster) { roster.map { it.className }.distinct().sorted() }
    var options by remember(context) { mutableStateOf(loadExamTemplateOptions(appContext)) }
    val activeSelection = remember(context) { FileActiveTemplateSelectionRepository(appContext).load() }

    var examName by remember { mutableStateOf("") }
    var schoolName by remember { mutableStateOf(settingsRepository.load().schoolName) }
    var subjectName by remember { mutableStateOf("") }
    var folderName by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(todayText()) }
    var selectedTemplate by remember {
        mutableStateOf(
            options.firstOrNull { it.selection == activeSelection }
                ?: options.firstOrNull {
                    it.selection.source == activeSelection.source &&
                        it.selection.templateId == activeSelection.templateId
                }
                ?: options.first()
        )
    }
    var wrongPolicy by remember { mutableStateOf(WrongAnswerPolicy.KEEP_AS_IS) }
    var selectedClasses by remember { mutableStateOf(emptySet<String>()) }
    var selectedStudentKeys by remember { mutableStateOf(emptySet<String>()) }
    var bookletCount by remember { mutableStateOf(1) }
    var personalizedFormsEnabled by remember { mutableStateOf(false) }
    var templateMenuOpen by remember { mutableStateOf(false) }
    var wrongMenuOpen by remember { mutableStateOf(false) }
    var classMenuOpen by remember { mutableStateOf(false) }
    var studentMenuOpen by remember { mutableStateOf(false) }
    var bookletMenuOpen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    fun refreshTemplateOptions() {
        val refreshed = loadExamTemplateOptions(appContext)
        if (refreshed.isEmpty()) return
        val current = selectedTemplate.selection
        options = refreshed
        selectedTemplate = refreshed.firstOrNull { it.selection == current }
            ?: refreshed.firstOrNull {
                it.selection.source == current.source &&
                    it.selection.templateId == current.templateId
            }
            ?: refreshed.first()
    }

    val selectedParticipants = roster.filter { student ->
        student.className in selectedClasses || student.identityKey in selectedStudentKeys
    }
    val designerBackedForm = selectedTemplate.selection.source == ActiveTemplateSource.DESIGNER_DOCUMENT
    val singleLessonExam = selectedTemplate.examMode == DesignerExamMode.SINGLE_LESSON

    fun warn(message: String) {
        status = message
        feedback.warning(message)
    }

    val saveExam = {
        val parsedDate = parseExamDate(dateText)
        when {
            examName.isBlank() -> warn("Sınav adı zorunludur.")
            schoolName.isBlank() -> warn("Okul alanı zorunludur. Ayarlar bölümünden okul adını kaydedebilirsiniz.")
            singleLessonExam && subjectName.isBlank() -> warn("Tek ders sınavı için ders adı zorunludur.")
            parsedDate == null -> warn("Tarih GG.AA.YYYY biçiminde olmalıdır.")
            personalizedFormsEnabled && selectedParticipants.isEmpty() ->
                warn("Öğrenciye özel form için en az bir sınıf veya öğrenci seçin.")
            personalizedFormsEnabled && !designerBackedForm ->
                warn("Öğrenciye özel form için Form Editörü ile oluşturulmuş bir optik form seçin.")
            else -> {
                runCatching {
                    ExamFactory.create(
                        name = examName,
                        schoolName = schoolName,
                        templateSelection = selectedTemplate.selection,
                        examDateEpochDay = parsedDate.toEpochDay(),
                        subjectName = if (singleLessonExam) subjectName else "",
                        wrongAnswerPolicy = wrongPolicy,
                        folderName = folderName,
                        participants = selectedParticipants.map { student ->
                            ExamParticipant(
                                studentNumber = student.studentNumber,
                                studentName = student.fullName,
                                className = student.className
                            )
                        },
                        bookletCount = bookletCount,
                        personalizedFormsEnabled = personalizedFormsEnabled
                    ).also(repository::save)
                }.onSuccess { exam ->
                    feedback.success("Sınav kaydedildi.")
                    onSaved(exam.id)
                }.onFailure { error ->
                    status = "Sınav kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
                    feedback.error(status)
                }
            }
        }
        Unit
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ProductTopBar(
                title = "Yeni Sınav",
                leadingText = "‹",
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
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProductSettingsSection(
                title = "Sınav Bilgileri",
                description = "Okul adı ayarlardan otomatik gelir. Form ve değerlendirme seçeneklerini bu sınav için belirleyin."
            ) {
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
                    ExamSelectField(
                        label = "Optik Form *",
                        value = selectedTemplate.name,
                        symbol = "F",
                        onClick = {
                            refreshTemplateOptions()
                            templateMenuOpen = true
                        }
                    )
                    DropdownMenu(
                        modifier = Modifier.heightIn(max = 320.dp),
                        expanded = templateMenuOpen,
                        onDismissRequest = { templateMenuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        option.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    selectedTemplate = option
                                    if (option.examMode != DesignerExamMode.SINGLE_LESSON) subjectName = ""
                                    if (option.selection.source != ActiveTemplateSource.DESIGNER_DOCUMENT) {
                                        personalizedFormsEnabled = false
                                    }
                                    templateMenuOpen = false
                                }
                            )
                        }
                    }
                }

                if (singleLessonExam) {
                    RoundedExamField(
                        value = subjectName,
                        onValueChange = {
                            subjectName = it
                            if (status == "Tek ders sınavı için ders adı zorunludur.") status = ""
                        },
                        label = "Ders Adı *",
                        prefix = "D"
                    )
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    ExamSelectField(
                        label = "Yanlış Cevaplar",
                        value = wrongPolicyLabel(wrongPolicy),
                        symbol = "✓",
                        onClick = { wrongMenuOpen = true }
                    )
                    DropdownMenu(
                        expanded = wrongMenuOpen,
                        onDismissRequest = { wrongMenuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        WrongAnswerPolicy.entries.forEach { policy ->
                            DropdownMenuItem(
                                text = { Text(wrongPolicyLabel(policy), style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    wrongPolicy = policy
                                    wrongMenuOpen = false
                                }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    ExamSelectField(
                        label = "Kitapçık Sayısı",
                        value = "$bookletCount kitapçık",
                        symbol = "K",
                        onClick = { bookletMenuOpen = true }
                    )
                    DropdownMenu(
                        expanded = bookletMenuOpen,
                        onDismissRequest = { bookletMenuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        (1..8).forEach { count ->
                            DropdownMenuItem(
                                text = { Text("$count kitapçık", style = MaterialTheme.typography.bodySmall) },
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

            ProductSettingsSection(
                title = "Sınava Girecek Öğrenciler",
                description = "Sınıfları toplu seçin veya yalnız istediğiniz öğrencileri ekleyin."
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    ExamSelectField(
                        label = "Toplu Sınıf Seçimi",
                        value = if (selectedClasses.isEmpty()) "Sınıf seçin" else selectedClasses.sorted().joinToString(", "),
                        symbol = "S",
                        onClick = { classMenuOpen = true }
                    )
                    DropdownMenu(
                        modifier = Modifier.heightIn(max = 320.dp),
                        expanded = classMenuOpen,
                        onDismissRequest = { classMenuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surface
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
                                            Text("$className · $count öğrenci", style = MaterialTheme.typography.bodySmall)
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
                    ExamSelectField(
                        label = "Bireysel Öğrenci Seçimi",
                        value = if (selectedStudentKeys.isEmpty()) "Öğrenci seçin" else "${selectedStudentKeys.size} öğrenci seçildi",
                        symbol = "Ö",
                        onClick = { studentMenuOpen = true }
                    )
                    DropdownMenu(
                        modifier = Modifier.heightIn(max = 360.dp),
                        expanded = studentMenuOpen,
                        onDismissRequest = { studentMenuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surface
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
                                                checked = student.identityKey in selectedStudentKeys,
                                                onCheckedChange = null
                                            )
                                            Column {
                                                Text(
                                                    student.fullName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    buildString {
                                                        if (student.schoolName.isNotBlank()) append(student.schoolName).append(" · ")
                                                        append(student.className).append(" · No ").append(student.studentNumber)
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedStudentKeys = if (student.identityKey in selectedStudentKeys) {
                                            selectedStudentKeys - student.identityKey
                                        } else {
                                            selectedStudentKeys + student.identityKey
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (selectedParticipants.isEmpty()) {
                            "Katılımcı seçilmedi · serbest tarama"
                        } else {
                            "${selectedParticipants.size} öğrenci sınava eklenecek"
                        },
                        modifier = Modifier.weight(1f),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ProductStatusBadge(
                        text = if (selectedParticipants.isEmpty()) "SERBEST" else "${selectedParticipants.size} ÖĞRENCİ",
                        tone = if (selectedParticipants.isEmpty()) ProductBadgeTone.NEUTRAL else ProductBadgeTone.GREEN
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text("Öğrenciye Özel Form", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                !designerBackedForm -> "Form Editörü ile oluşturulmuş bir form seçin."
                                selectedParticipants.isEmpty() -> "Önce sınıf veya öğrenci seçin."
                                else -> "Öğrenci bilgileri forma otomatik işlenir."
                            },
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = personalizedFormsEnabled,
                        onCheckedChange = { personalizedFormsEnabled = it },
                        enabled = designerBackedForm && selectedParticipants.isNotEmpty()
                    )
                }
            }

            if (status.isNotBlank()) {
                ProductCompactCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProductStatusBadge("UYARI", ProductBadgeTone.RED)
                        Text(
                            status,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamSelectField(
    label: String,
    value: String,
    symbol: String,
    onClick: () -> Unit
) {
    ProductCompactCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductInitialBadge(symbol)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    label,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("⌄", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
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
        label = { Text(label, fontSize = 11.sp) },
        leadingIcon = { Text(prefix, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(14.dp)
    )
}

private fun loadExamTemplateOptions(context: android.content.Context): List<ExamTemplateOption> {
    val hiddenReadyKeys = ReadyTemplateVisibilityRepository(context).hiddenKeys()
    val visibleStarters = DesignerStarterTemplates.all().filterNot { document ->
        ReadyTemplateVisibilityRepository.starterKey(document.id, document.version) in hiddenReadyKeys
    }
    val savedDocuments = FileDesignerDocumentRepository(context).list()

    val latestDesignerDocuments = (visibleStarters + savedDocuments)
        .groupBy { it.id }
        .values
        .mapNotNull { versions -> versions.maxByOrNull { it.version } }
        .sortedBy { it.name.lowercase(Locale.forLanguageTag("tr-TR")) }

    val designerOptions = latestDesignerDocuments.map { document ->
        ExamTemplateOption(
            name = document.name,
            selection = ActiveTemplateSelection(
                source = ActiveTemplateSource.DESIGNER_DOCUMENT,
                templateId = document.id,
                templateVersion = document.version
            ),
            examMode = document.formSpec.examMode
        )
    }

    val defaultKey = ReadyTemplateVisibilityRepository.defaultKey(
        ActiveOmrTemplateDefaults.selection.templateId,
        ActiveOmrTemplateDefaults.selection.templateVersion
    )
    val defaultOptions = if (defaultKey !in hiddenReadyKeys) {
        listOf(
            ExamTemplateOption(
                name = ActiveOmrTemplateDefaults.displayName,
                selection = ActiveOmrTemplateDefaults.selection
            )
        )
    } else {
        emptyList()
    }

    return (defaultOptions + designerOptions).ifEmpty {
        listOf(
            ExamTemplateOption(
                name = ActiveOmrTemplateDefaults.displayName,
                selection = ActiveOmrTemplateDefaults.selection
            )
        )
    }
}

private val ExamDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("tr-TR"))

private fun todayText(): String = LocalDate.now().format(ExamDateFormatter)

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
