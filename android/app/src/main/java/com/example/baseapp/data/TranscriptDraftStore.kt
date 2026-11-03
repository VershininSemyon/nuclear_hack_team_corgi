package com.example.baseapp.data

import android.content.Context

data class TranscriptDraft(
    val title: String,
    val text: String,
    val savedAt: Long,
)

/**
 * Черновик редактора на случай случайного выхода/пересоздания процесса.
 *
 * Черновик не заменяет сохранённую расшифровку и не меняет `originalText`.
 * При явном сохранении или отмене он удаляется.
 */
class TranscriptDraftStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        "transcript_drafts",
        Context.MODE_PRIVATE,
    )

    fun load(id: Long): TranscriptDraft? {
        val titleKey = key(id, "title")
        val textKey = key(id, "text")
        val savedAtKey = key(id, "saved_at")
        if (!preferences.contains(textKey)) return null

        return TranscriptDraft(
            title = preferences.getString(titleKey, "").orEmpty(),
            text = preferences.getString(textKey, "").orEmpty(),
            savedAt = preferences.getLong(savedAtKey, 0L),
        )
    }

    fun save(id: Long, title: String, text: String) {
        preferences.edit()
            .putString(key(id, "title"), title)
            .putString(key(id, "text"), text)
            .putLong(key(id, "saved_at"), System.currentTimeMillis())
            .apply()
    }

    fun clear(id: Long) {
        preferences.edit()
            .remove(key(id, "title"))
            .remove(key(id, "text"))
            .remove(key(id, "saved_at"))
            .apply()
    }

    private fun key(id: Long, suffix: String): String = "transcript_${id}_$suffix"
}
