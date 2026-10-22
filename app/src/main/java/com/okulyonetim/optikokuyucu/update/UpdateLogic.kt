package com.okulyonetim.optikokuyucu.update

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** One downloadable file attached to a GitHub release. */
internal data class ReleaseAsset(
    val name: String,
    val url: String,
    val sizeBytes: Long
)

internal object ReleaseAssetSelector {
    /** The publish workflow always uploads this fixed name; any other .apk is only a fallback. */
    const val PREFERRED_NAME = "optik-okuyucu.apk"

    fun select(assets: List<ReleaseAsset>): ReleaseAsset? =
        assets.firstOrNull { it.url.isNotBlank() && it.name.equals(PREFERRED_NAME, ignoreCase = true) }
            ?: assets.firstOrNull { it.url.isNotBlank() && it.name.endsWith(".apk", ignoreCase = true) }
}

/**
 * The release notes carry "SHA-256: <hex>" for the uploaded APK. It detects truncated or corrupted
 * downloads (flaky school Wi-Fi, captive portals that return an HTML page). It is not a trust anchor:
 * the notes come from the same release, and Android's signature check remains the security boundary.
 */
internal object UpdateChecksum {
    private val pattern = Regex("""SHA-?256\s*[:=]\s*([0-9a-fA-F]{64})""", RegexOption.IGNORE_CASE)

    fun fromReleaseNotes(notes: String): String? =
        pattern.find(notes)?.groupValues?.get(1)?.lowercase()

    fun hex(bytes: ByteArray): String =
        bytes.joinToString(separator = "") { "%02x".format(it.toInt() and 0xFF) }
}

internal object UpdateCheckThrottle {
    /** The unauthenticated GitHub API allows 60 requests/hour per IP, and a school shares one IP. */
    const val MIN_INTERVAL_MS = 60L * 60L * 1000L
    const val SNOOZE_MS = 24L * 60L * 60L * 1000L

    fun shouldCheck(nowMs: Long, lastCheckMs: Long): Boolean =
        lastCheckMs <= 0L || nowMs < lastCheckMs || nowMs - lastCheckMs >= MIN_INTERVAL_MS

    fun isSnoozed(nowMs: Long, snoozedVersion: String?, snoozedUntilMs: Long, candidate: String): Boolean =
        snoozedVersion != null && snoozedVersion == candidate && nowMs < snoozedUntilMs
}

internal enum class SigningVerdict {
    MATCH,
    MISMATCH,

    /** The platform did not expose certificates, so Android's installer must be the judge. */
    UNKNOWN
}

internal object SigningCertificates {
    /**
     * Android only installs an update when the new APK is signed by (a descendant of) the signer of
     * the installed app. Comparing certificate fingerprints up front turns the installer's vague
     * "package conflicts with an existing package" into an explanation before anything is installed.
     */
    fun compare(installed: Set<String>, candidate: Set<String>): SigningVerdict = when {
        installed.isEmpty() || candidate.isEmpty() -> SigningVerdict.UNKNOWN
        installed.any { it in candidate } -> SigningVerdict.MATCH
        else -> SigningVerdict.MISMATCH
    }
}

internal object UpdateErrorText {
    fun httpFailure(code: Int, rateLimitRemaining: String?): String = when {
        code == 429 || (code == 403 && rateLimitRemaining == "0") ->
            "GitHub istek sınırı doldu. Birkaç dakika sonra tekrar deneyin."
        code == 403 -> "GitHub isteği reddetti (HTTP 403)."
        code in 500..599 -> "GitHub şu an yanıt vermiyor (HTTP $code). Biraz sonra tekrar deneyin."
        else -> "Güncelleme sunucusu HTTP $code yanıtı verdi."
    }

    fun networkFailure(error: Throwable): String = when (error) {
        is UnknownHostException, is ConnectException -> "İnternet bağlantısı kurulamadı."
        is SocketTimeoutException -> "Bağlantı zaman aşımına uğradı. Tekrar deneyin."
        else -> error.message ?: "Bağlantı hatası"
    }

    const val SIGNATURE_MISMATCH =
        "Bu sürüm, kurulu uygulamadan farklı bir anahtarla imzalanmış; Android güncellemeyi reddeder. " +
            "Mevcut uygulamayı kaldırıp son sürümü yeniden yükleyin (cihazdaki yerel veriler silinir, " +
            "önce okul hesabıyla eşitlenenleri kontrol edin)."
}
