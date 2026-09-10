package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.school.SchoolPortalManager
import com.okulyonetim.optikokuyucu.school.SchoolSyncAccessPolicy
import com.okulyonetim.optikokuyucu.settings.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings-page entry for subject names. Visual surfaces come from ProductUi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSubjectsCard(repository: AppSettingsRepository) {
    val context = LocalContext.current
    val feedback = LocalAppFeedback.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { SchoolPortalManager.get(context.applicationContext) }
    val schoolAccount = LocalSchoolAccount.current
    val teacherAccount = schoolAccount?.profile?.let(SchoolSyncAccessPolicy::isTeacher) == true
    var subjects by remember(repository) { mutableStateOf(repository.load().subjects) }
    var editorOpen by remember { mutableStateOf(false) }
    var newSubject by remember { mutableStateOf("") }
    var editingSubject by remember { mutableStateOf<String?>(null) }
    var editingValue by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }

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

    fun syncFromSchoolManagement() {
        if (syncing) return
        if (schoolAccount == null) {
            feedback.warning("Önce Okul Yönetim hesabıyla giriş yapın.")
            return
        }
        if (teacherAccount) {
            feedback.warning("Öğretmen hesapları ders listesini Okul Yönetim ile eşitleyemez.")
            return
        }
        syncing = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { manager.syncSubjects() }
            }.onSuccess { result ->
                subjects = repository.load().subjects
                cancelRename()
                feedback.success("${result.importedSubjects} ders Okul Yönetim'den aktarıldı.")
            }.onFailure { error ->
                feedback.error("Dersler aktarılamadı: ${error.message ?: error.javaClass.simpleName}")
            }
            syncing = false
        }
    }

    ProductSettingsLink(
        symbol = "≡",
        title = "Dersler",
        description = if (teacherAccount) {
            "${subjects.size} ders · cihazdaki ders listesini düzenle"
        } else {
            "${subjects.size} ders · Okul Yönetim ile eşitle veya düzenle"
        },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("Dersleri Düzenle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (teacherAccount) {
                        "Tek ders sınavlarında bu cihazdaki liste kullanılır. Öğretmen hesaplarında Okul Yönetim ders eşitlemesi yönetici yetkisindedir."
                    } else {
                        "Tek ders sınavlarında bu liste kullanılır. Okul Yönetim'den aktarınca buluttaki ders listesi cihazdaki listeyle değiştirilir."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!teacherAccount) {
                    FilledTonalButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !syncing && schoolAccount != null,
                        onClick = ::syncFromSchoolManagement
                    ) {
                        Text(
                            if (syncing) "Okul Yönetim'den aktarılıyor…" else "Okul Yönetim'den Dersleri Getir",
                            fontSize = 11.sp
                        )
                    }
                }

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
