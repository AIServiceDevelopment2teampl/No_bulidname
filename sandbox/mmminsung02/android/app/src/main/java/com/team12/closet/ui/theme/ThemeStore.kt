package com.team12.closet.ui.theme

import android.content.Context

/** 테마는 라이트/다크 두 가지. 사용자가 고르기 전(null)에는 기기 설정을 따른다 */
enum class AppTheme { LIGHT, DARK }

interface ThemeStore {
    fun load(): AppTheme?

    fun save(theme: AppTheme)
}

class SharedPrefsThemeStore(
    context: Context,
) : ThemeStore {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override fun load(): AppTheme? = prefs.getString(KEY, null)?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }

    override fun save(theme: AppTheme) {
        prefs.edit().putString(KEY, theme.name).apply()
    }

    private companion object {
        const val KEY = "theme"
    }
}

class InMemoryThemeStore(
    private var theme: AppTheme? = null,
) : ThemeStore {
    override fun load() = theme

    override fun save(theme: AppTheme) {
        this.theme = theme
    }
}
