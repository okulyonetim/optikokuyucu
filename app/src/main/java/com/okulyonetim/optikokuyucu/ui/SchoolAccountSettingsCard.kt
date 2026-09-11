package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.school.SchoolSyncAccessPolicy
import kotlinx.coroutines.launch

@Composable
fun SchoolAccountSettingsCard() {
    val account = LocalSchoolAccount.current
    val scope = rememberCoroutineScope()
    var workingAction by remember { mutableStateOf<String?>(null) }
    var actionStatus by remember { mutableStateOf("") }

    ProductSettingsSection(
        title = "Okul Yönetim Hesabı",
        description = "Kurum bağlantısı ve eşitleme işlemleri",
        accentColor = MaterialTheme.colorScheme.primary
    ) {
        if (account == null) {
            Text(
                "Okul Yönetim oturumu bulunamadı.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.error
            )
            return@ProductSettingsSection
        }

        val teacherAccount = SchoolSyncAccessPolicy.isTeacher(account.profile)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    account.profile.displayName.ifBlank { account.profile.username },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (teacherAccount) {
                    Text(
                        "Öğretmen hesabı · yalnız sınav sonuçları Okul Yönetim'e gönderilebilir",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Öğrenciler: ${account.directoryStatus.ifBlank { "Henüz eşitlenmedi" }}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Text(
                    "${if (teacherAccount) "Sonuçlar" else "Sınavlar"}: ${account.cloudStatus.ifBlank { "Henüz eşitlenmedi" }}",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            ProductStatusBadge("BAĞLI", ProductBadgeTone.GREEN)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (!teacherAccount) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = workingAction == null,
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
                    Text(
                        if (workingAction == "directory") "Eşitleniyor…" else "Öğrenciler",
                        fontSize = 10.sp
                    )
                }
            }

            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = workingAction == null,
                onClick = {
                    workingAction = "cloud"
                    actionStatus = ""
                    scope.launch {
                        runCatching { account.syncCloudNow() }
                            .onSuccess { actionStatus = it }
                            .onFailure {
                                actionStatus = it.message ?: if (teacherAccount) {
                                    "Sonuçlar eşitlenemedi."
                                } else {
                                    "Sınav ve sonuçlar eşitlenemedi."
                                }
                            }
                        workingAction = null
                    }
                }
            ) {
                Text(
                    if (workingAction == "cloud") {
                        "Eşitleniyor…"
                    } else if (teacherAccount) {
                        "Sonuçları Eşitle"
                    } else {
                        "Sınav / Sonuç"
                    },
                    fontSize = 10.sp
                )
            }
        }

        if (actionStatus.isNotBlank()) {
            Text(
                actionStatus,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(
            modifier = Modifier.align(Alignment.End),
            enabled = workingAction == null,
            onClick = account.signOut
        ) {
            Text("Çıkış Yap", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
        }
    }
}
