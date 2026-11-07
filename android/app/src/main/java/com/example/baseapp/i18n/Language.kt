package com.example.baseapp.i18n

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage { RU, EN }

/**
 * Текущий язык интерфейса.
 *
 * Значение выставляется в MainActivity до композиции, поэтому [tr] можно
 * вызывать не только из композаблов, но и из ViewModel/лямбд.
 */
object AppLocale {
    @Volatile
    var language: AppLanguage = AppLanguage.RU
}

val LocalLanguage = compositionLocalOf { AppLanguage.RU }
val LocalSetLanguage = staticCompositionLocalOf<(AppLanguage) -> Unit> { {} }

/** Перевод строки: русский вариант и английский. */
fun tr(ru: String, en: String): String =
    if (AppLocale.language == AppLanguage.EN) en else ru

/** Локализованное имя спикера, приходящего из ASR как «Спикер N». */
fun localizedSpeakerName(name: String): String =
    if (AppLocale.language == AppLanguage.EN && name.startsWith("Спикер ")) {
        "Speaker " + name.removePrefix("Спикер ")
    } else {
        name
    }

object LanguageStore {
    private const val PREFS = "ui_language"
    private const val KEY = "language"

    fun load(context: Context): AppLanguage {
        val value = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        return if (value == "EN") AppLanguage.EN else AppLanguage.RU
    }

    fun save(context: Context, language: AppLanguage) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language.name)
            .apply()
    }
}
