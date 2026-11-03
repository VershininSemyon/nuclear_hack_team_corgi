package com.example.baseapp.transcriber.sherpa

import com.example.baseapp.transcriber.WordTimestamp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Превращает поток токенов sherpa-onnx в слова с таймкодами.
 *
 * GigaAM отдаёт по одному символу (пробел — тоже токен), поэтому слова
 * собираются до пробела. Тайминги токенов приходят в секундах от начала
 * поданного сегмента; [offset] — начало сегмента в исходном файле.
 *
 * Наружу время отдаётся в миллисекундах без промежуточного округления
 * секунд до целого.
 */
internal object WordTimestamps {

    private val whitespace = Regex("\\s+")

    fun fromTokens(
        tokens: Array<String>,
        timestamps: FloatArray,
        durations: FloatArray,
        chunkDuration: Float,
        offset: Float,
        speaker: String? = null,
    ): List<WordTimestamp> {
        if (tokens.isEmpty()) return emptyList()

        val n = tokens.size
        val starts = FloatArray(n)
        val ends = FloatArray(n)

        // Если модель не вернула тайминги, раскладываем длительность сегмента
        // пропорционально длине токенов — лучше, чем нули.
        if (timestamps.any { it > 0f }) {
            for (i in 0 until n) starts[i] = if (i < timestamps.size) timestamps[i] else 0f
        } else {
            val weights = FloatArray(n) { i -> tokenWeight(tokens[i]) }
            val total = weights.sum().coerceAtLeast(1f)
            var acc = 0f
            for (i in 0 until n) {
                starts[i] = acc / total * chunkDuration
                acc += weights[i]
            }
        }
        for (i in 0 until n) ends[i] = if (i < durations.size) durations[i] else 0f

        val words = ArrayList<WordTimestamp>()
        val current = StringBuilder()
        var currentStart = 0f
        var currentEnd = 0f
        var pendingSpace = false

        fun flush() {
            val text = current.toString().trim()
            if (text.isNotEmpty()) {
                val s = currentStart.coerceIn(0f, chunkDuration)
                val e = max(currentEnd, s).coerceAtMost(chunkDuration)
                words.add(
                    WordTimestamp(
                        word = text,
                        startMs = ((offset + s) * 1000f).roundToLong(),
                        endMs = ((offset + e) * 1000f).roundToLong(),
                        speaker = speaker,
                    ),
                )
            }
            current.setLength(0)
            currentStart = 0f
            currentEnd = 0f
        }

        for (i in 0 until n) {
            val raw = tokens[i]
            if (isSpecial(raw)) continue

            val cleaned = normalize(raw)
            val leadingSpace = cleaned.isNotEmpty() && cleaned.first().isWhitespace()
            val trimmed = cleaned.trim()
            if (trimmed.isEmpty()) {
                pendingSpace = true
                continue
            }

            val start = starts[i].coerceIn(0f, chunkDuration)
            val end = tokenEnd(tokens, starts, ends, i, chunkDuration)

            // В одном токене может быть несколько слов ("▁hello▁world").
            val parts = trimmed.split(whitespace).filter { it.isNotEmpty() }
            val weights = parts.map { max(1, it.length) }
            val totalWeight = weights.sum().toFloat().coerceAtLeast(1f)
            var consumed = 0
            var cursor = start

            for ((partIndex, part) in parts.withIndex()) {
                consumed += weights[partIndex]
                val partEnd = if (partIndex == parts.lastIndex) {
                    end
                } else {
                    start + (end - start) * (consumed / totalWeight)
                }
                val partStart = cursor
                cursor = partEnd

                val startsNew = partIndex > 0 ||
                    (partIndex == 0 && (leadingSpace || pendingSpace || current.isEmpty()))

                if (isPunctuationOnly(part)) {
                    // Пунктуацию приклеиваем к предыдущему слову.
                    if (current.isNotEmpty()) {
                        current.append(part)
                        currentEnd = max(currentEnd, partEnd)
                    }
                } else {
                    if (startsNew) flush()
                    if (current.isEmpty()) currentStart = partStart
                    current.append(part)
                    currentEnd = max(currentEnd, partEnd)
                }
            }
            pendingSpace = false
        }
        flush()
        return words
    }

    private fun tokenEnd(
        tokens: Array<String>,
        starts: FloatArray,
        durations: FloatArray,
        index: Int,
        chunkDuration: Float,
    ): Float {
        val start = starts[index]
        if (durations[index] > 0f) {
            return (start + durations[index]).coerceAtMost(chunkDuration)
        }

        val text = normalize(tokens[index]).trim()
        if (isPunctuationOnly(text)) {
            return (start + 0.02f).coerceAtMost(chunkDuration)
        }

        // Следующий неслужебный токен даёт границу текущего. Если его время
        // совпало с текущим, оцениваем конец по длине слова.
        var j = index + 1
        while (j < tokens.size && isSpecial(tokens[j])) j++
        if (j < tokens.size) {
            val next = starts[j]
            if (next > start + 0.001f) return min(next, chunkDuration)
        }
        return (start + 0.06f * max(1, text.length)).coerceAtMost(chunkDuration)
    }

    /** `▁` (SentencePiece/NeMo) и `Ġ` (GPT-2 BPE) — это пробел. */
    private fun normalize(token: String): String =
        token.replace('\u2581', ' ').replace('\u0120', ' ')

    private fun tokenWeight(token: String): Float {
        if (isSpecial(token)) return 0f
        val text = normalize(token).trim()
        if (text.isEmpty()) return 0f
        return max(1, text.length).toFloat()
    }

    /** Служебные токены вида `<blk>`, `<sos/eos>`, `<|ru|>`, `[unk]`. */
    private fun isSpecial(token: String): Boolean {
        val s = token.trim()
        if (s.isEmpty()) return false // пробел — граница слова, не спецтокен
        if (s.length < 2) return false
        return (s.first() == '<' && s.last() == '>') ||
            (s.first() == '[' && s.last() == ']')
    }

    private fun isPunctuationOnly(text: String): Boolean =
        text.isNotEmpty() && text.all { !it.isLetterOrDigit() }
}
