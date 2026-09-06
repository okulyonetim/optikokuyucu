package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.exam.Exam
import com.okulyonetim.optikokuyucu.exam.WrongAnswerPolicy
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val examEditDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@Composable
internal fun ExamEditDialog(
    exam: Exam,
    onDismiss: () -> Unit,
    onSave: (Exam) -> Unit
) {
    var name by remember(exam.id) { mutableStateOf(exam.name) }
    var school by remember(exam.id) { mutableStateOf(exam.schoolName) }
    var folder by remember(exam.id) { mutableStateOf(exam.folderName) }
    var date by remember(exam.id) { mutableStateOf(LocalDate.ofEpochDay(exam.examDateEpochDay).format(examEditDateFormatter)) }
    var wrongPolicy by remember(exam.id) { mutableStateOf(exam.wrongAnswerPolicy) }
    var bookletCount by remember(exam.id) { mutableStateOf(exam.bookletCount) }
    var personalized by remember(exam.id) { mutableStateOf(exam.personalizedFormsEnabled) }
    var wrongMenu by remember { mutableStateOf(false) }
    var bookletMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sınavı Düzenle") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Sınav Adı") }, singleLine = true)
                OutlinedTextField(school, { school = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Okul") }, singleLine = true)
                OutlinedTextField(folder, { folder = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Klasör") }, singleLine = true)
                OutlinedTextField(date, { date = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Tarih (GG.AA.YYYY)") }, singleLine = true)

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { wrongMenu = true }, shape = RoundedCornerShape(14.dp)) {
                        Text("Yanlış Cevap: ${wrongPolicyLabelForEdit(wrongPolicy)}")
                    }
                    DropdownMenu(expanded = wrongMenu, onDismissRequest = { wrongMenu = false }) {
                        WrongAnswerPolicy.entries.forEach { policy ->
                            DropdownMenuItem(text = { Text(wrongPolicyLabelForEdit(policy)) }, onClick = {
                                wrongPolicy = policy
                                wrongMenu = false
                            })
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { bookletMenu = true }, shape = RoundedCornerShape(14.dp)) {
                        Text("Kitapçık Sayısı: $bookletCount")
                    }
                    DropdownMenu(expanded = bookletMenu, onDismissRequest = { bookletMenu = false }) {
                        (1..8).forEach { count ->
                            DropdownMenuItem(text = { Text("$count kitapçık") }, onClick = {
                                bookletCount = count
                                bookletMenu = false
                            })
                        }
                    }
                }

                val canPersonalize = exam.templateSelection.source == ActiveTemplateSource.DESIGNER_DOCUMENT && exam.participants.isNotEmpty()
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Öğrenciye Özel Form")
                        Text(if (canPersonalize) "Seçili öğrenciler için kişisel form üretir." else "Bu sınavda kullanılamıyor.")
                    }
                    Switch(
                        checked = personalized,
                        enabled = canPersonalize,
                        onCheckedChange = { personalized = it }
                    )
                }

                Text("Optik form sürümü sınav oluşturulduktan sonra değiştirilemez.")
                if (error.isNotBlank()) Text(error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = runCatching { LocalDate.parse(date, examEditDateFormatter) }.getOrNull()
                when {
                    name.isBlank() -> error = "Sınav adı zorunludur."
                    school.isBlank() -> error = "Okul adı zorunludur."
                    parsed == null -> error = "Tarih GG.AA.YYYY biçiminde olmalıdır."
                    else -> onSave(
                        exam.copy(
                            name = name.trim(),
                            schoolName = school.trim(),
                            folderName = folder.trim(),
                            examDateEpochDay = parsed.toEpochDay(),
                            wrongAnswerPolicy = wrongPolicy,
                            bookletCount = bookletCount,
                            personalizedFormsEnabled = personalized && canPersonalize
                        )
                    )
                }
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

private fun wrongPolicyLabelForEdit(policy: WrongAnswerPolicy): String = when (policy) {
    WrongAnswerPolicy.KEEP_AS_IS -> "Olduğu Gibi"
    WrongAnswerPolicy.FOUR_WRONG_ONE_CORRECT -> "4 Yanlış 1 Doğru"
    WrongAnswerPolicy.THREE_WRONG_ONE_CORRECT -> "3 Yanlış 1 Doğru"
}
