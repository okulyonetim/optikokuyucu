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

private data class ExamLessonOption(
    val id: String,
    val label: String
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
    val initialScoringType = remember { defaultScoringType(selectedTemplate) }
    var scoringType by remember { mutableStateOf(initialScoringType) }
    var scoreMode by remember { mutableStateOf(ExamScoreMode.SCALED) }
    var minimumScoreText by remember {
        mutableStateOf(if (isOfficialMebType(initialScoringType)) "100" else "0")
    }
    var maximumScoreText by remember {
        mutableStateOf(if (isOfficialMebType(initialScoringType)) "500" else "100")
    }
    var customWrongDivisorText by remember { mutableStateOf("") }
    var customLessonWeightTexts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var wrongPolicy by remember {
        mutableStateOf(
            if (isOfficialMebType(initialScoringType)) {
                WrongAnswerPolicy.THREE_WRONG_ONE_CORRECT
            } else {
                WrongAnswerPolicy.KEEP_AS_IS
            }
        )
    }
    var selectedClasses by remember { mutableStateOf(emptySet<String>()) }
    var selectedStudentKeys by remember { mutableStateOf(emptySet<String>()) }
    var bookletCount by remember { mutableStateOf(1) }
    var personalizedFormsEnabled by remember { mutableStateOf(false) }
    var templateMenuOpen by remember { mutableStateOf(false) }
    var scoringTypeMenuOpen by remember { mutableStateOf(false) }
    var scoreModeMenuOpen by remember { mutableStateOf(false) }
    var wrongMenuOpen by remember { mutableStateOf(false) }
    var classMenuOpen by remember { mutableStateOf(false) }
    var studentMenuOpen by remember { mutableStateOf(false) }
    var bookletMenuOpen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    fun applyScoringType(nextType: ExamScoringType) {
        val previousWasOfficial = isOfficialMebType(scoringType)
        scoringType = nextType
        if (isOfficialMebType(nextType)) {
            scoreMode = ExamScoreMode.SCALED
            minimumScoreText = "100"
            maximumScoreText = "500"
            wrongPolicy = WrongAnswerPolicy.THREE_WRONG_ONE_CORRECT
            customWrongDivisorText = ""
            customLessonWeightTexts = emptyMap()
        } else {
            if (previousWasOfficial) {
                scoreMode = ExamScoreMode.SCALED
                minimumScoreText = "0"
                maximumScoreText = "100"
                wrongPolicy = WrongAnswerPolicy.KEEP_AS_IS
            }
            if (nextType != ExamScoringType.CUSTOM) {
                customWrongDivisorText = ""
                customLessonWeightTexts = emptyMap()
            }
        }
    }

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
    val singleSubjectExam = selectedTemplate.examMode == DesignerExamMode.SINGLE_LESSON ||
        scoringType == ExamScoringType.SINGLE_SUBJECT
    val officialMebScoring = isOfficialMebType(scoringType)
    val customLessonOptions = remember(selectedTemplate.selection) {
        loadExamLessonOptions(appContext, selectedTemplate.selection)
    }

    fun warn(message: String) {
        status = message
        feedback.warning(message)
    }

    val saveExam = {
        val parsedDate = parseExamDate(dateText)
        val minimumScore = if (officialMebScoring) 100.0 else parseScoreNumber(minimumScoreText)
        val maximumScore = if (officialMebScoring) 500.0 else parseScoreNumber(maximumScoreText)
        val customDivisor = customWrongDivisorText
            .takeIf { it.isNotBlank() }
            ?.let(::parseScoreNumber)
        val customLessonWeights = if (scoringType == ExamScoringType.CUSTOM) {
            customLessonOptions.associate { option ->
                option.id to (parseScoreNumber(customLessonWeightTexts[option.id] ?: "1") ?: Double.NaN)
            }
        } else {
            emptyMap()
        }
        val invalidLessonWeight = customLessonOptions.firstOrNull { option ->
            val value = customLessonWeights[option.id]
            scoringType == ExamScoringType.CUSTOM && (value == null || !value.isFinite() || value <= 0.0)
        }
        val scaledScore = scoreMode == ExamScoreMode.SCALED
        when {
            examName.isBlank() -> warn("Sınav adı zorunludur.")
            schoolName.isBlank() -> warn("Okul alanı zorunludur. Ayarlar bölümünden okul adını kaydedebilirsiniz.")
            singleSubjectExam && subjectName.isBlank() -> warn("Tek ders sınavı için ders adı zorunludur.")
            parsedDate == null -> warn("Tarih GG.AA.YYYY biçiminde olmalıdır.")
            scaledScore && !officialMebScoring && minimumScore == null ->
                warn("Taban puan geçerli bir sayı olmalıdır.")
            scaledScore && !officialMebScoring && maximumScore == null ->
                warn("Tavan puan geçerli bir sayı olmalıdır.")
            scaledScore && !officialMebScoring &&
                minimumScore != null && maximumScore != null && maximumScore <= minimumScore ->
                warn("Tavan puan taban puandan büyük olmalıdır.")
            scoringType == ExamScoringType.CUSTOM && customWrongDivisorText.isNotBlank() &&
                (customDivisor == null || customDivisor <= 0.0) ->
                warn("Özel yanlış oranı sıfırdan büyük bir sayı olmalıdır.")
            invalidLessonWeight != null ->
                warn("${invalidLessonWeight.label} ders ağırlığı sıfırdan büyük bir sayı olmalıdır.")
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
                            customWrongAnswerDivisor = if (scoringType == ExamScoringType.CUSTOM) {
                                customDivisor
                            } else {
                                null
                            },
                            lessonWeights = if (scoringType == ExamScoringType.CUSTOM) {
                                customLessonWeights
                            } else {
                                emptyMap()
                            }
                        )
                    }
                    ExamFactory.create(
                        name = examName,
                        schoolName = schoolName,
                        templateSelection = selectedTemplate.selection,
                        examDateEpochDay = parsedDate.toEpochDay(),
                        subjectName = if (singleSubjectExam) subjectName else "",
                        wrongAnswerPolicy = if (officialMebScoring) {
                            WrongAnswerPolicy.THREE_WRONG_ONE_CORRECT
                        } else {
                            wrongPolicy
                        },
                        folderName = folderName,
                        participants = selectedParticipants.map { student ->
                            ExamParticipant(
                                studentNumber = student.studentNumber,
                                studentName = student.fullName,
                                className = student.className
                            )
                        },
                        bookletCount = bookletCount,
                        personalizedFormsEnabled = personalizedFormsEnabled,
                        scoringConfiguration = scoringConfiguration
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
                                    customLessonWeightTexts = emptyMap()
                                    val suggestedType = defaultScoringType(option)
                                    applyScoringType(suggestedType)
                                    if (suggestedType != ExamScoringType.SINGLE_SUBJECT &&
                                        option.examMode != DesignerExamMode.SINGLE_LESSON
                                    ) {
                                        subjectName = ""
                                    }
                                    if (option.selection.source != ActiveTemplateSource.DESIGNER_DOCUMENT) {
                                        personalizedFormsEnabled = false
                                    }
                                    templateMenuOpen = false
                                }
                            )
                        }
                    }
                }

                if (singleSubjectExam) {
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
                        label = "Puanlama Türü",
                        value = scoringTypeLabel(scoringType),
                        symbol = "P",
                        onClick = { scoringTypeMenuOpen = true }
                    )
                    DropdownMenu(
                        expanded = scoringTypeMenuOpen,
                        onDismissRequest = { scoringTypeMenuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        ExamScoringType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(scoringTypeLabel(type), style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            scoringTypeDescription(type),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    applyScoringType(type)
                                    scoringTypeMenuOpen = false
                                }
                            )
                        }
                    }
                }

                if (officialMebScoring) {
                    ProductCompactCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "MEB puanlama kuralı",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                ProductStatusBadge("MEB 2026", ProductBadgeTone.GREEN)
                            }
                            Text(
                                "3 yanlış 1 doğruyu götürür · 100–500 ölçeği · standart puan ve ders katsayıları otomatik uygulanır.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Uygulamadaki sonuç yerel sınav grubunun istatistikleriyle MEB yöntemi kullanılarak hesaplanır; resmî ulusal sonuç değildir.",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExamSelectField(
                            label = "Sonuç Gösterimi",
                            value = scoreModeLabel(scoreMode),
                            symbol = "#",
                            onClick = { scoreModeMenuOpen = true }
                        )
                        DropdownMenu(
                            expanded = scoreModeMenuOpen,
                            onDismissRequest = { scoreModeMenuOpen = false },
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            ExamScoreMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(scoreModeLabel(mode), style = MaterialTheme.typography.bodySmall) },
                                    onClick = {
                                        scoreMode = mode
                                        scoreModeMenuOpen = false
                                    }
                                )
                            }
                        }
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

                    if (scoringType == ExamScoringType.CUSTOM) {
                        RoundedExamField(
                            value = customWrongDivisorText,
                            onValueChange = { customWrongDivisorText = it },
                            label = "Özel Yanlış Oranı (isteğe bağlı)",
                            prefix = "÷"
                        )
                        Text(
                            "Örnek: 5 yazılırsa 5 yanlış 1 doğruyu götürür. Boş bırakılırsa yukarıdaki yanlış kuralı kullanılır.",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (customLessonOptions.isNotEmpty()) {
                            Text(
                                "Ders Ağırlıkları",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            customLessonOptions.forEach { lesson ->
                                RoundedExamField(
                                    value = customLessonWeightTexts[lesson.id] ?: "1",
                                    onValueChange = { value ->
                                        customLessonWeightTexts = customLessonWeightTexts + (lesson.id to value)
                                    },
                                    label = "${lesson.label} Ağırlığı",
                                    prefix = "×"
                                )
                            }
                            Text(
                                "Ağırlıklar derslerin toplam puana katkısını belirler. 1 eşit ağırlıktır; daha büyük değer daha fazla katkı verir.",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "Seçili formda ayrı ders bölümleri bulunmadığı için ders ağırlığı tanımlanamaz.",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (scoreMode == ExamScoreMode.SCALED) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                RoundedExamField(
                                    value = minimumScoreText,
                                    onValueChange = { minimumScoreText = it },
                                    label = "Taban Puan",
                                    prefix = "↓"
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                RoundedExamField(
                                    value = maximumScoreText,
                                    onValueChange = { maximumScoreText = it },
                                    label = if (scoringType == ExamScoringType.CUSTOM) {
                                        "Toplam / Tavan Puan"
                                    } else {
                                        "Tavan Puan"
                                    },
                                    prefix = "↑"
                                )
                            }
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
            examMode = document.formSpec.examMode,
            examPreset = document.formSpec.examPreset
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

private fun loadExamLessonOptions(
    context: android.content.Context,
    selection: ActiveTemplateSelection
): List<ExamLessonOption> {
    if (selection.source != ActiveTemplateSource.DESIGNER_DOCUMENT) return emptyList()
    val document = (FileDesignerDocumentRepository(context).list() + DesignerStarterTemplates.all())
        .firstOrNull { it.id == selection.templateId && it.version == selection.templateVersion }
        ?: return emptyList()
    val configuredSubjects = AppSettingsRepository(context).load().subjects
    val genericLesson = Regex("^Ders\\s+\\d+$", RegexOption.IGNORE_CASE)
    return document.components
        .filterIsInstance<QuestionGroupComponent>()
        .mapIndexedNotNull { index, component ->
            val id = component.questionIdPrefix.ifBlank { component.id }.trim()
            if (id.isBlank()) return@mapIndexedNotNull null
            val rawLabel = component.label.trim()
            val label = when {
                rawLabel.isNotBlank() && !genericLesson.matches(rawLabel) -> rawLabel
                configuredSubjects.getOrNull(index)?.isNotBlank() == true -> configuredSubjects[index]
                rawLabel.isNotBlank() -> rawLabel
                else -> id.replace('-', ' ').replace('_', ' ')
            }
            ExamLessonOption(id = id, label = label)
        }
        .distinctBy { it.id }
}

private fun defaultScoringType(option: ExamTemplateOption): ExamScoringType = when (option.examPreset) {
    DesignerExamPreset.LGS -> ExamScoringType.LGS
    DesignerExamPreset.SCHOLARSHIP -> ExamScoringType.IOKBS
    else -> if (option.examMode == DesignerExamMode.SINGLE_LESSON) {
        ExamScoringType.SINGLE_SUBJECT
    } else {
        ExamScoringType.NORMAL
    }
}

private fun isOfficialMebType(type: ExamScoringType): Boolean =
    type == ExamScoringType.LGS || type == ExamScoringType.IOKBS

private fun scoringTypeLabel(type: ExamScoringType): String = when (type) {
    ExamScoringType.NORMAL -> "Normal Deneme"
    ExamScoringType.SINGLE_SUBJECT -> "Tek Ders Sınavı"
    ExamScoringType.LGS -> "LGS"
    ExamScoringType.IOKBS -> "İOKBS / Bursluluk"
    ExamScoringType.CUSTOM -> "Özel Puanlama"
}

private fun scoringTypeDescription(type: ExamScoringType): String = when (type) {
    ExamScoringType.NORMAL -> "Net veya seçilen puan aralığı"
    ExamScoringType.SINGLE_SUBJECT -> "Tek ders için net / puan"
    ExamScoringType.LGS -> "MEB yöntemi · 3 yanlış · 100–500"
    ExamScoringType.IOKBS -> "MEB yöntemi · 4 test · 100–500"
    ExamScoringType.CUSTOM -> "Ders ağırlıkları, özel yanlış oranı ve puan aralığı"
}

private fun scoreModeLabel(mode: ExamScoreMode): String = when (mode) {
    ExamScoreMode.RAW_NET -> "Net olarak göster"
    ExamScoreMode.SCALED -> "Puan aralığına dönüştür"
}

private val ExamDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("tr-TR"))

private fun todayText(): String = LocalDate.now().format(ExamDateFormatter)

private fun parseExamDate(value: String): LocalDate? = try {
    LocalDate.parse(value.trim(), ExamDateFormatter)
} catch (_: DateTimeParseException) {
    null
}

private fun parseScoreNumber(value: String): Double? =
    value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

private fun wrongPolicyLabel(policy: WrongAnswerPolicy): String = when (policy) {
    WrongAnswerPolicy.KEEP_AS_IS -> "Olduğu gibi bırak"
    WrongAnswerPolicy.FOUR_WRONG_ONE_CORRECT -> "4 yanlış 1 doğruyu götürsün"
    WrongAnswerPolicy.THREE_WRONG_ONE_CORRECT -> "3 yanlış 1 doğruyu götürsün"
}
