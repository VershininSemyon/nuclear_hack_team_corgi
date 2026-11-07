
package com.example.baseapp.data

import org.json.JSONArray
import org.json.JSONObject

object TranscriptJsonExporter {

    fun export(transcript: StoredTranscript): String {
        val segments = JSONArray()

        transcript.segments.forEach { segment ->
            segments.put(
                JSONObject().apply {
                    put("speaker_id", segment.speaker)
                    put("start_ms", segment.startMs)
                    put("end_ms", segment.endMs)
                    put("text", segment.text)
                },
            )
        }

        return JSONObject().apply {
            put("transcript", segments)
        }.toString(2)
    }
}
