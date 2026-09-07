package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SchoolAccountSettingsCard() {
    val account = LocalSchoolAccount.current
    val scope = rememberCoroutineScope()
    var workingAction by remember { mutableStateOf<String?>(null) }
    var actionStatus by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                "Okul Yönetim Hesabı",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (account == null) {
                Text(
                    "Okul Yönetim oturumu bulunamadı.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
                return@Column
            }

            Text(
                "Kullanıcı: ${account.profile.displayName.ifBlank { account.profile.username }}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Bağlantı durumu: Bağlı",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Son öğrenci senkronu: ${account.directoryStatus.ifBlank { "Henüz tamamlanmadı" }}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Sınav/sonuç senkronu: ${account.cloudStatus.ifBlank { "Henüz tamamlanmadı" }}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = workingAction == null,
                shape = RoundedCornerShape(12.dp),
                onClick = {
                    workingAction = "directory"
                    actionStatus = ""
                    scope.launch {
                        runCatching { account.syncDirectoryNow() }
                            .onSuccess { actionStatus = it }
                            .onFailure { actionStatus = it.message ?: "Öğrenciler eşitlenemedi." }
                        workingAction = null
                    }
                }
            ) {
                Text(if (workingAction == "directory") "Senkronize Ediliyor…" else "Öğrencileri Senkronize Et")
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = workingAction == null,
                shape = RoundedCornerShape(12.dp),
                onClick = {
                    workingAction = "cloud"
                    actionStatus = ""
                    scope.launch {
                        runCatching { account.syncCloudNow() }
                            .onSuccess { actionStatus = it }
                            .onFailure { actionStatus = it.message ?: "Sınav ve sonuçlar eşitlenemedi." }
                        workingAction = null
                    }
                }
            ) {
                Text(if (workingAction == "cloud") "Senkronize Ediliyor…" else "Sınav ve Sonuçları Senkronize Et")
            }

            if (actionStatus.isNotBlank()) {
                Text(
                    actionStatus,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = workingAction == null,
                onClick = account.signOut
            ) {
                Text("Çıkış Yap", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
