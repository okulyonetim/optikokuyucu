package com.okulyonetim.optikokuyucu.school

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenVaultTest {
    /** Reversible fake so the vault logic is tested without the Android Keystore. */
    private class FakeCipher(var failEncrypt: Boolean = false, var failDecrypt: Boolean = false) : SecretCipher {
        override fun encrypt(plain: String): String {
            if (failEncrypt) throw IllegalStateException("keystore unavailable")
            return plain.reversed()
        }

        override fun decrypt(sealed: String): String? {
            if (failDecrypt) throw IllegalStateException("key invalidated")
            return sealed.reversed()
        }
    }

    @Test
    fun `sealed token is prefixed and opens back to the original`() {
        val vault = TokenVault(FakeCipher())
        val sealed = vault.seal("refresh-123")
        assertTrue(sealed.startsWith(TokenVault.PREFIX))
        assertFalse(sealed.contains("refresh-123"))
        val opened = vault.open(sealed)
        assertEquals("refresh-123", opened.value)
        assertFalse(opened.wasPlaintext)
    }

    @Test
    fun `legacy plain text value is returned and flagged for migration`() {
        val opened = TokenVault(FakeCipher()).open("eyJhbGciOi.legacy.token")
        assertEquals("eyJhbGciOi.legacy.token", opened.value)
        assertTrue(opened.wasPlaintext)
    }

    @Test
    fun `empty values stay empty and are never flagged`() {
        val vault = TokenVault(FakeCipher())
        assertEquals("", vault.seal(""))
        val opened = vault.open("")
        assertEquals("", opened.value)
        assertFalse(opened.wasPlaintext)
    }

    @Test
    fun `encryption failure stores nothing instead of falling back to plain text`() {
        val vault = TokenVault(FakeCipher(failEncrypt = true))
        assertEquals("", vault.seal("secret"))
    }

    @Test
    fun `undecryptable value opens as empty so the user must sign in again`() {
        val sealed = TokenVault(FakeCipher()).seal("secret")
        val opened = TokenVault(FakeCipher(failDecrypt = true)).open(sealed)
        assertEquals("", opened.value)
        assertFalse(opened.wasPlaintext)
    }
}
