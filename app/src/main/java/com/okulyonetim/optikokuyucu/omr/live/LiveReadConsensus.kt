package com.okulyonetim.optikokuyucu.omr.live

/**
 * Small temporal consensus gate for live recognition.
 *
 * A result is accepted only after the same complete answer signature is observed on consecutive
 * locked frames. This costs a few extra camera frames while protecting against a transient
 * blur, glare stripe or partially moving sheet.
 *
 * When the optional per-item view is supplied, the gate also remembers which items changed the
 * last time the signature broke, so the UI can tell the teacher exactly which marks flicker
 * (usually a faint or half-erased fill) instead of silently never confirming.
 */
class LiveReadConsensus(
    private val requiredConsecutiveMatches: Int = 2
) {
    init {
        require(requiredConsecutiveMatches >= 2)
    }

    private var lastSignature: String? = null
    private var lastParts: Map<String, String> = emptyMap()
    private var consecutiveMatches = 0
    private var unstable: List<String> = emptyList()

    fun offer(signature: String, parts: Map<String, String> = emptyMap()): Boolean {
        if (signature.isBlank()) {
            reset()
            return false
        }

        if (signature == lastSignature) {
            consecutiveMatches += 1
        } else {
            if (lastSignature != null && parts.isNotEmpty()) {
                unstable = changedKeys(lastParts, parts)
            }
            lastSignature = signature
            lastParts = parts
            consecutiveMatches = 1
        }
        return consecutiveMatches >= requiredConsecutiveMatches
    }

    fun reset() {
        lastSignature = null
        lastParts = emptyMap()
        consecutiveMatches = 0
        unstable = emptyList()
    }

    fun currentMatches(): Int = consecutiveMatches

    /** Items whose reading differed between the two most recent mismatching frames. */
    fun unstableKeys(): List<String> = unstable

    private fun changedKeys(previous: Map<String, String>, current: Map<String, String>): List<String> {
        val changed = LinkedHashSet<String>()
        current.forEach { (key, value) -> if (previous[key] != value) changed += key }
        previous.keys.forEach { key -> if (key !in current) changed += key }
        return changed.take(MAX_REPORTED_KEYS)
    }

    private companion object {
        const val MAX_REPORTED_KEYS = 8
    }
}
