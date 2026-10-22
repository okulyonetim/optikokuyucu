package com.okulyonetim.optikokuyucu.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class UpdateLogicTest {
    private val hash = "6a85ccad36bf533b23b738c5de9a0748820592a37172cb972c2dd64b8e056c84"

    @Test
    fun `asset selector prefers the fixed published name`() {
        val chosen = ReleaseAssetSelector.select(
            listOf(
                ReleaseAsset("notes.txt", "https://x/notes", 10),
                ReleaseAsset("other.apk", "https://x/other", 20),
                ReleaseAsset("Optik-Okuyucu.apk", "https://x/main", 30)
            )
        )
        assertEquals("https://x/main", chosen?.url)
    }

    @Test
    fun `asset selector falls back to any apk and ignores blank urls`() {
        val chosen = ReleaseAssetSelector.select(
            listOf(
                ReleaseAsset("optik-okuyucu.apk", "", 5),
                ReleaseAsset("legacy.APK", "https://x/legacy", 7)
            )
        )
        assertEquals("https://x/legacy", chosen?.url)
        assertNull(ReleaseAssetSelector.select(listOf(ReleaseAsset("a.zip", "https://x/a", 1))))
    }

    @Test
    fun `checksum is read from release notes in common spellings`() {
        assertEquals(hash, UpdateChecksum.fromReleaseNotes("Sürüm\n\nSHA-256: $hash\n"))
        assertEquals(hash, UpdateChecksum.fromReleaseNotes("sha256 = ${hash.uppercase()}"))
        assertNull(UpdateChecksum.fromReleaseNotes("SHA-256: abc123"))
        assertNull(UpdateChecksum.fromReleaseNotes("no checksum here"))
    }

    @Test
    fun `hex encoding is lower case and zero padded`() {
        assertEquals("000fff10", UpdateChecksum.hex(byteArrayOf(0x00, 0x0F, 0xFF.toByte(), 0x10)))
    }

    @Test
    fun `throttle allows first check, clock rollback and elapsed interval only`() {
        val hour = UpdateCheckThrottle.MIN_INTERVAL_MS
        assertTrue(UpdateCheckThrottle.shouldCheck(1_000_000, 0))
        assertFalse(UpdateCheckThrottle.shouldCheck(1_000_000 + hour - 1, 1_000_000))
        assertTrue(UpdateCheckThrottle.shouldCheck(1_000_000 + hour, 1_000_000))
        assertTrue(UpdateCheckThrottle.shouldCheck(500, 1_000_000))
    }

    @Test
    fun `snooze applies only to the same version until it expires`() {
        val now = 10_000L
        assertTrue(UpdateCheckThrottle.isSnoozed(now, "0.20.5", now + 1, "0.20.5"))
        assertFalse(UpdateCheckThrottle.isSnoozed(now, "0.20.5", now + 1, "0.20.6"))
        assertFalse(UpdateCheckThrottle.isSnoozed(now, "0.20.5", now, "0.20.5"))
        assertFalse(UpdateCheckThrottle.isSnoozed(now, null, now + 1, "0.20.5"))
    }

    @Test
    fun `signing comparison distinguishes match, mismatch and unknown`() {
        assertEquals(SigningVerdict.MATCH, SigningCertificates.compare(setOf("a", "b"), setOf("b")))
        assertEquals(SigningVerdict.MISMATCH, SigningCertificates.compare(setOf("a"), setOf("c")))
        assertEquals(SigningVerdict.UNKNOWN, SigningCertificates.compare(emptySet(), setOf("c")))
        assertEquals(SigningVerdict.UNKNOWN, SigningCertificates.compare(setOf("a"), emptySet()))
    }

    @Test
    fun `http failures are explained in plain Turkish`() {
        assertTrue(UpdateErrorText.httpFailure(403, "0").contains("istek sınırı"))
        assertTrue(UpdateErrorText.httpFailure(429, null).contains("istek sınırı"))
        assertFalse(UpdateErrorText.httpFailure(403, "12").contains("istek sınırı"))
        assertTrue(UpdateErrorText.httpFailure(503, null).contains("503"))
        assertTrue(UpdateErrorText.httpFailure(404, null).contains("404"))
    }

    @Test
    fun `network failures map to readable text`() {
        assertEquals("İnternet bağlantısı kurulamadı.", UpdateErrorText.networkFailure(UnknownHostException()))
        assertTrue(UpdateErrorText.networkFailure(SocketTimeoutException()).contains("zaman aşımı"))
        assertNotNull(UpdateErrorText.networkFailure(IllegalStateException()))
    }
}
