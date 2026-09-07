package com.okulyonetim.optikokuyucu.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.okulyonetim.optikokuyucu.school.SchoolOfflineSessionPolicy
import com.okulyonetim.optikokuyucu.school.SchoolPortalManager
import com.okulyonetim.optikokuyucu.school.SchoolUserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class SchoolAccountUi(
    val profile: SchoolUserProfile,
    val directoryStatus: String,
    val cloudStatus: String,
    val syncDirectoryNow: suspend () -> String,
    val syncCloudNow: suspend () -> String,
    val signOut: () -> Unit
)

val LocalSchoolAccount = staticCompositionLocalOf<SchoolAccountUi?> { null }

/**
 * Reuses the Okul Yönetim username/password account. A previously authenticated device may open
 * with its cached session while offline; cloud refresh and student synchronization happen in the
 * background whenever the server is reachable.
 */
@Composable
fun SchoolPortalGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { SchoolPortalManager.get(context.applicationContext) }
    val cachedSession = remember(manager) { manager.cachedSession() }
    var profile by remember {
        mutableStateOf(
            cachedSession
                ?.takeIf { SchoolOfflineSessionPolicy.canOpenCached(it) }
                ?.profile
        )
    }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var directoryStatus by remember { mutableStateOf("") }
    var cloudStatus by remember { mutableStateOf("") }

    suspend fun syncDirectory(): String = withContext(Dispatchers.IO) {
        val result = manager.syncDirectory()
        "${result.importedStudents} öğrenci Okul Yönetim ile eşitlendi"
    }

    suspend fun syncCloud(force: Boolean): String = withContext(Dispatchers.IO) {
        val result = manager.syncExamsAndResults(force)
            ?: return@withContext cloudStatus.ifBlank { "Sınav verileri güncel" }
        when {
            result.failures.isNotEmpty() -> "${result.syncedStudentResults} sonuç gönderildi · ${result.failures.size} hata"
            result.skippedWithoutStudentIdentity > 0 ->
                "${result.syncedStudentResults} sonuç gönderildi · ${result.skippedWithoutStudentIdentity} eşleşmeyen kağıt cihazda kaldı"
            result.skippedWithoutPermission > 0 -> "${result.syncedStudentResults} sonuç gönderildi · bazı bulut işlemleri için yetki yok"
            else -> "${result.syncedStudentResults} öğrenci sonucu Okul Yönetim'e gönderildi"
        }
    }

    LaunchedEffect(profile?.uid) {
        if (profile == null) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) { manager.refreshProfile() }
        }.onSuccess { refreshed ->
            profile = refreshed.profile
        }
        runCatching {
            withContext(Dispatchers.IO) { manager.migrateLegacyAdminContent() }
        }
        runCatching { syncDirectory() }
            .onSuccess { directoryStatus = it }
            .onFailure { directoryStatus = "Çevrimdışı · cihazdaki öğrenci listesi kullanılıyor" }
        runCatching {
            withContext(Dispatchers.IO) { manager.syncTemplates() }
        }
        runCatching {
            withContext(Dispatchers.IO) { manager.refreshExamCatalog() }
        }
        runCatching { syncCloud(force = true) }
            .onSuccess { cloudStatus = it }
            .onFailure { cloudStatus = "Bulut senkronu bekliyor · internet geldiğinde tekrar denenecek" }
    }

    // Local OMR work remains offline-first. This loop computes a local fingerprint every 10 seconds;
    // Firestore is contacted only when an owned exam, paper, scan result, answer key or sharing flag changed.
    LaunchedEffect(profile?.uid, Unit) {
        if (profile == null) return@LaunchedEffect
        while (true) {
            delay(10_000)
            runCatching { syncCloud(force = false) }
                .onSuccess { cloudStatus = it }
                .onFailure { cloudStatus = "Bulut senkronu bekliyor · çevrimdışı" }
        }
    }

    val signedIn = profile
    if (signedIn != null) {
        val account = SchoolAccountUi(
            profile = signedIn,
            directoryStatus = directoryStatus,
            cloudStatus = cloudStatus,
            syncDirectoryNow = {
                runCatching { syncDirectory() }
                    .onSuccess { directoryStatus = it }
                    .onFailure { directoryStatus = it.message ?: "Öğrenciler eşitlenemedi" }
                    .getOrThrow()
            },
            syncCloudNow = {
                runCatching {
                    withContext(Dispatchers.IO) {
                        manager.syncTemplates()
                        manager.refreshExamCatalog()
                    }
                    syncCloud(force = true)
                }
                    .onSuccess { cloudStatus = it }
                    .onFailure { cloudStatus = it.message ?: "Bulut senkronu başarısız" }
                    .getOrThrow()
            },
            signOut = {
                manager.signOut()
                profile = null
                password = ""
                directoryStatus = ""
                cloudStatus = ""
            }
        )
        CompositionLocalProvider(LocalSchoolAccount provides account) {
            content()
        }
        return
    }

    OptikProductTheme {
        SchoolLoginScreen(
            username = username,
            onUsernameChange = {
                username = it
                errorMessage = ""
            },
            password = password,
            onPasswordChange = {
                password = it
                errorMessage = ""
            },
            working = working,
            errorMessage = errorMessage,
            onSubmit = {
                if (!working && username.isNotBlank() && password.isNotBlank()) {
                    working = true
                    errorMessage = ""
                }
            }
        )

        LaunchedEffect(working) {
            if (!working) return@LaunchedEffect
            val outcome = runCatching {
                withContext(Dispatchers.IO) { manager.signIn(username, password) }
            }
            outcome.onSuccess { session ->
                profile = session.profile
                password = ""
            }.onFailure { error ->
                errorMessage = error.message ?: "Giriş yapılamadı."
            }
            working = false
        }
    }
}
