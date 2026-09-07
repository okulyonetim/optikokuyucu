package com.okulyonetim.optikokuyucu.settings

import android.content.Context

/** Persists user-hidden built-in forms without altering the canonical templates used by old exams. */
class ReadyTemplateVisibilityRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hiddenKeys(): Set<String> = preferences.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()

    fun hide(key: String) {
        if (key.isBlank()) return
        preferences.edit().putStringSet(KEY_HIDDEN, hiddenKeys() + key).apply()
    }

    fun restoreAll() {
        preferences.edit().remove(KEY_HIDDEN).apply()
    }

    companion object {
        fun starterKey(id: String, version: Int): String = "starter:$id:$version"
        fun defaultKey(templateId: String, version: Int): String = "default:$templateId:$version"

        private const val PREFS_NAME = "ready_template_visibility"
        private const val KEY_HIDDEN = "hidden_ready_templates"
    }
}
