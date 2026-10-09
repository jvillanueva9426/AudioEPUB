package com.ferdyan.audioepub.repository

import android.content.Context
import com.ferdyan.audioepub.model.AppSettings
import com.ferdyan.audioepub.model.ReaderThemeMode

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("audioepub_settings", Context.MODE_PRIVATE)

    fun getSettings(): AppSettings {
        val fontSize = prefs.getInt("font_size", 17)
        val themeName = prefs.getString("theme_mode", ReaderThemeMode.SYSTEM.name) ?: ReaderThemeMode.SYSTEM.name
        val themeMode = try {
            ReaderThemeMode.valueOf(themeName)
        } catch (_: Exception) {
            ReaderThemeMode.SYSTEM
        }
        val speechRate = prefs.getFloat("default_speech_rate", 1.0f)
        val autoAdvance = prefs.getBoolean("auto_advance_chapters", true)

        return AppSettings(
            fontSizeSp = fontSize,
            themeMode = themeMode,
            defaultSpeechRate = speechRate,
            autoAdvanceChapters = autoAdvance
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit().apply {
            putInt("font_size", settings.fontSizeSp)
            putString("theme_mode", settings.themeMode.name)
            putFloat("default_speech_rate", settings.defaultSpeechRate)
            putBoolean("auto_advance_chapters", settings.autoAdvanceChapters)
            apply()
        }
    }
}
