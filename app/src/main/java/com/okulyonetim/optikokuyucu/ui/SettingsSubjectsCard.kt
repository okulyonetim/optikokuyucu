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

    fun persist(updated: List<String>) {
        runCatching { repository.saveSubjects(updated) }
            .onSuccess {
                subjects = repository.load().subjects
                feedback.success("Dersler güncellendi.")
            }
            .onFailure { feedback.error("Dersler kaydedilemedi: ${it.message ?: it.javaClass.simpleName}") }
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
                    "${subjects.size} ders · cevap anahtarı ve sonuçlarda kullanılır",
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
        ModalBottomSheet(onDismissRequest = { editorOpen = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Dersler", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Sınav, cevap anahtarı ve öğrenci sonuçlarında kullanılacak ders adlarını yönetin.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                subjects.forEach { subject ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(subject, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            TextButton(
                                enabled = subjects.size > 1,
                                onClick = { persist(subjects.filterNot { it == subject }) }
                            ) { Text("Sil", color = MaterialTheme.colorScheme.error) }
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
                            val value = newSubject.trim()
                            if (subjects.any { it.equals(value, ignoreCase = true) }) {
                                feedback.warning("Bu ders zaten listede.")
                            } else {
                                persist(subjects + value)
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
