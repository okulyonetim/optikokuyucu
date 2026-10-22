package com.okulyonetim.optikokuyucu.school

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.okulyonetim.optikokuyucu.storage.AppLog
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Minimal text-in/text-out cipher so the token handling can be unit-tested without a Keystore. */
internal interface SecretCipher {
    fun encrypt(plain: String): String

    /** Returns null when the value cannot be decrypted (key wiped, restored on another device, corrupt). */
    fun decrypt(sealed: String): String?
}

/**
 * Wraps Firebase ID/refresh tokens before they reach SharedPreferences.
 *
 * Why: a refresh token is a long-lived credential for the teacher's school account. Stored as plain
 * text it is readable from device backups, rooted devices and adb on debuggable builds. The AES key
 * lives in the Android Keystore and never leaves the secure hardware/TEE, so the stored value is
 * useless anywhere else.
 */
internal class TokenVault(private val cipher: SecretCipher) {
    data class Opened(val value: String, val wasPlaintext: Boolean)

    /**
     * Returns the text to persist. If encryption fails the token is NOT stored (empty string): the
     * user simply signs in again next launch, which is better than silently writing it in plain text.
     */
    fun seal(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            PREFIX + cipher.encrypt(plain)
        } catch (error: Exception) {
            AppLog.warn("SessionVault", "Token şifrelenemedi, saklanmayacak", error)
            ""
        }
    }

    /** Values written by older app versions have no prefix; they are returned and flagged for migration. */
    fun open(stored: String): Opened {
        if (stored.isEmpty()) return Opened("", wasPlaintext = false)
        if (!stored.startsWith(PREFIX)) return Opened(stored, wasPlaintext = true)
        val plain = try {
            cipher.decrypt(stored.removePrefix(PREFIX))
        } catch (error: Exception) {
            AppLog.warn("SessionVault", "Token çözülemedi, yeniden giriş gerekecek", error)
            null
        }
        return Opened(plain.orEmpty(), wasPlaintext = false)
    }

    companion object {
        const val PREFIX = "enc1:"
    }
}

internal class AndroidKeystoreCipher(
    private val alias: String = "optik_school_session_v1"
) : SecretCipher {
    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val data = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return encode(iv) + ":" + encode(data)
    }

    override fun decrypt(sealed: String): String? {
        val parts = sealed.split(':')
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val data = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(data), StandardCharsets.UTF_8)
    }

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
