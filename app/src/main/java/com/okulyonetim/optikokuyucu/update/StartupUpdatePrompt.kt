package com.okulyonetim.optikokuyucu.update

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.okulyonetim.optikokuyucu.BuildConfig
import com.okulyonetim.optikokuyucu.storage.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Startup update offer. This replaces the old Java AppUpdateManager, which duplicated the settings
 * card's logic without any verification: it never checked the signature, the size or a checksum, and
 * it hit the unauthenticated GitHub API on every single launch.
 */
internal object StartupUpdatePrompt {
    private const val PREFS = "optik_update"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_SNOOZED_VERSION = "snoozed_version"
    private const val KEY_SNOOZED_UNTIL = "snoozed_until_ms"

    /** True when the shared hourly budget allows another automatic GitHub request. */
    fun shouldAutoCheck(context: Context): Boolean =
        UpdateCheckThrottle.shouldCheck(
            System.currentTimeMillis(),
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_CHECK, 0L)
        )

    /** Records a completed check so the startup prompt and the settings card share one budget. */
    fun recordCheck(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    /** Never blocks or crashes the app: every failure is logged and swallowed. */
    fun checkOnStartup(activity: ComponentActivity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!shouldAutoCheck(activity)) return

        activity.lifecycleScope.launch {
            val update = try {
                AppUpdateManager.checkForUpdate()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLog.warn("Update", "Açılışta güncelleme kontrolü başarısız", error)
                return@launch
            }
            prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
            if (update == null) return@launch

            val snoozed = UpdateCheckThrottle.isSnoozed(
                nowMs = now,
                snoozedVersion = prefs.getString(KEY_SNOOZED_VERSION, null),
                snoozedUntilMs = prefs.getLong(KEY_SNOOZED_UNTIL, 0L),
                candidate = update.versionName
            )
            if (snoozed || activity.isFinishing || activity.isDestroyed) return@launch
            showUpdateDialog(activity, update, prefs)
        }
    }

    private fun showUpdateDialog(activity: ComponentActivity, update: AppUpdateInfo, prefs: SharedPreferences) {
        val notes = update.notes.lineSequence()
            .filterNot { it.contains("SHA-256", ignoreCase = true) || it.contains("SHA256", ignoreCase = true) }
            .joinToString("\n")
            .trim()
            .take(240)
        val message = buildString {
            append("Yeni bir Optik Okuyucu sürümü var.\n\n")
            append("Mevcut sürüm: ${BuildConfig.VERSION_NAME}\n")
            append("Yeni sürüm: ${update.versionName}")
            if (update.sizeBytes > 0L) append(" (%.1f MB)".format(update.sizeBytes / 1_048_576.0))
            if (notes.isNotEmpty()) append("\n\n").append(notes)
        }
        AlertDialog.Builder(activity)
            .setTitle("Uygulama güncellemesi")
            .setMessage(message)
            .setNegativeButton("Daha sonra") { _, _ ->
                prefs.edit()
                    .putString(KEY_SNOOZED_VERSION, update.versionName)
                    .putLong(KEY_SNOOZED_UNTIL, System.currentTimeMillis() + UpdateCheckThrottle.SNOOZE_MS)
                    .apply()
            }
            .setPositiveButton("Güncelle") { _, _ -> beginUpdate(activity, update) }
            .show()
    }

    private fun beginUpdate(activity: ComponentActivity, update: AppUpdateInfo) {
        if (!AppUpdateManager.canInstallPackages(activity)) {
            AlertDialog.Builder(activity)
                .setTitle("Kurulum izni gerekli")
                .setMessage(
                    "Güncellemeyi kurabilmek için bu uygulamaya bilinmeyen uygulama yükleme izni vermeniz " +
                        "gerekiyor. İzni verdikten sonra uygulamaya dönüp tekrar güncelleyin."
                )
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Ayarları Aç") { _, _ ->
                    runCatching { activity.startActivity(AppUpdateManager.unknownSourcesIntent(activity)) }
                }
                .show()
            return
        }

        val density = activity.resources.displayMetrics.density
        val padding = (24 * density).toInt()
        val label = TextView(activity).apply { text = "İndiriliyor…" }
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(label, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(bar, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        var job: Job? = null
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Güncelleme indiriliyor")
            .setView(content)
            .setCancelable(false)
            .setNegativeButton("İptal") { _, _ -> job?.cancel() }
            .show()

        job = activity.lifecycleScope.launch {
            try {
                val apk = AppUpdateManager.downloadApk(activity.applicationContext, update) { done, total ->
                    activity.runOnUiThread {
                        if (total > 0L) {
                            val percent = (done * 100 / total).toInt().coerceIn(0, 100)
                            bar.isIndeterminate = false
                            bar.progress = percent
                            label.text = "İndiriliyor… %$percent"
                        } else {
                            label.text = "İndiriliyor… %.1f MB".format(done / 1_048_576.0)
                        }
                    }
                }
                dialog.dismiss()
                when (val check = AppUpdateManager.verifyDownloadedApk(activity, apk)) {
                    is ApkCheck.Problem -> showMessage(activity, "Güncelleme kurulamıyor", check.message)
                    ApkCheck.Ok -> runCatching { AppUpdateManager.launchInstaller(activity, apk) }
                        .onFailure { showMessage(activity, "Kurulum ekranı açılamadı", it.message ?: "Bilinmeyen hata") }
                }
            } catch (cancelled: CancellationException) {
                dialog.dismiss()
                throw cancelled
            } catch (error: Exception) {
                dialog.dismiss()
                AppLog.warn("Update", "Güncelleme indirilemedi", error)
                showMessage(activity, "Güncelleme indirilemedi", error.message ?: "Bilinmeyen hata")
            }
        }
    }

    private fun showMessage(activity: ComponentActivity, title: String, message: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Tamam", null)
            .show()
    }
}
