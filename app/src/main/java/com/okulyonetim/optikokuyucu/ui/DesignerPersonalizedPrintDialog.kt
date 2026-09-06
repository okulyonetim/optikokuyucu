package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPrintContext
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPrintPersonalization

private data class PersonalizedStudentChoice(
    val name: String,
    val className: String,
    val number: String
) {
    val label: String get() = buildString {
        append(name.ifBlank { "Öğrenci" })
        if (className.isNotBlank()) append(" · $className")
        if (number.isNotBlank()) append(" · No $number")
    }
}

@Composable
internal fun DesignerPersonalizedPrintDialog(
    document: DesignerDocument,
    onDismiss: () -> Unit,
    onConfirm: (DesignerPrintContext) -> Unit
) {
    val context = LocalContext.current
    val exams = remember(context, document.id, document.version) {
        FileExamRepository(context.applicationContext).list()
    }
    val matchingExams = remember(exams, document.id, document.version) {
        exams.filter {
            it.templateSelection.templateId == document.id && it.templateSelection.templateVersion == document.version
        }.sortedByDescending { it.createdAtEpochMs }
    }
    val studentChoices = remember(exams) {
        exams.flatMap { it.papers }.mapNotNull { link ->
            val name = link.studentName.trim()
            val className = link.className.trim()
            val number = link.studentNumber.trim()
            if (name.isBlank() && className.isBlank() && number.isBlank()) null
            else PersonalizedStudentChoice(name, className, number)
        }.distinctBy { if (it.number.isNotBlank()) "n:${it.number}" else "${it.name}|${it.className}" }
            .sortedWith(compareBy<PersonalizedStudentChoice> { it.className }.thenBy { it.name }.thenBy { it.number })
    }

    val initialExam = matchingExams.firstOrNull()
    var studentName by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }
    var studentNumber by remember { mutableStateOf("") }
    var examName by remember { mutableStateOf(initialExam?.name.orEmpty()) }
    var schoolName by remember { mutableStateOf(initialExam?.schoolName.orEmpty()) }
    var studentMenuOpen by remember { mutableStateOf(false) }
    var examMenuOpen by remember { mutableStateOf(false) }

    val printContext = DesignerPrintContext(studentName, className, studentNumber, examName, schoolName)
    val numberIssue = DesignerPrintPersonalization.studentNumberIssue(document, printContext)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kişiye Özel Optik Form") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (matchingExams.isNotEmpty()) {
                    PickerField(
                        label = "Sınavdan otomatik doldur",
                        value = examName.ifBlank { "Sınav seç" },
                        expanded = examMenuOpen,
                        onExpandedChange = { examMenuOpen = it }
                    ) {
                        matchingExams.forEach { exam ->
                            DropdownMenuItem(
                                text = { Text("${exam.name} · ${exam.schoolName}") },
                                onClick = {
                                    examName = exam.name
                                    schoolName = exam.schoolName
                                    examMenuOpen = false
                                }
                            )
                        }
                    }
                }
                if (studentChoices.isNotEmpty()) {
                    PickerField(
                        label = "Kayıtlı öğrenciden doldur",
                        value = studentName.ifBlank { "Öğrenci seç" },
                        expanded = studentMenuOpen,
                        onExpandedChange = { studentMenuOpen = it }
                    ) {
                        studentChoices.take(250).forEach { student ->
                            DropdownMenuItem(
                                text = { Text(student.label) },
                                onClick = {
                                    studentName = student.name
                                    className = student.className
                                    studentNumber = student.number
                                    studentMenuOpen = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(studentName, { studentName = it }, Modifier.fillMaxWidth(), label = { Text("Öğrenci Adı Soyadı") }, singleLine = true)
                OutlinedTextField(className, { className = it }, Modifier.fillMaxWidth(), label = { Text("Sınıfı") }, singleLine = true)
                OutlinedTextField(
                    studentNumber,
                    { if (it.length <= 24) studentNumber = it.filterNot(Char::isWhitespace) },
                    Modifier.fillMaxWidth(),
                    label = { Text("Öğrenci Numarası") },
                    supportingText = { numberIssue?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    isError = numberIssue != null,
                    singleLine = true
                )
                OutlinedTextField(examName, { examName = it }, Modifier.fillMaxWidth(), label = { Text("Sınav Adı") }, singleLine = true)
                OutlinedTextField(schoolName, { schoolName = it }, Modifier.fillMaxWidth(), label = { Text("Okul Adı") }, singleLine = true)
                Text(
                    "Öğrenci numarası, formdaki numara alanında hem baloncuk olarak kodlanır hem üst kutulara yazılır.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = printContext.isPersonalized && numberIssue == null,
                onClick = { onConfirm(printContext) }
            ) { Text("A4 PDF Oluştur") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

@Composable
private fun PickerField(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onExpandedChange(true) },
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(value, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            menuContent()
        }
    }
}
