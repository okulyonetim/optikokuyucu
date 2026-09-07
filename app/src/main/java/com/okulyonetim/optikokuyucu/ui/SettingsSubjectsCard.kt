package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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

/** Compact Settings-page entry. Subject editing is intentionally separate from the appearance sheet. */
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clickable { editorOpen = true },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Dersler", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${subjects.size} ders · adları ekleyin, düzenleyin veya kaldırın",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("Yönet ›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }

    if (editorOpen) {
        ModalBottomSheet(
            onDismissRequest = {
                editorOpen = false
                cancelRename()
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Dersleri Düzenle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Sınav, cevap anahtarı ve öğrenci sonuçlarında kullanılacak ders adlarını yönetin.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                subjects.forEach { subject ->
                    val editing = editingSubject == subject
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (editing) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        if (editing) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = editingValue,
                                    onValueChange = { editingValue = it.take(60) },
                                    singleLine = true,
                                    label = { Text("Ders adı") },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = ::cancelRename
                                    ) { Text("Vazgeç") }
                                    FilledTonalButton(
                                        modifier = Modifier.weight(1f),
                                        enabled = editingValue.isNotBlank(),
                                        onClick = { saveRename(subject) }
                                    ) { Text("Kaydet") }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    subject,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(onClick = { beginRename(subject) }) { Text("Düzenle") }
                                TextButton(
                                    enabled = subjects.size > 1,
                                    onClick = {
                                        if (editingSubject == subject) cancelRename()
                                        persist(subjects.filterNot { it == subject }, "Ders silindi.")
                                    }
                                ) { Text("Sil", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = newSubject,
                        onValueChange = { newSubject = it.take(60) },
                        label = { Text("Yeni ders") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
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
                    ) { Text("Ekle") }
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }
}
