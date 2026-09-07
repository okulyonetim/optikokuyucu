package com.okulyonetim.optikokuyucu.settings

import android.content.Context

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class AppSettings(
    val schoolName: String = "",
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val subjects: List<String> = DEFAULT_SUBJECTS
) {
    fun normalized(): AppSettings = copy(
        schoolName = schoolName.trim(),
        subjects = subjects
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    )

    companion object {
        val DEFAULT_SUBJECTS = listOf(
            "Türkçe",
            "Matematik",
            "Fen Bilimleri",
            "T.C. İnkılap Tarihi ve Atatürkçülük",
            "Din Kültürü ve Ahlak Bilgisi",
            "Yabancı Dil"
        )
    }
}

/** Device-local application preferences. */
class AppSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val storedSubjects = preferences.getString(KEY_SUBJECTS, null)
        val subjects = storedSubjects
            ?.split(SUBJECT_SEPARATOR)
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?: AppSettings.DEFAULT_SUBJECTS
        val themeMode = runCatching {
            AppThemeMode.valueOf(preferences.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name).orEmpty())
        }.getOrDefault(AppThemeMode.SYSTEM)
        return AppSettings(
            schoolName = preferences.getString(KEY_SCHOOL_NAME, "").orEmpty(),
            themeMode = themeMode,
            subjects = subjects
        ).normalized()
    }

    fun save(settings: AppSettings) {
        val normalized = settings.normalized()
        check(
            preferences.edit()
                .putString(KEY_SCHOOL_NAME, normalized.schoolName)
                .putString(KEY_THEME_MODE, normalized.themeMode.name)
                .putString(KEY_SUBJECTS, normalized.subjects.joinToString(SUBJECT_SEPARATOR))
                .commit()
        ) { "Ayarlar kaydedilemedi." }
    }

    private companion object {
        const val PREFERENCES_NAME = "omr-app-settings"
        const val KEY_SCHOOL_NAME = "school-name"
        const val KEY_THEME_MODE = "theme-mode"
        const val KEY_SUBJECTS = "subjects"
        const val SUBJECT_SEPARATOR = "\u001F"
    }
}
