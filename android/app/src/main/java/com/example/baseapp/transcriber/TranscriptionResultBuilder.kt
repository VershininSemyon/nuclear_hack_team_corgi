package com.example.baseapp.transcriber

/**
 * Преобразование сырого результата ASR в модели основного приложения.
 *
 * GigaAM не выполняет диаризацию, поэтому создаётся ровно одна реплика
 * «Спикер 1»; её границы берутся из первого и последнего слова.
 */
object TranscriptionResultBuilder {

    const val SINGLE_SPEAKER = "Спикер 1"
    const val LANGUAGE = "ru"

    fun build(
        text: String,
        words: List<WordTimestamp>,
    ): TranscriptionResult {
        val normalizedText = text.trim()
        val fallbackText = words.joinToString(" ") { it.word }
        val finalText = normalizedText.ifEmpty { fallbackText }

        if (words.isEmpty()) {
            return TranscriptionResult(
                text = finalText,
                words = emptyList(),
                segments = emptyList(),
                language = LANGUAGE,
            )
        }

        val segment = TranscriptSegment(
            speaker = SINGLE_SPEAKER,
            startMs = words.first().startMs,
            endMs = words.last().endMs,
            text = finalText,
            words = words,
        )

        return TranscriptionResult(
            text = finalText,
            words = words,
            segments = listOf(segment),
            language = LANGUAGE,
        )
    }
}
