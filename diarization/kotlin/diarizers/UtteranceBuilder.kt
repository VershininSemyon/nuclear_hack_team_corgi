package com.nuclearhack.diarization

import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Одна реплика в итоговом транскрипте — ровно то, что уходит в JSON:
 * `speaker_id`, `start_ms`, `end_ms`, `text`.
 */
data class Utterance(
    val speakerId: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
) {
    init {
        require(endMs >= startMs) { "endMs ($endMs) должен быть >= startMs ($startMs)" }
        require(speakerId >= 1) { "speakerId должен быть >= 1" }
    }

    /** Словарь в формате из ТЗ. */
    fun toMap(): Map<String, Any> = linkedMapOf(
        "speaker_id" to speakerId,
        "start_ms" to startMs,
        "end_ms" to endMs,
        "text" to text,
    )
}

/**
 * Итоговый транскрипт с репликами.
 *
 * [toMap] и [toJson] выдают именно тот формат, который ждёт приложение:
 * ```json
 * {"transcript":[{"speaker_id":1,"start_ms":1250,"end_ms":4380,"text":"Добрый день!"}]}
 * ```
 */
data class DiarizedTranscript(
    val transcript: List<Utterance>,
    val speakerCount: Int = transcript.map { it.speakerId }.distinct().size,
) {
    fun toMap(): Map<String, Any> = linkedMapOf(
        "transcript" to transcript.map { it.toMap() },
    )

    fun toJson(): String {
        val sb = StringBuilder(64 + transcript.size * 96)
        sb.append("{\"transcript\":[")
        transcript.forEachIndexed { index, u ->
            if (index > 0) sb.append(',')
            sb.append("{\"speaker_id\":").append(u.speakerId)
            sb.append(",\"start_ms\":").append(u.startMs)
            sb.append(",\"end_ms\":").append(u.endMs)
            sb.append(",\"text\":").append(jsonString(u.text))
            sb.append('}')
        }
        sb.append("]}")
        return sb.toString()
    }

    companion object {
        internal fun jsonString(value: String): String {
            val sb = StringBuilder(value.length + 2)
            sb.append('"')
            for (ch in value) {
                when (ch) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> {
                        if (ch < ' ') {
                            sb.append("\\u")
                            sb.append(ch.code.toString(16).padStart(4, '0'))
                        } else {
                            sb.append(ch)
                        }
                    }
                }
            }
            sb.append('"')
            return sb.toString()
        }
    }
}

/**
 * Склеивает слова в реплики.
 *
 * Новая реплика начинается, когда меняется спикер, когда пауза больше
 * [maxGapSeconds] или когда реплика уже длиннее [maxUtteranceSeconds]
 * (длинный монолог удобнее и для LLM, и для чтения).
 */
object UtteranceBuilder {

    fun build(
        words: List<SpeakerWord>,
        labels: Map<Int, Int>,
        maxGapSeconds: Float = 1.2f,
        maxUtteranceSeconds: Float = 15f,
    ): List<Utterance> {
        require(maxGapSeconds >= 0f) { "maxGapSeconds должен быть >= 0" }
        require(maxUtteranceSeconds > 0f) { "maxUtteranceSeconds должен быть > 0" }

        val sorted = words
            .filter { it.word.isNotBlank() && it.end >= it.start }
            .sortedBy { it.start }
        if (sorted.isEmpty()) return emptyList()

        val result = mutableListOf<Utterance>()
        var speaker = sorted.first().speaker
        var start = sorted.first().start
        var end = sorted.first().end
        val buffer = mutableListOf<String>()

        fun flush() {
            if (buffer.isEmpty()) return
            val label = labels[speaker] ?: (speaker + 1)
            result += Utterance(
                speakerId = label,
                startMs = (start * 1000f).roundToLong().coerceAtLeast(0L),
                endMs = (end * 1000f).roundToLong().coerceAtLeast(0L),
                text = buffer.joinToString(" "),
            )
            buffer.clear()
        }

        for (w in sorted) {
            if (buffer.isEmpty()) {
                speaker = w.speaker
                start = w.start
                end = w.end
                buffer += w.word.trim()
                continue
            }
            val gap = w.start - end
            val duration = w.end - start
            val split = w.speaker != speaker ||
                gap > maxGapSeconds ||
                duration > maxUtteranceSeconds
            if (split) {
                flush()
                speaker = w.speaker
                start = w.start
                end = w.end
            } else {
                end = max(end, w.end)
            }
            buffer += w.word.trim()
        }
        flush()
        return result
    }
}
