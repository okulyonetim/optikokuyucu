package com.okulyonetim.optikokuyucu.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.okulyonetim.optikokuyucu.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class AppUpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val releaseUrl: String,
    val notes: String,
    /** Declared size of the APK asset (0 when unknown); used to detect truncated downloads. */
    val sizeBytes: Long = 0L,
    /** Lower-case SHA-256 from the release notes, when the publish workflow provided one. */
    val sha256: String? = null
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

internal sealed interface ApkCheck {
    data object Ok : ApkCheck
    data class Problem(val message: String) : ApkCheck
}

internal object AppUpdateManager {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/okulyonetim/optikokuyucu/releases/latest"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val MIN_PLAUSIBLE_APK_BYTES = 1_000_000L
    private const val PROGRESS_INTERVAL_MS = 150L

    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val connection = openConnection(LATEST_RELEASE_API, accept = "application/vnd.github+json")
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
            check(code in 200..299) {
                UpdateErrorText.httpFailure(code, connection.getHeaderField("X-RateLimit-Remaining"))
            }

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

            val assets = buildList {
                val array = root.optJSONArray("assets") ?: return@buildList
                for (index in 0 until array.length()) {
                    val asset = array.optJSONObject(index) ?: continue
                    add(
                        ReleaseAsset(
                            name = asset.optString("name"),
                            url = asset.optString("browser_download_url"),
                            sizeBytes = asset.optLong("size", 0L)
                        )
                    )
                }
            }
            val apk = ReleaseAssetSelector.select(assets)
            checkNotNull(apk) { "Bu sürüm için APK dosyası bulunamadı." }

            val notes = root.optString("body")
            AppUpdateInfo(
                versionName = remoteVersion,
                apkUrl = apk.url,
                releaseUrl = root.optString("html_url"),
                notes = notes,
                sizeBytes = apk.sizeBytes,
                sha256 = UpdateChecksum.fromReleaseNotes(notes)
            )
        } catch (error: IOException) {
            throw IllegalStateException(UpdateErrorText.networkFailure(error), error)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Downloads to a ".part" file, validates size and checksum, and only then publishes the final
     * file name. [onProgress] is called from the IO thread with (bytesDone, bytesTotal or -1).
     */
    suspend fun downloadApk(
        context: Context,
        update: AppUpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "optik-okuyucu-${update.versionName}.apk")
        val temporary = File(directory, target.name + ".part")
        // Older downloads and half-finished transfers would otherwise pile up in the cache forever.
        directory.listFiles()?.filter { it != target }?.forEach { it.delete() }
        temporary.delete()

        val connection = openConnection(update.apkUrl, accept = "application/octet-stream")
        try {
            val code = connection.responseCode
            check(code in 200..299) {
                "APK indirilemedi. " + UpdateErrorText.httpFailure(code, connection.getHeaderField("X-RateLimit-Remaining"))
            }
            val declaredLength = connection.contentLengthLong.takeIf { it > 0L } ?: update.sizeBytes
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            var lastReportAt = 0L

            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastReportAt >= PROGRESS_INTERVAL_MS) {
                            lastReportAt = now
                            onProgress(written, declaredLength.takeIf { it > 0L } ?: -1L)
                        }
                    }
                }
            }
            onProgress(written, declaredLength.takeIf { it > 0L } ?: -1L)

            check(written >= MIN_PLAUSIBLE_APK_BYTES) {
                "İndirilen dosya çok küçük; ağ girişi (captive portal) bir hata sayfası döndürmüş olabilir."
            }
            if (update.sizeBytes > 0L) {
                check(written == update.sizeBytes) {
                    "İndirme yarım kaldı ($written / ${update.sizeBytes} bayt). Tekrar deneyin."
                }
            }
            val expected = update.sha256
            if (expected != null) {
                val actual = UpdateChecksum.hex(digest.digest())
                check(actual == expected) { "İndirilen APK doğrulanamadı (SHA-256 uyuşmuyor). Tekrar deneyin." }
            }
        } catch (error: Throwable) {
            temporary.delete()
            if (error is IOException) throw IllegalStateException(UpdateErrorText.networkFailure(error), error)
            throw error
        } finally {
            connection.disconnect()
        }
        if (target.exists()) target.delete()
        check(temporary.renameTo(target)) { "APK dosyası hazırlanamadı." }
        target
    }

    /**
     * Asks Android's package parser what is inside the downloaded file before the installer opens.
     * A mismatching signing certificate is exactly the "package conflicts with existing package"
     * failure users hit when CI used to sign with a throw-away debug key.
     */
    @Suppress("DEPRECATION")
    fun verifyDownloadedApk(context: Context, apkFile: File): ApkCheck {
        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val parsed: PackageInfo? = try {
            packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } catch (_: Throwable) {
            null
        }
        val archive = parsed ?: return ApkCheck.Problem("İndirilen dosya geçerli bir Android paketi değil.")

        if (archive.packageName != context.packageName) {
            return ApkCheck.Problem("İndirilen APK bu uygulamaya ait değil (${archive.packageName}).")
        }

        val installed = try {
            packageManager.getPackageInfo(context.packageName, flags)
        } catch (_: Throwable) {
            return ApkCheck.Ok
        }
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) {
            return ApkCheck.Problem("İndirilen sürüm kurulu sürümden yeni değil.")
        }

        return when (SigningCertificates.compare(certificateHashes(installed, history = true), certificateHashes(archive, history = false))) {
            SigningVerdict.MISMATCH -> ApkCheck.Problem(UpdateErrorText.SIGNATURE_MISMATCH)
            SigningVerdict.MATCH, SigningVerdict.UNKNOWN -> ApkCheck.Ok
        }
    }

    @Suppress("DEPRECATION")
    private fun certificateHashes(info: PackageInfo, history: Boolean): Set<String> {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo
            when {
                signing == null -> null
                signing.hasMultipleSigners() -> signing.apkContentsSigners
                // The installed app may have rotated keys; any certificate in its lineage is accepted.
                history -> signing.signingCertificateHistory
                else -> signing.apkContentsSigners
            }
        } else {
            info.signatures
        }
        if (signatures == null) return emptySet()
        return signatures.mapTo(HashSet()) { signature ->
            UpdateChecksum.hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()))
        }
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

    private fun openConnection(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "OptikOkuyucu/${BuildConfig.VERSION_NAME}")
        }
}
