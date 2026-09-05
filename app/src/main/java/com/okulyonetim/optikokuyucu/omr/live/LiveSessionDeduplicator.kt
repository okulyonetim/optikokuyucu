package com.okulyonetim.optikokuyucu.omr.live

/**
 * Session-level duplicate suppression for sheets that leave the camera and are shown again.
 *
 * A fingerprint is created only when a stable student identity exists. Answer-only fingerprints
 * are intentionally rejected because two different students can legitimately have identical
 * answer patterns.
 */
class LiveSessionDeduplicator(
    private val maxEntries: Int = 512
) {
    init {
        require(maxEntries >= 8)
    }

    private val fingerprints = LinkedHashSet<String>()

    /** Returns true when [fingerprint] is new and records it; false for a duplicate. */
    fun registerIfNew(fingerprint: String): Boolean {
        require(fingerprint.isNotBlank())
        if (fingerprint in fingerprints) return false

        fingerprints += fingerprint
        while (fingerprints.size > maxEntries) {
            val oldest = fingerprints.firstOrNull() ?: break
            fingerprints.remove(oldest)
        }
        return true
    }

    fun size(): Int = fingerprints.size

    fun clear() {
        fingerprints.clear()
    }
}

object LiveScanFingerprint {
    fun build(
        templateId: String,
        templateVersion: Int,
        studentNumber: String?,
        answerSignature: String
    ): String? {
        val identity = studentNumber?.trim().orEmpty()
        if (identity.isEmpty()) return null
        require(templateId.isNotBlank())
        require(templateVersion > 0)
        require(answerSignature.isNotBlank())

        return "$templateId@$templateVersion|student:$identity|answers:$answerSignature"
    }
}
