package com.okulyonetim.optikokuyucu.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.okulyonetim.optikokuyucu.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal data class AppUpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val releaseUrl: String,
    val notes: String
)

internal object AppVersionComparator {
    fun isNewer(current: String, remote: String): Boolean {
        val currentParts = numericParts(current)
        val remoteParts = numericParts(remote)
        val count = maxOf(currentParts.size, remoteParts.size)
        repeat(count) { index ->
            val currentValue = currentParts.getOrElse(index) { 0 }
            val remoteValue = remoteParts.getOrElse(index) { 0 }
            if (remoteValue != currentValue) return remoteValue > currentValue
        }
        return false
    }

    private fun numericParts(value: String): List<Int> = value
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .split('.')
        .map { part -> part.filter(Char::isDigit).toIntOrNull() ?: 0 }
}

internal object AppUpdateManager {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/okulyonetim/optikokuyucu/releases/latest"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val connection = openConnection(LATEST_RELEASE_API)
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
            check(code in 200..299) { "Güncelleme sunucusu HTTP $code yanıtı verdi." }

            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val remoteVersion = root.optString("tag_name")
                .removePrefix("v")
                .removePrefix("V")
                .trim()
            check(remoteVersion.isNotBlank()) { "Yayın sürümü okunamadı." }

            if (!AppVersionComparator.isNewer(BuildConfig.VERSION_NAME, remoteVersion)) {
                return@withContext null
            }

            val assets = root.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                        if (apkUrl != null) break
                    }
                }
            }
            check(!apkUrl.isNullOrBlank()) { "Bu sürüm için APK dosyası bulunamadı." }

            AppUpdateInfo(
                versionName = remoteVersion,
                apkUrl = apkUrl,
                releaseUrl = root.optString("html_url"),
                notes = root.optString("body")
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadApk(context: Context, update: AppUpdateInfo): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "optik-okuyucu-${update.versionName}.apk")
        val temporary = File(directory, target.name + ".part")
        temporary.delete()

        val connection = openConnection(update.apkUrl)
        try {
            val code = connection.responseCode
            check(code in 200..299) { "APK indirilemedi. HTTP $code" }
            connection.inputStream.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        check(temporary.length() > 0L) { "İndirilen APK dosyası boş." }
        if (target.exists()) target.delete()
        check(temporary.renameTo(target)) { "APK dosyası hazırlanamadı." }
        target
    }

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
    )

    fun launchInstaller(context: Context, apkFile: File) {
        check(apkFile.isFile) { "Kurulacak APK dosyası bulunamadı." }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "OptikOkuyucu/${BuildConfig.VERSION_NAME}")
        }
}
