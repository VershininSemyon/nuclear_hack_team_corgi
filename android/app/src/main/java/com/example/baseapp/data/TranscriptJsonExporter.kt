
package com.example.baseapp.data

import org.json.JSONArray
import org.json.JSONObject

object TranscriptJsonExporter {

    fun export(transcript: StoredTranscript): String {
        val words = JSONArray()

        transcript.words.forEach { word ->
            val item = JSONObject().apply {
                put("word", word.word)
                put("start_ms", word.startMs)
                put("end_ms", word.endMs)
            }

            words.put(item)
        }

        return JSONObject().apply {
            put("transcript", words)
        }.toString(2)
    }
}
