package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AppLanguage(val code: String) {
    SYSTEM(""),
    ARABIC("ar"),
    ENGLISH("en")
}

class PreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("openpipe_prefs", Context.MODE_PRIVATE)

    private val _themeFlow = MutableStateFlow(loadTheme())
    val themeFlow: StateFlow<AppTheme> = _themeFlow.asStateFlow()

    private val _languageFlow = MutableStateFlow(loadLanguage())
    val languageFlow: StateFlow<AppLanguage> = _languageFlow.asStateFlow()

    private fun loadTheme(): AppTheme {
        val name = prefs.getString(KEY_THEME, AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name
        return try {
            AppTheme.valueOf(name)
        } catch (_: Exception) {
            AppTheme.SYSTEM
        }
    }

    private fun loadLanguage(): AppLanguage {
        val name = prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.name) ?: AppLanguage.SYSTEM.name
        return try {
            AppLanguage.valueOf(name)
        } catch (_: Exception) {
            AppLanguage.SYSTEM
        }
    }

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _themeFlow.value = theme
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
        _languageFlow.value = language
    }

    fun isEffectiveArabic(language: AppLanguage): Boolean {
        return when (language) {
            AppLanguage.ARABIC -> true
            AppLanguage.ENGLISH -> false
            AppLanguage.SYSTEM -> Locale.getDefault().language.startsWith("ar")
        }
    }

    companion object {
        private const val KEY_THEME = "app_theme"
        private const val KEY_LANGUAGE = "app_language"

        @Volatile
        private var instance: PreferencesRepository? = null

        fun getInstance(context: Context): PreferencesRepository {
            return instance ?: synchronized(this) {
                instance ?: PreferencesRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
