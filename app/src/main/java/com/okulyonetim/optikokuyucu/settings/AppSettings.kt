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
        val incoming = settings.normalized()
        val current = load()
        // Older settings UI constructs AppSettings(schoolName) and therefore supplies default
        // appearance/subjects. Preserve explicit custom values when only the school field changed.
        val legacySchoolOnlyUpdate =
            incoming.schoolName != current.schoolName &&
                incoming.themeMode == AppThemeMode.SYSTEM &&
                incoming.subjects == AppSettings.DEFAULT_SUBJECTS &&
                (current.themeMode != AppThemeMode.SYSTEM || current.subjects != AppSettings.DEFAULT_SUBJECTS)
        val normalized = if (legacySchoolOnlyUpdate) {
            incoming.copy(themeMode = current.themeMode, subjects = current.subjects)
        } else {
            incoming
        }
        check(
            preferences.edit()
                .putString(KEY_SCHOOL_NAME, normalized.schoolName)
                .putString(KEY_THEME_MODE, normalized.themeMode.name)
                .putString(KEY_SUBJECTS, normalized.subjects.joinToString(SUBJECT_SEPARATOR))
                .commit()
        ) { "Ayarlar kaydedilemedi." }
    }

    /** Updates only the school field without resetting appearance or the configured subject list. */
    fun saveSchoolName(schoolName: String) {
        save(load().copy(schoolName = schoolName))
    }

    fun saveThemeMode(themeMode: AppThemeMode) {
        save(load().copy(themeMode = themeMode))
    }

    fun saveSubjects(subjects: List<String>) {
        save(load().copy(subjects = subjects))
    }

    private companion object {
        const val PREFERENCES_NAME = "omr-app-settings"
        const val KEY_SCHOOL_NAME = "school-name"
        const val KEY_THEME_MODE = "theme-mode"
        const val KEY_SUBJECTS = "subjects"
        const val SUBJECT_SEPARATOR = "\u001F"
    }
}
