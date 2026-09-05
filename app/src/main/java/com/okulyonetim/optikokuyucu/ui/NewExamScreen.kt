package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.exam.ExamFactory
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.exam.WrongAnswerPolicy
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateDefaults
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import com.okulyonetim.optikokuyucu.omr.template.FileActiveTemplateSelectionRepository
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
    onSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(context) { FileExamRepository(appContext) }
    val options = remember(context) { loadExamTemplateOptions(appContext) }
    val activeSelection = remember(context) { FileActiveTemplateSelectionRepository(appContext).load() }

    var examName by remember { mutableStateOf("") }
    var schoolName by remember { mutableStateOf("") }
    var folderName by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(todayText()) }
    var selectedTemplate by remember {
        mutableStateOf(options.firstOrNull { it.selection == activeSelection } ?: options.first())
    }
    var wrongPolicy by remember { mutableStateOf(WrongAnswerPolicy.KEEP_AS_IS) }
    var templateMenuOpen by remember { mutableStateOf(false) }
    var wrongMenuOpen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ProductTopBar(
                title = "Yeni Sınav",
                leadingText = "×",
                onLeadingClick = onBack,
                actionText = "Kaydet",
                onActionClick = {
                    val parsedDate = parseExamDate(dateText)
                    when {
                        examName.isBlank() -> status = "Sınav adı zorunludur."
                        schoolName.isBlank() -> status = "Okul alanı zorunludur."
                        parsedDate == null -> status = "Tarih GG.AA.YYYY biçiminde olmalıdır."
                        else -> {
                            runCatching {
                                ExamFactory.create(
                                    name = examName,
                                    schoolName = schoolName,
                                    templateSelection = selectedTemplate.selection,
                                    examDateEpochDay = parsedDate.toEpochDay(),
                                    wrongAnswerPolicy = wrongPolicy,
                                    folderName = folderName
                                ).also(repository::save)
                            }.onSuccess { exam ->
                                onSaved(exam.id)
                            }.onFailure { error ->
                                status = "Sınav kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Sınav Bilgileri",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "* Zorunlu Alanlar",
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error,
                        fontStyle = FontStyle.Italic
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
                            onClick = { templateMenuOpen = true },
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("Optik Form *", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    selectedTemplate.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = templateMenuOpen,
                            onDismissRequest = { templateMenuOpen = false }
                        ) {
                            options.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(option.name)
                                            Text(
                                                "${option.selection.templateId} · v${option.selection.templateVersion}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedTemplate = option
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
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("Yanlış Cevaplara Ne Yapılsın", style = MaterialTheme.typography.labelMedium)
                                Text(wrongPolicyLabel(wrongPolicy), style = MaterialTheme.typography.bodyLarge)
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

                    if (status.isNotBlank()) {
                        Text(status, color = MaterialTheme.colorScheme.error)
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val parsedDate = parseExamDate(dateText)
                            when {
                                examName.isBlank() -> status = "Sınav adı zorunludur."
                                schoolName.isBlank() -> status = "Okul alanı zorunludur."
                                parsedDate == null -> status = "Tarih GG.AA.YYYY biçiminde olmalıdır."
                                else -> runCatching {
                                    ExamFactory.create(
                                        name = examName,
                                        schoolName = schoolName,
                                        templateSelection = selectedTemplate.selection,
                                        examDateEpochDay = parsedDate.toEpochDay(),
                                        wrongAnswerPolicy = wrongPolicy,
                                        folderName = folderName
                                    ).also(repository::save)
                                }.onSuccess { onSaved(it.id) }
                                    .onFailure { error ->
                                        status = "Sınav kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
                                    }
                            }
                        },
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Sınavı Kaydet", modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
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
        shape = RoundedCornerShape(28.dp)
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
