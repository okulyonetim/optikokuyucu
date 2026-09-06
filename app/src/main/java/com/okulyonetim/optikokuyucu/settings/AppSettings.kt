package com.okulyonetim.optikokuyucu.settings

import android.content.Context

data class AppSettings(
    val schoolName: String = ""
) {
    fun normalized(): AppSettings = copy(schoolName = schoolName.trim())
}

/** Device-local application preferences. */
class AppSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        schoolName = preferences.getString(KEY_SCHOOL_NAME, "").orEmpty()
    ).normalized()

    fun save(settings: AppSettings) {
        val normalized = settings.normalized()
        check(
            preferences.edit()
                .putString(KEY_SCHOOL_NAME, normalized.schoolName)
                .commit()
        ) { "Ayarlar kaydedilemedi." }
    }

    private companion object {
        const val PREFERENCES_NAME = "omr-app-settings"
        const val KEY_SCHOOL_NAME = "school-name"
    }
}
