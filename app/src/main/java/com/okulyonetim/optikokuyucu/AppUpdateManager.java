package com.okulyonetim.optikokuyucu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Checks GitHub Releases on startup and offers a local APK update. */
public final class AppUpdateManager {
    private static final String RELEASE_API =
            "https://api.github.com/repos/okulyonetim/optikokuyucu/releases/latest";
    private static final String APK_NAME = "optik-okuyucu-guncelleme.apk";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private AppUpdateManager() {}

    public static void checkOnStartup(Activity activity) {
        Context app = activity.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                ReleaseInfo latest = fetchLatestRelease();
                if (latest.build <= BuildConfig.VERSION_CODE || latest.apkUrl.isEmpty()) return;
                activity.runOnUiThread(() -> showUpdateDialog(activity, latest));
            } catch (Exception ignored) {
                // Update checking must never prevent the app from opening.
            }
        });
    }

    private static ReleaseInfo fetchLatestRelease() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(RELEASE_API).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(7000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "OptikOkuyucu-Android");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP " + connection.getResponseCode());
            }
            try (InputStream input = connection.getInputStream()) {
                StringBuilder json = new StringBuilder();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    json.append(new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8));
                }
                JSONObject release = new JSONObject(json.toString());
                int build = parseBuild(release.optString("tag_name", ""));
                String apkUrl = "";
                org.json.JSONArray assets = release.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.optJSONObject(i);
                        if (asset == null) continue;
                        if (asset.optString("name", "").toLowerCase().endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", "");
                            break;
                        }
                    }
                }
                return new ReleaseInfo(build, release.optString("name", "Yeni sürüm"), apkUrl);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static int parseBuild(String tag) {
        String digits = tag.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try { return Integer.parseInt(digits); } catch (Exception e) { return 0; }
    }

    private static void showUpdateDialog(Activity activity, ReleaseInfo release) {
        if (activity.isFinishing() || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;
        String message = "Yeni bir Optik Okuyucu sürümü bulundu.\n\n" +
                release.name + "\n\n" +
                "Mevcut sürüm: " + BuildConfig.VERSION_NAME + "\n" +
                "Yeni sürüm kodu: " + release.build;
        new AlertDialog.Builder(activity)
                .setTitle("Uygulama güncellemesi")
                .setMessage(message)
                .setNegativeButton("Daha sonra", null)
                .setPositiveButton("Güncelle", (dialog, which) -> downloadAndInstall(activity, release.apkUrl))
                .setCancelable(true)
                .show();
    }

    private static void downloadAndInstall(Activity activity, String apkUrl) {
        EXECUTOR.execute(() -> {
            try {
                File updates = new File(activity.getCacheDir(), "updates");
                if (!updates.exists() && !updates.mkdirs()) throw new Exception("Güncelleme klasörü oluşturulamadı");
                File apk = new File(updates, APK_NAME);

                HttpURLConnection connection = (HttpURLConnection) new URL(apkUrl).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "OptikOkuyucu-Android");
                try {
                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        throw new Exception("APK indirilemedi (HTTP " + connection.getResponseCode() + ")");
                    }
                    try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(apk)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                    }
                } finally {
                    connection.disconnect();
                }
                activity.runOnUiThread(() -> install(activity, apk));
            } catch (Exception e) {
                activity.runOnUiThread(() -> Toast.makeText(activity,
                        "Güncelleme indirilemedi: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private static void install(Activity activity, File apk) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity)
                        .setTitle("Kurulum izni gerekli")
                        .setMessage("Güncellemeyi kurabilmek için bu uygulamaya bilinmeyen uygulama yükleme izni vermeniz gerekiyor.")
                        .setNegativeButton("Vazgeç", null)
                        .setPositiveButton("Ayarları Aç", (d, w) -> activity.startActivity(
                                new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:" + activity.getPackageName()))))
                        .show();
                return;
            }

            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Kurulum ekranı açılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static final class ReleaseInfo {
        final int build;
        final String name;
        final String apkUrl;
        ReleaseInfo(int build, String name, String apkUrl) {
            this.build = build;
            this.name = name;
            this.apkUrl = apkUrl;
        }
    }
}
