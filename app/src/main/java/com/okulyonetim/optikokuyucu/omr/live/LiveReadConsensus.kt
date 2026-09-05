package com.okulyonetim.optikokuyucu.omr.live

/**
 * Small temporal consensus gate for live recognition.
 *
 * A result is accepted only after the same complete answer signature is observed on consecutive
 * locked frames. This costs roughly one extra camera frame while protecting against a transient
 * blur, glare stripe or partially moving sheet.
 */
class LiveReadConsensus(
    private val requiredConsecutiveMatches: Int = 2
) {
    init {
        require(requiredConsecutiveMatches >= 2)
    }

    private var lastSignature: String? = null
    private var consecutiveMatches = 0

    fun offer(signature: String): Boolean {
        if (signature.isBlank()) {
            reset()
            return false
        }

        if (signature == lastSignature) {
            consecutiveMatches += 1
        } else {
            lastSignature = signature
            consecutiveMatches = 1
        }
        return consecutiveMatches >= requiredConsecutiveMatches
    }

    fun reset() {
        lastSignature = null
        consecutiveMatches = 0
    }

    fun currentMatches(): Int = consecutiveMatches
}
