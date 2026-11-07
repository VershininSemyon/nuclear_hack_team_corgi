
package com.example.baseapp.transcriber

/**
 * Преобразует результат ASR и назначенные спикерам слова
 * в реплики диалога.
 */
object TranscriptionResultBuilder {

    const val SINGLE_SPEAKER = "Спикер 1"
    const val LANGUAGE = "ru"

    /**
     * Максимальная пауза между словами одной реплики.
     *
     * Если пауза больше этого значения, начинается новая реплика,
     * даже если спикер не изменился.
     */
    private const val MAX_GAP_BETWEEN_WORDS_MS = 1_200L

    fun build(
        text: String,
        words: List<WordTimestamp>,
    ): TranscriptionResult {
        val normalizedText = text.trim()
        val sortedWords = words.sortedWith(
            compareBy<WordTimestamp> { it.startMs }
                .thenBy { it.endMs },
        )

        val fallbackText = sortedWords.joinToString(" ") { it.word }
        val finalText = normalizedText.ifEmpty { fallbackText }

        if (sortedWords.isEmpty()) {
            return TranscriptionResult(
                text = finalText,
                words = emptyList(),
                segments = emptyList(),
                language = LANGUAGE,
            )
        }

        val normalizedWords = sortedWords.map { word ->
            word.copy(
                speaker = word.speaker?.takeIf { it.isNotBlank() }
                    ?: SINGLE_SPEAKER,
                startMs = word.startMs.coerceAtLeast(0L),
                endMs = word.endMs.coerceAtLeast(word.startMs),
            )
        }

        val segments = mutableListOf<TranscriptSegment>()
        val currentWords = mutableListOf<WordTimestamp>()

        fun flushSegment() {
            if (currentWords.isEmpty()) return

            val segmentWords = currentWords.toList()
            val speaker = segmentWords.first().speaker ?: SINGLE_SPEAKER

            segments += TranscriptSegment(
                speaker = speaker,
                startMs = segmentWords.minOf { it.startMs },
                endMs = segmentWords.maxOf { it.endMs },
                text = joinWords(segmentWords),
                words = segmentWords,
            )

            currentWords.clear()
        }

        for (word in normalizedWords) {
            val previous = currentWords.lastOrNull()

            val speakerChanged = previous != null &&
                previous.speaker != word.speaker

            val pauseIsTooLong = previous != null &&
                word.startMs - previous.endMs > MAX_GAP_BETWEEN_WORDS_MS

            if (speakerChanged || pauseIsTooLong) {
                flushSegment()
            }

            currentWords += word
        }

        flushSegment()

        return TranscriptionResult(
            text = finalText,
            words = normalizedWords,
            segments = segments,
            language = LANGUAGE,
        )
    }

    /**
     * Собирает слова в текст реплики.
     * Не добавляет пробел перед обычными знаками пунктуации.
     */
    private fun joinWords(words: List<WordTimestamp>): String {
        val result = StringBuilder()

        val punctuationWithoutSpace = setOf(
            '.', ',', '!', '?', ';', ':', '%',
            ')', ']', '}', '»', '…',
        )

        val punctuationAfterSpace = setOf(
            '(', '[', '{', '«',
        )

        for (wordTimestamp in words) {
            val word = wordTimestamp.word.trim()
            if (word.isEmpty()) continue

            val previousChar = result.lastOrNull()
            val firstChar = word.first()

            val shouldAddSpace = result.isNotEmpty() &&
                firstChar !in punctuationWithoutSpace &&
                previousChar !in punctuationAfterSpace

            if (shouldAddSpace) {
                result.append(' ')
            }

            result.append(word)
        }

        return result.toString()
    }
}
