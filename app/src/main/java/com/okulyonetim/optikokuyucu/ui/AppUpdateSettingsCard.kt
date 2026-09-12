package com.okulyonetim.optikokuyucu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.BuildConfig
import com.okulyonetim.optikokuyucu.update.AppUpdateInfo
import com.okulyonetim.optikokuyucu.update.AppUpdateManager
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun AppUpdateSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var status by remember { mutableStateOf("Güncellemeler GitHub üzerinden güvenli biçimde kontrol edilir.") }

    fun launchDownloadedInstaller() {
        val apk = downloadedApk ?: return
        runCatching { AppUpdateManager.launchInstaller(context, apk) }
            .onSuccess { status = "Android güncelleme ekranı açıldı." }
            .onFailure { error -> status = "Kurulum başlatılamadı: ${error.message ?: "Bilinmeyen hata"}" }
    }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (AppUpdateManager.canInstallPackages(context)) {
            launchDownloadedInstaller()
        } else {
            status = "Güncelleme için bu uygulamaya 'Bilinmeyen uygulamaları yükle' izni verin."
        }
    }

    fun checkForUpdate() {
        if (checking || downloading) return
        scope.launch {
            checking = true
            status = "Yeni sürüm kontrol ediliyor…"
            runCatching { AppUpdateManager.checkForUpdate() }
                .onSuccess { found ->
                    update = found
                    downloadedApk = null
                    status = if (found == null) {
                        "Uygulama güncel."
                    } else {
                        "Yeni sürüm hazır: ${found.versionName}"
                    }
                }
                .onFailure { error ->
                    update = null
                    status = "Güncelleme kontrol edilemedi: ${error.message ?: "Bağlantı hatası"}"
                }
            checking = false
        }
    }

    fun downloadAndInstall() {
        val currentUpdate = update ?: return
        if (downloading) return
        scope.launch {
            downloading = true
            status = "Sürüm ${currentUpdate.versionName} indiriliyor…"
            runCatching { AppUpdateManager.downloadApk(context.applicationContext, currentUpdate) }
                .onSuccess { apk ->
                    downloadedApk = apk
                    if (AppUpdateManager.canInstallPackages(context)) {
                        launchDownloadedInstaller()
                    } else {
                        status = "Android'ın yükleme izni gerekiyor. Açılan ekranda izni etkinleştirin."
                        unknownSourcesLauncher.launch(AppUpdateManager.unknownSourcesIntent(context))
                    }
                }
                .onFailure { error ->
                    status = "Güncelleme indirilemedi: ${error.message ?: "Bağlantı hatası"}"
                }
            downloading = false
        }
    }

    LaunchedEffect(Unit) {
        checkForUpdate()
    }

    ProductSettingsSection(
        title = "Uygulama Güncellemesi",
        description = "Yeni sürümü uygulamadan çıkmadan kontrol edin, indirin ve Android güncelleme ekranını açın.",
        accentColor = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Yüklü sürüm",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${BuildConfig.VERSION_NAME}  ·  ${BuildConfig.VERSION_CODE}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (checking || downloading) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }
        }

        update?.let { available ->
            Text(
                "Yeni sürüm ${available.versionName}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            available.notes.trim().takeIf { it.isNotEmpty() }?.let { notes ->
                Text(
                    notes.take(260),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            status,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (update == null) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !checking && !downloading,
                onClick = ::checkForUpdate
            ) {
                Text(if (checking) "Kontrol ediliyor…" else "Güncellemeyi Kontrol Et")
            }
        } else {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !checking && !downloading,
                onClick = ::downloadAndInstall
            ) {
                Text(if (downloading) "İndiriliyor…" else "İndir ve Güncelle")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !checking && !downloading,
                onClick = ::checkForUpdate
            ) {
                Text("Yeniden Kontrol Et")
            }
        }
    }
}
