package com.example.baseapp.ui.theme

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/** Текущая тема: true — тёмная. */
val LocalDarkTheme = compositionLocalOf { false }

/** Переключить тему (и сохранить выбор). */
val LocalToggleTheme = staticCompositionLocalOf<() -> Unit> { {} }

object ThemeStore {
    private const val PREFS = "ui_theme"
    private const val KEY = "dark"

    /** null — тема ещё не выбиралась вручную, берём системную. */
    fun load(context: Context): Boolean? {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY)) return null
        return prefs.getBoolean(KEY, false)
    }

    fun save(context: Context, dark: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, dark)
            .apply()
    }
}
