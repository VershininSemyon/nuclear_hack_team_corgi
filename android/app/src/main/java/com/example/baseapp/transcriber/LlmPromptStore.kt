package com.example.baseapp.transcriber

import android.content.Context

/**
 * Системный промпт для локальной LLM.
 *
 * Пользователь может изменить его на главном экране: текст хранится в
 * SharedPreferences и подставляется в каждый запрос к Qwen2.5. Если поле
 * очистить, возвращается [DEFAULT_PROMPT].
 */
object LlmPromptStore {

    const val DEFAULT_PROMPT =
        "Ты — редактор расшифровки русской речи. На вход даётся одна реплика. " +
            "Расставь знаки препинания, заглавные буквы и исправь очевидные ошибки " +
            "распознавания. Не добавляй новую информацию, не меняй смысл и не " +
            "пересказывай. Верни ТОЛЬКО исправленный текст реплики, без кавычек, " +
            "пояснений и markdown."

    private const val PREFS = "llm_prompt"
    private const val KEY = "system_prompt"

    fun load(context: Context): String {
        val value = prefs(context).getString(KEY, null)
        return value?.takeIf { it.isNotBlank() } ?: DEFAULT_PROMPT
    }

    fun save(context: Context, prompt: String) {
        val value = prompt.trim().ifBlank { DEFAULT_PROMPT }
        prefs(context).edit().putString(KEY, value).apply()
    }

    fun reset(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
