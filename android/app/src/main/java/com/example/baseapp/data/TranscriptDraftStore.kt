
package com.example.baseapp.data

import android.content.Context
import org.json.JSONArray

data class TranscriptDraft(
    val title: String,
    val text: String,
    val savedAt: Long,
    val segmentTexts: List<String> = emptyList(),
)

class TranscriptDraftStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        "transcript_drafts",
        Context.MODE_PRIVATE,
    )

    fun load(id: Long): TranscriptDraft? {
        val titleKey = key(id, "title")
        val textKey = key(id, "text")
        val savedAtKey = key(id, "saved_at")
        val segmentsKey = key(id, "segments")

        if (!preferences.contains(textKey)) return null

        val segmentTexts = runCatching {
            val array = JSONArray(
                preferences.getString(segmentsKey, "[]"),
            )
            List(array.length()) { index ->
                array.getString(index)
            }
        }.getOrDefault(emptyList())

        return TranscriptDraft(
            title = preferences.getString(titleKey, "").orEmpty(),
            text = preferences.getString(textKey, "").orEmpty(),
            savedAt = preferences.getLong(savedAtKey, 0L),
            segmentTexts = segmentTexts,
        )
    }

    fun save(
        id: Long,
        title: String,
        text: String,
        segmentTexts: List<String> = emptyList(),
    ) {
        val segments = JSONArray().apply {
            segmentTexts.forEach {
                put(it)
            }
        }

        preferences.edit()
            .putString(key(id, "title"), title)
            .putString(key(id, "text"), text)
            .putString(key(id, "segments"), segments.toString())
            .putLong(key(id, "saved_at"), System.currentTimeMillis())
            .apply()
    }

    fun clear(id: Long) {
        preferences.edit()
            .remove(key(id, "title"))
            .remove(key(id, "text"))
            .remove(key(id, "saved_at"))
            .remove(key(id, "segments"))
            .apply()
    }

    private fun key(id: Long, suffix: String): String =
        "transcript_${id}_$suffix"
}
