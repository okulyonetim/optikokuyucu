package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.settings.AppSettingsRepository

/** Settings-page entry for subject names. Visual surfaces come from ProductUi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSubjectsCard(repository: AppSettingsRepository) {
    val feedback = LocalAppFeedback.current
    var subjects by remember(repository) { mutableStateOf(repository.load().subjects) }
    var editorOpen by remember { mutableStateOf(false) }
    var newSubject by remember { mutableStateOf("") }
    var editingSubject by remember { mutableStateOf<String?>(null) }
    var editingValue by remember { mutableStateOf("") }

    fun persist(updated: List<String>, successMessage: String = "Dersler güncellendi.") {
        runCatching { repository.saveSubjects(updated) }
            .onSuccess {
                subjects = repository.load().subjects
                feedback.success(successMessage)
            }
            .onFailure { feedback.error("Dersler kaydedilemedi: ${it.message ?: it.javaClass.simpleName}") }
    }

    fun beginRename(subject: String) {
        editingSubject = subject
        editingValue = subject
    }

    fun cancelRename() {
        editingSubject = null
        editingValue = ""
    }

    fun saveRename(original: String) {
        val value = editingValue.trim().replace(Regex("\\s+"), " ")
        when {
            value.isBlank() -> feedback.warning("Ders adı boş olamaz.")
            subjects.any { it != original && it.equals(value, ignoreCase = true) } ->
                feedback.warning("Bu ders zaten listede.")
            else -> {
                persist(
                    subjects.map { if (it == original) value else it },
                    "Ders adı güncellendi."
                )
                cancelRename()
            }
        }
    }

    ProductSettingsLink(
        symbol = "≡",
        title = "Dersler",
        description = "${subjects.size} ders · ekle, düzenle veya kaldır",
        onClick = { editorOpen = true }
    )

    if (editorOpen) {
        ModalBottomSheet(
            onDismissRequest = {
                editorOpen = false
                cancelRename()
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("Dersleri Düzenle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Sınav, cevap anahtarı ve sonuçlarda kullanılacak ders adlarını yönetin.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                subjects.forEach { subject ->
                    val editing = editingSubject == subject
                    ProductCompactCard(modifier = Modifier.fillMaxWidth()) {
                        if (editing) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                OutlinedTextField(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = editingValue,
                                    onValueChange = { editingValue = it.take(60) },
                                    singleLine = true,
                                    label = { Text("Ders adı") }
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    OutlinedButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = ::cancelRename
                                    ) { Text("Vazgeç", fontSize = 10.sp) }
                                    FilledTonalButton(
                                        modifier = Modifier.weight(1f),
                                        enabled = editingValue.isNotBlank(),
                                        onClick = { saveRename(subject) }
                                    ) { Text("Kaydet", fontSize = 10.sp) }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    subject,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(onClick = { beginRename(subject) }) {
                                    Text("Düzenle", fontSize = 10.sp)
                                }
                                TextButton(
                                    enabled = subjects.size > 1,
                                    onClick = {
                                        if (editingSubject == subject) cancelRename()
                                        persist(subjects.filterNot { it == subject }, "Ders silindi.")
                                    }
                                ) {
                                    Text("Sil", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = newSubject,
                        onValueChange = { newSubject = it.take(60) },
                        label = { Text("Yeni ders") },
                        singleLine = true
                    )
                    FilledTonalButton(
                        enabled = newSubject.isNotBlank(),
                        onClick = {
                            val value = newSubject.trim().replace(Regex("\\s+"), " ")
                            if (subjects.any { it.equals(value, ignoreCase = true) }) {
                                feedback.warning("Bu ders zaten listede.")
                            } else {
                                persist(subjects + value, "Ders eklendi.")
                                newSubject = ""
                            }
                        }
                    ) {
                        Text("Ekle", fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
