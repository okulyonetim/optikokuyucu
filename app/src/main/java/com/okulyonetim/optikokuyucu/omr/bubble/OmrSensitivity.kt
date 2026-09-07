package com.okulyonetim.optikokuyucu.omr.bubble

/**
 * User-selectable decision sensitivity for live camera reads.
 *
 * NORMAL intentionally preserves the known-good production thresholds exactly. The scorer geometry,
 * canonical rectification and ink measurement do not change between profiles; only the final
 * mark/no-mark decision thresholds are adjusted when the user explicitly chooses another profile.
 */
enum class OmrSensitivity {
    LOW,
    NORMAL,
    HIGH
}

data class OmrDecisionThresholds(
    val minMarkScore: Double,
    val strongMarkScore: Double,
    val doubleMarkScore: Double,
    val doubleGap: Double,
    val confidentGap: Double
)

object OmrSensitivityPolicy {
    fun thresholds(sensitivity: OmrSensitivity): OmrDecisionThresholds = when (sensitivity) {
        OmrSensitivity.LOW -> OmrDecisionThresholds(
            minMarkScore = 0.14,
            strongMarkScore = 0.23,
            doubleMarkScore = 0.13,
            doubleGap = 0.050,
            confidentGap = 0.050
        )
        OmrSensitivity.NORMAL -> OmrDecisionThresholds(
            minMarkScore = 0.12,
            strongMarkScore = 0.20,
            doubleMarkScore = 0.11,
            doubleGap = 0.055,
            confidentGap = 0.045
        )
        OmrSensitivity.HIGH -> OmrDecisionThresholds(
            minMarkScore = 0.09,
            strongMarkScore = 0.18,
            doubleMarkScore = 0.10,
            doubleGap = 0.050,
            confidentGap = 0.035
        )
    }
}
