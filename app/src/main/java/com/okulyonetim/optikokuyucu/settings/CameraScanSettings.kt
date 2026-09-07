package com.okulyonetim.optikokuyucu.settings

import android.content.Context
import com.okulyonetim.optikokuyucu.omr.bubble.OmrSensitivity

data class CameraScanSettings(
    val sensitivity: OmrSensitivity = OmrSensitivity.NORMAL,
    val showStudentSummary: Boolean = true
)

/** Camera-only preferences kept separate from general application appearance/settings. */
class CameraScanSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): CameraScanSettings {
        val sensitivity = runCatching {
            OmrSensitivity.valueOf(
                preferences.getString(KEY_SENSITIVITY, OmrSensitivity.NORMAL.name).orEmpty()
            )
        }.getOrDefault(OmrSensitivity.NORMAL)
        return CameraScanSettings(
            sensitivity = sensitivity,
            showStudentSummary = preferences.getBoolean(KEY_SHOW_STUDENT_SUMMARY, true)
        )
    }

    fun save(settings: CameraScanSettings) {
        check(
            preferences.edit()
                .putString(KEY_SENSITIVITY, settings.sensitivity.name)
                .putBoolean(KEY_SHOW_STUDENT_SUMMARY, settings.showStudentSummary)
                .commit()
        ) { "Kamera ayarları kaydedilemedi." }
    }

    private companion object {
        const val PREFERENCES_NAME = "omr-camera-settings"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_SHOW_STUDENT_SUMMARY = "show-student-summary"
    }
}
