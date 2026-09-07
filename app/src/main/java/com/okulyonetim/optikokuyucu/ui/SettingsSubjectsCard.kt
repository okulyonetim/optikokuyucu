package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.settings.AppSettingsRepository

@Composable
internal fun SettingsSubjectsCard(repository: AppSettingsRepository) {
    val feedback = LocalAppFeedback.current
    var subjects by remember(repository) { mutableStateOf(repository.load().subjects) }
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Dersler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Sınav, cevap anahtarı ve öğrenci sonuçlarında kullanılacak ders adlarını buradan yönetin.",
                fontSize = 11.sp,
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
        }
    }
}
