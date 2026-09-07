package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.school.SchoolPortalManager
import com.okulyonetim.optikokuyucu.school.SchoolUserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SchoolAccountUi(
    val profile: SchoolUserProfile,
    val directoryStatus: String,
    val syncNow: suspend () -> String,
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
    var profile by remember { mutableStateOf(manager.cachedSession()?.profile) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var directoryStatus by remember { mutableStateOf("") }

    suspend fun syncDirectory(): String = withContext(Dispatchers.IO) {
        val result = manager.syncDirectory()
        "${result.importedStudents} öğrenci Okul Yönetim ile eşitlendi"
    }

    LaunchedEffect(profile?.uid) {
        if (profile == null) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                manager.refreshProfile().also { profile = it.profile }
            }
        }
        runCatching { syncDirectory() }
            .onSuccess { directoryStatus = it }
            .onFailure { directoryStatus = "Çevrimdışı · cihazdaki öğrenci listesi kullanılıyor" }
    }

    val signedIn = profile
    if (signedIn != null) {
        val account = SchoolAccountUi(
            profile = signedIn,
            directoryStatus = directoryStatus,
            syncNow = {
                runCatching { syncDirectory() }
                    .onSuccess { directoryStatus = it }
                    .getOrElse { error ->
                        val message = error.message ?: "Öğrenciler eşitlenemedi"
                        directoryStatus = message
                        message
                    }
            },
            signOut = {
                manager.signOut()
                profile = null
                password = ""
                directoryStatus = ""
            }
        )
        CompositionLocalProvider(LocalSchoolAccount provides account) {
            content()
        }
        return
    }

    OptikProductTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Optik Okuyucu",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Okul Yönetim hesabınızla giriş yapın",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Aynı kullanıcı adı ve şifre kullanılır. Öğrenci, sınıf ve veli bilgileri Okul Yönetim'den alınır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = username,
                        onValueChange = {
                            username = it
                            errorMessage = ""
                        },
                        enabled = !working,
                        singleLine = true,
                        label = { Text("Kullanıcı Adı") },
                        shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = ""
                        },
                        enabled = !working,
                        singleLine = true,
                        label = { Text("Şifre") },
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    if (errorMessage.isNotBlank()) {
                        Text(
                            errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !working && username.isNotBlank() && password.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        onClick = {
                            working = true
                            errorMessage = ""
                        }
                    ) {
                        if (working) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Giriş Yap")
                        }
                    }
                }
            }
        }

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
