package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.exam.Exam
import com.okulyonetim.optikokuyucu.exam.ExamFactory
import com.okulyonetim.optikokuyucu.exam.ExamParticipant
import com.okulyonetim.optikokuyucu.exam.ExamScoreMode
import com.okulyonetim.optikokuyucu.exam.ExamScoringConfiguration
import com.okulyonetim.optikokuyucu.exam.ExamScoringType
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.exam.WrongAnswerPolicy
import com.okulyonetim.optikokuyucu.omr.designer.DesignerExamMode
import com.okulyonetim.optikokuyucu.omr.designer.DesignerExamPreset
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.designer.QuestionGroupComponent
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
    val examMode: DesignerExamMode = DesignerExamMode.UNSPECIFIED,
    val examPreset: DesignerExamPreset = DesignerExamPreset.CUSTOM
)

private data class ScoringLessonOption(
    val id: String,
    val name: String
)

private enum class ExamStructureMode(val title: String, val description: String) {
    SINGLE("Tek Ders", "Bir derse ait soru ve sonuçlar"),
    MULTI("Çoklu Ders", "Birden fazla ders / deneme sınavı")
}

@Composable
fun NewExamScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    examId: String? = null
) {
    val context = LocalContext.current
    val feedback = LocalAppFeedback.current
    val appContext = context.applicationContext
    val repository = remember(context) { FileExamRepository(appContext) }
    val settingsRepository = remember(context) { AppSettingsRepository(appContext) }
    val roster = remember(context) { FileStudentRosterRepository(appContext).list() }
    val classNames = remember(roster) { roster.map { it.className }.distinct().sorted() }
    val existingExam = remember(examId) { examId?.let(repository::load) }
    var options by remember(context) { mutableStateOf(loadExamTemplateOptions(appContext)) }
    val activeSelection = remember(context) { FileActiveTemplateSelectionRepository(appContext).load() }

    val initialTemplate = remember(existingExam, options, activeSelection) {
        existingExam?.let { exam ->
            options.firstOrNull { it.selection == exam.templateSelection }
                ?: options.firstOrNull {
                    it.selection.source == exam.templateSelection.source &&
                        it.selection.templateId == exam.templateSelection.templateId
                }
        } ?: options.firstOrNull { it.selection == activeSelection }
            ?: options.firstOrNull {
                it.selection.source == activeSelection.source &&
                    it.selection.templateId == activeSelection.templateId
            }
            ?: options.first()
    }
    val initialMode = remember(existingExam, initialTemplate) {
        when {
            existingExam?.scoringConfiguration?.type == ExamScoringType.SINGLE_SUBJECT -> ExamStructureMode.SINGLE
            existingExam?.subjectName?.isNotBlank() == true -> ExamStructureMode.SINGLE
            initialTemplate.examMode == DesignerExamMode.SINGLE_LESSON -> ExamStructureMode.SINGLE
            else -> ExamStructureMode.MULTI
        }
    }

    var structureMode by remember { mutableStateOf(initialMode) }
    var examName by remember { mutableStateOf(existingExam?.name.orEmpty()) }
    var schoolName by remember { mutableStateOf(existingExam?.schoolName ?: settingsRepository.load().schoolName) }
    var subjectName by remember { mutableStateOf(existingExam?.subjectName.orEmpty()) }
    var folderName by remember { mutableStateOf(existingExam?.folderName.orEmpty()) }
    var dateText by remember { mutableStateOf(existingExam?.let { formatExamEditorDate(it.examDateEpochDay) } ?: todayText()) }
    var selectedTemplate by remember { mutableStateOf(initialTemplate) }

    val initialScoringType = existingExam?.scoringConfiguration?.type ?: defaultScoringTypeFor(initialTemplate, initialMode)
    var scoringType by remember { mutableStateOf(initialScoringType) }
    var scoreMode by remember { mutableStateOf(existingExam?.scoringConfiguration?.scoreMode ?: ExamScoreMode.SCALED) }
    var minimumScoreText by remember {
        mutableStateOf(
            existingExam?.scoringConfiguration?.minimumScore?.let(::editableNumber)
                ?: if (isOfficialMebType(initialScoringType)) "100" else "0"
        )
    }
    var maximumScoreText by remember {
        mutableStateOf(
            existingExam?.scoringConfiguration?.maximumScore?.let(::editableNumber)
                ?: if (isOfficialMebType(initialScoringType)) "500" else "100"
        )
    }
    var customWrongDivisorText by remember {
        mutableStateOf(existingExam?.scoringConfiguration?.customWrongAnswerDivisor?.let(::editableNumber).orEmpty())
    }
    var customLessonWeightTexts by remember {
        mutableStateOf(existingExam?.scoringConfiguration?.lessonWeights?.mapValues { editableNumber(it.value) }.orEmpty())
    }
    var wrongPolicy by remember {
        mutableStateOf(
            existingExam?.wrongAnswerPolicy
                ?: if (isOfficialMebType(initialScoringType)) WrongAnswerPolicy.THREE_WRONG_ONE_CORRECT
                else WrongAnswerPolicy.KEEP_AS_IS
        )
    }
    var selectedClasses by remember { mutableStateOf(emptySet<String>()) }
    var selectedStudentKeys by remember {
        mutableStateOf(
            existingExam?.participants
                ?.map { it.identityKey }
                ?.toSet()
                .orEmpty()
        )
    }
    var bookletCount by remember { mutableStateOf(existingExam?.bookletCount ?: 1) }
    var personalizedFormsEnabled by remember { mutableStateOf(existingExam?.personalizedFormsEnabled ?: false) }

    var templateMenuOpen by remember { mutableStateOf(false) }
    var scoringTypeMenuOpen by remember { mutableStateOf(false) }
    var scoreModeMenuOpen by remember { mutableStateOf(false) }
    var wrongMenuOpen by remember { mutableStateOf(false) }
    var classMenuOpen by remember { mutableStateOf(false) }
    var studentMenuOpen by remember { mutableStateOf(false) }
    var bookletMenuOpen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val templateLocked = existingExam?.papers?.isNotEmpty() == true
    val compatibleOptions = remember(options, structureMode) {
        options.filter { option -> templateCompatible(option.examMode, structureMode) }
    }.ifEmpty { options.filter { it.examMode == DesignerExamMode.UNSPECIFIED }.ifEmpty { options } }

    fun applyScoringType(nextType: ExamScoringType) {
        val previousWasOfficial = isOfficialMebType(scoringType)
        scoringType = nextType
        if (isOfficialMebType(nextType)) {
            scoreMode = ExamScoreMode.SCALED
            minimumScoreText = "100"
            maximumScoreText = "500"
            wrongPolicy = WrongAnswerPolicy.THREE_WRONG_ONE_CORRECT
            customWrongDivisorText = ""
        } else {
            if (previousWasOfficial) {
                scoreMode = ExamScoreMode.SCALED
                minimumScoreText = "0"
                maximumScoreText = "100"
                wrongPolicy = WrongAnswerPolicy.KEEP_AS_IS
            }
            if (nextType != ExamScoringType.CUSTOM) customWrongDivisorText = ""
        }
    }

    fun setStructureMode(next: ExamStructureMode) {
        if (templateLocked || structureMode == next) return
        structureMode = next
        if (next == ExamStructureMode.MULTI) subjectName = ""
        val nextCompatible = options.filter { templateCompatible(it.examMode, next) }
        if (selectedTemplate !in nextCompatible && nextCompatible.isNotEmpty()) {
            selectedTemplate = nextCompatible.first()
        }
        val suggested = defaultScoringTypeFor(selectedTemplate, next)
        if (next == ExamStructureMode.SINGLE) {
            applyScoringType(if (suggested == ExamScoringType.CUSTOM) suggested else ExamScoringType.SINGLE_SUBJECT)
        } else if (scoringType == ExamScoringType.SINGLE_SUBJECT) {
            applyScoringType(suggested.takeUnless { it == ExamScoringType.SINGLE_SUBJECT } ?: ExamScoringType.NORMAL)
        }
        personalizedFormsEnabled = personalizedFormsEnabled &&
            selectedTemplate.selection.source == ActiveTemplateSource.DESIGNER_DOCUMENT
    }

    fun refreshTemplateOptions() {
        val refreshed = loadExamTemplateOptions(appContext)
        if (refreshed.isEmpty()) return
        options = refreshed
        val allowed = refreshed.filter { templateCompatible(it.examMode, structureMode) }.ifEmpty { refreshed }
        selectedTemplate = allowed.firstOrNull { it.selection == selectedTemplate.selection }
            ?: allowed.firstOrNull {
                it.selection.source == selectedTemplate.selection.source &&
                    it.selection.templateId == selectedTemplate.selection.templateId
            }
            ?: allowed.first()
    }

    val selectedParticipants = roster.filter { student ->
        student.className in selectedClasses || student.identityKey in selectedStudentKeys
    }
    val designerBackedForm = selectedTemplate.selection.source == ActiveTemplateSource.DESIGNER_DOCUMENT
    val singleSubjectExam = structureMode == ExamStructureMode.SINGLE
    val officialMebScoring = isOfficialMebType(scoringType)
    val customLessonOptions = remember(selectedTemplate.selection) {
        loadScoringLessonOptions(appContext, selectedTemplate.selection)
    }
    val allowedScoringTypes = if (singleSubjectExam) {
        listOf(ExamScoringType.SINGLE_SUBJECT, ExamScoringType.NORMAL, ExamScoringType.CUSTOM)
    } else {
        listOf(ExamScoringType.NORMAL, ExamScoringType.LGS, ExamScoringType.IOKBS, ExamScoringType.CUSTOM)
    }

    fun warn(message: String) {
        status = message
        feedback.warning(message)
    }

    val saveExam = {
        val parsedDate = parseExamDate(dateText)
        val minimumScore = if (officialMebScoring) 100.0 else parseScoreNumber(minimumScoreText)
        val maximumScore = if (officialMebScoring) 500.0 else parseScoreNumber(maximumScoreText)
        val customDivisor = customWrongDivisorText.takeIf { it.isNotBlank() }?.let(::parseScoreNumber)
        val scaledScore = scoreMode == ExamScoreMode.SCALED
        val parsedLessonWeights = if (scoringType == ExamScoringType.CUSTOM && scaledScore) {
            customLessonOptions.map { option ->
                option.id to parseScoreNumber(customLessonWeightTexts[option.id] ?: "1")
            }
        } else emptyList()
        val invalidLessonWeight = parsedLessonWeights.any { (_, weight) -> weight == null || weight <= 0.0 }

        when {
            examName.isBlank() -> warn("Sınav adı zorunludur.")
            schoolName.isBlank() -> warn("Okul alanı zorunludur.")
            singleSubjectExam && subjectName.isBlank() -> warn("Tek ders sınavı için ders adı zorunludur.")
            parsedDate == null -> warn("Tarih GG.AA.YYYY biçiminde olmalıdır.")
            scaledScore && !officialMebScoring && minimumScore == null -> warn("Taban puan geçerli bir sayı olmalıdır.")
            scaledScore && !officialMebScoring && maximumScore == null -> warn("Tavan puan geçerli bir sayı olmalıdır.")
            scaledScore && !officialMebScoring && minimumScore != null && maximumScore != null && maximumScore <= minimumScore ->
                warn("Tavan puan taban puandan büyük olmalıdır.")
            scoringType == ExamScoringType.CUSTOM && customWrongDivisorText.isNotBlank() &&
                (customDivisor == null || customDivisor <= 0.0) -> warn("Özel yanlış oranı sıfırdan büyük olmalıdır.")
            invalidLessonWeight -> warn("Ders ağırlıkları sıfırdan büyük geçerli sayılar olmalıdır.")
            personalizedFormsEnabled && selectedParticipants.isEmpty() ->
                warn("Öğrenciye özel form için en az bir sınıf veya öğrenci seçin.")
            personalizedFormsEnabled && !designerBackedForm ->
                warn("Öğrenciye özel form için Form Editörü ile oluşturulmuş bir optik form seçin.")
            else -> {
                runCatching {
                    val scoringConfiguration = if (officialMebScoring) {
                        ExamScoringConfiguration.forType(scoringType)
                    } else {
                        ExamScoringConfiguration(
                            type = scoringType,
                            scoreMode = scoreMode,
                            minimumScore = if (scaledScore) requireNotNull(minimumScore) else 0.0,
                            maximumScore = if (scaledScore) requireNotNull(maximumScore) else 100.0,
                            customWrongAnswerDivisor = if (scoringType == ExamScoringType.CUSTOM) customDivisor else null,
                            lessonWeights = if (scoringType == ExamScoringType.CUSTOM && scaledScore) {
                                parsedLessonWeights.associate { (id, weight) -> id to requireNotNull(weight) }
                            } else emptyMap()
                        )
                    }
                    val participants = selectedParticipants.map { student ->
                        ExamParticipant(
                            studentNumber = student.studentNumber,
                            studentName = student.fullName,
                            className = student.className
                        )
                    }
                    val saved = existingExam?.copy(
                        name = examName.trim(),
                        schoolName = schoolName.trim(),
                        templateSelection = selectedTemplate.selection,
                        subjectName = if (singleSubjectExam) subjectName.trim() else "",
                        wrongAnswerPolicy = if (officialMebScoring) WrongAnswerPolicy.THREE_WRONG_ONE_CORRECT else wrongPolicy,
                        folderName = folderName.trim(),
                        examDateEpochDay = requireNotNull(parsedDate).toEpochDay(),
                        participants = participants,
                        bookletCount = bookletCount,
                        personalizedFormsEnabled = personalizedFormsEnabled,
                        scoringConfiguration = scoringConfiguration
                    ) ?: ExamFactory.create(
                        name = examName,
                        schoolName = schoolName,
                        templateSelection = selectedTemplate.selection,
                        examDateEpochDay = requireNotNull(parsedDate).toEpochDay(),
                        subjectName = if (singleSubjectExam) subjectName else "",
                        wrongAnswerPolicy = if (officialMebScoring) WrongAnswerPolicy.THREE_WRONG_ONE_CORRECT else wrongPolicy,
                        folderName = folderName,
                        participants = participants,
                        bookletCount = bookletCount,
                        personalizedFormsEnabled = personalizedFormsEnabled,
                        scoringConfiguration = scoringConfiguration
                    )
                    repository.save(saved)
                    saved
                }.onSuccess { exam ->
                    feedback.success(if (existingExam == null) "Sınav kaydedildi." else "Sınav güncellendi.")
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
                title = if (existingExam == null) "Yeni Sınav" else "Sınavı Düzenle",
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
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            ProductSettingsSection(
                title = "Temel Bilgiler",
                description = if (existingExam == null) "Sınavın adını, okulunu ve tarihini belirleyin." else "Sınav bilgilerini güncelleyin."
            ) {
                RoundedExamField(examName, { examName = it }, "Sınav Adı *", "✎")
                RoundedExamField(schoolName, { schoolName = it }, "Okul *", "⌂")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        RoundedExamField(dateText, { dateText = it }, "Tarih", "▣")
                    }
                    Box(Modifier.weight(1f)) {
                        RoundedExamField(folderName, { folderName = it }, "Klasör", "□")
                    }
                }
            }

            ProductSettingsSection(
                title = "Sınav Yapısı ve Optik Form",
                description = "Önce tek ders / çoklu ders seçin. Yalnız bu yapıya uygun optik formlar listelenir."
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StructureModeCard(
                        modifier = Modifier.weight(1f),
                        mode = ExamStructureMode.SINGLE,
                        selected = structureMode == ExamStructureMode.SINGLE,
                        enabled = !templateLocked,
                        onClick = { setStructureMode(ExamStructureMode.SINGLE) }
                    )
                    StructureModeCard(
                        modifier = Modifier.weight(1f),
                        mode = ExamStructureMode.MULTI,
                        selected = structureMode == ExamStructureMode.MULTI,
                        enabled = !templateLocked,
                        onClick = { setStructureMode(ExamStructureMode.MULTI) }
                    )
                }

                if (templateLocked) {
                    Text(
                        "Okunmuş kağıt bulunduğu için sınav yapısı ve optik form kilitli.",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    ExamSelectField(
                        label = "Optik Form * · ${compatibleOptions.size} uyumlu form",
                        value = selectedTemplate.name,
                        symbol = "F",
                        enabled = !templateLocked,
                        onClick = {
                            refreshTemplateOptions()
                            templateMenuOpen = true
                        }
                    )
                    DropdownMenu(
                        modifier = Modifier.heightIn(max = 340.dp),
                        expanded = templateMenuOpen,
                        onDismissRequest = { templateMenuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        compatibleOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(option.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            templateMeta(option),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedTemplate = option
                                    val suggested = defaultScoringTypeFor(option, structureMode)
                                    if (suggested in allowedScoringTypes) applyScoringType(suggested)
                                    if (option.selection.source != ActiveTemplateSource.DESIGNER_DOCUMENT) personalizedFormsEnabled = false
                                    templateMenuOpen = false
                                }
                            )
                        }
                    }
                }

                if (singleSubjectExam) {
                    RoundedExamField(subjectName, { subjectName = it }, "Ders Adı *", "D")
                } else {
                    Text(
                        "Çoklu ders sınavında dersler seçilen optik formdaki soru gruplarından otomatik alınır.",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    ExamSelectField("Kitapçık Sayısı", "$bookletCount kitapçık", "K") { bookletMenuOpen = true }
                    DropdownMenu(
                        expanded = bookletMenuOpen,
                        onDismissRequest = { bookletMenuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        (1..8).forEach { count ->
                            DropdownMenuItem(
                                text = { Text("$count kitapçık") },
                                onClick = { bookletCount = count; bookletMenuOpen = false }
                            )
                        }
                    }
                }
            }

            ProductSettingsSection(
                title = "Puanlama",
                description = if (singleSubjectExam) "Tek ders için net veya puanlama yöntemini belirleyin." else "Deneme sınavının değerlendirme yöntemini belirleyin."
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    ExamSelectField("Puanlama Türü", scoringTypeLabel(scoringType), "P") { scoringTypeMenuOpen = true }
                    DropdownMenu(
                        expanded = scoringTypeMenuOpen,
                        onDismissRequest = { scoringTypeMenuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        allowedScoringTypes.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(scoringTypeLabel(type), style = MaterialTheme.typography.bodySmall)
                                        Text(scoringTypeDescription(type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = { applyScoringType(type); scoringTypeMenuOpen = false }
                            )
                        }
                    }
                }

                if (officialMebScoring) {
                    ProductCompactCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("MEB puanlama kuralı", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                ProductStatusBadge("MEB 2026", ProductBadgeTone.GREEN)
                            }
                            Text(
                                "3 yanlış 1 doğruyu götürür · 100–500 ölçeği · standart puan ve ders katsayıları otomatik uygulanır.",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExamSelectField("Sonuç Gösterimi", scoreModeLabel(scoreMode), "#") { scoreModeMenuOpen = true }
                        DropdownMenu(expanded = scoreModeMenuOpen, onDismissRequest = { scoreModeMenuOpen = false }) {
                            ExamScoreMode.entries.forEach { mode ->
                                DropdownMenuItem(text = { Text(scoreModeLabel(mode)) }, onClick = { scoreMode = mode; scoreModeMenuOpen = false })
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExamSelectField("Yanlış Cevaplar", wrongPolicyLabel(wrongPolicy), "✓") { wrongMenuOpen = true }
                        DropdownMenu(expanded = wrongMenuOpen, onDismissRequest = { wrongMenuOpen = false }) {
                            WrongAnswerPolicy.entries.forEach { policy ->
                                DropdownMenuItem(text = { Text(wrongPolicyLabel(policy)) }, onClick = { wrongPolicy = policy; wrongMenuOpen = false })
                            }
                        }
                    }

                    if (scoringType == ExamScoringType.CUSTOM) {
                        RoundedExamField(customWrongDivisorText, { customWrongDivisorText = it }, "Özel Yanlış Oranı", "÷")
                        if (scoreMode == ExamScoreMode.SCALED && customLessonOptions.size > 1) {
                            ProductCompactCard(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Ders Ağırlıkları", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    customLessonOptions.forEach { lesson ->
                                        RoundedExamField(
                                            customLessonWeightTexts[lesson.id] ?: "1",
                                            { value -> customLessonWeightTexts = customLessonWeightTexts + (lesson.id to value) },
                                            "${lesson.name} Ağırlığı",
                                            "×"
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (scoreMode == ExamScoreMode.SCALED) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { RoundedExamField(minimumScoreText, { minimumScoreText = it }, "Taban Puan", "↓") }
                            Box(Modifier.weight(1f)) { RoundedExamField(maximumScoreText, { maximumScoreText = it }, "Tavan Puan", "↑") }
                        }
                    }
                }
            }

            ProductSettingsSection(
                title = "Sınava Girecek Öğrenciler",
                description = "Sınıfı toplu seçin veya bireysel öğrenci ekleyin. Seçim zorunlu değildir."
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    ExamSelectField(
                        "Toplu Sınıf Seçimi",
                        if (selectedClasses.isEmpty()) "Sınıf seçin" else selectedClasses.sorted().joinToString(", "),
                        "S"
                    ) { classMenuOpen = true }
                    DropdownMenu(
                        modifier = Modifier.heightIn(max = 320.dp),
                        expanded = classMenuOpen,
                        onDismissRequest = { classMenuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        if (classNames.isEmpty()) {
                            DropdownMenuItem(text = { Text("Önce öğrenci içe aktarın") }, enabled = false, onClick = {})
                        } else {
                            classNames.forEach { className ->
                                val count = roster.count { it.className == className }
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = className in selectedClasses, onCheckedChange = null)
                                            Text("$className · $count öğrenci", style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    onClick = {
                                        selectedClasses = if (className in selectedClasses) selectedClasses - className else selectedClasses + className
                                    }
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    ExamSelectField(
                        "Bireysel Öğrenci Seçimi",
                        if (selectedStudentKeys.isEmpty()) "Öğrenci seçin" else "${selectedStudentKeys.size} öğrenci seçildi",
                        "Ö"
                    ) { studentMenuOpen = true }
                    DropdownMenu(
                        modifier = Modifier.heightIn(max = 360.dp),
                        expanded = studentMenuOpen,
                        onDismissRequest = { studentMenuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        if (roster.isEmpty()) {
                            DropdownMenuItem(text = { Text("Önce öğrenci içe aktarın") }, enabled = false, onClick = {})
                        } else {
                            roster.forEach { student ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = student.identityKey in selectedStudentKeys, onCheckedChange = null)
                                            Column {
                                                Text(student.fullName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    "${student.className} · No ${student.studentNumber}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedStudentKeys = if (student.identityKey in selectedStudentKeys) {
                                            selectedStudentKeys - student.identityKey
                                        } else selectedStudentKeys + student.identityKey
                                    }
                                )
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (selectedParticipants.isEmpty()) "Katılımcı seçilmedi · serbest tarama" else "${selectedParticipants.size} öğrenci sınava eklenecek",
                        modifier = Modifier.weight(1f),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ProductStatusBadge(
                        if (selectedParticipants.isEmpty()) "SERBEST" else "${selectedParticipants.size} ÖĞRENCİ",
                        if (selectedParticipants.isEmpty()) ProductBadgeTone.NEUTRAL else ProductBadgeTone.GREEN
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Öğrenciye Özel Form", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                !designerBackedForm -> "Form Editörü ile oluşturulmuş bir form seçin."
                                selectedParticipants.isEmpty() -> "Önce sınıf veya öğrenci seçin."
                                else -> "Öğrenci bilgileri forma otomatik işlenir."
                            },
                            fontSize = 9.sp,
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

            if (status.isNotBlank()) {
                ProductCompactCard(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(11.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProductStatusBadge("UYARI", ProductBadgeTone.RED)
                        Text(status, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StructureModeCard(
    modifier: Modifier,
    mode: ExamStructureMode,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        color = background,
        contentColor = content,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(mode.title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text(mode.description, fontSize = 9.sp, color = content.copy(alpha = 0.72f), maxLines = 2)
        }
    }
}

@Composable
private fun ExamSelectField(
    label: String,
    value: String,
    symbol: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    ProductCompactCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (enabled) onClick else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductInitialBadge(symbol)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(if (enabled) "⌄" else "🔒", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
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

private fun templateCompatible(templateMode: DesignerExamMode, selected: ExamStructureMode): Boolean = when (templateMode) {
    DesignerExamMode.UNSPECIFIED -> true
    DesignerExamMode.SINGLE_LESSON -> selected == ExamStructureMode.SINGLE
    DesignerExamMode.MULTI_LESSON -> selected == ExamStructureMode.MULTI
}

private fun templateMeta(option: ExamTemplateOption): String {
    val mode = when (option.examMode) {
        DesignerExamMode.SINGLE_LESSON -> "Tek ders"
        DesignerExamMode.MULTI_LESSON -> "Çoklu ders"
        DesignerExamMode.UNSPECIFIED -> "Her iki yapıyla uyumlu"
    }
    val preset = option.examPreset.takeUnless { it == DesignerExamPreset.CUSTOM }?.displayName
    return listOfNotNull(mode, preset).joinToString(" · ")
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
            examMode = document.formSpec.examMode,
            examPreset = document.formSpec.examPreset
        )
    }

    val defaultKey = ReadyTemplateVisibilityRepository.defaultKey(
        ActiveOmrTemplateDefaults.selection.templateId,
        ActiveOmrTemplateDefaults.selection.templateVersion
    )
    val defaultOptions = if (defaultKey !in hiddenReadyKeys) {
        listOf(ExamTemplateOption(ActiveOmrTemplateDefaults.displayName, ActiveOmrTemplateDefaults.selection))
    } else emptyList()

    return (defaultOptions + designerOptions).ifEmpty {
        listOf(ExamTemplateOption(ActiveOmrTemplateDefaults.displayName, ActiveOmrTemplateDefaults.selection))
    }
}

private fun loadScoringLessonOptions(
    context: android.content.Context,
    selection: ActiveTemplateSelection
): List<ScoringLessonOption> {
    if (selection.source != ActiveTemplateSource.DESIGNER_DOCUMENT) return emptyList()
    val document = FileDesignerDocumentRepository(context).load(selection.templateId, selection.templateVersion)
        ?: DesignerStarterTemplates.all().firstOrNull {
            it.id == selection.templateId && it.version == selection.templateVersion
        }
        ?: return emptyList()
    val options = linkedMapOf<String, ScoringLessonOption>()
    document.components.filterIsInstance<QuestionGroupComponent>().forEach { component ->
        val id = component.questionIdPrefix.trim().ifBlank { "genel" }
        val rawLabel = component.label.trim()
        val generic = rawLabel.isBlank() || rawLabel.equals("Ders", ignoreCase = true) ||
            Regex("^Ders\\s+\\d+$", RegexOption.IGNORE_CASE).matches(rawLabel)
        val name = when {
            id == "genel" && generic -> "Genel"
            !generic -> rawLabel
            else -> humanizeScoringLessonId(id)
        }
        options.putIfAbsent(id, ScoringLessonOption(id, name))
    }
    return options.values.toList()
}

private fun humanizeScoringLessonId(id: String): String = id
    .replace('-', ' ')
    .replace('_', ' ')
    .split(' ')
    .filter { it.isNotBlank() }
    .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.forLanguageTag("tr-TR")) } }
    .ifBlank { id }

private fun defaultScoringTypeFor(option: ExamTemplateOption, structure: ExamStructureMode): ExamScoringType {
    if (structure == ExamStructureMode.SINGLE) return ExamScoringType.SINGLE_SUBJECT
    return when (option.examPreset) {
        DesignerExamPreset.LGS -> ExamScoringType.LGS
        DesignerExamPreset.SCHOLARSHIP -> ExamScoringType.IOKBS
        else -> ExamScoringType.NORMAL
    }
}

private fun isOfficialMebType(type: ExamScoringType): Boolean =
    type == ExamScoringType.LGS || type == ExamScoringType.IOKBS

private fun scoringTypeLabel(type: ExamScoringType): String = when (type) {
    ExamScoringType.NORMAL -> "Normal"
    ExamScoringType.SINGLE_SUBJECT -> "Tek Ders"
    ExamScoringType.LGS -> "LGS"
    ExamScoringType.IOKBS -> "İOKBS / Bursluluk"
    ExamScoringType.CUSTOM -> "Özel Puanlama"
}

private fun scoringTypeDescription(type: ExamScoringType): String = when (type) {
    ExamScoringType.NORMAL -> "Net veya seçilen puan aralığı"
    ExamScoringType.SINGLE_SUBJECT -> "Tek ders için net / puan"
    ExamScoringType.LGS -> "MEB yöntemi · 3 yanlış · 100–500"
    ExamScoringType.IOKBS -> "MEB yöntemi · 100–500"
    ExamScoringType.CUSTOM -> "Özel yanlış oranı, puan aralığı ve ders ağırlıkları"
}

private fun scoreModeLabel(mode: ExamScoreMode): String = when (mode) {
    ExamScoreMode.RAW_NET -> "Net olarak göster"
    ExamScoreMode.SCALED -> "Puan aralığına dönüştür"
}

private val ExamDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("tr-TR"))

private fun todayText(): String = LocalDate.now().format(ExamDateFormatter)

private fun formatExamEditorDate(epochDay: Long): String =
    runCatching { LocalDate.ofEpochDay(epochDay).format(ExamDateFormatter) }.getOrDefault(todayText())

private fun parseExamDate(value: String): LocalDate? = try {
    LocalDate.parse(value.trim(), ExamDateFormatter)
} catch (_: DateTimeParseException) {
    null
}

private fun parseScoreNumber(value: String): Double? =
    value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

private fun editableNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().replace('.', ',')

private fun wrongPolicyLabel(policy: WrongAnswerPolicy): String = when (policy) {
    WrongAnswerPolicy.KEEP_AS_IS -> "Olduğu gibi bırak"
    WrongAnswerPolicy.FOUR_WRONG_ONE_CORRECT -> "4 yanlış 1 doğruyu götürsün"
    WrongAnswerPolicy.THREE_WRONG_ONE_CORRECT -> "3 yanlış 1 doğruyu götürsün"
}
